package com.xinyv.median;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Small, dependency-free vector icon view. Paths are decoded once and shared by every button. */
final class BrowserIconView extends View {
    static final int BACK = 1, FORWARD = 2, HOME = 3, TABS = 4, MENU = 5, RELOAD = 6,
            SHIELD = 7, SEARCH = 8, CLOSE = 9, PLUS = 10, KEY = 11, SPEED = 12,
            STORAGE = 13, DESKTOP = 14, SHARE = 15, INFO = 16, SCRIPT = 17,
            DOWNLOAD = 18, SETTINGS = 19, BOOKMARK = 20, HISTORY = 21,
            APPEARANCE = 22, COOKIE = 23, STARTUP = 24, DELETE = 25, CLEAN = 26;

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    /* M/L: one point, C: three points, Z: close. Coordinates use a signed 1/100 grid. */
    private static final String[] DATA = {
        null,
        "MMgL0gMMgLeOMMgLey",
        "MMgL0gM0gLiOM0gLiy",
        "MHeLgIL5eMOaLO2Ly2Lya",
        null,
        "MLRL1RMLgL1gMLvL1v",
        "M0SCoFPKKeCFxb7txL0qM0SL0jLkd",
        "MgFCsM1M3NL0lCx1k8g_Cc8P1MlLJNCUMYJgF",
        "McMCnMwVwgCwrn0c0CR0IrIgCIVRMcMMosL37",
        "MPPLxxMxPLPx",
        "MMgL0gMgMLg0",
        "MVNCcNhShZChgclVlCOlJgJZCJSONVNMfhL24MsuLyoMy0L4u",
        "MHsCMWWNgNCqN0W5sMglLvWMenLij",
        "MLRCLI1I1RC1aLaLRLLwCL5151wL1RMLgCLp1p1g",
        "MHML5ML5vLHvZMgvLg5MT5Lt5",
        "MPgLxPMPgLxxMPaCXaXmPmCHmHaPaMxJC5J5VxVCpVpJxJMxrC5r53x3Cp3prxr",
        "MgJCtJ3T3gC3tt3g3CT3JtJgCJTTJgJMgULgWMgeLgv",
        "MZNLNgLZzMnNLzgLnzMkJLc3",
        "MgHLgtMTfLgtLtfMKuLK5L25L2u",
        "MgJCtJ3T3gC3tt3g3CT3JtJgCJTTJgJMgWCmWqaqgCqmmqgqCaqWmWgCWaaWgWM3gL9gMJgLDgMg3Lg9MgJLgDMwwL11MQQLLLMwQL1LMQwLL1",
        "MOHLyHLy6LguLO6Z",
        "MgICtI4T4gC4tt4g4CT4ItIgCITTIgIMggLgTMggLsn",
        "MgECjYod8gCojjog8CdoYjEgCYddYgEZ",
        "MgICtI4T4gC4tt4g4CT4ItIgCITTIgIMYYLZZMppLqqMVqLWr",
        "M0SCoFPKKeCFxb7txC2r5g0SMgELgi",
        "MQTLwTLw5LQ5ZMKLL2LMZELnEMabLawMmbLmw",
        "MO0LtMMJrLY4MNkLf0M0LL1MM4YL5Z"
    };
    private static final Path[] PATHS = new Path[DATA.length];
    private static final Typeface BOLD = Typeface.create("sans", Typeface.BOLD);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final float minimumStrokeWidth;
    private int icon;
    private int count = 1;
    private String countText = "1";
    private boolean active;
    private int tintColor = Color.rgb(60, 64, 67);

    BrowserIconView(Context context, int icon) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        minimumStrokeWidth = 1.75f * density;
        this.icon = icon;
        setClickable(true);
        setFocusable(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setMinimumWidth(dp(40));
        setMinimumHeight(dp(40));
    }

    void setIcon(int value) { if (icon != value) { icon = value; invalidate(); } }
    void setActive(boolean value) { if (active != value) { active = value; invalidate(); } }
    void setTintColor(int value) { if (tintColor != value) { tintColor = value; invalidate(); } }

    void setCount(int value) {
        if (count == value) return;
        count = value;
        countText = value > 99 ? "99" : String.valueOf(Math.max(1, value));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float width = getWidth(), height = getHeight(), size = Math.min(width, height);
        float cx = width * .5f, cy = height * .5f;
        paint.setColor(active ? Color.rgb(26, 115, 232) : tintColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(minimumStrokeWidth, size * .044f));
        if (icon == TABS) {
            rect.set(cx - size * .22f, cy - size * .22f, cx + size * .22f, cy + size * .22f);
            canvas.drawRoundRect(rect, size * .06f, size * .06f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(BOLD);
            paint.setTextSize(count > 9 ? size * .22f : size * .27f);
            canvas.drawText(countText, cx, cy - (paint.ascent() + paint.descent()) * .5f, paint);
            return;
        }
        Path path = path(icon);
        if (path == null) return;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(size, size);
        paint.setStrokeWidth(Math.max(.044f, minimumStrokeWidth / size));
        canvas.drawPath(path, paint);
        if (icon == SHIELD && active) {
            canvas.drawLine(-.10f, 0f, -.02f, .08f, paint);
            canvas.drawLine(-.02f, .08f, .13f, -.09f, paint);
        }
        canvas.restore();
    }

    private static Path path(int icon) {
        if (icon <= 0 || icon >= DATA.length || DATA[icon] == null) return null;
        Path cached = PATHS[icon];
        if (cached != null) return cached;
        String data = DATA[icon];
        Path result = new Path();
        for (int i = 0; i < data.length();) {
            char command = data.charAt(i++);
            if (command == 'Z') {
                result.close();
            } else if (command == 'C') {
                result.cubicTo(value(data.charAt(i++)), value(data.charAt(i++)),
                        value(data.charAt(i++)), value(data.charAt(i++)),
                        value(data.charAt(i++)), value(data.charAt(i++)));
            } else {
                float x = value(data.charAt(i++)), y = value(data.charAt(i++));
                if (command == 'M') result.moveTo(x, y); else result.lineTo(x, y);
            }
        }
        PATHS[icon] = result;
        return result;
    }

    private static float value(char encoded) { return (ALPHABET.indexOf(encoded) - 32) * .01f; }
    private int dp(float value) { return Math.round(value * density); }
}
