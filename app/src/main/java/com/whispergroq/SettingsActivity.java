package com.whispergroq;

import android.Manifest;
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

        // API Key (encrypted)
        EditText editApiKey = findViewById(R.id.editApiKey);
        editApiKey.setText(com.whispergroq.utils.SecurePrefs.getApiKey(this));
        editApiKey.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                com.whispergroq.utils.SecurePrefs.setApiKey(this, editApiKey.getText().toString().trim());
            }
        });

        ImageButton infoApiKey = findViewById(R.id.infoApiKey);
        infoApiKey.setOnClickListener(v ->
            Toast.makeText(this, R.string.settings_api_key_hint, Toast.LENGTH_LONG).show()
        );

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

        // Theme spinner
        Spinner spinnerTheme = findViewById(R.id.spinnerTheme);
        final String[] THEME_MODES = {
                com.whispergroq.utils.ThemeUtils.MODE_LIGHT,
                com.whispergroq.utils.ThemeUtils.MODE_DARK,
                com.whispergroq.utils.ThemeUtils.MODE_SYSTEM,
                com.whispergroq.utils.ThemeUtils.MODE_AUTO_DYNAMIC
        };
        String[] themeLabels = {
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_system),
                getString(R.string.theme_auto_dynamic)
        };
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themeLabels);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTheme.setAdapter(themeAdapter);
        String currentTheme = sp.getString(com.whispergroq.utils.ThemeUtils.PREF_THEME_MODE,
                com.whispergroq.utils.ThemeUtils.MODE_SYSTEM);
        int themeIndex = 2; // default SYSTEM
        for (int i = 0; i < THEME_MODES.length; i++) {
            if (THEME_MODES[i].equals(currentTheme)) { themeIndex = i; break; }
        }
        spinnerTheme.setSelection(themeIndex);
        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = THEME_MODES[position];
                String previous = sp.getString(com.whispergroq.utils.ThemeUtils.PREF_THEME_MODE,
                        com.whispergroq.utils.ThemeUtils.MODE_SYSTEM);
                if (!selected.equals(previous)) {
                    sp.edit().putString(com.whispergroq.utils.ThemeUtils.PREF_THEME_MODE, selected).apply();
                    // Apply immediately so the user sees the change, then recreate.
                    com.whispergroq.utils.ThemeUtils.applyTheme(SettingsActivity.this);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

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
        EditText editApiKey = findViewById(R.id.editApiKey);
        if (editApiKey != null) {
            com.whispergroq.utils.SecurePrefs.setApiKey(this, editApiKey.getText().toString().trim());
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
}
