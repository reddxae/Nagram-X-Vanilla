package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
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
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
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
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings("unused")
public class NekoCameraSettingsActivity extends BaseNekoXSettingsActivity {
    private static final int[] VIDEO_NOTE_BITRATE_VALUES = {600, 800, 1000, 1200, 1400};
    private static final int[] VIDEO_NOTE_RESOLUTION_VALUES = {128, 256, 384, 512, 640};

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
    private final AbstractConfigCell cameraResolutionRow = cellGroup.appendCell(new ConfigCellCustom("CameraResolution", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell cameraBitrateRow = cellGroup.appendCell(new ConfigCellCustom("CameraBitrate", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
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
            cell.setText(getString(R.string.CameraVideoMessagesNotice));
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
            } else if (a instanceof ConfigCellCustom) {
                if (position == cellGroup.rows.indexOf(cameraResolutionRow)) {
                    showVideoNoteValuePopup(view, VIDEO_NOTE_RESOLUTION_VALUES, false, value -> {
                        NaConfig.INSTANCE.getCameraVideoNoteResolution().setConfigInt(value);
                        getMessagesController().applyCustomRoundVideoEncodingSettings();
                        listAdapter.notifyItemChanged(position);
                    });
                } else if (position == cellGroup.rows.indexOf(cameraBitrateRow)) {
                    showVideoNoteValuePopup(view, VIDEO_NOTE_BITRATE_VALUES, true, value -> {
                        NaConfig.INSTANCE.getCameraVideoNoteBitrate().setConfigInt(value);
                        getMessagesController().applyCustomRoundVideoEncodingSettings();
                        listAdapter.notifyItemChanged(position);
                    });
                }
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
        cellGroup.rows.add(cameraResolutionRow);
        cellGroup.rows.add(cameraBitrateRow);
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
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
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
        BLUR_CAMERA_SWITCH
    }

    private class RecordFromDropdownCell extends AbstractConfigCell {
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

    private class StartCameraDropdownCell extends AbstractConfigCell {
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

    private class Camera2ApiToggleCell extends AbstractConfigCell {
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

    private class VideoMessagesToggleCell extends AbstractConfigCell {
        private final ConfigItem bindConfig;
        private final int titleResId;
        private final ToggleType toggleType;

        private VideoMessagesToggleCell(ConfigItem bindConfig, int titleResId, ToggleType toggleType) {
            this.bindConfig = bindConfig;
            this.titleResId = titleResId;
            this.toggleType = toggleType;
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

    private class StabilizationDropdownCell extends AbstractConfigCell {
        private static final int FRONT_CAMERA_ID = 0;
        private static final int REAR_CAMERA_ID = 1;

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
            cell.setTextAndValueAndDescription(
                    getString(R.string.VideoMessagesStabilization),
                    getStabilizationSummary(),
                    getString(R.string.VideoMessagesStabilizationDescription),
                    false,
                    cellGroup.needSetDivider(this),
                    false
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

    private void showVideoNoteValuePopup(View view, int[] values, boolean bitrate, ValueConsumer onSelected) {
        ArrayList<String> items = new ArrayList<>(values.length);
        for (int value : values) {
            items.add(formatVideoNoteValue(value, bitrate));
        }
        PopupBuilder builder = new PopupBuilder(view);
        builder.setItems(items, (index, str) -> {
            onSelected.accept(values[index]);
            return Unit.INSTANCE;
        });
        builder.show();
    }

    private String formatVideoNoteValue(int value, boolean bitrate) {
        return bitrate ? value + " kbps" : value + " px";
    }

    private interface ValueConsumer {
        void accept(int value);
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
                if (a instanceof ConfigCellCustom) {
                    if (holder.itemView instanceof TextSettingsCell textCell) {
                        if (position == cellGroup.rows.indexOf(cameraResolutionRow)) {
                            textCell.setTextAndValue(getString(R.string.Resolution), formatVideoNoteValue(NaConfig.INSTANCE.getCameraVideoNoteResolution().Int(), false), true);
                        } else if (position == cellGroup.rows.indexOf(cameraBitrateRow)) {
                            textCell.setTextAndValue(getString(R.string.Bitrate), formatVideoNoteValue(NaConfig.INSTANCE.getCameraVideoNoteBitrate().Int(), true), false);
                        }
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
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }
}
