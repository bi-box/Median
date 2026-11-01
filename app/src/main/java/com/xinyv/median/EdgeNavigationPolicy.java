package com.xinyv.median;

/** Pure gesture decisions kept separate from WebView touch dispatch for deterministic testing. */
final class EdgeNavigationPolicy {
    static final int NONE = 0;
    static final int BACK = 1;
    static final int FORWARD = 2;

    private EdgeNavigationPolicy() {}

    static int start(float x, float width, float edge, boolean canGoBack, boolean canGoForward) {
        if (width <= 0f || edge <= 0f || x < 0f || x > width) return NONE;
        float boundedEdge = Math.min(edge, width * .5f);
        if (x <= boundedEdge && canGoBack) return BACK;
        if (x >= width - boundedEdge && canGoForward) return FORWARD;
        return NONE;
    }

    static boolean shouldCancel(int direction, float dx, float dy,
                                float touchSlop, float verticalCancel) {
        if (direction != BACK && direction != FORWARD) return true;
        float horizontal = Math.abs(dx);
        float vertical = Math.abs(dy);
        boolean correctDirection = direction == BACK ? dx > 0f : dx < 0f;
        return (!correctDirection && horizontal > touchSlop) ||
                (vertical > verticalCancel && vertical > horizontal * .95f);
    }

    static float activationDistance(float width, float minimum, float maximum) {
        return Math.min(maximum, Math.max(minimum, Math.max(0f, width) * .07f));
    }

    static boolean shouldArm(int direction, float dx, float dy, float threshold) {
        boolean correctDirection = direction == BACK ? dx > 0f :
                direction == FORWARD && dx < 0f;
        return correctDirection && Math.abs(dx) >= threshold && Math.abs(dx) > Math.abs(dy) * 1.05f;
    }

    static boolean quickEnough(long elapsedMs) {
        return elapsedMs >= 0L && elapsedMs <= 1600L;
    }
}
