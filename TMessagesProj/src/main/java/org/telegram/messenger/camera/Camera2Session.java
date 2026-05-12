package org.telegram.messenger.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Session {

    private static final int VIDEO_STABILIZATION_OFF = CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF;
    private static final float DEFAULT_MAIN_EQUIVALENT_FOCAL_LENGTH = 26f;
    private static final int ROUND_VIDEO_RECORD_FPS = 30;
    private static RoundVideoCameraOptionsSnapshot roundVideoCameraOptionsSnapshot;

    private boolean isError;
    private boolean isSuccess;
    private boolean isClosed;

    private final CameraManager cameraManager;
    private final boolean isFront;
    public final String cameraId;
    private final String physicalCameraId;
    private CameraCharacteristics cameraCharacteristics;

    private HandlerThread thread;
    private Handler handler;

    private CameraDevice cameraDevice;
    private SurfaceTexture surfaceTexture;
    private CameraCaptureSession captureSession;
    private Surface surface;

    private final CameraDevice.StateCallback cameraStateCallback;
    private final CameraCaptureSession.StateCallback captureStateCallback;
    private CaptureRequest.Builder captureRequestBuilder;
    private Rect sensorSize;
    private float maxZoom = 1f;
    private float minZoom = 1f;
    private float currentZoom = 1f;
    private final boolean zoomRatioSupported;
    private final int preferredVideoStabilizationMode;
    private final boolean opticalStabilizationSupported;
    private final Range<Integer> preferredRecordingFpsRange;
    private final Set<CaptureRequest.Key<?>> availablePhysicalRequestKeys;
    private boolean stabilizationEnabled;

    private final Size previewSize;

    private ImageReader imageReader;

    private long lastTime;

    public static Camera2Session create(boolean front, int viewWidth, int viewHeight) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return null;
        }

        float bestAspectRatio = 0;
        Size bestSize = null;
        String cameraId = null;
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (int i = 0; i < cameraIds.length; ++i) {
                final String id = cameraIds[i];
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics == null) continue;
                if (characteristics.get(CameraCharacteristics.LENS_FACING) != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
                    continue;
                }
                StreamConfigurationMap confMap = (StreamConfigurationMap) characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                float cameraAspectRatio = pixelSize == null ? 0 : (float) pixelSize.getWidth() / pixelSize.getHeight();
                if ((viewWidth / (float) viewHeight >= 1f) != (cameraAspectRatio >= 1f)) {
                    cameraAspectRatio = 1f / cameraAspectRatio;
                }
                if (bestAspectRatio <= 0 || Math.abs((float) viewWidth / viewHeight - bestAspectRatio) > Math.abs((float) viewWidth / viewHeight - cameraAspectRatio)) {
                    if (confMap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Size size = chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false);
                        if (size != null) {
                            bestAspectRatio = cameraAspectRatio;
                            cameraId = id;
                            bestSize = size;
                        }
                    }
                } else {

                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (cameraId == null || bestSize == null) {
            return null;
        }
        return new Camera2Session(context, front, cameraId, null, bestSize);
    }

    public static Camera2Session create(RoundVideoCameraSelection selection) {
        if (selection == null) {
            return null;
        }
        final Context context = ApplicationLoader.applicationContext;
        if (context == null || selection.previewSize == null) {
            return null;
        }
        return new Camera2Session(context, selection.front, selection.cameraId, selection.physicalCameraId, selection.previewSize);
    }

    public static ArrayList<RoundVideoCameraOption> getRoundVideoCameraOptions() {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return new ArrayList<>();
        }
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(getRoundVideoCameraOptionsSnapshot(cameraManager).options);
        } catch (Exception e) {
            FileLog.e(e);
            return new ArrayList<>();
        }
    }

    public static RoundVideoCameraSelection resolveRoundVideoCameraSelection(String selectedKey, int viewWidth, int viewHeight) {
        return resolveRoundVideoCameraSelectionInternal(selectedKey, null, viewWidth, viewHeight);
    }

    public static RoundVideoCameraSelection resolveRoundVideoCameraSelectionForFacing(boolean front, String selectedKey, int viewWidth, int viewHeight) {
        return resolveRoundVideoCameraSelectionForFacing(front, selectedKey, viewWidth, viewHeight, false);
    }

    public static RoundVideoCameraSelection resolveRoundVideoCameraSelectionForFacing(boolean front, String selectedKey, int viewWidth, int viewHeight, boolean preferLogicalBackCamera) {
        return resolveRoundVideoCameraSelectionInternal(selectedKey, front, viewWidth, viewHeight, preferLogicalBackCamera);
    }

    private static RoundVideoCameraSelection resolveRoundVideoCameraSelectionInternal(String selectedKey, Boolean requiredFacing, int viewWidth, int viewHeight) {
        return resolveRoundVideoCameraSelectionInternal(selectedKey, requiredFacing, viewWidth, viewHeight, false);
    }

    private static RoundVideoCameraSelection resolveRoundVideoCameraSelectionInternal(String selectedKey, Boolean requiredFacing, int viewWidth, int viewHeight, boolean preferLogicalBackCamera) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return null;
        }
        try {
            RoundVideoCameraOptionsSnapshot snapshot = getRoundVideoCameraOptionsSnapshot(cameraManager);
            ArrayList<RoundVideoCameraOption> options = snapshot.options;
            if (options.isEmpty()) {
                return null;
            }
            RoundVideoCameraOption selectedOption = findRoundVideoCameraOption(options, selectedKey, requiredFacing);
            if (selectedOption == null) {
                selectedOption = findDefaultRoundVideoCameraOption(options, requiredFacing);
            }
            if (selectedOption == null) {
                return null;
            }

            String resolvedCameraId = selectedOption.cameraId;
            String resolvedPhysicalCameraId = null;
            String previewCameraId = selectedOption.cameraId;
            float initialZoom = 1f;
            boolean canOpenSelectedCameraDirectly = snapshot.availableCameraIds.contains(selectedOption.cameraId);
            if (!selectedOption.front) {
                String logicalCameraId = selectedOption.logicalCameraId;
                if (logicalCameraId == null) {
                    logicalCameraId = findBestLogicalBackCameraId(cameraManager, selectedOption);
                }
                if (preferLogicalBackCamera && logicalCameraId != null) {
                    CameraCharacteristics logicalCharacteristics = cameraManager.getCameraCharacteristics(logicalCameraId);
                    resolvedCameraId = logicalCameraId;
                    previewCameraId = logicalCameraId;
                    initialZoom = clampZoomRatio(logicalCharacteristics, Math.max(0.1f, selectedOption.startZoomRatio));
                } else if (canOpenSelectedCameraDirectly) {
                    resolvedCameraId = selectedOption.cameraId;
                    previewCameraId = selectedOption.cameraId;
                } else if (logicalCameraId != null) {
                    CameraCharacteristics logicalCharacteristics = cameraManager.getCameraCharacteristics(logicalCameraId);
                    resolvedCameraId = logicalCameraId;
                    previewCameraId = logicalCameraId;
                    initialZoom = clampZoomRatio(logicalCharacteristics, Math.max(0.1f, selectedOption.startZoomRatio));
                } else {
                    return null;
                }
            }
            int previewWidth = viewWidth;
            int previewHeight = viewHeight;
            Size previewSize = getPreviewSize(cameraManager, previewCameraId, previewWidth, previewHeight);
            if (previewSize == null && resolvedCameraId != null && !resolvedCameraId.equals(previewCameraId)) {
                previewSize = getPreviewSize(cameraManager, resolvedCameraId, previewWidth, previewHeight);
            }
            if (previewSize == null) {
                return null;
            }
            return new RoundVideoCameraSelection(selectedOption.key, selectedOption.title, selectedOption.front, resolvedCameraId, resolvedPhysicalCameraId, previewSize, initialZoom);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static synchronized RoundVideoCameraOptionsSnapshot getRoundVideoCameraOptionsSnapshot(CameraManager cameraManager) throws Exception {
        if (roundVideoCameraOptionsSnapshot == null) {
            roundVideoCameraOptionsSnapshot = queryRoundVideoCameraOptions(cameraManager);
        }
        return roundVideoCameraOptionsSnapshot;
    }

    private static CameraSelection buildSelection(String id, CameraCharacteristics characteristics, boolean front, int viewWidth, int viewHeight, boolean skipLogicalMultiCamera) {
        if (characteristics == null) {
            return null;
        }
        Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (lensFacing == null || lensFacing != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
            return null;
        }
        if (skipLogicalMultiCamera && isLogicalMultiCamera(characteristics)) {
            return null;
        }
        StreamConfigurationMap confMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (confMap == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        Size previewSize = chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false);
        if (previewSize == null) {
            return null;
        }
        return new CameraSelection(id, previewSize, getAspectDifference(characteristics, viewWidth, viewHeight), getSensorArea(characteristics), getPrimaryModuleScore(characteristics));
    }

    private static RoundVideoCameraOptionsSnapshot queryRoundVideoCameraOptions(CameraManager cameraManager) throws Exception {
        ArrayList<RoundVideoCameraOption> options = new ArrayList<>();
        HashSet<String> availableCameraIds = new HashSet<>();
        HashSet<String> addedCameraIds = new HashSet<>();
        HashMap<String, String> physicalToLogical = new HashMap<>();
        HashMap<String, Float> logicalMainEquivalentFocal = new HashMap<>();

        String[] cameraIds = cameraManager.getCameraIdList();
        availableCameraIds.addAll(Arrays.asList(cameraIds));
        for (String id : cameraIds) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            if (!isLogicalMultiCamera(characteristics)) {
                continue;
            }
            float groupMainEquivalentFocal = DEFAULT_MAIN_EQUIVALENT_FOCAL_LENGTH;
            Float bestScore = null;
            for (String physicalId : characteristics.getPhysicalCameraIds()) {
                try {
                    CameraCharacteristics physicalCharacteristics = cameraManager.getCameraCharacteristics(physicalId);
                    Integer lensFacing = physicalCharacteristics.get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing == null || lensFacing != CameraCharacteristics.LENS_FACING_BACK) {
                        continue;
                    }
                    float equivalentFocalLength = getEquivalentOrRepresentativeFocalLength(physicalCharacteristics);
                    float score = Math.abs(equivalentFocalLength - DEFAULT_MAIN_EQUIVALENT_FOCAL_LENGTH);
                    if (bestScore == null || score < bestScore) {
                        bestScore = score;
                        groupMainEquivalentFocal = equivalentFocalLength > 0f ? equivalentFocalLength : DEFAULT_MAIN_EQUIVALENT_FOCAL_LENGTH;
                    }
                    physicalToLogical.put(physicalId, id);
                } catch (Exception ignore) {

                }
            }
            logicalMainEquivalentFocal.put(id, groupMainEquivalentFocal);
        }

        for (String physicalId : physicalToLogical.keySet()) {
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(physicalId);
                String logicalCameraId = physicalToLogical.get(physicalId);
                Float mainEquivalentFocal = logicalMainEquivalentFocal.get(logicalCameraId);
                RoundVideoCameraOption option = buildRoundVideoCameraOption(physicalId, characteristics, logicalCameraId, mainEquivalentFocal != null ? mainEquivalentFocal : DEFAULT_MAIN_EQUIVALENT_FOCAL_LENGTH);
                if (option != null && addedCameraIds.add(option.cameraId)) {
                    options.add(option);
                }
            } catch (Exception ignore) {

            }
        }

        for (String id : cameraIds) {
            if (addedCameraIds.contains(id)) {
                continue;
            }
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            if (isLogicalMultiCamera(characteristics)) {
                continue;
            }
            RoundVideoCameraOption option = buildRoundVideoCameraOption(id, characteristics, null, DEFAULT_MAIN_EQUIVALENT_FOCAL_LENGTH);
            if (option != null && addedCameraIds.add(option.cameraId)) {
                options.add(option);
            }
        }

        options.sort((first, second) -> {
            if (first.front != second.front) {
                return first.front ? -1 : 1;
            }
            if (!first.front && first.moduleOrder != second.moduleOrder) {
                return Integer.compare(first.moduleOrder, second.moduleOrder);
            }
            if (first.aspectDifference != second.aspectDifference) {
                return Float.compare(first.aspectDifference, second.aspectDifference);
            }
            return Double.compare(second.sensorArea, first.sensorArea);
        });

        HashMap<String, Integer> totals = new HashMap<>();
        for (RoundVideoCameraOption option : options) {
            totals.put(option.title, totals.getOrDefault(option.title, 0) + 1);
        }
        HashMap<String, Integer> indexes = new HashMap<>();
        ArrayList<RoundVideoCameraOption> normalized = new ArrayList<>(options.size());
        for (RoundVideoCameraOption option : options) {
            String title = option.title;
            if (totals.getOrDefault(title, 0) > 1) {
                int index = indexes.getOrDefault(title, 0) + 1;
                indexes.put(title, index);
                title = title + " " + index;
            }
            normalized.add(option.withTitle(title));
        }
        return new RoundVideoCameraOptionsSnapshot(normalized, availableCameraIds);
    }

    private static RoundVideoCameraOption buildRoundVideoCameraOption(String cameraId, CameraCharacteristics characteristics, String logicalCameraId, float mainEquivalentFocalLength) {
        if (characteristics == null) {
            return null;
        }
        Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (lensFacing == null || (lensFacing != CameraCharacteristics.LENS_FACING_FRONT && lensFacing != CameraCharacteristics.LENS_FACING_BACK)) {
            return null;
        }
        StreamConfigurationMap confMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (confMap == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M || confMap.getOutputSizes(SurfaceTexture.class) == null) {
            return null;
        }

        boolean front = lensFacing == CameraCharacteristics.LENS_FACING_FRONT;
        float equivalentFocalLength = getEquivalentOrRepresentativeFocalLength(characteristics);
        float referenceFocalLength = mainEquivalentFocalLength > 0f ? mainEquivalentFocalLength : DEFAULT_MAIN_EQUIVALENT_FOCAL_LENGTH;
        float startZoomRatio = !front && equivalentFocalLength > 0f ? equivalentFocalLength / referenceFocalLength : 1f;
        if (!Float.isFinite(startZoomRatio) || startZoomRatio <= 0f) {
            startZoomRatio = 1f;
        }
        float aspectDifference = getAspectDifference(characteristics, 1, 1);
        double sensorArea = getSensorArea(characteristics);
        int moduleOrder = front ? 0 : getRoundVideoModuleOrder(startZoomRatio);
        return new RoundVideoCameraOption(
                (front ? "front:" : "rear:") + cameraId,
                cameraId,
                logicalCameraId,
                front,
                buildRoundVideoCameraTitle(front, startZoomRatio),
                startZoomRatio,
                equivalentFocalLength,
                aspectDifference,
                sensorArea,
                moduleOrder
        );
    }

    private static RoundVideoCameraOption findRoundVideoCameraOption(ArrayList<RoundVideoCameraOption> options, String selectedKey, Boolean requiredFacing) {
        if (selectedKey == null || selectedKey.isEmpty()) {
            return null;
        }
        for (RoundVideoCameraOption option : options) {
            if (option.key.equals(selectedKey) && (requiredFacing == null || option.front == requiredFacing)) {
                return option;
            }
        }
        return null;
    }

    private static RoundVideoCameraOption findDefaultRoundVideoCameraOption(ArrayList<RoundVideoCameraOption> options, Boolean requiredFacing) {
        if (options.isEmpty()) {
            return null;
        }
        for (RoundVideoCameraOption option : options) {
            if (requiredFacing == null || option.front == requiredFacing) {
                return option;
            }
        }
        return options.get(0);
    }

    private static String buildRoundVideoCameraTitle(boolean front, float zoomRatio) {
        String baseTitle = LocaleController.getString(front ? R.string.VideoMessagesFrontCamera : R.string.VideoMessagesRearCamera);
        if (front) {
            return baseTitle;
        }
        String suffix;
        if (zoomRatio <= 0.75f) {
            suffix = LocaleController.getString(R.string.VideoMessagesCameraModuleUltraWide);
        } else if (zoomRatio >= 1.75f) {
            suffix = LocaleController.getString(R.string.VideoMessagesCameraModuleTelephoto);
        } else if (Math.abs(zoomRatio - 1f) <= 0.25f) {
            suffix = LocaleController.getString(R.string.VideoMessagesCameraModuleMain);
        } else {
            suffix = String.format(Locale.US, "%.1fx", zoomRatio);
        }
        return baseTitle + " · " + suffix;
    }

    private static int getRoundVideoModuleOrder(float zoomRatio) {
        if (zoomRatio <= 0.75f) {
            return 1;
        }
        if (Math.abs(zoomRatio - 1f) <= 0.25f) {
            return 0;
        }
        if (zoomRatio >= 1.75f) {
            return 2;
        }
        return 3;
    }

    private static float getEquivalentOrRepresentativeFocalLength(CameraCharacteristics characteristics) {
        float equivalentFocalLength = getEquivalentFocalLength(characteristics);
        if (equivalentFocalLength > 0f) {
            return equivalentFocalLength;
        }
        return getRepresentativeFocalLength(characteristics);
    }

    private static String findBestLogicalBackCameraId(CameraManager cameraManager, RoundVideoCameraOption option) throws Exception {
        String bestCameraId = null;
        float bestScore = Float.MAX_VALUE;
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            if (characteristics == null || !isLogicalMultiCamera(characteristics)) {
                continue;
            }
            Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (lensFacing == null || lensFacing != CameraCharacteristics.LENS_FACING_BACK) {
                continue;
            }
            Range<Float> zoomRatioRange = getZoomRatioRange(characteristics);
            if (zoomRatioRange == null) {
                continue;
            }
            float targetZoomRatio = Math.max(0.1f, option.startZoomRatio);
            float clampedZoomRatio = clampZoomRatio(characteristics, targetZoomRatio);
            float score = Math.abs(targetZoomRatio - clampedZoomRatio) * 1000f - (zoomRatioRange.getUpper() - zoomRatioRange.getLower());
            if (bestCameraId == null || score < bestScore) {
                bestCameraId = id;
                bestScore = score;
            }
        }
        return bestCameraId;
    }

    private static Size getPreviewSize(CameraManager cameraManager, String cameraId, int viewWidth, int viewHeight) throws Exception {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        if (characteristics == null) {
            return null;
        }
        StreamConfigurationMap confMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (confMap == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M || confMap.getOutputSizes(SurfaceTexture.class) == null) {
            return null;
        }
        return chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false);
    }

    private static boolean isLogicalMultiCamera(CameraCharacteristics characteristics) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && hasCapability(characteristics, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
                && characteristics.getPhysicalCameraIds() != null
                && !characteristics.getPhysicalCameraIds().isEmpty();
    }

    private static boolean hasCapability(CameraCharacteristics characteristics, int capability) {
        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (capabilities == null) {
            return false;
        }
        for (int currentCapability : capabilities) {
            if (currentCapability == capability) {
                return true;
            }
        }
        return false;
    }

    private static float getAspectDifference(CameraCharacteristics characteristics, int viewWidth, int viewHeight) {
        Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        if (pixelSize == null) {
            return Float.MAX_VALUE;
        }
        float cameraAspectRatio = (float) pixelSize.getWidth() / pixelSize.getHeight();
        if ((viewWidth / (float) viewHeight >= 1f) != (cameraAspectRatio >= 1f)) {
            cameraAspectRatio = 1f / cameraAspectRatio;
        }
        return Math.abs((float) viewWidth / viewHeight - cameraAspectRatio);
    }

    private static double getSensorArea(CameraCharacteristics characteristics) {
        SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (physicalSize != null) {
            return physicalSize.getWidth() * physicalSize.getHeight();
        }
        Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        if (pixelSize != null) {
            return (double) pixelSize.getWidth() * pixelSize.getHeight();
        }
        return 0;
    }

    private static float getPrimaryModuleScore(CameraCharacteristics characteristics) {
        float equivalentFocalLength = getEquivalentFocalLength(characteristics);
        if (equivalentFocalLength > 0f) {
            return Math.abs(equivalentFocalLength - 26f);
        }
        float focalLength = getRepresentativeFocalLength(characteristics);
        if (focalLength > 0f) {
            return Math.abs(focalLength - 4.5f);
        }
        return Float.MAX_VALUE;
    }

    private static float getEquivalentFocalLength(CameraCharacteristics characteristics) {
        float focalLength = getRepresentativeFocalLength(characteristics);
        SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (focalLength <= 0f || physicalSize == null) {
            return -1f;
        }
        double diagonal = Math.hypot(physicalSize.getWidth(), physicalSize.getHeight());
        if (diagonal <= 0d) {
            return -1f;
        }
        return (float) (focalLength * 43.266615305567875d / diagonal);
    }

    private static float getRepresentativeFocalLength(CameraCharacteristics characteristics) {
        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (focalLengths == null || focalLengths.length == 0) {
            return -1f;
        }
        float[] sorted = Arrays.copyOf(focalLengths, focalLengths.length);
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static class CameraSelection {
        private final String cameraId;
        private final Size previewSize;
        private final float aspectDifference;
        private final double sensorArea;
        private final float primaryModuleScore;

        private CameraSelection(String cameraId, Size previewSize, float aspectDifference, double sensorArea, float primaryModuleScore) {
            this.cameraId = cameraId;
            this.previewSize = previewSize;
            this.aspectDifference = aspectDifference;
            this.sensorArea = sensorArea;
            this.primaryModuleScore = primaryModuleScore;
        }
    }

    private static class RoundVideoCameraOptionsSnapshot {
        private final ArrayList<RoundVideoCameraOption> options;
        private final HashSet<String> availableCameraIds;

        private RoundVideoCameraOptionsSnapshot(ArrayList<RoundVideoCameraOption> options, HashSet<String> availableCameraIds) {
            this.options = options;
            this.availableCameraIds = availableCameraIds;
        }
    }

    public static boolean isRoundVideoStabilizationSupported(boolean front) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return false;
        }
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics == null) {
                    continue;
                }
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing == null || lensFacing != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
                    continue;
                }
                return getPreferredVideoStabilizationMode(characteristics) != VIDEO_STABILIZATION_OFF || isOpticalStabilizationSupported(characteristics);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private Camera2Session(Context context, boolean isFront, String cameraId, String physicalCameraId, Size size) {
        thread = new HandlerThread("tg_camera2");
        thread.start();
        handler = new Handler(thread.getLooper());

        cameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Camera2Session.this.cameraDevice = camera;
                Camera2Session.this.lastTime = System.currentTimeMillis();
                FileLog.d("Camera2Session camera #" + cameraId + " opened");
                checkOpen();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                Camera2Session.this.cameraDevice = camera;
                FileLog.d("Camera2Session camera #" + cameraId + " disconnected");
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Camera2Session.this.cameraDevice = camera;
                FileLog.e("Camera2Session camera #" + cameraId + " received " + error + " error");
                AndroidUtilities.runOnUIThread(() -> {
                    isError = true;
                });
            }
        };

        captureStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                captureSession = session;
                FileLog.e("Camera2Session camera #" + cameraId + " capture session configured");
                Camera2Session.this.lastTime = System.currentTimeMillis();
                try {
                    updateCaptureRequest();
                    AndroidUtilities.runOnUIThread(() -> {
                        isSuccess = true;
                        if (doneCallback != null) {
                            doneCallback.run();
                            doneCallback = null;
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                captureSession = session;
                FileLog.e("Camera2Session camera #" + cameraId + " capture session failed to configure");
                AndroidUtilities.runOnUIThread(() -> {
                    isError = true;
                });
            }
        };

        this.isFront = isFront;
        this.cameraId = cameraId;
        this.physicalCameraId = physicalCameraId;
        this.previewSize = size;
        this.lastTime = System.currentTimeMillis();
        this.imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 1);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        boolean zoomRatioSupported = false;
        int preferredVideoStabilizationMode = VIDEO_STABILIZATION_OFF;
        boolean opticalStabilizationSupported = false;
        Range<Integer> preferredRecordingFpsRange = null;
        Set<CaptureRequest.Key<?>> availablePhysicalRequestKeys = null;
        float minZoom = 1f;
        try {
            CameraCharacteristics openedCameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            CameraCharacteristics zoomCameraCharacteristics = physicalCameraId != null ? cameraManager.getCameraCharacteristics(physicalCameraId) : openedCameraCharacteristics;
            cameraCharacteristics = openedCameraCharacteristics;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalCameraId != null) {
                List<CaptureRequest.Key<?>> physicalRequestKeys = openedCameraCharacteristics.getAvailablePhysicalCameraRequestKeys();
                if (physicalRequestKeys != null && !physicalRequestKeys.isEmpty()) {
                    availablePhysicalRequestKeys = new HashSet<>(physicalRequestKeys);
                }
            }
            sensorSize = zoomCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Range<Float> zoomRatioRange = getZoomRatioRange(zoomCameraCharacteristics);
            boolean physicalZoomRatioSupported = physicalCameraId != null
                    && availablePhysicalRequestKeys != null
                    && availablePhysicalRequestKeys.contains(CaptureRequest.CONTROL_ZOOM_RATIO);
            if (zoomRatioRange != null && (physicalCameraId == null || physicalZoomRatioSupported)) {
                zoomRatioSupported = true;
                minZoom = Math.min(zoomRatioRange.getLower(), 1f);
                maxZoom = Math.max(zoomRatioRange.getUpper(), 1f);
            } else {
                final Float value = zoomCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                maxZoom = (value == null || value < 1f) ? 1f : value;
            }
            preferredVideoStabilizationMode = getPreferredVideoStabilizationMode(openedCameraCharacteristics);
            if (preferredVideoStabilizationMode == VIDEO_STABILIZATION_OFF && zoomCameraCharacteristics != openedCameraCharacteristics) {
                preferredVideoStabilizationMode = getPreferredVideoStabilizationMode(zoomCameraCharacteristics);
            }
            opticalStabilizationSupported = isOpticalStabilizationSupported(openedCameraCharacteristics) || isOpticalStabilizationSupported(zoomCameraCharacteristics);
            preferredRecordingFpsRange = getPreferredRecordingFpsRange(openedCameraCharacteristics);
            if (preferredRecordingFpsRange == null && zoomCameraCharacteristics != openedCameraCharacteristics) {
                preferredRecordingFpsRange = getPreferredRecordingFpsRange(zoomCameraCharacteristics);
            }
            cameraManager.openCamera(cameraId, cameraStateCallback, handler);
        } catch (Exception e) {
            FileLog.e(e);
            AndroidUtilities.runOnUIThread(() -> {
                isError = true;
            });
        }
        this.minZoom = minZoom;
        this.zoomRatioSupported = zoomRatioSupported;
        this.preferredVideoStabilizationMode = preferredVideoStabilizationMode;
        this.opticalStabilizationSupported = opticalStabilizationSupported;
        this.preferredRecordingFpsRange = preferredRecordingFpsRange;
        this.availablePhysicalRequestKeys = availablePhysicalRequestKeys;
    }

    private Runnable doneCallback;
    public void whenDone(Runnable doneCallback) {
        if (isInitiated()) {
            doneCallback.run();
            this.doneCallback = null;
        } else {
            this.doneCallback = doneCallback;
        }
    }

    public void open(SurfaceTexture surfaceTexture) {
        handler.post(() -> {
            this.surfaceTexture = surfaceTexture;
            if (surfaceTexture != null) {
                surfaceTexture.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
            }
            checkOpen();
        });
    }

    private boolean opened = false;
    private void checkOpen() {
        if (opened) return;
        if (surfaceTexture == null || cameraDevice == null) return;
        opened = true;

        surface = new Surface(surfaceTexture);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && physicalCameraId != null) {
                ArrayList<OutputConfiguration> outputConfigurations = new ArrayList<>();
                OutputConfiguration previewOutput = new OutputConfiguration(surface);
                previewOutput.setPhysicalCameraId(physicalCameraId);
                outputConfigurations.add(previewOutput);
                OutputConfiguration imageOutput = new OutputConfiguration(imageReader.getSurface());
                imageOutput.setPhysicalCameraId(physicalCameraId);
                outputConfigurations.add(imageOutput);
                cameraDevice.createCaptureSessionByOutputConfigurations(outputConfigurations, captureStateCallback, handler);
            } else {
                ArrayList<Surface> surfaces = new ArrayList<>();
                surfaces.add(surface);
                surfaces.add(imageReader.getSurface());
                cameraDevice.createCaptureSession(surfaces, captureStateCallback, null);
            }
        } catch (Exception e) {
            FileLog.e(e);
            AndroidUtilities.runOnUIThread(() -> {
                isError = true;
            });
        }
    }

    public boolean isInitiated() {
        return !isError && isSuccess && !isClosed;
    }

    public int getDisplayOrientation() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int displayOrientation;
            if (isFront) {
                displayOrientation = (sensorOrientation + degrees) % 360;
                displayOrientation = (360 - displayOrientation) % 360; // compensate the mirror
            } else { // back-facing
                displayOrientation = (sensorOrientation - degrees + 360) % 360;
            }
            return displayOrientation;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    private int getJpegOrientation() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int jpegOrientation;
            if (isFront) {
                jpegOrientation = (sensorOrientation + degrees) % 360;
                jpegOrientation = (360 - jpegOrientation) % 360; // compensate the mirror
            } else { // back-facing
                jpegOrientation = (sensorOrientation - degrees + 360) % 360;
            }
            return jpegOrientation;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public int getWorldAngle() {
        int displayOrientation = getDisplayOrientation();
        int jpegOrientation = getJpegOrientation();
        int diffOrientation = jpegOrientation - displayOrientation;
        if (diffOrientation < 0) {
            diffOrientation += 360;
        }
        return diffOrientation;
    }

    public int getCurrentOrientation() {
        return getJpegOrientation();
    }

    private final Rect cropRegion = new Rect();
    private boolean cropRegionApplied;
    public void setZoom(float value) {
        if (!isInitiated()) return;
        if (captureRequestBuilder == null || cameraDevice == null || sensorSize == null) return;

        currentZoom = Utilities.clamp(value, maxZoom, minZoom);
        updateCaptureRequest();
    }

    private boolean flashing;
    public void setFlash(boolean flash) {
        if (flashing != flash) {
            flashing = flash;
            updateCaptureRequest();
        }
    }
    public boolean getFlash() {
        return flashing;
    }

    public float getZoom() {
        return currentZoom;
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    public float getMinZoom() {
        return minZoom;
    }

    public int getPreviewWidth() {
        return previewSize.getWidth();
    }

    public int getPreviewHeight() {
        return previewSize.getHeight();
    }

    public void destroy(boolean async) {
        destroy(async, null);
    }

    public void destroy(boolean async, Runnable afterCallback) {
        isClosed = true;
        if (async) {
            handler.post(() -> {
                if (captureSession != null) {
                    captureSession.close();
                    captureSession = null;
                }
                if (cameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
                if (imageReader != null) {
                    imageReader.close();
                    imageReader = null;
                }
                thread.quitSafely();
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        thread.join();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (afterCallback != null) {
                        afterCallback.run();
                    }
                });
            });
        } else {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            thread.quitSafely();
            try {
                thread.join();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (afterCallback != null) {
                AndroidUtilities.runOnUIThread(afterCallback);
            }
        }
    }

    private boolean recordingVideo;
    public void setRecordingVideo(boolean recording) {
        if (recordingVideo != recording) {
            recordingVideo = recording;
            updateCaptureRequest();
        }
    }

    public void setStabilizationEnabled(boolean enabled) {
        if (stabilizationEnabled != enabled) {
            stabilizationEnabled = enabled;
            updateCaptureRequest();
        }
    }

    private boolean scanningBarcode;
    public void setScanningBarcode(boolean scanning) {
        if (scanningBarcode != scanning) {
            scanningBarcode = scanning;
            updateCaptureRequest();
        }
    }

    private boolean nightMode;
    public void setNightMode(boolean enable) {
        if (nightMode != enable) {
            nightMode = enable;
            updateCaptureRequest();
        }
    }

    private void updateCaptureRequest() {
        if (cameraDevice == null || surface == null || captureSession == null) return;
        try {
            int template;
            if (recordingVideo) {
                template = CameraDevice.TEMPLATE_RECORD;
            } else if (scanningBarcode) {
                template = CameraDevice.TEMPLATE_STILL_CAPTURE;
            } else {
                template = CameraDevice.TEMPLATE_PREVIEW;
            }
            captureRequestBuilder = cameraDevice.createCaptureRequest(template);

            if (scanningBarcode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
            } else if (nightMode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, isFront ? CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT : CameraMetadata.CONTROL_SCENE_MODE_NIGHT);
            }

            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, flashing ? (recordingVideo ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_SINGLE) : CaptureRequest.FLASH_MODE_OFF);

            if (recordingVideo) {
                if (preferredRecordingFpsRange != null) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, preferredRecordingFpsRange);
                }
                captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
                applyStabilization(captureRequestBuilder);
            }

            if (zoomRatioSupported) {
                setRequestKey(captureRequestBuilder, CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom, true, false);
            } else if (sensorSize != null) {
                if (currentZoom > 1f && Math.abs(currentZoom - 1f) >= 0.01f) {
                    final int centerX = sensorSize.width() / 2;
                    final int centerY = sensorSize.height() / 2;
                    final int deltaX = (int) ((0.5f * sensorSize.width()) / currentZoom);
                    final int deltaY = (int) ((0.5f * sensorSize.height()) / currentZoom);
                    cropRegion.set(
                            centerX - deltaX,
                            centerY - deltaY,
                            centerX + deltaX,
                            centerY + deltaY
                    );
                    cropRegionApplied = true;
                    setRequestKey(captureRequestBuilder, CaptureRequest.SCALER_CROP_REGION, cropRegion, true, false);
                } else if (cropRegionApplied) {
                    cropRegion.set(sensorSize);
                    cropRegionApplied = false;
                    setRequestKey(captureRequestBuilder, CaptureRequest.SCALER_CROP_REGION, cropRegion, true, false);
                }
            }
            captureRequestBuilder.addTarget(surface);
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
        } catch (Exception e) {
            FileLog.e("Camera2Sessions setRepeatingRequest error in updateCaptureRequest", e);
        }
    }

    public boolean takePicture(final File file, Utilities.Callback<Integer> whenDone) {
        if (cameraDevice == null || captureSession == null) return false;
        try {
            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            final int orientation = getJpegOrientation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        image.close();
                        if (null != output) {
                            try {
                                output.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        if (whenDone != null) {
                            whenDone.run(orientation);
                        }
                    });
                }
            }, null);
            if (scanningBarcode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
            }
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {}, null);
            return true;
        } catch (Exception e) {
            FileLog.e("Camera2Sessions takePicture error", e);
            return false;
        }
    }


    public static Size chooseOptimalSize(Size[] choices, int width, int height, boolean notBigger) {
        List<Size> bigEnoughWithAspectRatio = new ArrayList<>(choices.length);
        List<Size> bigEnough = new ArrayList<>(choices.length);
        int w = width;
        int h = height;
        for (int a = 0; a < choices.length; a++) {
            Size option = choices[a];
            if (notBigger && (option.getHeight() > height || option.getWidth() > width)) {
                continue;
            }
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnoughWithAspectRatio.add(option);
            } else if (option.getHeight() * option.getWidth() <= width * height * 4 && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnoughWithAspectRatio.size() > 0) {
            return Collections.min(bigEnoughWithAspectRatio, new CompareSizesByArea());
        } else if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(Arrays.asList(choices), new CompareSizesByArea());
        }
    }
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private static Range<Float> getZoomRatioRange(CameraCharacteristics characteristics) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || characteristics == null) {
            return null;
        }
        return characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
    }

    private static boolean isZoomRatioSupportedFor(CameraCharacteristics characteristics, float targetZoomRatio) {
        Range<Float> zoomRatioRange = getZoomRatioRange(characteristics);
        return zoomRatioRange != null
                && zoomRatioRange.getLower() <= targetZoomRatio + 0.001f
                && zoomRatioRange.getUpper() >= targetZoomRatio - 0.001f;
    }

    private static float clampZoomRatio(CameraCharacteristics characteristics, float targetZoomRatio) {
        Range<Float> zoomRatioRange = getZoomRatioRange(characteristics);
        if (zoomRatioRange == null) {
            return Math.max(1f, targetZoomRatio);
        }
        return Utilities.clamp(targetZoomRatio, zoomRatioRange.getUpper(), zoomRatioRange.getLower());
    }

    public static class RoundVideoCameraOption {
        public final String key;
        public final String cameraId;
        public final String logicalCameraId;
        public final boolean front;
        public final String title;
        public final float startZoomRatio;
        public final float equivalentFocalLength;
        public final float aspectDifference;
        public final double sensorArea;
        public final int moduleOrder;

        private RoundVideoCameraOption(String key, String cameraId, String logicalCameraId, boolean front, String title, float startZoomRatio, float equivalentFocalLength, float aspectDifference, double sensorArea, int moduleOrder) {
            this.key = key;
            this.cameraId = cameraId;
            this.logicalCameraId = logicalCameraId;
            this.front = front;
            this.title = title;
            this.startZoomRatio = startZoomRatio;
            this.equivalentFocalLength = equivalentFocalLength;
            this.aspectDifference = aspectDifference;
            this.sensorArea = sensorArea;
            this.moduleOrder = moduleOrder;
        }

        private RoundVideoCameraOption withTitle(String title) {
            return new RoundVideoCameraOption(key, cameraId, logicalCameraId, front, title, startZoomRatio, equivalentFocalLength, aspectDifference, sensorArea, moduleOrder);
        }
    }

    public static class RoundVideoCameraSelection {
        public final String key;
        public final String title;
        public final boolean front;
        public final String cameraId;
        public final String physicalCameraId;
        public final Size previewSize;
        public final float initialZoom;

        private RoundVideoCameraSelection(String key, String title, boolean front, String cameraId, String physicalCameraId, Size previewSize, float initialZoom) {
            this.key = key;
            this.title = title;
            this.front = front;
            this.cameraId = cameraId;
            this.physicalCameraId = physicalCameraId;
            this.previewSize = previewSize;
            this.initialZoom = initialZoom;
        }
    }

    private static int getPreferredVideoStabilizationMode(CameraCharacteristics characteristics) {
        int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        if (modes == null) {
            return VIDEO_STABILIZATION_OFF;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (int mode : modes) {
                if (mode == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) {
                    return mode;
                }
            }
        }
        for (int mode : modes) {
            if (mode == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) {
                return mode;
            }
        }
        return VIDEO_STABILIZATION_OFF;
    }

    private static Range<Integer> getPreferredRecordingFpsRange(CameraCharacteristics characteristics) {
        Range<Integer>[] ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (ranges == null || ranges.length == 0) {
            return null;
        }
        Range<Integer> exact30 = null;
        Range<Integer> upper30 = null;
        Range<Integer> fallback = null;
        for (Range<Integer> range : ranges) {
            if (range == null) {
                continue;
            }
            if (fallback == null
                    || range.getUpper() < fallback.getUpper()
                    || (range.getUpper().equals(fallback.getUpper()) && range.getLower() > fallback.getLower())) {
                fallback = range;
            }
            if (range.getUpper() != ROUND_VIDEO_RECORD_FPS) {
                continue;
            }
            if (range.getLower() == ROUND_VIDEO_RECORD_FPS) {
                exact30 = range;
                break;
            }
            if (upper30 == null || range.getLower() > upper30.getLower()) {
                upper30 = range;
            }
        }
        if (exact30 != null) {
            return exact30;
        }
        if (upper30 != null) {
            return upper30;
        }
        return fallback;
    }

    private static boolean isOpticalStabilizationSupported(CameraCharacteristics characteristics) {
        int[] modes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        if (modes == null) {
            return false;
        }
        for (int mode : modes) {
            if (mode == CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) {
                return true;
            }
        }
        return false;
    }

    private void applyStabilization(CaptureRequest.Builder requestBuilder) {
        if (stabilizationEnabled) {
            if (preferredVideoStabilizationMode != VIDEO_STABILIZATION_OFF) {
                setRequestKey(requestBuilder, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, preferredVideoStabilizationMode, true, true);
            }
            if (opticalStabilizationSupported) {
                setRequestKey(requestBuilder, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON, true, false);
            }
        } else {
            if (preferredVideoStabilizationMode != VIDEO_STABILIZATION_OFF) {
                setRequestKey(requestBuilder, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, VIDEO_STABILIZATION_OFF, true, true);
            }
            if (opticalStabilizationSupported) {
                setRequestKey(requestBuilder, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF, true, false);
            }
        }
    }

    private <T> void setRequestKey(CaptureRequest.Builder requestBuilder, CaptureRequest.Key<T> key, T value, boolean preferPhysical, boolean alsoSetLogical) {
        requestBuilder.set(key, value);
    }

}
