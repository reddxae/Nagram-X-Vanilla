package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;
import static tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity.PARTIAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Parcelable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsService;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;

import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.NekoXConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCheckBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck2;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.DrawerProfilePreviewCell;
import tw.nekomimi.nekogram.ui.cells.EmojiSetCell;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.ui.cells.StickerSizePreviewMessagesCell;
import tw.nekomimi.nekogram.utils.AndroidUtil;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.TabStyle;
import tw.nekomimi.nekogram.helpers.remote.EmojiHelper;

@SuppressLint("RtlHardcoded")
@SuppressWarnings("unused")
public class NekoAppearanceSettingsActivity extends BaseNekoXSettingsActivity implements NotificationCenter.NotificationCenterDelegate, EmojiHelper.EmojiPacksLoadedListener {

    private final CellGroup cellGroup = new CellGroup(this);

    private ListAdapter listAdapter;
    private ActionBarMenuItem menuItem;
    private DrawerProfilePreviewCell profilePreviewCell;
    private ChatBlurAlphaSeekBar chatBlurAlphaSeekbar;
    private StickerSizeCell stickerSizeCell;
    private UndoView restartTooltip;
    private Parcelable recyclerViewState = null;
    private boolean wasCentered = false;
    private boolean wasCenteredAtBeginning = false;
    private float centeredMeasure = -1;

