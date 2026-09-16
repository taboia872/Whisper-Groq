package com.whispergroq.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.TypedValue;

import androidx.preference.PreferenceManager;

/**
 * Accessibility / display scaling: user-adjustable text scale and
 * button/icon size, applied on top of the system fontScale.
 *
 * Text scale is applied via createConfigurationContext so activities pick
 * it up without recreating; button scale is applied by the IME/capture
 * dialog when building their views (setLayoutParams sizes × factor).
 */
public final class UiPrefs {
    public static final String PREF_TEXT_SCALE = "ui_text_scale";
    public static final String PREF_BUTTON_SCALE = "ui_button_scale";

    /** 0.85f to 1.30f, default 1.0 */
    public static final float TEXT_MIN = 0.85f;
    public static final float TEXT_MAX = 1.30f;
    /** 0.90f to 1.30f, default 1.0 */
    public static final float BUTTON_MIN = 0.90f;
    public static final float BUTTON_MAX = 1.30f;

    private UiPrefs() {}

    public static float textScale(Context ctx) {
        return clamp(getFloat(ctx, PREF_TEXT_SCALE, 1.0f), TEXT_MIN, TEXT_MAX);
    }

    public static float buttonScale(Context ctx) {
        return clamp(getFloat(ctx, PREF_BUTTON_SCALE, 1.0f), BUTTON_MIN, BUTTON_MAX);
    }

    public static boolean isDefault(Context ctx) {
        return textScale(ctx) == 1.0f && buttonScale(ctx) == 1.0f;
    }

    /** Reset both scales to 1.0 (button that forgets nothing else). */
    public static void reset(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        sp.edit().remove(PREF_TEXT_SCALE).remove(PREF_BUTTON_SCALE).apply();
    }

    /** Wrap a context so the app's own text scale multiplies the system one. */
    public static Context applyTextScale(Context base) {
        float f = textScale(base);
        if (f == 1.0f) return base;
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        // Multiply by the system fontScale: respect accessibility settings
        cfg.fontScale = cfg.fontScale * f;
        return base.createConfigurationContext(cfg);
        // Note: activities are the exception — they need recreate() because
        // setContentView already happened with the base configuration.
    }

    /** dp value scaled by the user's button scale (for LayoutParams math). */
    public static int scaledDp(Context ctx, int dp) {
        float px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
        return Math.round(px * buttonScale(ctx));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float getFloat(Context ctx, String key, float def) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        // stored as Int (x100) to survive SharedPreferences.getFloat precision
        return sp.getInt(key, (int) (def * 100)) / 100f;
    }
}
