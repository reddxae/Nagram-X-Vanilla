package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;

public class BlurredFrameLayout extends FrameLayout {

    public static final int TRANSLUCENT_PANEL_DEFAULT = 0;
    public static final int TRANSLUCENT_PANEL_HEADER = 1;
    public static final int TRANSLUCENT_PANEL_BOTTOM = 2;

    protected final SizeNotifierFrameLayout sizeNotifierFrameLayout;
    protected Paint backgroundPaint;
    public int backgroundColor = Color.TRANSPARENT;
    public int backgroundPaddingBottom;
    public int backgroundPaddingTop;
    public boolean isTopView = true;
    public boolean drawBlur = true;
    protected int translucentPanelMode = TRANSLUCENT_PANEL_DEFAULT;

    public BlurredFrameLayout(@NonNull Context context, SizeNotifierFrameLayout sizeNotifierFrameLayout) {
        super(context);
        this.sizeNotifierFrameLayout = sizeNotifierFrameLayout;
    }

    public void setTranslucentPanelMode(int translucentPanelMode) {
        this.translucentPanelMode = translucentPanelMode;
        updateBackgroundColor();
    }

    protected boolean useTranslucentPanelBackground() {
        return switch (translucentPanelMode) {
            case TRANSLUCENT_PANEL_HEADER -> SharedConfig.isChatHeaderTranslucentEnabled();
            case TRANSLUCENT_PANEL_BOTTOM -> SharedConfig.isChatBottomTranslucentEnabled();
            default -> SharedConfig.chatBlurEnabled();
        };
    }

    protected static int makeOpaqueColor(int color) {
        if (Color.alpha(color) == 0) {
            return color;
        }
        return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
    }

    private android.graphics.Rect blurBounds = new android.graphics.Rect();

    private void updateBackgroundColor() {
        if (translucentPanelMode != TRANSLUCENT_PANEL_DEFAULT && sizeNotifierFrameLayout != null) {
            super.setBackgroundColor(Color.TRANSPARENT);
        } else if (SharedConfig.chatBlurEnabled() && sizeNotifierFrameLayout != null) {
            super.setBackgroundColor(Color.TRANSPARENT);
        } else {
            super.setBackgroundColor(backgroundColor);
        }
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (sizeNotifierFrameLayout != null && drawBlur && backgroundColor != Color.TRANSPARENT) {
            if (backgroundPaint == null) {
                backgroundPaint = new Paint();
            }
            blurBounds.set(0, backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight() - backgroundPaddingBottom);
            if (useTranslucentPanelBackground()) {
                backgroundPaint.setColor(backgroundColor);
                float y = 0;
                View view = this;
                while (view != sizeNotifierFrameLayout) {
                    y += view.getY();
                    ViewParent parent = view.getParent();
                    if (parent instanceof View) {
                        view = (View) parent;
                    } else {
                        super.dispatchDraw(canvas);
                        return;
                    }
                }
                sizeNotifierFrameLayout.drawBlurRect(canvas, y, blurBounds, backgroundPaint, isTopView);
            } else if (translucentPanelMode != TRANSLUCENT_PANEL_DEFAULT) {
                backgroundPaint.setColor(makeOpaqueColor(backgroundColor));
                canvas.drawRect(blurBounds, backgroundPaint);
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void setBackgroundColor(int color) {
        backgroundColor = color;
        updateBackgroundColor();
    }

    @Override
    protected void onAttachedToWindow() {
        if (useTranslucentPanelBackground() && sizeNotifierFrameLayout != null) {
            sizeNotifierFrameLayout.blurBehindViews.add(this);
        }
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sizeNotifierFrameLayout != null) {
            sizeNotifierFrameLayout.blurBehindViews.remove(this);
        }
        super.onDetachedFromWindow();
    }
}
