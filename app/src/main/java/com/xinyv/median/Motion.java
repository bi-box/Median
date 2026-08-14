package com.xinyv.median;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.ProgressBar;

/** Small, dependency-free motion layer tuned for Android 8-10. */
final class Motion {
    private static final TimeInterpolator EMPHASIZED = new PathInterpolator(0.20f, 0.00f, 0.00f, 1.00f);
    private static final TimeInterpolator STANDARD = new PathInterpolator(0.20f, 0.00f, 0.20f, 1.00f);

    static void focusPill(View view, boolean focused, boolean reduceMotion) {
        if (view == null) return;
        long duration = reduceMotion ? 90L : 165L;
        view.animate().cancel();
        view.animate()
                .scaleX(focused ? 1.012f : 1f)
                .scaleY(focused ? 1.045f : 1f)
                .translationY(focused ? -1f : 0f)
                .setDuration(duration)
                .setInterpolator(EMPHASIZED)
                .start();
        view.setElevation(focused ? 8f : 0f);
    }

    static void showSheet(final View overlay, final View panel, boolean reduceMotion) {
        if (overlay == null || panel == null) return;
        long duration = reduceMotion ? 105L : 205L;
        overlay.setAlpha(0f);
        panel.setTranslationY(96f);
        panel.setScaleX(.99f);
        panel.setScaleY(.99f);
        overlay.animate().alpha(1f).setDuration(duration).setInterpolator(STANDARD).start();
        panel.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(duration)
                .setInterpolator(EMPHASIZED)
                .withLayer().start();
    }

    static void showPage(final View overlay, final View page, boolean reduceMotion) {
        if (overlay == null || page == null) return;
        long duration = reduceMotion ? 100L : 190L;
        overlay.setAlpha(0f);
        page.setAlpha(.65f);
        page.setScaleX(.975f);
        page.setScaleY(.975f);
        page.setTranslationY(18f);
        overlay.animate().alpha(1f).setDuration(duration).setInterpolator(STANDARD).start();
        page.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(duration)
                .setInterpolator(EMPHASIZED)
                .withLayer().start();
    }

    static void hideOverlay(final View overlay, final View panel, boolean sheet, boolean reduceMotion, final Runnable end) {
        if (overlay == null) {
            if (end != null) end.run();
            return;
        }
        long duration = reduceMotion ? 75L : 145L;
        overlay.animate().cancel();
        if (panel != null) {
            panel.animate().cancel();
            panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            panel.animate().alpha(sheet ? .85f : .55f)
                    .translationY(sheet ? 70f : 10f)
                    .scaleX(sheet ? .985f : .98f)
                    .scaleY(sheet ? .985f : .98f)
                    .setDuration(duration).setInterpolator(STANDARD).start();
        }
        overlay.animate().alpha(0f).setDuration(duration).setInterpolator(STANDARD)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (end != null) end.run();
                    }
                }).start();
    }

    static void animateProgress(ProgressBar bar, int from, int to, boolean reduceMotion) {
        if (bar == null) return;
        if (reduceMotion || from < 0 || to <= from) {
            bar.setProgress(to);
            return;
        }
        // API 26+ keeps one internal drawable transition instead of allocating and
        // stacking a new ObjectAnimator for every WebView progress callback.
        bar.setProgress(to, true);
    }

    private Motion() {}
}
