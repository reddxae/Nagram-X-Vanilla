package org.telegram.messenger.video;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Range;

import org.telegram.messenger.FileLog;

public class RoundVideoEncodingOptions {
    public static final String AVC_MIME_TYPE = "video/avc";
    public static final int DEFAULT_RESOLUTION = 496;
    public static final int DEFAULT_BITRATE = 1150;
    public static final int HIGH_QUALITY_RESOLUTION = 640;
    public static final int HIGH_QUALITY_FALLBACK_RESOLUTION = DEFAULT_RESOLUTION;
    public static final int HIGH_QUALITY_MASTER_BITRATE = 2000;
    public static final int HIGH_QUALITY_MAX_FINAL_BITRATE = 2000;
    public static final int HIGH_QUALITY_MIN_FINAL_BITRATE = 1200;
    public static final long SERVER_FILE_SIZE_LIMIT_BYTES = 9_800_000L;
    private static final int[] RESOLUTION_VALUES = {128, 256, 384, DEFAULT_RESOLUTION, HIGH_QUALITY_RESOLUTION};
    private static final int[] BITRATE_VALUES = {600, 800, DEFAULT_BITRATE, 1400, 1600, 1800, HIGH_QUALITY_MASTER_BITRATE};

    private static Boolean highQualityAvcSupported;

    public static boolean isHighQualityResolution(int resolution) {
        return resolution == HIGH_QUALITY_RESOLUTION;
    }

    public static String getVideoMimeType(int resolution) {
        return AVC_MIME_TYPE;
    }

    public static int[] getResolutionValues() {
        return RESOLUTION_VALUES.clone();
    }

    public static int[] getBitrateValues() {
        return BITRATE_VALUES.clone();
    }

    public static int normalizeResolution(int value) {
        return normalizeEncodingValue(value, RESOLUTION_VALUES);
    }

    public static int normalizeBitrate(int value) {
        return normalizeEncodingValue(value, BITRATE_VALUES);
    }

    public static int getAdaptiveResolution() {
        return isHighQualityAvcSupported() ? HIGH_QUALITY_RESOLUTION : HIGH_QUALITY_FALLBACK_RESOLUTION;
    }

    public static boolean isHighQualityAvcSupported() {
        if (highQualityAvcSupported != null) {
            return highQualityAvcSupported;
        }
        highQualityAvcSupported = checkHighQualityAvcSupported();
        return highQualityAvcSupported;
    }

    private static boolean checkHighQualityAvcSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        return isCodecSupported(AVC_MIME_TYPE, true, true, HIGH_QUALITY_MASTER_BITRATE * 1024);
    }

    private static boolean isCodecSupported(String mimeType, boolean encoder, boolean requireSurfaceInput, int bitrate) {
        try {
            for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (codecInfo == null || codecInfo.isEncoder() != encoder) {
                    continue;
                }
                String[] supportedTypes = codecInfo.getSupportedTypes();
                if (supportedTypes == null) {
                    continue;
                }
                for (String type : supportedTypes) {
                    if (mimeType.equalsIgnoreCase(type) && isHighQualityCodecSupported(codecInfo, mimeType, requireSurfaceInput, bitrate)) {
                        return true;
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    private static boolean isHighQualityCodecSupported(MediaCodecInfo codecInfo, String mimeType, boolean requireSurfaceInput, int bitrate) {
        try {
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
            if (capabilities == null) {
                return false;
            }
            if (requireSurfaceInput) {
                if (capabilities.colorFormats == null) {
                    return false;
                }
                boolean surfaceInputSupported = false;
                for (int colorFormat : capabilities.colorFormats) {
                    if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                        surfaceInputSupported = true;
                        break;
                    }
                }
                if (!surfaceInputSupported) {
                    return false;
                }
            }
            MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
            if (videoCapabilities == null) {
                return false;
            }
            if (!videoCapabilities.isSizeSupported(HIGH_QUALITY_RESOLUTION, HIGH_QUALITY_RESOLUTION)) {
                return false;
            }
            try {
                if (!videoCapabilities.areSizeAndRateSupported(HIGH_QUALITY_RESOLUTION, HIGH_QUALITY_RESOLUTION, 30)) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            Range<Integer> bitrateRange = videoCapabilities.getBitrateRange();
            return bitrate <= 0 || bitrateRange == null || bitrateRange.contains(bitrate);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    private static int normalizeEncodingValue(int value, int[] allowedValues) {
        int bestValue = allowedValues[0];
        long bestDistance = Math.abs((long) value - bestValue);
        for (int i = 1; i < allowedValues.length; i++) {
            long distance = Math.abs((long) value - allowedValues[i]);
            if (distance < bestDistance || (distance == bestDistance && allowedValues[i] > bestValue)) {
                bestValue = allowedValues[i];
                bestDistance = distance;
            }
        }
        return bestValue;
    }

    private RoundVideoEncodingOptions() {
    }
}
