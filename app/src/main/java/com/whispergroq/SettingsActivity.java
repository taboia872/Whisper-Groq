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
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
    private LinearLayout keyList;
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

        // API Keys (encrypted, dynamic list with '+' — round-robin per transcription)
        keyList = findViewById(R.id.keyList);
        int savedCount = com.whispergroq.utils.SecurePrefs.getKeyCount(this);
        for (int i = 0; i < savedCount; i++) {
            addKeyField(i, com.whispergroq.utils.SecurePrefs.getApiKey(this, i));
        }
        if (savedCount == 0) addKeyField(0, com.whispergroq.utils.SecurePrefs.getApiKey(this));

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
                    // Toggle the color dropdown BEFORE recreating: in Dynamic
                    // mode the accent comes from the wallpaper. (Local lookup —
                    // the spinnerColor is declared later.)
                    Spinner colorSpinner = findViewById(R.id.spinnerColor);
                    boolean dyn = com.whispergroq.utils.ThemeUtils.MODE_AUTO_DYNAMIC.equals(selected);
                    colorSpinner.setEnabled(!dyn);
                    colorSpinner.setAlpha(dyn ? 0.4f : 1.0f);
                    // Apply immediately so the user sees the change, then recreate.
                    com.whispergroq.utils.ThemeUtils.applyTheme(SettingsActivity.this);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Accent color: compact dropdown on the SAME LINE as the theme spinner
        // (color swatch + name), grayed out in Dynamic mode.
        Spinner spinnerColor = findViewById(R.id.spinnerColor);
        final String[] ACCENTS = {
                com.whispergroq.utils.ThemeUtils.ACCENT_PURPLE,
                com.whispergroq.utils.ThemeUtils.ACCENT_BLUE,
                com.whispergroq.utils.ThemeUtils.ACCENT_LINK,
                com.whispergroq.utils.ThemeUtils.ACCENT_ORANGE,
                com.whispergroq.utils.ThemeUtils.ACCENT_SLATE
        };
        int[] accentSwatches = {
                R.color.accent_purple, R.color.accent_blue, R.color.accent_link,
                R.color.accent_orange, R.color.accent_slate
        };
        String[] accentNames = {
                getString(R.string.accent_purple), getString(R.string.accent_blue),
                getString(R.string.accent_link), getString(R.string.accent_orange),
                getString(R.string.accent_slate)
        };
        ColorSwatchAdapter colorAdapter = new ColorSwatchAdapter(this, ACCENTS, accentSwatches, accentNames);
        spinnerColor.setAdapter(colorAdapter);
        String currentAccent = com.whispergroq.utils.ThemeUtils.accent(this);
        int colorIndex = 0;
        for (int i = 0; i < ACCENTS.length; i++) {
            if (ACCENTS[i].equals(currentAccent)) { colorIndex = i; break; }
        }
        spinnerColor.setSelection(colorIndex);
        spinnerColor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = ACCENTS[position];
                String previous = com.whispergroq.utils.ThemeUtils.accent(SettingsActivity.this);
                if (!selected.equals(previous)) {
                    sp.edit().putString(com.whispergroq.utils.ThemeUtils.PREF_ACCENT, selected).apply();
                    com.whispergroq.utils.ThemeUtils.applyTheme(SettingsActivity.this);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        boolean dyn = com.whispergroq.utils.ThemeUtils.isDynamic(this);
        spinnerColor.setEnabled(!dyn);
        spinnerColor.setAlpha(dyn ? 0.4f : 1.0f);

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
        // Persist all key fields on leaving the screen.
        saveKeyFields();
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

    /** Spinner adapter: color swatch circle + accent name. */
    private static class ColorSwatchAdapter extends ArrayAdapter<String> {
        private final int[] swatches;
        private final String[] names;

        ColorSwatchAdapter(Context ctx, String[] accents, int[] swatches, String[] names) {
            super(ctx, android.R.layout.simple_spinner_item, names);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            this.swatches = swatches;
            this.names = names;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return row(getContext(), position);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return row(getContext(), position);
        }

        private View row(Context ctx, int position) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int pad = dp(ctx, 8);
            row.setPadding(pad, pad, pad, pad);

            android.widget.ImageView sw = new android.widget.ImageView(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20));
            sw.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(ContextCompat.getColor(ctx, swatches[position]));
            sw.setImageDrawable(g);
            row.addView(sw);

            TextView tv = new TextView(ctx);
            tv.setText(names[position]);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setPadding(dp(ctx, 8), 0, 0, 0);
            row.addView(tv);
            return row;
        }
    }

    /** Adds a multi-line text area for an API key slot. The last field always
     *  gets a '+' button below it to append another slot. */
    private void addKeyField(int index, String savedKey) {
        // remove any existing '+' button first
        if (keyList.getChildCount() > 0) {
            View last = keyList.getChildAt(keyList.getChildCount() - 1);
            if (last instanceof Button) keyList.removeView(last);
        }
        EditText field = new EditText(this);
        field.setHint("gsk_...");
        field.setText(savedKey != null ? savedKey : "");
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setMinLines(1);
        field.setMaxLines(3);
        field.setSingleLine(false);
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        field.setTypeface(android.graphics.Typeface.MONOSPACE);
        // Multi-line password fields don't mask by default — force the
        // PasswordTransformationMethod so keys show as dots again.
        field.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(this, 4);
        field.setLayoutParams(lp);
        keyList.addView(field);
        Button add = new Button(this, null, 0, com.google.android.material.R.attr.materialIconButtonStyle);
        add.setText("+");
        add.setOnClickListener(v -> {
            saveKeyFields();
            addKeyField(keyList.getChildCount(), "");
        });
        keyList.addView(add);
    }

    /** Persists all key fields to encrypted slots (only filled ones). */
    private void saveKeyFields() {
        int n = 0;
        for (int i = 0; i < keyList.getChildCount(); i++) {
            View c = keyList.getChildAt(i);
            if (c instanceof EditText) {
                String v = ((EditText) c).getText().toString().trim();
                if (!v.isEmpty()) {
                    com.whispergroq.utils.SecurePrefs.setApiKey(this, n, v);
                    n++;
                }
            }
        }
        com.whispergroq.utils.SecurePrefs.setKeyCount(this, Math.max(1, n));
    }
}
