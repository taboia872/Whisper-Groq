package com.whispergroq.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public class ThemeUtils {

    public static final String PREF_THEME_MODE = "theme_mode";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_SYSTEM = "system";
    /** Follow system light/dark AND use wallpaper-based dynamic colors. */
    public static final String MODE_AUTO_DYNAMIC = "auto_dynamic";

    private ThemeUtils() {}

    /** Map a stored theme mode to an AppCompatDelegate night-mode constant. */
    public static int nightModeFor(String mode) {
        if (MODE_LIGHT.equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        if (MODE_DARK.equals(mode)) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /** Re-read the stored mode and apply night mode, then recreate. */
    public static void applyTheme(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = sp.getString(PREF_THEME_MODE, MODE_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(nightModeFor(mode));
        if (context instanceof Activity) {
            ((Activity) context).recreate();
        }
    }

    /** True when the user picked the wallpaper-based dynamic mode. */
    public static boolean isDynamic(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return MODE_AUTO_DYNAMIC.equals(sp.getString(PREF_THEME_MODE, MODE_SYSTEM));
    }

    /**
     * Apply dynamic (wallpaper) colors to an activity when auto-dynamic mode
     * is on. Call in onCreate BEFORE setContentView. Old "dynamic" prefs map
     * to the same behavior.
     */
    public static void applyDynamicIfNeeded(Activity activity) {
        if (isDynamic(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(activity);
        }
    }

    /** Wrap a (Material3-themed) context so ?attr/color* resolve to wallpaper
     *  colors in auto-dynamic mode. Used by the IME service. */
    public static Context wrapDynamicIfNeeded(Context base) {
        if (isDynamic(base) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return com.google.android.material.color.DynamicColors.wrapContextIfAvailable(base);
        }
        return base;
    }

    public static void setStatusBarAppearance(Activity activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int nightModeFlags = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);
            WindowInsetsController insetsController = activity.getWindow().getInsetsController();
            if (insetsController != null) {
                insetsController.setSystemBarsAppearance(
                        isDarkMode ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        }
    }
}