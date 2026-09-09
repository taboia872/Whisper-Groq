package com.whispergroq;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import com.whispergroq.asr.Recorder;
import com.whispergroq.asr.Whisper;
import com.whispergroq.asr.WhisperResult;
import com.whispergroq.utils.HapticFeedback;
import com.whispergroq.utils.ThemeUtils;

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
    private ImageView btnStatus;
    private TextView tvStatus;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private SharedPreferences sp = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context mContext;
    private Context themedContext;
    private CountDownTimer countDownTimer;
    private String lastStatusMessage = "Pronto";
    private PopupWindow numbersPopup;

    private void showNumbersPopup(View anchor) {
        if (numbersPopup != null && numbersPopup.isShowing()) {
            numbersPopup.dismiss();
            return;
        }

        Context ctx = themedContext != null ? themedContext : this;
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundResource(R.drawable.kb_button);
        layout.setPadding(8, 8, 8, 8);

        String[] nums = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        for (String n : nums) {
            TextView btn = new TextView(ctx);
            btn.setText(n);
            btn.setTextSize(20);
            btn.setTextColor(onSurfaceColor(ctx));
            btn.setGravity(Gravity.CENTER);
            btn.setBackgroundResource(R.drawable.kb_button);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dpToPx(36), dpToPx(40));
            lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> safeCommit(n));
            layout.addView(btn);
        }

        numbersPopup = new PopupWindow(layout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        numbersPopup.setElevation(8);
        numbersPopup.setOutsideTouchable(true);
        numbersPopup.setFocusable(false);
        numbersPopup.showAsDropDown(anchor, 0, -dpToPx(110));
    }

    private PopupWindow punctuationPopup;

    @Override
    public void onCreate() {
        mContext = this;
        super.onCreate();
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        // When the target app is fullscreen (hides system bars), Android
        // computes the IME area as if the nav bar were still visible,
        // clipping our bottom buttons. Force the touchable area to the
        // full visible frame so the second button row stays on screen.
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onDestroy() {
        if (mRecorder != null && mRecorder.isInProgress()) mRecorder.stop();
        if (punctuationPopup != null) punctuationPopup.dismiss();
        if (numbersPopup != null) numbersPopup.dismiss();
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
        // Base the IME inflation on an explicit Material3 theme so ?attr/color*
        // always resolve (the service's own context theme may not be Material).
        Context base = new android.view.ContextThemeWrapper(this, R.style.Theme_WhisperGroq_IME);
        themedContext = ThemeUtils.wrapImeContext(base);
        View view = LayoutInflater.from(themedContext).inflate(R.layout.voice_service, null);

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
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    HapticFeedback.vibrateDone(mContext);
                    handler.post(() -> btnRecord.setImageResource(R.drawable.ic_mic_48dp));
                    startTranscription();
                } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                    HapticFeedback.vibrateError(mContext);
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
        btnNumbers.setOnClickListener(v -> showNumbersPopup(v));

        // Punctuation popup
        btnPunctuation.setOnClickListener(v -> showPunctuationPopup(v));

        return view;
    }

    private void sendKeyWithMeta(int keyCode, int meta) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        if (ic != null) ic.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta));
    }

    private void showPunctuationPopup(View anchor) {
        if (punctuationPopup != null && punctuationPopup.isShowing()) {
            punctuationPopup.dismiss();
            return;
        }

        Context ctx = themedContext != null ? themedContext : this;
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundResource(R.drawable.kb_button);
        layout.setPadding(8, 8, 8, 8);

        String[] puncts = {".", ",", "?", "!", ":", ";", "-", "(", ")", "\"", "'"};
        for (String p : puncts) {
            TextView btn = new TextView(ctx);
            btn.setText(p);
            btn.setTextSize(20);
            btn.setTextColor(onSurfaceColor(ctx));
            btn.setGravity(Gravity.CENTER);
            btn.setBackgroundResource(R.drawable.kb_button);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dpToPx(40), dpToPx(40));
            lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> safeCommit(p));
            layout.addView(btn);
        }

        punctuationPopup = new PopupWindow(layout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        punctuationPopup.setElevation(8);
        punctuationPopup.setOutsideTouchable(true);
        punctuationPopup.setFocusable(false);
        punctuationPopup.showAsDropDown(anchor, 0, -dpToPx(110));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /** Resolve ?attr/colorOnSurface against the (possibly dynamic) IME context. */
    private int onSurfaceColor(Context ctx) {
        return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
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
