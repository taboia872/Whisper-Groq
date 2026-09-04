package com.whispergroq;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.whispergroq.asr.Recorder;
import com.whispergroq.asr.Whisper;
import com.whispergroq.asr.WhisperResult;
import com.whispergroq.utils.HapticFeedback;
import com.whispergroq.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Context mContext;
    private static final String TAG = "MainActivity";

    private TextView tvStatus;
    private EditText tvResult;
    private FloatingActionButton fabCopy;
    private ImageButton btnRecord;
    private CheckBox append;
    private ProgressBar processingBar;

    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private SharedPreferences sp = null;
    private CountDownTimer countDownTimer;

    @Override
    protected void onDestroy() {
        if (mRecorder != null && mRecorder.isInProgress()) mRecorder.stop();
        if (mWhisper != null) mWhisper.stop();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        stopProcessing();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        ThemeUtils.setStatusBarAppearance(this);
        checkInputMethodEnabled();
        processingBar = findViewById(R.id.processing_bar);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        append = findViewById(R.id.mode_append);

        initWhisper();

        // Tap-to-record
        btnRecord = findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(v -> {
            if (!checkPermissions()) return;
            if (mRecorder.isInProgress()) {
                stopRecording();
            } else if (!mWhisper.isInProgress()) {
                HapticFeedback.vibrate(this);
                startRecording();
                startCountdown();
            } else {
                Toast.makeText(this, getString(R.string.please_wait), Toast.LENGTH_SHORT).show();
            }
        });

        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        tvResult.setOnClickListener(view -> tvResult.setCursorVisible(true));

        fabCopy = findViewById(R.id.fabCopy);
        fabCopy.setOnClickListener(v -> {
            String textToCopy = tvResult.getText().toString().trim();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.model_output), textToCopy));
            Toast.makeText(this, R.string.copy_to_clipboard, Toast.LENGTH_SHORT).show();
        });

        mRecorder = new Recorder(this);
        mRecorder.setListener(message -> {
            if (message.equals(Recorder.MSG_RECORDING)) {
                runOnUiThread(() -> tvStatus.setText(getString(R.string.record_button) + "…"));
                if (!append.isChecked()) runOnUiThread(() -> tvResult.setText(""));
                runOnUiThread(() -> btnRecord.setImageResource(R.drawable.ic_mic_recording_48dp));
            } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                HapticFeedback.vibrate(mContext);
                runOnUiThread(() -> btnRecord.setImageResource(R.drawable.ic_mic_48dp));
                startTranscription();
            } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                HapticFeedback.vibrate(mContext);
                if (countDownTimer != null) countDownTimer.cancel();
                runOnUiThread(() -> {
                    btnRecord.setImageResource(R.drawable.ic_mic_48dp);
                    processingBar.setProgress(0);
                    tvStatus.setText(getString(R.string.error_no_input));
                });
            }
        });

        checkPermissions();
    }

    private void checkInputMethodEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> enabledInputMethodList = imm.getEnabledInputMethodList();
        String myInputMethodId = getPackageName() + "/.WhisperInputMethodService";
        boolean enabled = false;
        for (InputMethodInfo imi : enabledInputMethodList) {
            if (imi.getId().equals(myInputMethodId)) { enabled = true; break; }
        }
        boolean wasPromptedBefore = sp.getBoolean("ime_prompt_shown", false);
        if (!enabled) {
            if (!wasPromptedBefore) {
                // First run — guide user to the system settings
                sp.edit().putBoolean("ime_prompt_shown", true).apply();
                Toast.makeText(this, "Ative o Whisper-Groq nas configurações de teclado", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } else {
                Toast.makeText(this, "Ative o Whisper-Groq nas configurações de teclado do sistema", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initWhisper() {
        mWhisper = new Whisper(this);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Update: " + message);
                if (message.equals(Whisper.MSG_PROCESSING)) {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.processing)));
                } else if (message.startsWith("ERROR")) {
                    runOnUiThread(() -> {
                        tvStatus.setText(message);
                        processingBar.setIndeterminate(false);
                    });
                }
            }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                runOnUiThread(() -> {
                    processingBar.setIndeterminate(false);
                    tvStatus.setText(getString(R.string.processing_done_short));
                    tvResult.append(whisperResult.getResult());
                });
            }
        });
    }

    private boolean checkPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (sp.getBoolean("bluetooth", false)
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[]{}), 0);
            return false;
        }
        return true;
    }

    private void startRecording() {
        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
    }

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        runOnUiThread(() -> processingBar.setProgress(100));
        countDownTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long l) {
                runOnUiThread(() -> processingBar.setProgress((int) (l / 300)));
            }
            @Override
            public void onFinish() {}
        };
        countDownTimer.start();
    }

    private void startTranscription() {
        if (countDownTimer != null) countDownTimer.cancel();
        runOnUiThread(() -> {
            processingBar.setProgress(0);
            processingBar.setIndeterminate(true);
        });
        mWhisper.setLanguage("auto");
        mWhisper.start();
    }

    private void stopProcessing() {
        processingBar.setIndeterminate(false);
        if (mWhisper != null && mWhisper.isInProgress()) mWhisper.stop();
    }
}
