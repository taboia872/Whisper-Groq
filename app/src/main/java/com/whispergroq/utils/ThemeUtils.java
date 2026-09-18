package com.whispergroq.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.WindowInsetsController;

import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.whispergroq.R;

public class ThemeUtils {

    public static final String PREF_THEME_MODE = "theme_mode";
    public static final String PREF_ACCENT = "accent_color";
    /** Legacy single-dimension modes kept for pref migration. */
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_AUTO_DYNAMIC = "auto_dynamic";
    /** Two-dimension mode: light/dark follows system, accent user-chosen. */
    public static final String MODE_AUTO = "auto";

    public static final String ACCENT_PURPLE = "purple";
    public static final String ACCENT_BLUE = "blue";
    public static final String ACCENT_LINK = "link";
    public static final String ACCENT_BROWN = "brown";
    public static final String ACCENT_SLATE = "slate";
    public static final String ACCENT_DEFAULT = ACCENT_PURPLE;

    private ThemeUtils() {}

    /** Map a stored theme mode to an AppCompatDelegate night-mode constant. */
    public static int nightModeFor(String mode) {
        if (MODE_LIGHT.equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        if (MODE_DARK.equals(mode)) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /** Store mode + accent together and re-apply (activities recreate). */
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
     * is on. Call in onCreate BEFORE setContentView.
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

    /** Compact signature of the theme prefs, to detect changes cheaply. */
    public static String themeSignature(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(PREF_THEME_MODE, MODE_AUTO) + "|" + sp.getString(PREF_ACCENT, ACCENT_DEFAULT);
    }

    /** The user's accent, independent of mode (purple/teal/link). */
    public static String accent(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(PREF_ACCENT, ACCENT_DEFAULT);
    }

    /** ThemeOverlay resId for the chosen accent. 0 = none (dynamic colors). */
    @StyleRes
    public static int accentOverlayId(Context context) {
        if (isDynamic(context)) return 0;
        String a = accent(context);
        if (ACCENT_BLUE.equals(a)) return R.style.AccentOverlay_Blue;
        if (ACCENT_LINK.equals(a)) return R.style.AccentOverlay_Link;
        if (ACCENT_BROWN.equals(a)) return R.style.AccentOverlay_Brown;
        if (ACCENT_SLATE.equals(a)) return R.style.AccentOverlay_Slate;
        return R.style.AccentOverlay_Purple;
    }

    /** Wrap a themed context with the accent overlay. */
    public static Context wrapAccentIfNeeded(Context base) {
        int overlayId = accentOverlayId(base);
        if (overlayId == 0) return base;
        return new android.view.ContextThemeWrapper(base, overlayId);
    }

    public static void setStatusBarAppearance(Activity activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int nightModeFlags = activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
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
