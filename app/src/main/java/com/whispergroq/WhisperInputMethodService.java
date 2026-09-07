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
import android.view.inputmethod.InputConnection;
import android.widget.ImageButton;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;

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
    private ImageButton btnSelectAll;
    private ImageButton btnCut;
    private ImageButton btnCopy;
    private ImageButton btnPaste;
    private ImageButton btnPunctuation;
    private ImageButton btnNumbers;
    private ImageButton btnStatus;
    private TextView tvStatus;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private SharedPreferences sp = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context mContext;
    private CountDownTimer countDownTimer;
    private String lastStatusMessage = "Pronto";
    private ObjectAnimator pulseAnimator;
    private PopupWindow punctuationPopup;

    @Override
    public void onCreate() {
        mContext = this;
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (mRecorder != null && mRecorder.isInProgress()) mRecorder.stop();
        if (punctuationPopup != null) punctuationPopup.dismiss();
        super.onDestroy();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        if (attribute.inputType == EditorInfo.TYPE_NULL) {
            if (mRecorder != null && mRecorder.isInProgress()) mRecorder.stop();
        }
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting){
        if (mWhisper == null) initModel();
        if (btnKeyboard != null) {
            boolean showKeyboard = sp.getBoolean("show_keyboard_btn", true);
            btnKeyboard.setVisibility(showKeyboard ? View.VISIBLE : View.GONE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateInputView() {
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        View view = getLayoutInflater().inflate(R.layout.voice_service, null);

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
        btnSelectAll = view.findViewById(R.id.btnSelectAll);
        btnCut = view.findViewById(R.id.btnCut);
        btnCopy = view.findViewById(R.id.btnCopy);
        btnPaste = view.findViewById(R.id.btnPaste);
        btnPunctuation = view.findViewById(R.id.btnPunctuation);
        btnNumbers = view.findViewById(R.id.btnNumbers);
        btnDel = view.findViewById(R.id.btnDel);
        btnEnter = view.findViewById(R.id.btnEnter);
        btnStatus = view.findViewById(R.id.btnStatus);
        tvStatus = view.findViewById(R.id.tv_status);

        btnStatus.setOnClickListener(v ->
            Toast.makeText(mContext, lastStatusMessage, Toast.LENGTH_SHORT).show()
        );

        checkRecordPermission();

        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.equals(Recorder.MSG_RECORDING)) {
                    handler.post(() -> btnRecord.setImageResource(R.drawable.ic_mic_recording_48dp));
                    startRecordingPulse();
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    HapticFeedback.vibrateDone(mContext);
                    stopRecordingPulse();
                    handler.post(() -> btnRecord.setImageResource(R.drawable.ic_mic_48dp));
                    startTranscription();
                } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                    HapticFeedback.vibrateError(mContext);
                    stopRecordingPulse();
                    if (countDownTimer != null) countDownTimer.cancel();
                    handler.post(() -> {
                        btnRecord.setImageResource(R.drawable.ic_mic_48dp);
                        tvStatus.setText(getString(R.string.error_no_input));
                        tvStatus.setVisibility(View.VISIBLE);
                    });
                }
            }
        });

        btnRecord.setOnClickListener(v -> {
            if (!checkRecordPermission()) return;
            if (mRecorder.isInProgress()) {
                mRecorder.stop();
            } else if (mWhisper != null && !mWhisper.isInProgress()) {
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

        btnDel.setOnTouchListener((v, event) -> {
            InputConnection ic = getCurrentInputConnection();
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
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        });

        // Text editing actions
        btnSelectAll.setOnClickListener(v -> sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON));
        btnCut.setOnClickListener(v -> sendKeyWithMeta(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON));
        btnCopy.setOnClickListener(v -> sendKeyWithMeta(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON));
        btnPaste.setOnClickListener(v -> sendKeyWithMeta(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON));
        btnNumbers.setOnClickListener(v -> showKeyboard());

        // Punctuation popup
        btnPunctuation.setOnClickListener(v -> showPunctuationPopup(v));

        return view;
    }

    private void sendKeyWithMeta(int keyCode, int meta) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        if (ic != null) ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta));
    }

    private void showKeyboard() {
        // Open the previous keyboard (not a hard keyboard)
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        } catch (Exception ignored) {}
    }

    private void showPunctuationPopup(View anchor) {
        if (punctuationPopup != null && punctuationPopup.isShowing()) {
            punctuationPopup.dismiss();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundResource(R.drawable.kb_button);
        layout.setPadding(8, 8, 8, 8);

        String[] puncts = {".", ",", "?", "!", ":", ";", "-", "(", ")", "\"", "'"};
        for (String p : puncts) {
            TextView btn = new TextView(this);
            btn.setText(p);
            btn.setTextSize(20);
            btn.setTextColor(0xFFFFFFFF);
            btn.setGravity(Gravity.CENTER);
            btn.setBackgroundResource(R.drawable.kb_button);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dpToPx(40), dpToPx(40));
            lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                safeCommit(p);
                punctuationPopup.dismiss();
            });
            layout.addView(btn);
        }

        punctuationPopup = new PopupWindow(layout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        punctuationPopup.setElevation(8);
        punctuationPopup.showAsDropDown(anchor, 0, -dpToPx(60));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void safeCommit(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
    }

    private Runnable deleteRepeatRunnable;
    private final Handler deleteHandler = new Handler(Looper.getMainLooper());

    private void startDeleteRepeat() {
        deleteRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                InputConnection ic = getCurrentInputConnection();
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
            HapticFeedback.vibrateStart(this);
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

    private void startRecordingPulse() {
        stopRecordingPulse();
        pulseAnimator = ObjectAnimator.ofFloat(btnRecord, "scaleX", 1.0f, 1.15f);
        pulseAnimator.setDuration(600);
        pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(btnRecord, "scaleY", 1.0f, 1.15f);
        pulseY.setDuration(600);
        pulseY.setRepeatMode(ObjectAnimator.REVERSE);
        pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(pulseAnimator, pulseY);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }

    private void stopRecordingPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            btnRecord.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
    }

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        int maxSeconds = sp.getInt("max_recording_seconds", 60);
        final int maxMs = maxSeconds * 1000;
        countDownTimer = new CountDownTimer(maxMs, 1000) {
            @Override public void onTick(long l) {}
            @Override public void onFinish() {
                if (mRecorder != null && mRecorder.isInProgress()) {
                    mRecorder.stop();
                }
            }
        };
        countDownTimer.start();
    }

    private void initModel() {
        mWhisper = new Whisper(this);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.startsWith("ERROR")) {
                    handler.post(() -> Toast.makeText(mContext, message, Toast.LENGTH_LONG).show());
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
                handler.post(() -> btnStatus.setImageResource(resId));
            }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                handler.post(() -> {
                    tvStatus.setText("");
                    tvStatus.setVisibility(View.GONE);
                });
                String result = whisperResult.getResult().trim();
                if (!result.isEmpty()) {
                    InputConnection ic = getCurrentInputConnection();
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
