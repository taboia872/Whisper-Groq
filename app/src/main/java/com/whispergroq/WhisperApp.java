package com.whispergroq;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.whispergroq.utils.ThemeUtils;

public class WhisperApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String mode = sp.getString(ThemeUtils.PREF_THEME_MODE, ThemeUtils.MODE_SYSTEM);
        // Migrate the removed "dynamic" mode to auto-dynamic (same behavior).
        if ("dynamic".equals(mode)) {
            mode = ThemeUtils.MODE_AUTO_DYNAMIC;
            sp.edit().putString(ThemeUtils.PREF_THEME_MODE, mode).apply();
        }
        AppCompatDelegate.setDefaultNightMode(ThemeUtils.nightModeFor(mode));
    }
}