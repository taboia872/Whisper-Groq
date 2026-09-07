package com.whispergroq;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.whispergroq.asr.Recorder;
import com.whispergroq.asr.Whisper;
import com.whispergroq.asr.WhisperResult;
import com.whispergroq.utils.HapticFeedback;

public class WhisperInputMethodService extends InputMethodService {
    private static final String TAG = "WhisperInputMethodService";
    private ImageButton btnRecord;
    private ImageButton btnKeyboard;
    private ImageButton btnEnter;
    private ImageButton btnDel;
    private TextView btnPeriod;
    private TextView btnComma;
    private TextView btnQuestion;
    private TextView btnExclaim;
    private TextView btnSpace;
    private TextView tvStatus;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private ProgressBar processingBar = null;
    private SharedPreferences sp = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context mContext;
    private CountDownTimer countDownTimer;
    private boolean modeAuto = false;

    @Override
    public void onCreate() {
        mContext = this;
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (mRecorder != null && mRecorder.isInProgress()) {
            mRecorder.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        if (attribute.inputType == EditorInfo.TYPE_NULL) {
            if (mRecorder != null && mRecorder.isInProgress()) {
                mRecorder.stop();
            }
        }
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting){
        if (mWhisper == null) initModel();
        // Reapply visibility toggles every time view is shown
        if (btnPeriod != null) {
            boolean showPunctuation = sp.getBoolean("show_punctuation", true);
            boolean showKeyboard = sp.getBoolean("show_keyboard_btn", true);
            int punctVis = showPunctuation ? View.VISIBLE : View.GONE;
            btnPeriod.setVisibility(punctVis);
            btnComma.setVisibility(punctVis);
            btnQuestion.setVisibility(punctVis);
            btnExclaim.setVisibility(punctVis);
            btnSpace.setVisibility(punctVis);
            btnKeyboard.setVisibility(showKeyboard ? View.VISIBLE : View.GONE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateInputView() {
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        View view = getLayoutInflater().inflate(R.layout.voice_service, null);

        // Fallback: don't crash if window insets API fails on weird hosts
        try {
            ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
                try {
                    androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    mlp.leftMargin = insets.left;
                    mlp.bottomMargin = insets.bottom;
                    mlp.rightMargin = insets.right;
                    v.setLayoutParams(mlp);
                } catch (Exception ignored) {}
                return WindowInsetsCompat.CONSUMED;
            });
        } catch (Exception ignored) {}

        btnRecord = view.findViewById(R.id.btnRecord);
        btnKeyboard = view.findViewById(R.id.btnKeyboard);
        btnEnter = view.findViewById(R.id.btnEnter);
        btnDel = view.findViewById(R.id.btnDel);
        btnPeriod = view.findViewById(R.id.btnPeriod);
        btnComma = view.findViewById(R.id.btnComma);
        btnQuestion = view.findViewById(R.id.btnQuestion);
        btnExclaim = view.findViewById(R.id.btnExclaim);
        btnSpace = view.findViewById(R.id.btnSpace);
        btnStatus = view.findViewById(R.id.btnStatus);
        processingBar = view.findViewById(R.id.processing_bar);
        tvStatus = view.findViewById(R.id.tv_status);

        btnStatus.setOnClickListener(v ->
            Toast.makeText(mContext, lastStatusMessage, Toast.LENGTH_SHORT).show()
        );

        // Apply visibility toggles from settings
        boolean showPunctuation = sp.getBoolean("show_punctuation", true);
        boolean showKeyboard = sp.getBoolean("show_keyboard_btn", true);
        int punctVis = showPunctuation ? View.VISIBLE : View.GONE;
        btnPeriod.setVisibility(punctVis);
        btnComma.setVisibility(punctVis);
        btnQuestion.setVisibility(punctVis);
        btnExclaim.setVisibility(punctVis);
        btnSpace.setVisibility(punctVis);
        btnKeyboard.setVisibility(showKeyboard ? View.VISIBLE : View.GONE);

        Log.d(TAG, "Visibility: punct=" + showPunctuation + " kb=" + showKeyboard);

        modeAuto = false;
        checkRecordPermission();

        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.equals(Recorder.MSG_RECORDING)) {
                    handler.post(() -> btnRecord.setImageResource(R.drawable.ic_mic_recording_48dp));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    HapticFeedback.vibrate(mContext);
                    handler.post(() -> btnRecord.setImageResource(R.drawable.ic_mic_48dp));
                    startTranscription();
                } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                    HapticFeedback.vibrate(mContext);
                    if (countDownTimer != null) countDownTimer.cancel();
                    handler.post(() -> {
                        btnRecord.setImageResource(R.drawable.ic_mic_48dp);
                        tvStatus.setText(getString(R.string.error_no_input));
                        tvStatus.setVisibility(View.VISIBLE);
                        processingBar.setProgress(0);
                    });
                }
            }
        });

        if (modeAuto) {
            HapticFeedback.vibrate(this);
            startRecording();
            startCountdown();
        }
        btnRecord.setOnClickListener(v -> {
            if (!checkRecordPermission()) return;
            if (mRecorder.isInProgress()) {
                mRecorder.stop();
            } else if (mWhisper != null && !mWhisper.isInProgress()) {
                HapticFeedback.vibrate(this);
                startRecording();
                startCountdown();
                handler.post(() -> {
                    tvStatus.setText("");
                    tvStatus.setVisibility(View.GONE);
                });
            } else {
                handler.post(() -> {
                    tvStatus.setText(getString(R.string.please_wait));
                    tvStatus.setVisibility(View.VISIBLE);
                });
            }
        });

        // Delete with long-press repeat
        btnDel.setOnTouchListener((v, event) -> {
            android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
            if (ic == null) return true;
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                startDeleteRepeat();
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                stopDeleteRepeat();
            }
            return true;
        });

        btnKeyboard.setOnClickListener(v -> {
            if (mWhisper != null) mWhisper.stop();
            switchToPreviousInputMethod();
        });

        btnEnter.setOnClickListener(v -> {
            android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        });

        // Punctuation buttons
        btnPeriod.setOnClickListener(v -> safeCommit("."));
        btnComma.setOnClickListener(v -> safeCommit(", "));
        btnQuestion.setOnClickListener(v -> safeCommit("?"));
        btnExclaim.setOnClickListener(v -> safeCommit("!"));
        btnSpace.setOnClickListener(v -> safeCommit(" "));

        return view;
    }

    private void safeCommit(String text) {
        android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
    }

    private Runnable deleteRepeatRunnable;
    private final Handler deleteHandler = new Handler(Looper.getMainLooper());

    private void startDeleteRepeat() {
        deleteRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                deleteHandler.postDelayed(this, 100);
            }
        };
        deleteHandler.postDelayed(deleteRepeatRunnable, 500);
    }

    private void stopDeleteRepeat() {
        if (deleteRepeatRunnable != null) deleteHandler.removeCallbacks(deleteRepeatRunnable);
    }

    private void startRecording() {
        try {
            mRecorder.start();
        } catch (Exception e) {
            Log.e(TAG, "startRecording failed", e);
            try {
                handler.post(() -> {
                    tvStatus.setText("Failed to start recording");
                    tvStatus.setVisibility(View.VISIBLE);
                });
            } catch (Exception ignored) {}
        }
    }

    private void startCountdown() {
        try {
            if (countDownTimer != null) countDownTimer.cancel();
            int maxSeconds = sp.getInt("max_recording_seconds", 60);
            final int maxMs = maxSeconds * 1000;
            handler.post(() -> processingBar.setProgress(100));
            countDownTimer = new CountDownTimer(maxMs, 1000) {
                @Override
                public void onTick(long l) {
                    try {
                        int pct = (int)((l * 100) / maxMs);
                        handler.post(() -> processingBar.setProgress(pct));
                    } catch (Exception ignored) {}
                }
                @Override
                public void onFinish() {}
            };
            countDownTimer.start();
        } catch (Exception e) {
            Log.e(TAG, "startCountdown failed", e);
        }
    }

    private ImageButton btnStatus;
    private String lastStatusMessage = "Pronto";
    private int lastStatusResId = R.drawable.status_led_idle;

    private void initModel() {
        mWhisper = new Whisper(this);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.startsWith("ERROR")) {
                    handler.post(() -> {
                        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
                        processingBar.setIndeterminate(false);
                    });
                }
            }

            @Override
            public void onStatusChanged(String status, String detail) {
                int resId;
                switch (status) {
                    case "OK":
                        resId = R.drawable.status_led_ok;
                        lastStatusMessage = detail != null ? detail : "OK";
                        break;
                    case "BUSY":
                        resId = R.drawable.status_led_busy;
                        lastStatusMessage = "Processando...";
                        break;
                    case "ERROR":
                        resId = R.drawable.status_led_error;
                        lastStatusMessage = detail != null ? detail : "Erro desconhecido";
                        break;
                    default:
                        resId = R.drawable.status_led_idle;
                        lastStatusMessage = "Pronto";
                }
                lastStatusResId = resId;
                handler.post(() -> btnStatus.setImageResource(resId));
            }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                handler.post(() -> {
                    processingBar.setIndeterminate(false);
                    tvStatus.setText("");
                    tvStatus.setVisibility(View.GONE);
                });
                String result = whisperResult.getResult().trim();
                if (!result.isEmpty()) {
                    android.view.inputmethod.InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(result + " ", 1);
                    } else {
                        Log.w(TAG, "InputConnection null when trying to commit: " + result);
                    }
                }
            }
        });
    }

    private void startTranscription() {
        if (countDownTimer != null) countDownTimer.cancel();
        handler.post(() -> {
            processingBar.setProgress(0);
            processingBar.setIndeterminate(true);
        });
        if (mWhisper != null) {
            mWhisper.start();
        }
    }

    private boolean checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(getString(R.string.need_record_audio_permission));
        }
        return (permission == PackageManager.PERMISSION_GRANTED);
    }
}
