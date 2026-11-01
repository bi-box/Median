// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.ProgressBar;

/** Small, dependency-free motion layer tuned for Android 8-10. */
final class Motion {
    // Keep transitions short so a busy WebView does not lose input frames.
    private static final TimeInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);
    private static final TimeInterpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.0f, 1.0f);
    private static final TimeInterpolator STANDARD = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    private static final TimeInterpolator STANDARD_ACCELERATE = new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f);
    private static final TimeInterpolator DECELERATE = new PathInterpolator(0.0f, 0.0f, 0.2f, 1.0f);

    // Keep startup motion visible without holding the first useful page behind a long fade.
    private static final long STARTUP_FADE_IN = 140L;
    private static final long STARTUP_FADE_OUT = 120L;

    static void focusPill(View view, boolean focused, boolean reduceMotion) {
        if (view == null) return;
        long duration = reduceMotion ? 90L : 180L;
        cancelAnimation(view);
        view.animate()
                .scaleX(focused ? 1.012f : 1f)
                .scaleY(focused ? 1.045f : 1f)
                .translationY(focused ? -1f : 0f)
                .setDuration(duration)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .start();
        view.setElevation(focused ? 8f : 0f);
    }

    static void showSheet(final View overlay, final View panel, boolean reduceMotion) {
        if (overlay == null || panel == null) return;
        long duration = reduceMotion ? 90L : 160L;
        cancelAnimation(overlay);
        cancelAnimation(panel);
        overlay.setAlpha(0f);
        panel.setAlpha(1f);
        panel.setTranslationY(56f);
        overlay.animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(STANDARD)
                .start();
        panel.animate()
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .start();
    }

    static void showPage(final View overlay, final View page, boolean reduceMotion) {
        if (overlay == null || page == null) return;
        long duration = reduceMotion ? 80L : 140L;
        cancelAnimation(overlay);
        cancelAnimation(page);
        overlay.setAlpha(0f);
        page.setAlpha(.6f);
        page.setTranslationY(12f);
        overlay.animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(STANDARD)
                .start();
        page.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withLayer()
                .start();
    }

    static void hideOverlay(final View overlay, final View panel, boolean sheet, boolean reduceMotion, final Runnable end) {
        if (overlay == null) {
            if (end != null) end.run();
            return;
        }
        long duration = reduceMotion ? 70L : 120L;
        cancelAnimation(overlay);
        if (panel != null) {
            cancelAnimation(panel);
            // Continue from the finger/entrance position. An absolute target can pull a
            // sheet that has already been dragged far down back upward while closing.
            float exitTranslation = panel.getTranslationY() + (sheet ? 70f : 10f);
            panel.animate()
                    .alpha(Math.min(panel.getAlpha(), sheet ? .8f : .5f))
                    .translationY(exitTranslation)
                    .setDuration(duration)
                    .setInterpolator(STANDARD_ACCELERATE)
                    .withLayer()
                    .start();
        }
        overlay.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(STANDARD)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        // End actions do not run on cancel. Release the child layer before
                        // its parent is removed, even if its last frame has not run yet.
                        if (panel != null) cancelAnimation(panel);
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

    static void fadeInStartup(final View view, boolean reduceMotion, final Runnable end) {
        if (view == null) return;
        long duration = reduceMotion ? 120L : STARTUP_FADE_IN;
        cancelAnimation(view);
        view.setAlpha(0f);
        view.animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .withEndAction(end)
                .start();
    }

    static void fadeOutStartup(final View view, boolean reduceMotion, final Runnable end) {
        if (view == null) {
            if (end != null) end.run();
            return;
        }
        long duration = reduceMotion ? 100L : STARTUP_FADE_OUT;
        cancelAnimation(view);
        view.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(STANDARD_ACCELERATE)
                .withEndAction(end)
                .start();
    }

    private static void cancelAnimation(View view) {
        // ViewPropertyAnimator retains its listener. Clear it before cancel(), which
        // dispatches both onAnimationCancel and onAnimationEnd to an old listener.
        // withLayer() restores the original layer on both completion and cancellation.
        view.animate().setListener(null).withEndAction(null).cancel();
    }

    private Motion() {}
}
