package com.xinyv.median;

/** Produces short, non-empty UI-safe error messages without repeating formatting code. */
final class ErrorText {
    private ErrorText() {}

    static String message(Throwable error, int maxLength) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        if (message == null || (message = message.trim()).length() == 0)
            message = error.getClass().getSimpleName();
        return maxLength > 0 && message.length() > maxLength
                ? message.substring(0, maxLength) : message;
    }
}
