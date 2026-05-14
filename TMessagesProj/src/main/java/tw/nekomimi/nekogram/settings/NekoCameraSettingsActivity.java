package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.video.RoundVideoEncodingOptions;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Stories.recorder.DualCameraView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.WithKey;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings("unused")
public class NekoCameraSettingsActivity extends BaseNekoXSettingsActivity {
    private static final int ITEM_TYPE_VIDEO_NOTE_SLIDER = 1001;
    private static final int ITEM_TYPE_VIDEO_NOTE_SEPARATOR = 1002;

    private final CellGroup cellGroup = new CellGroup(this);
    private final ArrayList<Camera2Session.RoundVideoCameraOption> startCameraOptions = new ArrayList<>();
    private final AbstractConfigCell headerHardwareSettings = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.CameraHardwareSettings)));
    private final AbstractConfigCell camera2ApiRow = cellGroup.appendCell(new Camera2ApiToggleCell());
    private final AbstractConfigCell camera2ApiNoticeRow = cellGroup.appendCell(new AbstractConfigCell() {
        @Override
        public int getType() {
            return CellGroup.ITEM_TYPE_TEXT;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
            cell.setTopPadding(10);
            cell.setBottomPadding(17);
            cell.getTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cell.setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4);
            cell.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            cell.setText(getString(R.string.VideoMessagesCamera2ApiNotice));
        }
    });
    private final AbstractConfigCell startCameraRow = cellGroup.appendCell(new StartCameraDropdownCell());
    private final AbstractConfigCell seamlessSwitchingRow = cellGroup.appendCell(new VideoMessagesToggleCell(NekoConfig.videoMessagesSeamlessSwitching, R.string.VideoMessagesSeamlessSwitching, ToggleType.SEAMLESS_SWITCHING));
    private final AbstractConfigCell stabilizationRow = cellGroup.appendCell(new StabilizationDropdownCell());
    private final AbstractConfigCell headerRecordingOptions = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.CameraRecordingOptions)));
    private final AbstractConfigCell recordFromRow = cellGroup.appendCell(new RecordFromDropdownCell());
    private final AbstractConfigCell saveZoomPositionRow = cellGroup.appendCell(new VideoMessagesToggleCell(NekoConfig.videoMessagesSaveZoomPosition, R.string.VideoMessagesSaveZoomPosition, ToggleType.SAVE_ZOOM_POSITION));
    private final AbstractConfigCell blurCameraSwitchRow = cellGroup.appendCell(new VideoMessagesToggleCell(NekoConfig.videoMessagesBlurCameraSwitch, R.string.VideoMessagesBlurCameraSwitch, ToggleType.BLUR_CAMERA_SWITCH));
    private final AbstractConfigCell dividerRecordingOptions = cellGroup.appendCell(new ConfigCellDivider());
    private final AbstractConfigCell headerQuality = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Quality)));
    private final AbstractConfigCell adaptiveBitrateRow = cellGroup.appendCell(new VideoMessagesToggleCell(NaConfig.INSTANCE.getCameraVideoNoteAdaptiveBitrate(), R.string.VideoMessagesAdaptiveBitrate, ToggleType.ADAPTIVE_BITRATE));
    private final AbstractConfigCell cameraResolutionHeaderRow = cellGroup.appendCell(new ConfigCellHeader("Resolution (in px)"));
    private final AbstractConfigCell cameraResolutionRow = cellGroup.appendCell(new ConfigCellCustom("CameraResolution", ITEM_TYPE_VIDEO_NOTE_SLIDER, true));
    private final AbstractConfigCell cameraQualitySeparatorRow = cellGroup.appendCell(new ConfigCellCustom("CameraQualitySeparator", ITEM_TYPE_VIDEO_NOTE_SEPARATOR, false));
    private final AbstractConfigCell cameraBitrateHeaderRow = cellGroup.appendCell(new ConfigCellHeader("Bitrate (in kbps)"));
    private final AbstractConfigCell cameraBitrateRow = cellGroup.appendCell(new ConfigCellCustom("CameraBitrate", ITEM_TYPE_VIDEO_NOTE_SLIDER, true));
    private final AbstractConfigCell cameraResetDefaultsSeparatorRow = cellGroup.appendCell(new ConfigCellCustom("CameraResetDefaultsSeparator", ITEM_TYPE_VIDEO_NOTE_SEPARATOR, false));
    private final AbstractConfigCell cameraResetDefaultsRow = cellGroup.appendCell(new ResetVideoNoteDefaultsCell());
    private final AbstractConfigCell cameraBitrateSeparatorRow = cellGroup.appendCell(new ConfigCellCustom("CameraBitrateSeparator", ITEM_TYPE_VIDEO_NOTE_SEPARATOR, false));
    private boolean animateCameraVideoMessagesNotice;
    private final AbstractConfigCell cameraVideoMessagesNoticeRow = cellGroup.appendCell(new AbstractConfigCell() {
        @Override
        public int getType() {
            return CellGroup.ITEM_TYPE_TEXT;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
            cell.setTopPadding(10);
            cell.setBottomPadding(17);
            cell.getTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cell.setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4);
            cell.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            setCameraVideoMessagesNoticeText(cell, animateCameraVideoMessagesNotice);
            animateCameraVideoMessagesNotice = false;
        }
    });
    private ListAdapter listAdapter;

    public NekoCameraSettingsActivity() {
        rebuildRows();
        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getTitle());

        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);
        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new BlurredRecyclerView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setChangeDuration(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener((view, position, x, y) -> {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a instanceof Camera2ApiToggleCell toggleCell) {
                toggleCell.onClick((TextCheckCell) view);
            } else if (a instanceof RecordFromDropdownCell dropdownCell) {
                dropdownCell.onClick(view);
            } else if (a instanceof StartCameraDropdownCell dropdownCell) {
                dropdownCell.onClick(view);
            } else if (a instanceof StabilizationDropdownCell dropdownCell) {
                dropdownCell.onClick(view);
            } else if (a instanceof VideoMessagesToggleCell toggleCell) {
                toggleCell.onClick((TextCheckCell) view);
            } else if (a instanceof ResetVideoNoteDefaultsCell resetCell) {
                resetCell.onClick();
            }
        });

        listView.setOnItemLongClickListener((view, position, x, y) -> {
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, NekoCameraSettingsActivity.this, "camera", position);
                return true;
            }
            return false;
        });

        cellGroup.setListAdapter(listView, listAdapter);

        return fragmentView;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void updateRows() {
        getMessagesController().applyCustomRoundVideoEncodingSettings();
        normalizeSelectedStartCamera();
        normalizeStabilizationSelection();
        rebuildRows();
        addRowsToMap(cellGroup);
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        getMessagesController().applyCustomRoundVideoEncodingSettings();
        normalizeSelectedStartCamera();
        normalizeStabilizationSelection();
        rebuildRows();
        addRowsToMap(cellGroup);
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void rebuildRows() {
        cellGroup.rows.clear();
        cellGroup.rows.add(headerHardwareSettings);
        cellGroup.rows.add(camera2ApiRow);
        if (isCamera2Enabled()) {
            cellGroup.rows.add(startCameraRow);
            cellGroup.rows.add(seamlessSwitchingRow);
            cellGroup.rows.add(stabilizationRow);
        }
        cellGroup.rows.add(camera2ApiNoticeRow);
        cellGroup.rows.add(headerRecordingOptions);
        cellGroup.rows.add(recordFromRow);
        cellGroup.rows.add(saveZoomPositionRow);
        cellGroup.rows.add(blurCameraSwitchRow);
        cellGroup.rows.add(dividerRecordingOptions);
        cellGroup.rows.add(headerQuality);
        if (!isAdaptiveVideoNoteEnabled()) {
            cellGroup.rows.add(cameraResolutionHeaderRow);
            cellGroup.rows.add(cameraResolutionRow);
            cellGroup.rows.add(cameraQualitySeparatorRow);
            cellGroup.rows.add(cameraBitrateHeaderRow);
            cellGroup.rows.add(cameraBitrateRow);
            if (!isVideoNoteDefaultQualitySelected()) {
                cellGroup.rows.add(cameraResetDefaultsSeparatorRow);
                cellGroup.rows.add(cameraResetDefaultsRow);
            }
            cellGroup.rows.add(cameraBitrateSeparatorRow);
        }
        cellGroup.rows.add(adaptiveBitrateRow);
        cellGroup.rows.add(cameraVideoMessagesNoticeRow);
    }

    private List<AbstractConfigCell> getCamera2DependentRows() {
        return Arrays.asList(startCameraRow, seamlessSwitchingRow, stabilizationRow);
    }

    private void setCamera2DependentRowsVisible(boolean visible) {
        List<AbstractConfigCell> dependentRows = getCamera2DependentRows();
        boolean currentlyVisible = cellGroup.rows.contains(startCameraRow);
        if (visible == currentlyVisible || listAdapter == null) {
            return;
        }
        int camera2RowIndex = cellGroup.rows.indexOf(camera2ApiRow);
        if (visible) {
            int insertIndex = cellGroup.rows.indexOf(camera2ApiNoticeRow);
            cellGroup.rows.addAll(insertIndex, dependentRows);
            addRowsToMap(cellGroup);
            listAdapter.notifyItemRangeInserted(insertIndex, dependentRows.size());
        } else {
            int removeIndex = cellGroup.rows.indexOf(startCameraRow);
            cellGroup.rows.removeAll(dependentRows);
            addRowsToMap(cellGroup);
            listAdapter.notifyItemRangeRemoved(removeIndex, dependentRows.size());
        }
        if (camera2RowIndex >= 0) {
            listAdapter.notifyItemChanged(camera2RowIndex);
        }
    }

    private List<AbstractConfigCell> getAdaptiveBitrateDependentRows() {
        List<AbstractConfigCell> rows = new ArrayList<>(Arrays.asList(cameraResolutionHeaderRow, cameraResolutionRow, cameraQualitySeparatorRow, cameraBitrateHeaderRow, cameraBitrateRow));
        if (!isVideoNoteDefaultQualitySelected()) {
            rows.add(cameraResetDefaultsSeparatorRow);
            rows.add(cameraResetDefaultsRow);
        }
        rows.add(cameraBitrateSeparatorRow);
        return rows;
    }

    private void setAdaptiveBitrateDependentRowsVisible(boolean visible) {
        List<AbstractConfigCell> dependentRows = getAdaptiveBitrateDependentRows();
        boolean currentlyVisible = cellGroup.rows.contains(cameraResolutionHeaderRow);
        if (visible == currentlyVisible || listAdapter == null) {
            return;
        }
        if (visible) {
            int insertIndex = cellGroup.rows.indexOf(adaptiveBitrateRow);
            cellGroup.rows.addAll(insertIndex, dependentRows);
            addRowsToMap(cellGroup);
            listAdapter.notifyItemRangeInserted(insertIndex, dependentRows.size());
        } else {
            int removeIndex = cellGroup.rows.indexOf(cameraResolutionHeaderRow);
            cellGroup.rows.removeAll(dependentRows);
            addRowsToMap(cellGroup);
            listAdapter.notifyItemRangeRemoved(removeIndex, dependentRows.size());
        }
        int adaptiveRowIndex = cellGroup.rows.indexOf(adaptiveBitrateRow);
        if (adaptiveRowIndex >= 0) {
            listAdapter.notifyItemChanged(adaptiveRowIndex);
        }
        int noticeIndex = cellGroup.rows.indexOf(cameraVideoMessagesNoticeRow);
        if (noticeIndex >= 0) {
            animateCameraVideoMessagesNotice = true;
            listAdapter.notifyItemChanged(noticeIndex);
        }
    }

    private List<AbstractConfigCell> getResetDefaultsRows() {
        return Arrays.asList(cameraResetDefaultsSeparatorRow, cameraResetDefaultsRow);
    }

    private boolean isVideoNoteDefaultQualitySelected() {
        return NaConfig.INSTANCE.getCameraVideoNoteResolution().Int() == RoundVideoEncodingOptions.DEFAULT_RESOLUTION
                && NaConfig.INSTANCE.getCameraVideoNoteBitrate().Int() == RoundVideoEncodingOptions.DEFAULT_BITRATE;
    }

    private void setResetDefaultsRowsVisible(boolean visible) {
        if (isAdaptiveVideoNoteEnabled() || !cellGroup.rows.contains(cameraBitrateRow) || listAdapter == null) {
            return;
        }
        List<AbstractConfigCell> resetRows = getResetDefaultsRows();
        boolean currentlyVisible = cellGroup.rows.contains(cameraResetDefaultsRow);
        if (visible == currentlyVisible) {
            return;
        }
        if (visible) {
            int insertIndex = cellGroup.rows.indexOf(cameraBitrateRow) + 1;
            cellGroup.rows.addAll(insertIndex, resetRows);
            addRowsToMap(cellGroup);
            listAdapter.notifyItemRangeInserted(insertIndex, resetRows.size());
        } else {
            int removeIndex = cellGroup.rows.indexOf(cameraResetDefaultsSeparatorRow);
            cellGroup.rows.removeAll(resetRows);
            addRowsToMap(cellGroup);
            listAdapter.notifyItemRangeRemoved(removeIndex, resetRows.size());
        }
    }

    private void notifyVideoNoteSliderRowsChanged() {
        int resolutionIndex = cellGroup.rows.indexOf(cameraResolutionRow);
        if (resolutionIndex >= 0) {
            listAdapter.notifyItemChanged(resolutionIndex);
        }
        int bitrateIndex = cellGroup.rows.indexOf(cameraBitrateRow);
        if (bitrateIndex >= 0) {
            listAdapter.notifyItemChanged(bitrateIndex);
        }
    }

    private void resetVideoNoteDefaults() {
        if (isVideoNoteDefaultQualitySelected()) {
            return;
        }
        NaConfig.INSTANCE.getCameraVideoNoteResolution().setConfigInt(RoundVideoEncodingOptions.DEFAULT_RESOLUTION);
        NaConfig.INSTANCE.getCameraVideoNoteBitrate().setConfigInt(RoundVideoEncodingOptions.DEFAULT_BITRATE);
        getMessagesController().applyCustomRoundVideoEncodingSettings();
        if (listAdapter != null) {
            notifyVideoNoteSliderRowsChanged();
            setResetDefaultsRowsVisible(false);
        }
    }

    private void setCameraVideoMessagesNoticeText(TextInfoPrivacyCell cell, boolean animated) {
        CharSequence text = getString(isAdaptiveVideoNoteEnabled() ? R.string.CameraVideoMessagesAdaptiveNotice : R.string.CameraVideoMessagesNotice);
        cell.getTextView().animate().cancel();
        if (animated && !TextUtils.isEmpty(cell.getText()) && !TextUtils.equals(cell.getText(), text)) {
            cell.getTextView().animate()
                    .alpha(0f)
                    .translationY(-AndroidUtilities.dp(4))
                    .setDuration(120)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .withEndAction(() -> {
                        cell.setText(text);
                        cell.getTextView().setTranslationY(AndroidUtilities.dp(4));
                        cell.getTextView().animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(180)
                                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                                .start();
                    })
                    .start();
        } else {
            cell.getTextView().setAlpha(1f);
            cell.getTextView().setTranslationY(0f);
            cell.setText(text);
        }
    }

    @Override
    public int getBaseGuid() {
        return 16000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_camera;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Camera);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, TextCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailSettingsCell.class, NotificationsCheckCell.class, SlideChooseView.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        return themeDescriptions;
    }

    private boolean isCamera2Enabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && SharedConfig.isUsingCamera2(currentAccount);
    }

    private void normalizeSelectedStartCamera() {
        startCameraOptions.clear();
        ArrayList<Camera2Session.RoundVideoCameraOption> allOptions = Camera2Session.getRoundVideoCameraOptions();
        for (Camera2Session.RoundVideoCameraOption option : allOptions) {
            if (!option.front) {
                startCameraOptions.add(option);
            }
        }
        if (startCameraOptions.isEmpty()) {
            if (!TextUtils.isEmpty(NekoConfig.videoMessagesStartCamera.String())) {
                NekoConfig.videoMessagesStartCamera.setConfigString("");
            }
            return;
        }
        if (findSelectedStartCameraOption() == null) {
            NekoConfig.videoMessagesStartCamera.setConfigString(startCameraOptions.get(0).key);
        }
    }

    private Camera2Session.RoundVideoCameraOption findSelectedStartCameraOption() {
        String selectedKey = NekoConfig.videoMessagesStartCamera.String();
        for (Camera2Session.RoundVideoCameraOption option : startCameraOptions) {
            if (option.key.equals(selectedKey)) {
                return option;
            }
        }
        return null;
    }

    private boolean isStabilizationSupported(boolean front) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Camera2Session.isRoundVideoStabilizationSupported(front);
    }

    private void setSettingsCellContentAlpha(TextSettingsCell cell, float alpha) {
        cell.setEnabled(true, null);
        cell.setAlpha(1.0f);
        cell.getTextView().setAlpha(alpha);
        cell.getValueTextView().setAlpha(alpha);
        if (cell.getValueImageView() != null) {
            cell.getValueImageView().setAlpha(alpha);
        }
    }

    private void setDetailCellContentAlpha(TextDetailSettingsCell cell, float alpha) {
        cell.setAlpha(1.0f);
        cell.getTextView().setAlpha(alpha);
        cell.getValueTextView().setAlpha(alpha);
    }

    private enum ToggleType {
        SEAMLESS_SWITCHING,
        SAVE_ZOOM_POSITION,
        BLUR_CAMERA_SWITCH,
        ADAPTIVE_BITRATE
    }

    private class RecordFromDropdownCell extends AbstractConfigCell implements WithKey {
        @Override
        public String getKey() {
            return NekoConfig.videoMessagesRecordFrom.getKey();
        }

        @Override
        public int getType() {
            return CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            cell.setTextAndValue(getString(R.string.VideoMessagesRecordFrom), getRecordFromSummary(), cellGroup.needSetDivider(this));
            cell.setEnabled(true, null);
            cell.setAlpha(1.0f);
        }

        private void onClick(View anchor) {
            PopupBuilder builder = new PopupBuilder(anchor);
            ArrayList<String> items = new ArrayList<>(3);
            items.add(getString(R.string.VideoMessagesFrontCamera));
            items.add(getString(R.string.VideoMessagesRearCamera));
            items.add(getString(R.string.VideoMessagesAlwaysAsk));
            builder.setItems(items, (index, str) -> {
                NekoConfig.videoMessagesRecordFrom.setConfigInt(index);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(cellGroup.rows.indexOf(this));
                }
                return Unit.INSTANCE;
            });
            builder.show();
        }
    }

    private class StartCameraDropdownCell extends AbstractConfigCell implements WithKey {
        @Override
        public String getKey() {
            return NekoConfig.videoMessagesStartCamera.getKey();
        }

        @Override
        public int getType() {
            return CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL;
        }

        @Override
        public boolean isEnabled() {
            return isCamera2Enabled() && !startCameraOptions.isEmpty();
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            cell.setCanDisable(true);
            cell.setTextAndValue(getString(R.string.VideoMessagesStartCamera), getStartCameraSummary(), cellGroup.needSetDivider(this));
            cell.setEnabled(isEnabled(), null);
        }

        private void onClick(View anchor) {
            if (!isEnabled()) {
                return;
            }
            PopupBuilder builder = new PopupBuilder(anchor);
            ArrayList<String> items = new ArrayList<>(startCameraOptions.size());
            for (Camera2Session.RoundVideoCameraOption option : startCameraOptions) {
                items.add(formatStartCameraOptionTitle(option.title));
            }
            builder.setItems(items, (index, str) -> {
                NekoConfig.videoMessagesStartCamera.setConfigString(startCameraOptions.get(index).key);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(cellGroup.rows.indexOf(this));
                }
                return Unit.INSTANCE;
            });
            builder.show();
        }
    }

    private class Camera2ApiToggleCell extends AbstractConfigCell implements WithKey {
        @Override
        public String getKey() {
            return "VideoMessagesCamera2Api";
        }

        @Override
        public int getType() {
            return CellGroup.ITEM_TYPE_TEXT_CHECK;
        }

        @Override
        public boolean isEnabled() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            cell.setTextAndCheck(getString(R.string.VideoMessagesCamera2Api), isCamera2Enabled(), isCamera2Enabled(), true);
            cell.setEnabled(isEnabled(), null);
        }

        private void onClick(TextCheckCell cell) {
            if (!isEnabled()) {
                return;
            }
            SharedConfig.toggleUseCamera2(currentAccount);
            normalizeSelectedStartCamera();
            normalizeStabilizationSelection();
            cell.setChecked(isCamera2Enabled());
            setCamera2DependentRowsVisible(isCamera2Enabled());
        }
    }

    private class VideoMessagesToggleCell extends AbstractConfigCell implements WithKey {
        private final ConfigItem bindConfig;
        private final int titleResId;
        private final ToggleType toggleType;

        private VideoMessagesToggleCell(ConfigItem bindConfig, int titleResId, ToggleType toggleType) {
            this.bindConfig = bindConfig;
            this.titleResId = titleResId;
            this.toggleType = toggleType;
        }

        @Override
        public String getKey() {
            return bindConfig.getKey();
        }

        @Override
        public int getType() {
            return CellGroup.ITEM_TYPE_TEXT_CHECK;
        }

        @Override
        public boolean isEnabled() {
            if (toggleType == ToggleType.SEAMLESS_SWITCHING && !isCamera2Enabled()) {
                return false;
            }
            if (toggleType == ToggleType.SEAMLESS_SWITCHING) {
                Context context = getParentActivity() != null ? getParentActivity() : ApplicationLoader.applicationContext;
                return context != null && DualCameraView.roundDualAvailableStatic(context);
            }
            return true;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            String subtitle = getSubtitle();
            if (TextUtils.isEmpty(subtitle)) {
                cell.setTextAndCheck(getString(titleResId), isEnabled() && bindConfig.Bool(), cellGroup.needSetDivider(this), true);
            } else {
                cell.setTextAndValueAndCheck(getString(titleResId), subtitle, isEnabled() && bindConfig.Bool(), true, cellGroup.needSetDivider(this), true);
            }
            cell.setEnabled(isEnabled(), null);
        }

        private void onClick(TextCheckCell cell) {
            if (!isEnabled()) {
                return;
            }
            boolean newValue = bindConfig.toggleConfigBool();
            cell.setChecked(newValue);
            cellGroup.runCallback(bindConfig.getKey(), newValue);
            if (toggleType == ToggleType.ADAPTIVE_BITRATE) {
                getMessagesController().applyCustomRoundVideoEncodingSettings();
                setAdaptiveBitrateDependentRowsVisible(!newValue);
            }
        }

        private String getSubtitle() {
            if (toggleType == ToggleType.SEAMLESS_SWITCHING && !isCamera2Enabled()) {
                return getString(R.string.VideoMessagesCamera2Required);
            }
            if (toggleType == ToggleType.SEAMLESS_SWITCHING) {
                Context context = getParentActivity() != null ? getParentActivity() : ApplicationLoader.applicationContext;
                if (context != null && !DualCameraView.roundDualAvailableStatic(context)) {
                    return getString(R.string.VideoMessagesSeamlessSwitchingUnsupported);
                }
                return getString(R.string.VideoMessagesSeamlessSwitchingDescription);
            }
            if (toggleType == ToggleType.SAVE_ZOOM_POSITION) {
                return getString(R.string.VideoMessagesSaveZoomPositionDescription);
            }
            if (toggleType == ToggleType.BLUR_CAMERA_SWITCH) {
                return getString(R.string.VideoMessagesBlurCameraSwitchDescription);
            }
            return "";
        }
    }

    private class StabilizationDropdownCell extends AbstractConfigCell implements WithKey {
        private static final int FRONT_CAMERA_ID = 0;
        private static final int REAR_CAMERA_ID = 1;

        @Override
        public String getKey() {
            return "VideoMessagesStabilization";
        }

        @Override
        public int getType() {
            return CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL;
        }

        @Override
        public boolean isEnabled() {
            return isCamera2Enabled() && isAnyStabilizationSupported();
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            cell.setCanDisable(true);
            cell.setTextAndValue(
                    getString(R.string.VideoMessagesStabilization),
                    getStabilizationSummary(),
                    cellGroup.needSetDivider(this)
            );
            cell.setEnabled(isEnabled(), null);
        }

        private void onClick(View anchor) {
            if (!isEnabled()) {
                return;
            }
            showStabilizationPopup(anchor);
        }

        private void showStabilizationPopup(View anchor) {
            PopupBuilder builder = new PopupBuilder(anchor);
            ActionBarMenuSubItem frontItem = builder.addSubItem(FRONT_CAMERA_ID, 0, null, getString(R.string.VideoMessagesFrontCamera), false, true);
            ActionBarMenuSubItem rearItem = builder.addSubItem(REAR_CAMERA_ID, 0, null, getString(R.string.VideoMessagesRearCamera), false, true);
            updateStabilizationMenuItem(frontItem, true);
            updateStabilizationMenuItem(rearItem, false);
            builder.setDelegate(id -> {
                if (id == FRONT_CAMERA_ID) {
                    toggleStabilizationCamera(true);
                    updateStabilizationMenuItem(frontItem, true);
                } else if (id == REAR_CAMERA_ID) {
                    toggleStabilizationCamera(false);
                    updateStabilizationMenuItem(rearItem, false);
                }
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(cellGroup.rows.indexOf(stabilizationRow));
                }
            });
            builder.show();
        }
    }

    private void updateStabilizationMenuItem(ActionBarMenuSubItem item, boolean front) {
        boolean supported = isStabilizationSupported(front);
        item.setChecked(isStabilizationEnabledForCamera(front));
        item.setEnabled(supported);
        item.setAlpha(supported ? 1.0f : 0.5f);
    }

    private void toggleStabilizationCamera(boolean front) {
        if (!isStabilizationSupported(front)) {
            return;
        }
        ConfigItem config = getStabilizationConfig(front);
        config.setConfigBool(!config.Bool());
    }

    private ConfigItem getStabilizationConfig(boolean front) {
        return front ? NekoConfig.videoMessagesStabilizationFront : NekoConfig.videoMessagesStabilizationRear;
    }

    private boolean isAnyStabilizationSupported() {
        return isStabilizationSupported(true) || isStabilizationSupported(false);
    }

    private boolean isStabilizationEnabledForCamera(boolean front) {
        return isStabilizationSupported(front) && getStabilizationConfig(front).Bool();
    }

    private void normalizeStabilizationSelection() {
        if (!isStabilizationSupported(true) && NekoConfig.videoMessagesStabilizationFront.Bool()) {
            NekoConfig.videoMessagesStabilizationFront.setConfigBool(false);
        }
        if (!isStabilizationSupported(false) && NekoConfig.videoMessagesStabilizationRear.Bool()) {
            NekoConfig.videoMessagesStabilizationRear.setConfigBool(false);
        }
    }

    private class ResetVideoNoteDefaultsCell extends AbstractConfigCell implements WithKey {
        @Override
        public String getKey() {
            return "VideoMessagesResetQualityDefaults";
        }

        @Override
        public int getType() {
            return CellGroup.ITEM_TYPE_TEXT_CHECK_ICON;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextCell cell = (TextCell) holder.itemView;
            cell.setEnabled(true);
            cell.setTextAndIcon(getString(R.string.VideoMessagesResetQualityDefaults), R.drawable.msg_reset, false);
        }

        private void onClick() {
            resetVideoNoteDefaults();
        }
    }

    private String getStabilizationSummary() {
        if (!isCamera2Enabled()) {
            return getString(R.string.VideoMessagesCamera2Required);
        }
        if (!isAnyStabilizationSupported()) {
            return getString(R.string.VideoMessagesStabilizationUnsupported);
        }
        boolean front = isStabilizationEnabledForCamera(true);
        boolean rear = isStabilizationEnabledForCamera(false);
        if (front && rear) {
            return getString(R.string.VideoMessagesBoth);
        } else if (front) {
            return getString(R.string.VideoMessagesFrontCamera);
        } else if (rear) {
            return getString(R.string.VideoMessagesRearCamera);
        }
        return getString(R.string.None);
    }

    private String getRecordFromSummary() {
        int mode = NekoConfig.videoMessagesRecordFrom.Int();
        if (mode == NekoConfig.VIDEO_MESSAGES_RECORD_FROM_REAR) {
            return getString(R.string.VideoMessagesRearCamera);
        } else if (mode == NekoConfig.VIDEO_MESSAGES_RECORD_FROM_ASK) {
            return getString(R.string.VideoMessagesAlwaysAsk);
        }
        return getString(R.string.VideoMessagesFrontCamera);
    }

    private String getStartCameraSummary() {
        if (!isCamera2Enabled()) {
            return getString(R.string.VideoMessagesCamera2Required);
        }
        Camera2Session.RoundVideoCameraOption selectedOption = findSelectedStartCameraOption();
        if (selectedOption != null) {
            return formatStartCameraOptionTitle(selectedOption.title);
        }
        if (!startCameraOptions.isEmpty()) {
            return formatStartCameraOptionTitle(startCameraOptions.get(0).title);
        }
        return getString(R.string.VideoMessagesStartCameraUnavailable);
    }

    private String formatStartCameraOptionTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return "";
        }
        int separatorIndex = title.indexOf(" · ");
        if (separatorIndex >= 0 && separatorIndex + 3 < title.length()) {
            return title.substring(separatorIndex + 3);
        }
        return title;
    }

    private boolean isAdaptiveVideoNoteEnabled() {
        return NaConfig.INSTANCE.getCameraVideoNoteAdaptiveBitrate().Bool();
    }

    private int getVideoNoteResolutionValue() {
        return isAdaptiveVideoNoteEnabled() ? RoundVideoEncodingOptions.getAdaptiveResolution() : NaConfig.INSTANCE.getCameraVideoNoteResolution().Int();
    }

    private int getVideoNoteBitrateValue() {
        return isAdaptiveVideoNoteEnabled() ? RoundVideoEncodingOptions.HIGH_QUALITY_MASTER_BITRATE : NaConfig.INSTANCE.getCameraVideoNoteBitrate().Int();
    }

    private void bindVideoNoteSlider(SlideChooseView slideView, boolean bitrate) {
        int[] values = bitrate ? RoundVideoEncodingOptions.getBitrateValues() : RoundVideoEncodingOptions.getResolutionValues();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = String.valueOf(values[i]);
        }
        int selectedIndex = findValueIndex(values, bitrate ? getVideoNoteBitrateValue() : getVideoNoteResolutionValue());
        slideView.setCallback(index -> {
            if (index < 0 || index >= values.length || isAdaptiveVideoNoteEnabled()) {
                return;
            }
            ConfigItem config = bitrate ? NaConfig.INSTANCE.getCameraVideoNoteBitrate() : NaConfig.INSTANCE.getCameraVideoNoteResolution();
            if (config.Int() == values[index]) {
                return;
            }
            config.setConfigInt(values[index]);
            getMessagesController().applyCustomRoundVideoEncodingSettings();
            setResetDefaultsRowsVisible(!isVideoNoteDefaultQualitySelected());
        });
        slideView.setOptions(selectedIndex, labels);
    }

    private int findValueIndex(int[] values, int value) {
        int bestIndex = 0;
        long bestDistance = Math.abs((long) value - values[0]);
        for (int i = 1; i < values.length; i++) {
            long distance = Math.abs((long) value - values[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static class VideoNoteSeparatorCell extends View {
        public VideoNoteSeparatorCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 1);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int offset = AndroidUtilities.dp(20);
            canvas.drawLine(LocaleController.isRTL ? 0 : offset, 0, getMeasuredWidth() - (LocaleController.isRTL ? offset : 0), 0, Theme.dividerPaint);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return cellGroup.rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            AbstractConfigCell a = cellGroup.rows.get(position);
            return a == null || a.isEnabled();
        }

        @Override
        public int getItemViewType(int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            return a != null ? a.getType() : CellGroup.ITEM_TYPE_TEXT_DETAIL;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom && holder.itemView instanceof SlideChooseView slideView) {
                    if (position == cellGroup.rows.indexOf(cameraResolutionRow)) {
                        bindVideoNoteSlider(slideView, false);
                    } else if (position == cellGroup.rows.indexOf(cameraBitrateRow)) {
                        bindVideoNoteSlider(slideView, true);
                    }
                } else {
                    a.onBindViewHolder(holder);
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case CellGroup.ITEM_TYPE_DIVIDER:
                    view = new ShadowSectionCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK_ICON:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_DETAIL:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case ITEM_TYPE_VIDEO_NOTE_SLIDER:
                    view = new SlideChooseView(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case ITEM_TYPE_VIDEO_NOTE_SEPARATOR:
                    view = new VideoNoteSeparatorCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }
}
