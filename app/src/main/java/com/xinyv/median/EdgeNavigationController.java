package com.xinyv.median;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

/** Lightweight edge gestures that never take WebView touch ownership. */
final class EdgeNavigationController {
    interface Callback {
        boolean canGoBack();
        boolean canGoForward();
        void goBack();
        void goForward();
    }

    private EdgeNavigationController() {}

    static void attach(final WebView view, final Callback callback) {
        final float density = view.getResources().getDisplayMetrics().density;
        final float edge = 24f * density;
        final float distance = 72f * density;
        view.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            boolean fromLeft;
            boolean fromRight;

            @Override public boolean onTouch(View ignored, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    fromLeft = downX <= edge;
                    fromRight = downX >= Math.max(0, view.getWidth() - edge);
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) >= distance && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        if (fromLeft && dx > 0 && callback.canGoBack()) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            callback.goBack();
                        } else if (fromRight && dx < 0 && callback.canGoForward()) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            callback.goForward();
                        }
                    }
                    fromLeft = false;
                    fromRight = false;
                } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    fromLeft = false;
                    fromRight = false;
                }
                return false;
            }
        });
    }
}
