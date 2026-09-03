package com.whispergroq;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.whispergroq.asr.Recorder;
import com.whispergroq.asr.Whisper;
import com.whispergroq.asr.WhisperResult;
import com.whispergroq.utils.HapticFeedback;

import java.util.ArrayList;

public class WhisperRecognizeActivity extends AppCompatActivity {
    private static final String TAG = "WhisperRecognizeActivity";
    private ImageButton btnRecord;
    private ImageButton btnCancel;
    private ImageButton btnStop;
    private ImageButton btnModeAuto;
    private ProgressBar processingBar = null;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private SharedPreferences sp = null;
    private Context mContext;
    private CountDownTimer countDownTimer;
    private boolean modeAuto = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        sp = PreferenceManager.getDefaultSharedPreferences(this);

        initWhisper();

        setContentView(R.layout.activity_recognize);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.BOTTOM;

        btnCancel = findViewById(R.id.btnCancel);
        btnStop = findViewById(R.id.btnStop);
        btnRecord = findViewById(R.id.btnRecord);
        btnModeAuto = findViewById(R.id.btnModeAuto);
        processingBar = findViewById(R.id.processing_bar);

        modeAuto = sp.getBoolean("imeModeAuto", false);
        btnModeAuto.setImageResource(modeAuto ? R.drawable.ic_auto_on_36dp : R.drawable.ic_auto_off_36dp);

        mRecorder = new Recorder(this);
        mRecorder.setListener(message -> {
            if (message.equals(Recorder.MSG_RECORDING)) {
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
                    Toast.makeText(mContext, R.string.error_no_input, Toast.LENGTH_SHORT).show();
                });
            }
        });

        if (modeAuto) {
            btnRecord.setVisibility(View.GONE);
            btnStop.setVisibility(View.VISIBLE);
            HapticFeedback.vibrate(this);
            startRecording();
            startCountdown();
        }

        btnModeAuto.setOnClickListener(v -> {
            modeAuto = !modeAuto;
            sp.edit().putBoolean("imeModeAuto", modeAuto).apply();
            btnRecord.setVisibility(modeAuto ? View.GONE : View.VISIBLE);
            btnStop.setVisibility(modeAuto ? View.VISIBLE : View.GONE);
            btnModeAuto.setImageResource(modeAuto ? R.drawable.ic_auto_on_36dp : R.drawable.ic_auto_off_36dp);
            if (mWhisper != null) mWhisper.stop();
            setResult(RESULT_CANCELED, null);
            finish();
        });

        // Tap-to-record
        btnRecord.setOnClickListener(v -> {
            if (!checkRecordPermission()) return;
            if (mRecorder.isInProgress()) {
                mRecorder.stop();
            } else if (mWhisper != null && !mWhisper.isInProgress()) {
                HapticFeedback.vibrate(this);
                startRecording();
                startCountdown();
            } else {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.please_wait), Toast.LENGTH_SHORT).show());
            }
        });

        btnCancel.setOnClickListener(v -> {
            if (mWhisper != null) mWhisper.stop();
            setResult(RESULT_CANCELED, null);
            finish();
        });

        btnStop.setOnClickListener(v -> {
            if (mRecorder != null) mRecorder.requestStopVad();
        });
    }

    private void startRecording() {
        if (modeAuto) mRecorder.initVad();
        mRecorder.start();
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

    private void initWhisper() {
        mWhisper = new Whisper(this);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.startsWith("ERROR")) {
                    runOnUiThread(() -> Toast.makeText(mContext, message, Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                runOnUiThread(() -> processingBar.setIndeterminate(false));
                String result = whisperResult.getResult();
                if (result.trim().length() > 0) {
                    sendResult(result.trim());
                }
            }
        });
    }

    private void sendResult(String result) {
        Intent sendResultIntent = new Intent();
        ArrayList<String> results = new ArrayList<>();
        results.add(result);
        sendResultIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results);
        sendResultIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, new float[]{1.0f});
        setResult(RESULT_OK, sendResultIntent);

        PendingIntent pendingIntent = null;
        try {
            pendingIntent = getIntent().getParcelableExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get EXTRA_RESULTS_PENDINGINTENT", e);
        }

        if (pendingIntent != null) {
            Intent pendingResultIntent = new Intent();
            pendingResultIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results);
            pendingResultIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, new float[]{1.0f});
            pendingResultIntent.putExtra(SearchManager.QUERY, result);

            if (getIntent().hasExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT_BUNDLE)) {
                Bundle pendingIntentBundle = null;
                try {
                    pendingIntentBundle = getIntent().getParcelableExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT_BUNDLE);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get EXTRA_RESULTS_PENDINGINTENT_BUNDLE", e);
                }
                if (pendingIntentBundle == null) {
                    pendingIntentBundle = new Bundle();
                }
                pendingIntentBundle.putStringArrayList(RecognizerIntent.EXTRA_RESULTS, results);
                pendingIntentBundle.putFloatArray(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, new float[]{1.0f});
                pendingIntentBundle.putString(SearchManager.QUERY, result);
                pendingResultIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT_BUNDLE, pendingIntentBundle);
            }

            try {
                pendingIntent.send(this, RESULT_OK, pendingResultIntent);
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "PendingIntent cancelled", e);
            }
        } else if (getCallingActivity() == null) {
            String action = getIntent().getAction();
            if (RecognizerIntent.ACTION_WEB_SEARCH.equals(action)) {
                Intent webSearchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
                webSearchIntent.putExtra(SearchManager.QUERY, result);
                webSearchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(webSearchIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start web search intent", e);
                }
            } else {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Whisper result", result);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, R.string.copy_to_clipboard, Toast.LENGTH_SHORT).show();
                }
            }
        }
        finish();
    }

    private void startTranscription() {
        if (countDownTimer != null) countDownTimer.cancel();
        runOnUiThread(() -> {
            processingBar.setProgress(0);
            processingBar.setIndeterminate(true);
        });
        if (mWhisper != null) {
            mWhisper.setLanguage("auto");
            mWhisper.start();
            Log.d(TAG, "Start Transcription");
        }
    }

    private boolean checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.need_record_audio_permission), Toast.LENGTH_SHORT).show();
        }
        return (permission == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onDestroy() {
        if (mRecorder != null && mRecorder.isInProgress()) {
            mRecorder.stop();
        }
        super.onDestroy();
    }
}
