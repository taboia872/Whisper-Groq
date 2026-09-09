package com.whispergroq.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

public class ThemeUtils {

    public static final String PREF_THEME_MODE = "theme_mode";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_DYNAMIC = "dynamic";

    private ThemeUtils() {}

    /** Map a stored theme mode to an AppCompatDelegate night-mode constant. */
    public static int nightModeFor(String mode) {
        if (MODE_LIGHT.equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        if (MODE_DARK.equals(mode)) return AppCompatDelegate.MODE_NIGHT_YES;
        // MODE_SYSTEM and MODE_DYNAMIC both follow the device setting.
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

    /**
     * Apply the dynamic (wallpaper) theme to a single activity. Call this in
     * {@code onCreate} BEFORE {@code setContentView}, after the night mode has
     * been resolved. No-op for non-dynamic modes.
     */
    public static void applyDynamicIfNeeded(Activity activity) {
        if (isDynamicTheme(activity)) {
            DynamicColors.applyToActivitiesIfAvailable((Application) activity.getApplicationContext());
        }
    }

    /** True if the user picked the wallpaper-based dynamic theme. */
    public static boolean isDynamicTheme(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return MODE_DYNAMIC.equals(sp.getString(PREF_THEME_MODE, MODE_SYSTEM));
    }

    /**
     * Wrap a context so that ?attr/color* resolves to wallpaper-based dynamic
     * colors when the user selected the dynamic theme. Used by the IME service
     * (not an Activity, so DynamicColors does not apply automatically). Falls
     * back to the plain context for non-dynamic modes.
     */
    public static Context wrapImeContext(Context base) {
        if (isDynamicTheme(base)) {
            return DynamicColors.wrapContextIfAvailable(base);
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