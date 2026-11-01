// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Native-feeling edge navigation that observes, but never consumes, WebView touches.
 *
 * <p>The controller arms as soon as a deliberate horizontal gesture crosses the adaptive
 * threshold, gives one quiet tick, and still lets the page own the entire touch stream. A small
 * diagonal component is tolerated; clear vertical scrolling, multi-touch and reversals cancel.</p>
 */
final class EdgeNavigationController {
    private static final float EDGE_DP = 40f;
    interface Callback {
        boolean isEnabled();
        boolean canGoBack();
        boolean canGoForward();
        default void onUserGesture() {}
        default void onTouchEvent(MotionEvent event) {}
        void goBack();
        void goForward();
    }

    private EdgeNavigationController() {}

    static void attach(final WebView view, final Callback callback) {
        final float density = view.getResources().getDisplayMetrics().density;
        // Keep the start zone reachable without swallowing ordinary page controls. A slightly
        // shorter commit path makes one-handed navigation deliberate but no longer tiring.
        final float edge = EDGE_DP * density;
        // Keep the commit path short enough for a natural one-handed swipe. The edge zone and
        // vertical cancellation remain unchanged; only the horizontal activation distance is
        // relaxed so ordinary short swipes are not discarded before the page can respond.
        final float minimumDistance = 24f * density;
        final float maximumDistance = 52f * density;
        final float touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        final float verticalCancel = Math.max(28f * density, touchSlop * 2.4f);
        view.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            long downAt;
            boolean fromLeft;
            boolean fromRight;
            boolean cancelled;
            boolean armed;
            int direction = EdgeNavigationPolicy.NONE;
            int pointerId = MotionEvent.INVALID_POINTER_ID;

            @Override public boolean onTouch(View ignored, MotionEvent event) {
                callback.onTouchEvent(event);
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    callback.onUserGesture();
                    downX = event.getX();
                    downY = event.getY();
                    downAt = event.getEventTime();
                    pointerId = event.getPointerId(0);
                    direction = callback.isEnabled() ? EdgeNavigationPolicy.start(downX, view.getWidth(), edge,
                            callback.canGoBack(), callback.canGoForward()) : EdgeNavigationPolicy.NONE;
                    fromLeft = direction == EdgeNavigationPolicy.BACK;
                    fromRight = direction == EdgeNavigationPolicy.FORWARD;
                    cancelled = direction == EdgeNavigationPolicy.NONE;
                    armed = false;
                } else if (!callback.isEnabled() || action == MotionEvent.ACTION_POINTER_DOWN ||
                        event.getPointerCount() > 1) {
                    cancel();
                } else if (action == MotionEvent.ACTION_POINTER_UP &&
                        event.getPointerId(event.getActionIndex()) == pointerId) {
                    cancel();
                } else if (action == MotionEvent.ACTION_MOVE && !cancelled) {
                    int index = event.findPointerIndex(pointerId);
                    if (index < 0) {
                        cancel();
                    } else {
                        float dx = event.getX(index) - downX;
                        float dy = event.getY(index) - downY;
                        float horizontal = Math.abs(dx);
                        if (EdgeNavigationPolicy.shouldCancel(direction, dx, dy, touchSlop, verticalCancel)) {
                            cancel();
                        } else {
                            float threshold = EdgeNavigationPolicy.activationDistance(view.getWidth(),
                                    minimumDistance, maximumDistance);
                            boolean nextArmed = EdgeNavigationPolicy.shouldArm(direction, dx, dy, threshold);
                            if (nextArmed && !armed) {
                                armed = true;
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            } else if (armed && horizontal < threshold * .62f) {
                                armed = false;
                            }
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP) {
                    boolean quickEnough = EdgeNavigationPolicy.quickEnough(event.getEventTime() - downAt);
                    if (!cancelled && armed && quickEnough) {
                        if (fromLeft && callback.canGoBack()) callback.goBack();
                        else if (fromRight && callback.canGoForward()) callback.goForward();
                    }
                    cancel();
                } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    cancel();
                }
                return false;
            }

            private void cancel() {
                fromLeft = false;
                fromRight = false;
                cancelled = true;
                armed = false;
                direction = EdgeNavigationPolicy.NONE;
                pointerId = MotionEvent.INVALID_POINTER_ID;
            }
        });
        // MIUI's full-screen back recognizer can consume the first edge events before WebView
        // sees them. Exclude only the side that currently has browser history; when there is no
        // history the system back gesture remains untouched.
        updateSystemGestureExclusion(view, callback.isEnabled(), callback.canGoBack(),
                callback.canGoForward());
    }

    static void updateSystemGestureExclusion(WebView view, boolean enabled,
                                             boolean canGoBack, boolean canGoForward) {
        if (view == null || android.os.Build.VERSION.SDK_INT < 29) return;
        if (!enabled || view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.setSystemGestureExclusionRects(Collections.<Rect>emptyList());
            return;
        }
        float density = view.getResources().getDisplayMetrics().density;
        int width = Math.max(1, Math.round(EDGE_DP * density));
        width = Math.min(width, view.getWidth() / 2);
        if (width <= 0) {
            view.setSystemGestureExclusionRects(Collections.<Rect>emptyList());
            return;
        }
        if (!canGoBack && !canGoForward) {
            view.setSystemGestureExclusionRects(Collections.<Rect>emptyList());
            return;
        }
        List<Rect> rects = new ArrayList<>(2);
        if (canGoBack) rects.add(new Rect(0, 0, width, view.getHeight()));
        if (canGoForward) rects.add(new Rect(view.getWidth() - width, 0,
                view.getWidth(), view.getHeight()));
        view.setSystemGestureExclusionRects(rects);
    }
}
