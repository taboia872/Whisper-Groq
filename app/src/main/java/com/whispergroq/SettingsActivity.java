package com.whispergroq;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.slider.Slider;
import com.whispergroq.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    private SharedPreferences sp = null;
    private final String[] MODELS = {
            "whisper-large-v3-turbo",
            "whisper-large-v3",
            "distil-whisper-large-v3-en"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Accent overlay (purple/teal/link) on top of the manifest theme.
        // In Dynamic mode the overlay is skipped and wallpaper colors win.
        int overlayId = ThemeUtils.accentOverlayId(this);
        if (overlayId != 0) getTheme().applyStyle(overlayId, true);
        ThemeUtils.applyDynamicIfNeeded(this);
        setContentView(R.layout.activity_settings);
        ThemeUtils.setStatusBarAppearance(this);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        sp = PreferenceManager.getDefaultSharedPreferences(this);

        // Migrate plaintext API key to encrypted prefs on first run
        String legacyKey = sp.getString("groq_api_key", "");
        if (!legacyKey.isEmpty()) {
            com.whispergroq.utils.SecurePrefs.setApiKey(this, legacyKey);
            sp.edit().remove("groq_api_key").apply();
        }

        // API Keys (encrypted, up to 3 slots — round-robin per transcription)
        final EditText[] keyFields = {
                findViewById(R.id.editApiKey),
                findViewById(R.id.editApiKey2),
                findViewById(R.id.editApiKey3)
        };
        for (int i = 0; i < 3; i++) {
            String k = com.whispergroq.utils.SecurePrefs.getApiKey(this, i);
            keyFields[i].setText(k != null ? k : "");
        }
        Spinner spinnerKeyCount = findViewById(R.id.spinnerKeyCount);
        Integer[] counts = {1, 2, 3};
        ArrayAdapter<Integer> countAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, counts);
        countAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerKeyCount.setAdapter(countAdapter);
        int keyCount = com.whispergroq.utils.SecurePrefs.getKeyCount(this);
        spinnerKeyCount.setSelection(Math.max(0, Math.min(2, keyCount - 1)));
        final Runnable[] showKeyFields = new Runnable[1];
        showKeyFields[0] = () -> {
            int n = com.whispergroq.utils.SecurePrefs.getKeyCount(this);
            for (int i = 0; i < 3; i++) {
                keyFields[i].setVisibility(i < n ? View.VISIBLE : View.GONE);
            }
        };
        showKeyFields[0].run();
        spinnerKeyCount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int n = position + 1;
                if (n != com.whispergroq.utils.SecurePrefs.getKeyCount(SettingsActivity.this)) {
                    com.whispergroq.utils.SecurePrefs.setKeyCount(SettingsActivity.this, n);
                    showKeyFields[0].run();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ImageButton infoApiKey = findViewById(R.id.infoApiKey);
        infoApiKey.setOnClickListener(v -> {
            String html = getString(R.string.settings_api_key_hint);
            CharSequence styled = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY);
            com.whispergroq.utils.InfoTooltip.show(this, v, styled);
        });

        // Model spinner
        Spinner spinnerModel = findViewById(R.id.spinnerModel);
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MODELS);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(modelAdapter);
        String currentModel = sp.getString("groq_model", "whisper-large-v3-turbo");
        int modelIndex = 0;
        for (int i = 0; i < MODELS.length; i++) {
            if (MODELS[i].equals(currentModel)) { modelIndex = i; break; }
        }
        spinnerModel.setSelection(modelIndex);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sp.edit().putString("groq_model", MODELS[position]).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Theme mode spinner (Dark / Light / Auto / Dynamic) + accent color
        Spinner spinnerTheme = findViewById(R.id.spinnerTheme);
        final String[] THEME_MODES = {
                com.whispergroq.utils.ThemeUtils.MODE_DARK,
                com.whispergroq.utils.ThemeUtils.MODE_LIGHT,
                com.whispergroq.utils.ThemeUtils.MODE_AUTO,
                com.whispergroq.utils.ThemeUtils.MODE_AUTO_DYNAMIC
        };
        String[] themeLabels = {
                getString(R.string.theme_dark),
                getString(R.string.theme_light),
                getString(R.string.theme_auto),
                getString(R.string.theme_dynamic)
        };
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themeLabels);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTheme.setAdapter(themeAdapter);
        String currentTheme = sp.getString(com.whispergroq.utils.ThemeUtils.PREF_THEME_MODE,
                com.whispergroq.utils.ThemeUtils.MODE_AUTO);
        // migrate legacy modes to the new mode x color structure
        if (com.whispergroq.utils.ThemeUtils.MODE_SYSTEM.equals(currentTheme)) {
            currentTheme = com.whispergroq.utils.ThemeUtils.MODE_AUTO;
        } else if (com.whispergroq.utils.ThemeUtils.MODE_AUTO_DYNAMIC.equals(currentTheme)) {
            currentTheme = com.whispergroq.utils.ThemeUtils.MODE_AUTO_DYNAMIC; // stays
        }
        int themeIndex = 2; // default AUTO
        for (int i = 0; i < THEME_MODES.length; i++) {
            if (THEME_MODES[i].equals(currentTheme)) { themeIndex = i; break; }
        }
        spinnerTheme.setSelection(themeIndex);
        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = THEME_MODES[position];
                String previous = sp.getString(com.whispergroq.utils.ThemeUtils.PREF_THEME_MODE,
                        com.whispergroq.utils.ThemeUtils.MODE_AUTO);
                if (!selected.equals(previous)) {
                    sp.edit().putString(com.whispergroq.utils.ThemeUtils.PREF_THEME_MODE, selected).apply();
                    // Toggle the accent circles BEFORE recreating: in Dynamic
                    // mode the accent comes from the wallpaper. (Local lookup —
                    // the accent row is declared later.)
                    LinearLayout accentRowLocal = findViewById(R.id.accentRow);
                    boolean dyn = com.whispergroq.utils.ThemeUtils.MODE_AUTO_DYNAMIC.equals(selected);
                    applyAccentRowEnabled(accentRowLocal, !dyn);
                    // Apply immediately so the user sees the change, then recreate.
                    com.whispergroq.utils.ThemeUtils.applyTheme(SettingsActivity.this);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Accent color picker: row of colored circles (tap to select).
        // Grayed out in Dynamic mode (colors come from the wallpaper).
        final String[] ACCENTS = {
                com.whispergroq.utils.ThemeUtils.ACCENT_PURPLE,
                com.whispergroq.utils.ThemeUtils.ACCENT_BLUE,
                com.whispergroq.utils.ThemeUtils.ACCENT_LINK,
                com.whispergroq.utils.ThemeUtils.ACCENT_BROWN,
                com.whispergroq.utils.ThemeUtils.ACCENT_SLATE
        };
        LinearLayout accentRow = findViewById(R.id.accentRow);
        android.widget.ImageView[] accentCircles = buildAccentCircles(ACCENTS);
        for (android.widget.ImageView c : accentCircles) accentRow.addView(c);

        // In Dynamic mode the accent comes from the wallpaper — gray out the row.
        applyAccentRowEnabled(accentRow, !com.whispergroq.utils.ThemeUtils.isDynamic(this));

        // Silence slider (Material 3)
        Slider minSilence = findViewById(R.id.settings_min_silence);
        TextView valueMinSilence = findViewById(R.id.valueMinSilence);
        int silenceMs = sp.getInt("silenceDurationMs", 800);
        minSilence.setValue(silenceMs);
        valueMinSilence.setText(silenceMs + " ms");
        minSilence.addOnChangeListener((slider, value, fromUser) -> {
            int v = (int) value;
            valueMinSilence.setText(v + " ms");
            sp.edit().putInt("silenceDurationMs", v).apply();
        });

        // Max recording seconds (Material 3 slider)
        Slider maxSeconds = findViewById(R.id.settings_max_seconds);
        TextView valueMaxSeconds = findViewById(R.id.valueMaxSeconds);
        int maxSec = sp.getInt("max_recording_seconds", 60);
        maxSeconds.setValue(maxSec);
        valueMaxSeconds.setText(maxSec + " s");
        maxSeconds.addOnChangeListener((slider, value, fromUser) -> {
            int v = (int) value;
            valueMaxSeconds.setText(v + " s");
            sp.edit().putInt("max_recording_seconds", v).apply();
        });

        // No-limit checkbox: when on, the slider value is ignored.
        CheckBox toggleNoLimit = findViewById(R.id.toggleNoLimit);
        toggleNoLimit.setChecked(sp.getBoolean("no_recording_limit", false));
        toggleNoLimit.setOnCheckedChangeListener((b, checked) -> {
            sp.edit().putBoolean("no_recording_limit", checked).apply();
            maxSeconds.setEnabled(!checked);
            maxSeconds.setAlpha(checked ? 0.4f : 1.0f);
        });
        maxSeconds.setEnabled(!toggleNoLimit.isChecked());
        maxSeconds.setAlpha(toggleNoLimit.isChecked() ? 0.4f : 1.0f);

        // Bluetooth
        CheckBox modeBluetooth = findViewById(R.id.mode_bluetooth);
        modeBluetooth.setChecked(sp.getBoolean("bluetooth", false));
        modeBluetooth.setOnCheckedChangeListener((btn, isChecked) -> {
            sp.edit().putBoolean("bluetooth", isChecked).apply();
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 111);
            }
        });

        // Button visibility toggles
        CheckBox togglePunctuation = findViewById(R.id.togglePunctuation);
        togglePunctuation.setChecked(sp.getBoolean("show_punctuation", true));
        togglePunctuation.setOnCheckedChangeListener((b, checked) ->
            sp.edit().putBoolean("show_punctuation", checked).apply()
        );

        CheckBox toggleKeyboard = findViewById(R.id.toggleKeyboard);
        toggleKeyboard.setChecked(sp.getBoolean("show_keyboard_btn", true));
        toggleKeyboard.setOnCheckedChangeListener((b, checked) ->
            sp.edit().putBoolean("show_keyboard_btn", checked).apply()
        );

        CheckBox toggleAuto = findViewById(R.id.toggleAuto);
        toggleAuto.setChecked(sp.getBoolean("show_auto_btn", true));
        toggleAuto.setOnCheckedChangeListener((b, checked) ->
            sp.edit().putBoolean("show_auto_btn", checked).apply()
        );

        checkPermissions();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Persist all visible key fields on leaving the screen.
        int n = com.whispergroq.utils.SecurePrefs.getKeyCount(this);
        int[] ids = {R.id.editApiKey, R.id.editApiKey2, R.id.editApiKey3};
        for (int i = 0; i < n && i < 3; i++) {
            EditText f = findViewById(ids[i]);
            if (f != null) {
                com.whispergroq.utils.SecurePrefs.setApiKey(this, i, f.getText().toString().trim());
            }
        }
    }

    private void checkPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO);
            Toast.makeText(this, getString(R.string.need_record_audio_permission), Toast.LENGTH_SHORT).show();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[]{}), 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission granted");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** dp helper */
    private static int dp(Context c, int v) {
        return Math.round(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** Builds the row of accent circles (purple, blue, pink, brown, slate). */
    private android.widget.ImageView[] buildAccentCircles(final String[] accents) {
        String current = com.whispergroq.utils.ThemeUtils.accent(this);
        android.widget.ImageView[] out = new android.widget.ImageView[accents.length];
        int size = dp(this, 36);
        int margin = dp(this, 10);
        int[][] palette = {
                {R.color.accent_purple, R.color.container_purple},
                {R.color.accent_blue, R.color.container_blue},
                {R.color.accent_link, R.color.container_link},
                {R.color.accent_brown, R.color.container_brown},
                {R.color.accent_slate, R.color.container_slate}
        };
        for (int i = 0; i < accents.length; i++) {
            final String accent = accents[i];
            android.widget.ImageView c = new android.widget.ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.leftMargin = margin;
            c.setLayoutParams(lp);
            boolean selected = accent.equals(current);
            // Oval fill + ring when selected, plain fill otherwise.
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(ContextCompat.getColor(this, palette[i][0]));
            if (selected) {
                g.setStroke(dp(this, 3), ContextCompat.getColor(this, R.color.text_primary));
            }
            c.setImageDrawable(g);
            c.setOnClickListener(v -> {
                sp.edit().putString(com.whispergroq.utils.ThemeUtils.PREF_ACCENT, accent).apply();
                com.whispergroq.utils.ThemeUtils.applyTheme(SettingsActivity.this);
            });
            String[] names = {getString(R.string.accent_purple), getString(R.string.accent_blue),
                    getString(R.string.accent_link), getString(R.string.accent_brown),
                    getString(R.string.accent_slate)};
            c.setContentDescription(names[i]);
            out[i] = c;
        }
        return out;
    }

    /** Enables/disables (and grays) the accent row — Dynamic mode. */
    private void applyAccentRowEnabled(LinearLayout row, boolean enabled) {
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1.0f : 0.4f);
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).setEnabled(enabled);
            row.getChildAt(i).setAlpha(enabled ? 1.0f : 0.4f);
        }
    }
}
