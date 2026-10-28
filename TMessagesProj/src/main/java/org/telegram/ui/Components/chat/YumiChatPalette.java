package org.telegram.ui.Components.chat;

import android.graphics.Color;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

import app.amegram.theme.YumiMd3;

/** Chat-local Yumi colors. Kept here so message themes do not recolor other screens. */
public final class YumiChatPalette {

    private YumiChatPalette() {
    }

    public static int color(int key, boolean dark) {
        final int primary = dark ? 0xFFFF8FB7 : 0xFFB83268;
        final int incoming = dark ? 0xFF2C222E : 0xFFFFF4F8;
        final int outgoingStart = dark ? 0xFF9C315F : 0xFFB83268;
        final int outgoingMiddle = dark ? 0xFF783B83 : 0xFF913D82;
        final int outgoingEnd = dark ? 0xFF51458C : 0xFF674B9B;
        final int incomingReaction = dark ? 0xFF4B3448 : 0xFFF1DCE8;
        final int outgoingReaction = dark ? 0xFF55304B : 0xFFEBC6D9;
        final int onSurface = dark ? 0xFFF8EDF3 : 0xFF291A24;
        final int onSurfaceVariant = dark ? 0xFFD0BAC7 : 0xFF705966;
        final int white = 0xFFFFFFFF;
        switch (key) {
            case Theme.key_chat_inBubble:
                return incoming;
            case Theme.key_chat_inBubbleSelected:
                return blend(incoming, primary, 0.16f);
            case Theme.key_chat_inBubbleSelectedOverlay:
                return withAlpha(primary, 0.12f);
            case Theme.key_chat_inBubbleShadow:
                return withAlpha(0xFF21141D, dark ? 0.26f : 0.10f);
            case Theme.key_chat_outBubble:
                return outgoingStart;
            case Theme.key_chat_outBubbleGradient1:
                return outgoingMiddle;
            case Theme.key_chat_outBubbleGradient2:
                return outgoingEnd;
            case Theme.key_chat_outBubbleGradient3:
                return outgoingStart;
            case Theme.key_chat_outBubbleGradientAnimated:
                return 0;
            case Theme.key_chat_outBubbleSelected:
                return blend(outgoingMiddle, white, 0.13f);
            case Theme.key_chat_outBubbleSelectedOverlay:
            case Theme.key_chat_outBubbleGradientSelectedOverlay:
                return withAlpha(white, 0.14f);
            case Theme.key_chat_outBubbleShadow:
                return withAlpha(0xFF21141D, dark ? 0.22f : 0.10f);
            case Theme.key_chat_messageTextIn:
            case Theme.key_chat_inReplyMessageText:
            case Theme.key_chat_inReplyMediaMessageText:
            case Theme.key_chat_inReplyMediaMessageSelectedText:
                return onSurface;
            case Theme.key_chat_messageTextOut:
            case Theme.key_chat_outReplyMessageText:
            case Theme.key_chat_outReplyMediaMessageText:
            case Theme.key_chat_outReplyMediaMessageSelectedText:
            case Theme.key_chat_outTimeText:
            case Theme.key_chat_outSentCheck:
            case Theme.key_chat_outSentCheckSelected:
            case Theme.key_chat_outSentCheckRead:
            case Theme.key_chat_outSentCheckReadSelected:
                return withAlpha(white, 0.92f);
            case Theme.key_chat_inTimeText:
                return withAlpha(onSurfaceVariant, 0.82f);
            case Theme.key_chat_inReplyLine:
            case Theme.key_chat_inReplyNameText:
            case Theme.key_chat_stickerReplyLine:
            case Theme.key_chat_messageLinkIn:
                return primary;
            case Theme.key_chat_outReplyLine:
            case Theme.key_chat_outReplyLine2:
            case Theme.key_chat_outReplyNameText:
                return withAlpha(white, 0.90f);
            case Theme.key_chat_messageLinkOut:
                return dark ? 0xFFFFC3D8 : 0xFF8B315E;
            case Theme.key_actionBarDefault:
                return dark ? 0xE51D1720 : 0xEFFFFFFB;
            case Theme.key_actionBarDefaultTitle:
            case Theme.key_actionBarDefaultIcon:
                return onSurface;
            case Theme.key_actionBarDefaultSubtitle:
                return onSurfaceVariant;
            case Theme.key_actionBarDefaultSelector:
                return withAlpha(primary, 0.14f);
            case Theme.key_actionBarDefaultSubmenuBackground:
                return dark ? 0xFF29212B : 0xFFFFF8FB;
            case Theme.key_actionBarDefaultSubmenuItem:
                return onSurface;
            case Theme.key_actionBarDefaultSubmenuItemIcon:
                return primary;
            case Theme.key_actionBarDefaultSubmenuSeparator:
                return withAlpha(primary, 0.16f);
            case Theme.key_chat_inReactionButtonBackground:
                return incomingReaction;
            case Theme.key_chat_outReactionButtonBackground:
                return outgoingReaction;
            case Theme.key_chat_inReactionButtonText:
                return primary;
            case Theme.key_chat_outReactionButtonText:
                return dark ? 0xFFFFC3D8 : 0xFF8B315E;
            case Theme.key_chat_inReactionButtonTextSelected:
                return dark ? 0xFFFFD7E5 : 0xFF702544;
            case Theme.key_chat_outReactionButtonTextSelected:
                return dark ? 0xFFFFD7E5 : 0xFF702544;
            case Theme.key_chat_messagePanelBackground:
                return dark ? 0xF5281F2B : 0xFDFBF5F8;
            case Theme.key_chat_messagePanelShadow:
                return withAlpha(0xFF2C1420, dark ? 0.42f : 0.12f);
            case Theme.key_chat_messagePanelText:
                return onSurface;
            case Theme.key_chat_messagePanelHint:
                return onSurfaceVariant;
            case Theme.key_chat_messagePanelCursor:
            case Theme.key_chat_messagePanelIcons:
                return primary;
            case Theme.key_chat_messagePanelSend:
                return outgoingMiddle;
            case Theme.key_chat_outVoiceSeekbar:
            case Theme.key_chat_outVoiceSeekbarSelected:
            case Theme.key_chat_outVoiceSeekbarFill:
                return withAlpha(white, 0.90f);
            case Theme.key_chat_inVoiceSeekbarSelected:
            case Theme.key_chat_inVoiceSeekbarFill:
                return primary;
            case Theme.key_chat_inVoiceSeekbar:
                return withAlpha(primary, 0.24f);
            default:
                return 0;
        }
    }

    public static BlurredBackgroundColorProvider inputSurfaceProvider() {
        return new BlurredBackgroundColorProvider() {
            @Override public int getShadowColor() {
                return withAlpha(0xFF321528, YumiMd3.isDark() ? 0.34f : 0.14f);
            }
            @Override public int getBackgroundColor() {
                return color(Theme.key_chat_messagePanelBackground, YumiMd3.isDark());
            }
            @Override public int getStrokeColorTop() {
                return withAlpha(YumiMd3.primary(), 0.30f);
            }
            @Override public int getStrokeColorBottom() {
                return withAlpha(YumiMd3.primary(), 0.12f);
            }
            @Override public int getStrokeColorFull() {
                return withAlpha(YumiMd3.primary(), 0.20f);
            }
        };
    }

    private static int blend(int from, int to, float fraction) {
        final float inverse = 1f - fraction;
        return Color.rgb(
                Math.round(Color.red(from) * inverse + Color.red(to) * fraction),
                Math.round(Color.green(from) * inverse + Color.green(to) * fraction),
                Math.round(Color.blue(from) * inverse + Color.blue(to) * fraction));
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255f) << 24);
    }
}
