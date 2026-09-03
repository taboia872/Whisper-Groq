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
import android.widget.FrameLayout;
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
    private ImageButton btnStop;
    private ImageButton btnModeAuto;
    private ImageButton btnEnter;
    private ImageButton btnDel;
    private TextView btnPeriod;
    private TextView btnComma;
    private TextView btnQuestion;
    private TextView btnExclaim;
    private TextView tvStatus;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private ProgressBar processingBar = null;
    private SharedPreferences sp = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context mContext;
    private CountDownTimer countDownTimer;
    private boolean modeAuto = false;
    private FrameLayout layoutButtons;

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
        btnStop = view.findViewById(R.id.btnStop);
        btnModeAuto = view.findViewById(R.id.btnModeAuto);
        btnEnter = view.findViewById(R.id.btnEnter);
        btnDel = view.findViewById(R.id.btnDel);
        btnPeriod = view.findViewById(R.id.btnPeriod);
        btnComma = view.findViewById(R.id.btnComma);
        btnQuestion = view.findViewById(R.id.btnQuestion);
        btnExclaim = view.findViewById(R.id.btnExclaim);
        processingBar = view.findViewById(R.id.processing_bar);
        tvStatus = view.findViewById(R.id.tv_status);
        layoutButtons = view.findViewById(R.id.layout_buttons);

        modeAuto = sp.getBoolean("imeModeAuto", false);
        btnModeAuto.setImageResource(modeAuto ? R.drawable.ic_auto_on_36dp : R.drawable.ic_auto_off_36dp);
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
            layoutButtons.setVisibility(View.GONE);
            btnStop.setVisibility(View.VISIBLE);
            HapticFeedback.vibrate(this);
            startRecording();
            startCountdown();
        }

        // Tap-to-record: start/stop on click
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
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
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

        btnStop.setOnClickListener(v -> {
            if (mRecorder != null) mRecorder.requestStopVad();
        });

        btnEnter.setOnClickListener(v ->
            getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        );

        btnModeAuto.setOnClickListener(v -> {
            modeAuto = !modeAuto;
            sp.edit().putBoolean("imeModeAuto", modeAuto).apply();
            layoutButtons.setVisibility(modeAuto ? View.GONE : View.VISIBLE);
            btnStop.setVisibility(modeAuto ? View.VISIBLE : View.GONE);
            btnModeAuto.setImageResource(modeAuto ? R.drawable.ic_auto_on_36dp : R.drawable.ic_auto_off_36dp);
            int mode = modeAuto ? 1 : 0;
            Toast.makeText(this, modeAuto ? "Auto mode on" : "Auto mode off", Toast.LENGTH_SHORT).show();
            switchToPreviousInputMethod();
        });

        // Punctuation buttons
        btnPeriod.setOnClickListener(v -> getCurrentInputConnection().commitText(". ", 1));
        btnComma.setOnClickListener(v -> getCurrentInputConnection().commitText(", ", 1));
        btnQuestion.setOnClickListener(v -> getCurrentInputConnection().commitText("? ", 1));
        btnExclaim.setOnClickListener(v -> getCurrentInputConnection().commitText("! ", 1));

        return view;
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
            if (modeAuto) mRecorder.initVad();
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
            handler.post(() -> processingBar.setProgress(100));
            countDownTimer = new CountDownTimer(30000, 1000) {
                @Override
                public void onTick(long l) {
                    try {
                        handler.post(() -> processingBar.setProgress((int) (l / 300)));
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
            public void onResultReceived(WhisperResult whisperResult) {
                handler.post(() -> {
                    processingBar.setIndeterminate(false);
                    tvStatus.setText("");
                    tvStatus.setVisibility(View.GONE);
                });
                String result = whisperResult.getResult().trim();
                if (!result.isEmpty()) {
                    boolean commitSuccess = getCurrentInputConnection().commitText(result + " ", 1);
                    if (modeAuto && commitSuccess) {
                        handler.postDelayed(() -> switchToPreviousInputMethod(), 100);
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
            mWhisper.setLanguage("auto"); // Groq auto-detects
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
