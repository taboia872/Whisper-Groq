package com.whispergroq.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Wrapper for secure preference storage.
 * groq_api_key is stored encrypted with AES-256 via Android Keystore.
 * All other prefs go to default SharedPreferences (no sensitivity).
 */
public class SecurePrefs {

    private static final String TAG = "SecurePrefs";
    private static final String PREFS_SECURE = "secure_prefs";
    private static SharedPreferences securePrefs;

    public static synchronized SharedPreferences getSecure(Context ctx) {
        if (securePrefs == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(ctx)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                securePrefs = EncryptedSharedPreferences.create(
                        ctx,
                        PREFS_SECURE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Failed to create encrypted prefs, falling back", e);
                return androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx);
            }
        }
        return securePrefs;
    }

    // ---------- Multi-key support ----------

    /** How many keys are configured (1-3). */
    public static int getKeyCount(Context ctx) {
        return getSecure(ctx).getInt("api_key_count", 1);
    }

    /** Set number of key slots (1-3). Each is secured in EncryptedSharedPreferences. */
    public static void setKeyCount(Context ctx, int count) {
        int c = Math.max(1, Math.min(3, count));
        getSecure(ctx).edit().putInt("api_key_count", c).apply();
    }

    /** Read API key for slot 0..N-1, null if not set. */
    public static String getApiKey(Context ctx) {
        return getApiKey(ctx, 0);
    }

    public static String getApiKey(Context ctx, int index) {
        String key = getSecure(ctx).getString("groq_api_key_" + index, "");
        return key.isEmpty() ? null : key;
    }

    /** Write API key for slot 0..N-1. */
    public static void setApiKey(Context ctx, String key) {
        setApiKey(ctx, 0, key);
    }

    public static void setApiKey(Context ctx, int index, String key) {
        getSecure(ctx).edit().putString("groq_api_key_" + index, key).apply();
    }

    /** Get active slot index (i.e. which one is currently being used). */
    public static int getActiveKeyIndex(Context ctx) {
        return getSecure(ctx).getInt("active_key_index", 0);
    }

    public static void setActiveKeyIndex(Context ctx, int index) {
        getSecure(ctx).edit().putInt("active_key_index", index).apply();
    }

    /** Returns the currently active API key (for slot), or falls back to slot 0. */
    public static String getCurrentApiKey(Context ctx) {
        int idx = getActiveKeyIndex(ctx);
        String k = getApiKey(ctx, idx);
        if (k == null) {
            setActiveKeyIndex(ctx, 0);
            k = getApiKey(ctx, 0);
        }
        return k == null ? "" : k;
    }

    /** Mark current key as failed and rotate to next (wraps around). */
    public static void rotateKey(Context ctx) {
        int count = getKeyCount(ctx);
        int next = (getActiveKeyIndex(ctx) + 1) % count;
        setActiveKeyIndex(ctx, next);
    }

    /** Clear the active slot and go to next. */
    public static void clearActiveApiKey(Context ctx) {
        setApiKey(ctx, getActiveKeyIndex(ctx), "");
    }
}
