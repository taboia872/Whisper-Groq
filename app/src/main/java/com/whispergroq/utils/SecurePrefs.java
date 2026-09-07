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
                // Fall back to plain prefs (still better than exposing via API)
                return androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx);
            }
        }
        return securePrefs;
    }

    /** Read API key from secure storage. */
    public static String getApiKey(Context ctx) {
        return getSecure(ctx).getString("groq_api_key", "");
    }

    /** Write API key to secure storage. */
    public static void setApiKey(Context ctx, String key) {
        getSecure(ctx).edit().putString("groq_api_key", key).apply();
    }
}
