package tw.nekomimi.nekogram.menu.ghostmode;

import static org.telegram.messenger.LocaleController.getString;

import app.amegram.core.net.GhostExclusions;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PopupSwipeBackLayout;

public class GhostModeExclusionPopupWrapper {

    private final long chatId;
    private final ActionBarMenuSubItem defaultItem;
    private final ActionBarMenuSubItem readExclusionItem;
    private final ActionBarMenuSubItem typingExclusionItem;
    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;

    public GhostModeExclusionPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, long chatId, Theme.ResourcesProvider resourcesProvider) {
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);
        this.chatId = chatId;

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
            ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        }

        defaultItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.Default), true, resourcesProvider);
        defaultItem.setChecked(!GhostExclusions.getTypingExclusion(chatId) && !GhostExclusions.getReadExclusion(chatId));
        defaultItem.setOnClickListener(view -> {
            GhostExclusions.setTypingExclusion(chatId, false);
            GhostExclusions.setReadExclusion(chatId, false);
            updateItems();
        });

        readExclusionItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.GhostModeExcludeRead), true, resourcesProvider);
        readExclusionItem.setChecked(GhostExclusions.getReadExclusion(chatId));
        readExclusionItem.setOnClickListener(view -> {
            GhostExclusions.setReadExclusion(chatId, !GhostExclusions.getReadExclusion(chatId));
            updateItems();
        });

        typingExclusionItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.GhostModeExcludeTyping), true, resourcesProvider);
        typingExclusionItem.setChecked(GhostExclusions.getTypingExclusion(chatId));
        typingExclusionItem.setOnClickListener(view -> {
            GhostExclusions.setTypingExclusion(chatId, !GhostExclusions.getTypingExclusion(chatId));
            updateItems();
        });
    }

    public void updateItems() {
        boolean readExcluded = GhostExclusions.getReadExclusion(chatId);
        boolean typingExcluded = GhostExclusions.getTypingExclusion(chatId);

        defaultItem.setChecked(!typingExcluded && !readExcluded);
        readExclusionItem.setChecked(readExcluded);
        typingExclusionItem.setChecked(typingExcluded);

    }
}