    // General
    private final AbstractConfigCell headerGeneral = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.General)));
    private final AbstractConfigCell centerActionBarTitleRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getCenterActionBarTitle(), null, getString(R.string.CenterActionBarTitleType)));
    private final AbstractConfigCell relativeOnlineTimeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getRelativeOnlineTime()));
    private final AbstractConfigCell tabsTitleTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabsTitleType, new String[]{
            getString(R.string.TabTitleTypeText),
            getString(R.string.TabTitleTypeIcon),
            getString(R.string.TabTitleTypeMix)
    }, null) {
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            String[] options = {
                    getString(R.string.TabTitleTypeText),
                    getString(R.string.TabTitleTypeIcon),
                    getString(R.string.TabTitleTypeMix)
            };
            int value = NekoConfig.tabsTitleType.Int();
            String valueText = value >= 0 && value < options.length ? options[value] : "";
            SpannableStringBuilder description = new SpannableStringBuilder();
            description.append(buildTabsPreview(false));
            description.append("\n").append(getString(R.string.ShowOnChatsTabsNotice));
            cell.setValueSpacingDp(18);
            cell.setTextAndValueAndDescription(getString(R.string.TabTitleType), valueText, description, false, cellGroup.needSetDivider(this), true);
        }
    });
    private final AbstractConfigCell attachmentTabsTitleTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.attachmentTabsTitleType, new String[]{
            getString(R.string.TabTitleTypeText),
            getString(R.string.TabTitleTypeIcon),
            getString(R.string.TabTitleTypeMix)
    }, null) {
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            String[] options = {
                    getString(R.string.TabTitleTypeText),
                    getString(R.string.TabTitleTypeIcon),
                    getString(R.string.TabTitleTypeMix)
            };
            int value = NekoConfig.attachmentTabsTitleType.Int();
            String valueText = value >= 0 && value < options.length ? options[value] : "";
            cell.setValueSpacingDp(18);
            cell.setTextAndValueAndDescription(getString(R.string.AttachmentTabTitleType), valueText, buildTabsPreview(true), false, cellGroup.needSetDivider(this), true);
        }
    });
    private final AbstractConfigCell tabStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SelectedTabStyle", NaConfig.INSTANCE.getTabStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.TabStylePure),
            getString(R.string.TabStylePills)
    }, null));
    private final AbstractConfigCell transcluentPanelsRow = cellGroup.appendCell(new ConfigCellTextCheck2("TranscluentPanels", getString(R.string.TranscluentPanels), new ArrayList<>() {{
            add(new ConfigCellCheckBox(NekoConfig.translucentBottomPanel, null, getString(R.string.BottomPanel), 0, true));
            add(new ConfigCellCheckBox(NekoConfig.translucentHeaderPanel, null, getString(R.string.HeaderPanel), 0, true));
            add(new ConfigCellCheckBox(NekoConfig.translucentDialogWindows, null, getString(R.string.DialogWindows), 0, false));
    }}, null));
    private final AbstractConfigCell forceBlurInChatRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.forceBlurInChat) {
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            this.cell = cell;
            cell.setTextAndCheck(getTitle(), getBindConfig().Bool(), false, true);
            cell.setEnabled(isEnabled(), null);
        }

        @Override
        public void setEnabledAndUpdateState(boolean enabled) {
            setEnabled(enabled);
            if (this.cell != null) {
                this.cell.setEnabled(isEnabled());
                this.cell.setTextAndCheck(getTitle(), getBindConfig().Bool(), false, true);
            }
        }
    });
    private final AbstractConfigCell headerChatBlur = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.ChatBlurAlphaValue)));
    private final AbstractConfigCell chatBlurAlphaValueRow = cellGroup.appendCell(new ConfigCellCustom("ChatBlurAlphaValue", ConfigCellCustom.CUSTOM_ITEM_CharBlurAlpha, NekoConfig.forceBlurInChat.Bool()));
    private final AbstractConfigCell appBarShadowRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAppBarShadow));
    private final AbstractConfigCell hideDividersRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideDividers()));
    private final AbstractConfigCell dividerGeneral = cellGroup.appendCell(new ConfigCellDivider());

    // Profiles
    private final AbstractConfigCell headerProfiles = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AppearanceProfiles)));
    private final AbstractConfigCell disableAvatarBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableAvatarBlur()));
    private final AbstractConfigCell disableGooeyAvatarAnimationRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableGooeyAvatarAnimation()));
    private final AbstractConfigCell dividerProfiles = cellGroup.appendCell(new ConfigCellDivider());

    // Icons
    private final AbstractConfigCell headerIcons = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AppearanceIcons)));
    private final AbstractConfigCell actionBarDecorationRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.actionBarDecoration, new String[]{
            getString(R.string.DependsOnDate),
            getString(R.string.Snowflakes),
            getString(R.string.Fireworks),
            getString(R.string.DisableIgnoreDate),
    }, null));
    private final AbstractConfigCell iconDecorationRow = cellGroup.appendCell(new ConfigCellSelectBox("DrawerIconsDecoration", NaConfig.INSTANCE.getIconDecoration(), new String[]{
            getString(R.string.DependsOnDate),
            getString(R.string.Christmas),
            getString(R.string.Valentine),
            getString(R.string.HalloWeen),
            getString(R.string.DisableIgnoreDate),
    }, null));
    private final AbstractConfigCell chatDecorationRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getChatDecoration(), new String[]{
            getString(R.string.DependsOnDate),
            getString(R.string.Snowflakes),
            getString(R.string.DisableIgnoreDate),
    }, null));
    private final AbstractConfigCell replaceSubscribersWithIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getReplaceSubscribersWithIcon()));
    private final AbstractConfigCell replaceMembersWithIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getReplaceMembersWithIcon()));
    private final AbstractConfigCell hideOnlineMembersCounterRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideOnlineMembersCounter()));
    private final AbstractConfigCell useEditedIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUseEditedIcon()));
    private final AbstractConfigCell customEditedMessageRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomEditedMessage(), "", null));
    private final AbstractConfigCell dividerIcons = cellGroup.appendCell(new ConfigCellDivider());

    // Chats page
    private final AbstractConfigCell headerChatsPage = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AppearanceChatsPage)));
    private final AbstractConfigCell customTitleRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomTitle(),
            getString(R.string.CustomTitleHint), null));
    private final AbstractConfigCell customTitleUserNameRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getCustomTitleUserName()));
    private final AbstractConfigCell newYearRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.newYear));
    private final AbstractConfigCell mediaPreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.mediaPreview));
    private final AbstractConfigCell userAvatarsInMessagePreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUserAvatarsInMessagePreview()));
    private final AbstractConfigCell disableDialogsFloatingButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableDialogsFloatingButton()));
    private final AbstractConfigCell disableBotOpenButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableBotOpenButton()));
    private final AbstractConfigCell dividerChatsPage = cellGroup.appendCell(new ConfigCellDivider());

    // Stickers and Messages
    private final AbstractConfigCell headerStickersAndMessages = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AppearanceStickersAndMessages)));
    private final AbstractConfigCell stickerSizeRow = cellGroup.appendCell(new ConfigCellCustom("StickerSize", ConfigCellCustom.CUSTOM_ITEM_StickerSize, false));
    private final AbstractConfigCell hideTimeForStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideTimeForSticker));
    private final AbstractConfigCell showTimeHintRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTimeHint()));
    private final AbstractConfigCell disableReplyBackgroundRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getMessageColoredBackground()));
    private final AbstractConfigCell dividerStickersAndMessages = cellGroup.appendCell(new ConfigCellDivider());

    // Chats, Groups & Channels
    private final AbstractConfigCell headerChatsGroupsAndChannels = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AppearanceChatsGroupsAndChannels)));
    private final AbstractConfigCell hideSendAsChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideSendAsChannel, getString(R.string.HideSendAsChannelDetails)));
    private final AbstractConfigCell disableChannelMuteButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableChannelMuteButton()));
    private final AbstractConfigCell emojiSetsRow = cellGroup.appendCell(new ConfigCellCustom("EmojiSet", ConfigCellCustom.CUSTOM_ITEM_EmojiSet, true));
    private final AbstractConfigCell showSmallGifRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowSmallGIF()));
    private final AbstractConfigCell dividerChatsGroupsAndChannels = cellGroup.appendCell(new ConfigCellDivider());

    // Drawer
    private final AbstractConfigCell headerDrawer = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Drawer)));
    private final AbstractConfigCell profilePreviewRow = cellGroup.appendCell(new ConfigCellDrawerProfilePreview());
    private final AbstractConfigCell largeAvatarInDrawerRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.largeAvatarInDrawer, null, null) {
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            String[] options = getDrawerBackgroundOptions();
            int displayType = getDisplayDrawerBackgroundType(NekoConfig.largeAvatarInDrawer.Int());
            String valueText = displayType >= 0 && displayType < options.length ? options[displayType] : "";
            cell.setTextAndValueAndDescription(getString(R.string.AvatarAsBackground), valueText, null, false, cellGroup.needSetDivider(this), true);
        }

        @Override
        public void onClick(View view) {
            Context context = getParentActivity();
            if (context == null) {
                return;
            }
            String[] options = getDrawerBackgroundOptions();
            PopupBuilder builder = new PopupBuilder(view);
            builder.setItems(options, (which, value) -> {
                int storedType = getStoredDrawerBackgroundType(which);
                NekoConfig.largeAvatarInDrawer.setConfigInt(storedType);

                if (cellGroup.listAdapter != null) {
                    cellGroup.listAdapter.notifyItemChanged(cellGroup.rows.indexOf(this));
                }
                if (cellGroup.thisFragment != null) {
                    cellGroup.thisFragment.getParentLayout().rebuildAllFragmentViews(false, false);
                }

                cellGroup.runCallback(NekoConfig.largeAvatarInDrawer.getKey(), storedType);
                return Unit.INSTANCE;
            });
            builder.show();
        }
    });
    private final AbstractConfigCell avatarBackgroundBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.avatarBackgroundBlur));
    private final AbstractConfigCell avatarBackgroundDarkenRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.avatarBackgroundDarken));
    private final AbstractConfigCell hidePhoneRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hidePhone));
    private final AbstractConfigCell drawerElementsRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "DrawerElements", null, R.drawable.menu_newfilter, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.DrawerElements, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemMyProfile(), getString(R.string.MyProfile), R.drawable.left_status_profile));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSetEmojiStatus(), getString(R.string.SetEmojiStatus), R.drawable.msg_status_set, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemArchivedChats(), getString(R.string.ArchivedChats), R.drawable.msg_archive, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNewGroup(), getString(R.string.NewGroup), R.drawable.msg_groups));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNewChannel(), getString(R.string.NewChannel), R.drawable.msg_channel));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemContacts(), getString(R.string.Contacts), R.drawable.msg_contacts));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemCalls(), getString(R.string.Calls), R.drawable.msg_calls));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSaved(), getString(R.string.SavedMessages), R.drawable.msg_saved));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSettings(), getString(R.string.Settings), R.drawable.msg_settings_old, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNSettings(), getString(R.string.NekoSettings), R.drawable.actions_reactions));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemBrowser(), getString(R.string.InappBrowser), R.drawable.web_browser));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemQrLogin(), getString(R.string.ImportLogin), R.drawable.msg_qrcode));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSessions(), getString(R.string.Devices), R.drawable.msg2_devices, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemRestartApp(), getString(R.string.RestartApp), R.drawable.msg_retry));
            }}))
    ));
    private final AbstractConfigCell dividerDrawer = cellGroup.appendCell(new ConfigCellDivider());

    public NekoAppearanceSettingsActivity() {
        normalizeDrawerBackgroundType();
        wasCentered = isCentered();
        wasCenteredAtBeginning = wasCentered;
        if (NaConfig.INSTANCE.getUseEditedIcon().Bool()) {
            cellGroup.rows.remove(customEditedMessageRow);
        }
        checkProfileConfigCellRows();
        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        EmojiHelper.getInstance().loadEmojisInfo(this);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @SuppressLint({"NewApi", "NotifyDataSetChanged", "UseCompatLoadingForDrawables"})
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getTitle());

        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }

        if (cellGroup.rows.contains(stickerSizeRow)) {
            ActionBarMenu menu = actionBar.createMenu();
            menuItem = menu.addItem(0, R.drawable.ic_ab_other);
            menuItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
            menuItem.addSubItem(1, R.drawable.msg_reset, getString(R.string.ResetStickerSize));
            menuItem.setVisibility(NekoConfig.stickerSize.Float() != 14.0f ? View.VISIBLE : View.GONE);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    NekoConfig.stickerSize.setConfigFloat(14.0f);
                    if (menuItem != null) {
                        menuItem.setVisibility(View.GONE);
                    }
                    if (stickerSizeCell != null) {
                        stickerSizeCell.invalidate();
                    }
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        setCanNotChange();

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
            if (a instanceof ConfigCellTextCheck) {
                ((ConfigCellTextCheck) a).onClick((TextCheckCell) view);
            } else if (a instanceof ConfigCellSelectBox) {
                ((ConfigCellSelectBox) a).onClick(view);
            } else if (a instanceof ConfigCellTextInput) {
                ((ConfigCellTextInput) a).onClick();
            } else if (a instanceof ConfigCellTextDetail) {
                RecyclerListView.OnItemClickListener o = ((ConfigCellTextDetail) a).onItemClickListener;
                if (o != null) {
                    try {
                        o.onItemClick(view, position);
                    } catch (Exception ignored) {
                    }
                }
            } else if (a instanceof ConfigCellCustom) {
                if (position == cellGroup.rows.indexOf(emojiSetsRow)) {
                    presentFragment(new NekoEmojiSettingsActivity());
                }
            } else if (a instanceof ConfigCellTextCheckIcon) {
                ((ConfigCellTextCheckIcon) a).onClick();
            } else if (a instanceof ConfigCellTextCheck2) {
                ((ConfigCellTextCheck2) a).onClick();
            } else if (a instanceof ConfigCellCheckBox) {
                ((ConfigCellCheckBox) a).onClick((CheckBoxCell) view);
                notifyParentToggleRowChanged((ConfigCellCheckBox) a);
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (cellGroup.rows.get(position) instanceof ConfigCellCheckBox) {
                return true;
            }
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, NekoAppearanceSettingsActivity.this, "appearance", position);
                return true;
            }
            return false;
        });

        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NekoConfig.hidePhone.getKey())) {
                parentLayout.rebuildFragments(0);
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                notifyIfPresent(profilePreviewRow);
            } else if (key.equals(NekoConfig.actionBarDecoration.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.newYear.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.largeAvatarInDrawer.getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                if (profilePreviewCell != null) {
                    TransitionManager.beginDelayedTransition(profilePreviewCell);
                }
                checkProfileConfigCellRows();
            } else if (key.equals(NekoConfig.avatarBackgroundBlur.getKey()) || key.equals(NekoConfig.avatarBackgroundDarken.getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                notifyIfPresent(profilePreviewRow);
            } else if (key.equals(NekoConfig.disableAppBarShadow.getKey())) {
                ActionBarLayout.headerShadowDrawable = (boolean) newValue ? null : parentLayout.getParentActivity().getResources().getDrawable(R.drawable.header_shadow).mutate();
                parentLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST | INavigationLayout.REBUILD_FLAG_REBUILD_ONLY_LAST);
            } else if (key.equals(NekoConfig.forceBlurInChat.getKey())) {
                SharedConfig.syncBlurSettingsFromForceBlur((Boolean) newValue);
                boolean enabled = NekoConfig.forceBlurInChat.Bool();
                if (chatBlurAlphaSeekbar != null) {
                    chatBlurAlphaSeekbar.setEnabled(enabled);
                }
                ((ConfigCellCustom) chatBlurAlphaValueRow).enabled = enabled;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NaConfig.INSTANCE.getCenterActionBarTitle().getKey())) {
                animateActionBarUpdate(this);
            } else if (key.equals(NaConfig.INSTANCE.getRelativeOnlineTime().getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NaConfig.INSTANCE.getHideDividers().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getCustomTitleUserName().getKey())) {
                boolean enabled = (Boolean) newValue;
                ((ConfigCellTextInput) customTitleRow).setEnabled(!enabled);
                notifyIfPresent(customTitleRow);
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableBotOpenButton().getKey()) ||
                    key.equals(NaConfig.INSTANCE.getDisableDialogsFloatingButton().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.tabsTitleType.getKey()) ||
                    key.equals(NekoConfig.attachmentTabsTitleType.getKey()) ||
                    key.equals(NaConfig.INSTANCE.getTabStyle().getKey())) {
                notifyIfPresent(tabsTitleTypeRow);
                notifyIfPresent(attachmentTabsTitleTypeRow);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NaConfig.INSTANCE.getUseEditedIcon().getKey())) {
                if ((boolean) newValue) {
                    if (cellGroup.rows.contains(customEditedMessageRow)) {
                        final int index = cellGroup.rows.indexOf(customEditedMessageRow);
                        cellGroup.rows.remove(customEditedMessageRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(customEditedMessageRow)) {
                        final int index = cellGroup.rows.indexOf(useEditedIconRow) + 1;
                        cellGroup.rows.add(index, customEditedMessageRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
            } else if (key.equals(NaConfig.INSTANCE.getMessageColoredBackground().getKey()) || key.equals(NekoConfig.hideTimeForSticker.getKey())) {
                if (stickerSizeCell != null) {
                    stickerSizeCell.invalidate();
                }
            } else if (key.equals(NaConfig.INSTANCE.getReplaceSubscribersWithIcon().getKey()) ||
                    key.equals(NaConfig.INSTANCE.getReplaceMembersWithIcon().getKey()) ||
                    key.equals(NaConfig.INSTANCE.getHideOnlineMembersCounter().getKey()) ||
                    key.equals(NaConfig.INSTANCE.getUseEditedIcon().getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals("TranscluentPanels_check")) {
                SharedConfig.syncBlurSettingsFromTranslucentPanels((Boolean) newValue);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NekoConfig.translucentBottomPanel.getKey()) ||
                    key.equals(NekoConfig.translucentHeaderPanel.getKey()) ||
                    key.equals(NekoConfig.translucentDialogWindows.getKey())) {
                SharedConfig.syncBlurSettingsFromTranslucentPanels(SharedConfig.hasEnabledTranslucentPanels());
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals("TranscluentPanels")) {
                addRowsToMap(cellGroup);
            }
        };

        cellGroup.setListAdapter(listView, listAdapter);

        restartTooltip = new UndoView(context);
        frameLayout.addView(restartTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        return fragmentView;
    }

    private void notifyParentToggleRowChanged(ConfigCellCheckBox checkBoxCell) {
        if (listAdapter == null) {
            return;
        }
        for (AbstractConfigCell row : cellGroup.rows) {
            if (!(row instanceof ConfigCellTextCheck2)) {
                continue;
            }
            ConfigCellTextCheck2 toggleRow = (ConfigCellTextCheck2) row;
            if (!toggleRow.getCheckBox().contains(checkBoxCell)) {
                continue;
            }
            int toggleRowIndex = cellGroup.rows.indexOf(toggleRow);
            if (toggleRowIndex != -1) {
                listAdapter.notifyItemRangeChanged(toggleRowIndex, toggleRow.getCheckBox().size() + 1);
            }
            return;
        }
    }

    private void notifyIfPresent(AbstractConfigCell row) {
        if (listAdapter == null) {
            return;
        }
        int index = cellGroup.rows.indexOf(row);
        if (index != -1) {
            listAdapter.notifyItemChanged(index);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        setCanNotChange();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void updateRows() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        super.onFragmentDestroy();
    }

    @Override
    public int getBaseGuid() {
        return 14000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.menu_profile_colors;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Appearance);
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

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2Track));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2TrackChecked));

        return themeDescriptions;
    }

    @Override
    public void emojiPacksLoaded(String error) {
        int index = cellGroup.rows.indexOf(emojiSetsRow);
        if (listAdapter != null && index != -1) {
            listAdapter.notifyItemChanged(index, PARTIAL);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded && listAdapter != null) {
            int index = cellGroup.rows.indexOf(emojiSetsRow);
            if (index != -1) {
                listAdapter.notifyItemChanged(index, PARTIAL);
            }
        }
    }

    private class ConfigCellDrawerProfilePreview extends AbstractConfigCell {
        @Override
        public int getType() {
            return ConfigCellCustom.CUSTOM_ITEM_ProfilePreview;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            DrawerProfilePreviewCell cell = (DrawerProfilePreviewCell) holder.itemView;
            cell.setUser(getUserConfig().getCurrentUser(), false);
        }
    }

    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewMessagesCell messagesCell;
        private final SeekBarView sizeBar;
        private final int startStickerSize = 2;
        private final int endStickerSize = 20;
        private final TextPaint textPaint;

        public StickerSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endStickerSize - startStickerSize + 1);
            sizeBar.setDelegate((stop, progress) -> {
                NekoConfig.stickerSize.setConfigFloat(startStickerSize + (endStickerSize - startStickerSize) * progress);
                StickerSizeCell.this.invalidate();
                if (menuItem != null) {
                    menuItem.setVisibility(View.VISIBLE);
                }
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));

            messagesCell = new StickerSizePreviewMessagesCell(context, NekoAppearanceSettingsActivity.this);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText(String.valueOf(Math.round(NekoConfig.stickerSize.Float())), getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((NekoConfig.stickerSize.Float() - startStickerSize) / (float) (endStickerSize - startStickerSize));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            messagesCell.invalidate();
            sizeBar.invalidate();
        }
    }

    private static class ChatBlurAlphaSeekBar extends FrameLayout {

        private final SeekBarView sizeBar;
        private final TextPaint textPaint;
        private boolean enabled = true;

        @SuppressLint("ClickableViewAccessibility")
        public ChatBlurAlphaSeekBar(Context context) {
            super(context);

            setWillNotDraw(false);
            setClickable(true);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(256);
            sizeBar.setDelegate((stop, progress) -> {
                NekoConfig.chatBlueAlphaValue.setConfigInt(Math.min(255, (int) (255 * progress)));
                invalidate();
            });
            sizeBar.setOnTouchListener((v, event) -> !enabled);
            sizeBar.setProgress(NekoConfig.chatBlueAlphaValue.Int());
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText(String.valueOf(NekoConfig.chatBlueAlphaValue.Int()), getMeasuredWidth() - dp(39), dp(28), textPaint);
            canvas.drawLine(dp(20), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((NekoConfig.chatBlueAlphaValue.Int() / 255.0f));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            sizeBar.invalidate();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            this.enabled = enabled;
            sizeBar.setAlpha(enabled ? 1.0f : 0.5f);
            textPaint.setAlpha((int) ((enabled ? 1.0f : 0.3f) * 255));
            invalidate();
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
            if (a != null) {
                return a.isEnabled();
            }
            return true;
        }

        @Override
        public int getItemViewType(int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                return a.getType();
            }
            return CellGroup.ITEM_TYPE_TEXT_DETAIL;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View view = holder.itemView;
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom) {
                    if (view instanceof EmojiSetCell emojiSetCell) {
                        emojiSetCell.setData(EmojiHelper.getInstance().getCurrentEmojiPackInfo(), false, true);
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
                case ConfigCellCustom.CUSTOM_ITEM_ProfilePreview:
                    view = profilePreviewCell = new DrawerProfilePreviewCell(mContext);
                    view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case ConfigCellCustom.CUSTOM_ITEM_CharBlurAlpha:
                    view = chatBlurAlphaSeekbar = new ChatBlurAlphaSeekBar(mContext);
                    chatBlurAlphaSeekbar.setEnabled(NekoConfig.forceBlurInChat.Bool());
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case ConfigCellCustom.CUSTOM_ITEM_StickerSize:
                    view = stickerSizeCell = new StickerSizeCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case ConfigCellCustom.CUSTOM_ITEM_EmojiSet:
                    view = new EmojiSetCell(mContext, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK_ICON:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_CHECK2:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_CHECK_BOX:
                    CheckBoxCell checkBoxCell = new CheckBoxCell(mContext, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, getResourceProvider());
                    checkBoxCell.getCheckBoxRound().setDrawBackgroundAsArc(14);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setEnabled(true);
                    view = checkBoxCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }

    private void setCanNotChange() {
        if (NaConfig.INSTANCE.getCustomTitleUserName().Bool()) {
            ((ConfigCellTextInput) customTitleRow).setEnabled(false);
        }

        boolean enabled = true;
        ((ConfigCellTextCheck) forceBlurInChatRow).setEnabledAndUpdateState(true);
        enabled = NekoConfig.forceBlurInChat.Bool();
        ((ConfigCellCustom) chatBlurAlphaValueRow).enabled = enabled;
        if (chatBlurAlphaSeekbar != null) {
            chatBlurAlphaSeekbar.setEnabled(enabled);
        }
    }

    private void checkProfileConfigCellRows() {
        int backgroundType = NekoConfig.largeAvatarInDrawer.Int();
        boolean useAvatar = backgroundType == NekoConfig.DRAWER_BACKGROUND_AVATAR || backgroundType == NekoConfig.DRAWER_BACKGROUND_BIG_AVATAR;
        if (listAdapter == null) {
            if (!useAvatar) {
                cellGroup.rows.remove(avatarBackgroundBlurRow);
                cellGroup.rows.remove(avatarBackgroundDarkenRow);
            }
            return;
        }
        if (useAvatar) {
            final int index = cellGroup.rows.indexOf(largeAvatarInDrawerRow);
            if (!cellGroup.rows.contains(avatarBackgroundBlurRow)) {
                cellGroup.rows.add(index + 1, avatarBackgroundBlurRow);
                listAdapter.notifyItemInserted(index + 1);
            }
            if (!cellGroup.rows.contains(avatarBackgroundDarkenRow)) {
                cellGroup.rows.add(index + 2, avatarBackgroundDarkenRow);
                listAdapter.notifyItemInserted(index + 2);
            }
        } else {
            int blurRowIndex = cellGroup.rows.indexOf(avatarBackgroundBlurRow);
            if (blurRowIndex != -1) {
                cellGroup.rows.remove(avatarBackgroundBlurRow);
                listAdapter.notifyItemRemoved(blurRowIndex);
            }
            int darkenRowIndex = cellGroup.rows.indexOf(avatarBackgroundDarkenRow);
            if (darkenRowIndex != -1) {
                cellGroup.rows.remove(avatarBackgroundDarkenRow);
                listAdapter.notifyItemRemoved(darkenRowIndex);
            }
        }
        notifyIfPresent(profilePreviewRow);
    }

    private void normalizeDrawerBackgroundType() {
        if (NekoConfig.largeAvatarInDrawer.Int() == NekoConfig.DRAWER_BACKGROUND_BIG_AVATAR) {
            NekoConfig.largeAvatarInDrawer.setConfigInt(NekoConfig.DRAWER_BACKGROUND_AVATAR);
        }
    }

    private int getDisplayDrawerBackgroundType(int storedType) {
        if (storedType == NekoConfig.DRAWER_BACKGROUND_AVATAR || storedType == NekoConfig.DRAWER_BACKGROUND_BIG_AVATAR) {
            return 1;
        } else if (storedType == NekoConfig.DRAWER_BACKGROUND_WALLPAPER) {
            return 2;
        }
        return 0;
    }

    private int getStoredDrawerBackgroundType(int displayType) {
        if (displayType == 1) {
            return NekoConfig.DRAWER_BACKGROUND_AVATAR;
        } else if (displayType == 2) {
            return NekoConfig.DRAWER_BACKGROUND_WALLPAPER;
        }
        return NekoConfig.DRAWER_BACKGROUND_DEFAULT;
    }

    private String[] getDrawerBackgroundOptions() {
        return getString(R.string.valuesLargeAvatarInDrawer).split("\n");
    }

    private boolean isCentered() {
        return NaConfig.INSTANCE.getCenterActionBarTitle().Bool();
    }

    private void animateActionBarUpdate(BaseNekoXSettingsActivity fragment) {
        boolean centered = isCentered();
        ActionBar actionBar = fragment.getActionBar();
        if (wasCentered == centered) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            return;
        }
        if (actionBar != null) {
            SimpleTextView titleTextView = actionBar.getTitleTextView();
            if (titleTextView == null) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                reloadUI(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                return;
            }
            if (centeredMeasure == -1) {
                centeredMeasure = actionBar.getMeasuredWidth() / 2f - titleTextView.getTextWidth() / 2f - dp((AndroidUtilities.isTablet() ? 80 : 72));
            }
            titleTextView.animate()
                    .translationX(centeredMeasure * (centered ? 1 : 0) - (wasCenteredAtBeginning ? Math.abs(centeredMeasure) : 0))
                    .setDuration(150)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            wasCentered = centered;
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                            reloadUI(0);
                        }
                    })
                    .start();
        } else {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            reloadUI(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
        }
    }

    private void reloadUI(int flags) {
        RecyclerView.LayoutManager recyclerLayoutManager = listView.getLayoutManager();
        if (recyclerLayoutManager != null) {
            recyclerViewState = recyclerLayoutManager.onSaveInstanceState();
            parentLayout.rebuildFragments(flags);
            recyclerLayoutManager.onRestoreInstanceState(recyclerViewState);
        }
    }

    private CharSequence buildTabsPreview(boolean attachments) {
        int titleType = attachments ? NekoConfig.attachmentTabsTitleType.Int() : NekoConfig.tabsTitleType.Int();
        int tabStyle = NaConfig.INSTANCE.getTabStyle().Int();
        PreviewTabItem[] items = attachments ? new PreviewTabItem[]{
                new PreviewTabItem(R.drawable.msg_media, R.string.SharedMediaTab2),
                new PreviewTabItem(R.drawable.msg_link, R.string.SharedLinksTab2),
                new PreviewTabItem(R.drawable.msg_filehq, R.string.Files)
        } : new PreviewTabItem[]{
                new PreviewTabItem(R.drawable.msg_folders_groups, R.string.FilterGroups),
                new PreviewTabItem(R.drawable.msg_folders_channels, R.string.FilterChannels),
                new PreviewTabItem(R.drawable.msg_folders_bots, R.string.FilterBots)
        };

        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                builder.append("   ");
            }
            appendPreviewItem(builder, items[i], titleType, tabStyle, i == 1);
        }
        return builder;
    }

    private void appendPreviewItem(SpannableStringBuilder builder, PreviewTabItem item, int titleType, int tabStyle, boolean selected) {
        int start = builder.length();
        boolean showIcon = titleType != NekoXConfig.TITLE_TYPE_TEXT;
        boolean showTitle = titleType != NekoXConfig.TITLE_TYPE_ICON;
        boolean pillStyle = tabStyle == TabStyle.PILLS.getValue();
        boolean defaultStyle = tabStyle == TabStyle.DEFAULT.getValue();

        if (selected && (pillStyle || defaultStyle)) {
            builder.append('[');
        }
        if (showIcon) {
            appendIcon(builder, item.iconRes);
            if (showTitle) {
                builder.append(' ');
            }
        }
        if (showTitle) {
            builder.append(getString(item.titleRes));
        }
        if (selected && (pillStyle || defaultStyle)) {
            builder.append(']');
        }

        int end = builder.length();
        if (selected) {
            builder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void appendIcon(SpannableStringBuilder builder, int drawableRes) {
        int start = builder.length();
        builder.append("i");
        ColoredImageSpan span = new ColoredImageSpan(drawableRes, ColoredImageSpan.ALIGN_CENTER);
        span.setSize(AndroidUtilities.dp(14));
        builder.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static class PreviewTabItem {
        final int iconRes;
        final int titleRes;

        PreviewTabItem(int iconRes, int titleRes) {
            this.iconRes = iconRes;
            this.titleRes = titleRes;
        }
    }
}
