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
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.slider.RangeSlider;
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
        setContentView(R.layout.activity_settings);
        ThemeUtils.setStatusBarAppearance(this);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        sp = PreferenceManager.getDefaultSharedPreferences(this);

        // API Key
        EditText editApiKey = findViewById(R.id.editApiKey);
        editApiKey.setText(sp.getString("groq_api_key", ""));
        editApiKey.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                sp.edit().putString("groq_api_key", editApiKey.getText().toString().trim()).apply();
            }
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

        // Silence slider
        RangeSlider minSilence = findViewById(R.id.settings_min_silence);
        float silence = sp.getInt("silenceDurationMs", 800);
        minSilence.setValues(silence);
        minSilence.addOnChangeListener((slider, value, fromUser) ->
            sp.edit().putInt("silenceDurationMs", (int) value).apply()
        );

        // Max recording seconds
        EditText editMaxSeconds = findViewById(R.id.editMaxSeconds);
        editMaxSeconds.setText(String.valueOf(sp.getInt("max_recording_seconds", 60)));
        editMaxSeconds.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int sec = Integer.parseInt(editMaxSeconds.getText().toString().trim());
                    if (sec < 5) sec = 5;
                    if (sec > 300) sec = 300;
                    sp.edit().putInt("max_recording_seconds", sec).apply();
                } catch (NumberFormatException ignored) {}
            }
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
            sp.edit().putString("groq_api_key", editApiKey.getText().toString().trim()).apply();
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
