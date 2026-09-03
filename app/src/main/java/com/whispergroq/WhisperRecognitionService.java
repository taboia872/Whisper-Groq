package com.whispergroq;

import static android.speech.SpeechRecognizer.ERROR_CLIENT;
import static android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.whispergroq.asr.Recorder;
import com.whispergroq.asr.Whisper;
import com.whispergroq.asr.WhisperResult;
import com.whispergroq.utils.HapticFeedback;

import java.util.ArrayList;

public class WhisperRecognitionService extends RecognitionService {
    private static final String TAG = "WhisperRecognitionService";
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;
    private boolean recognitionCancelled = false;

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback callback) {
        try {
            ParcelFileDescriptor audioExtra = recognizerIntent.getParcelableExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE);
            if (audioExtra != null) {
                Log.w(TAG, "EXTRA_AUDIO_SOURCE not supported");
                try {
                    callback.error(SpeechRecognizer.ERROR_CLIENT);
                } catch (RemoteException e) {
                    Log.e(TAG, "callback.error failed", e);
                }
                return;
            }

            checkRecordPermission(callback);
            initWhisper(callback);

            if (mRecorder != null && mRecorder.isInProgress()) {
                stopRecording();
            }
            mRecorder = new Recorder(this);
            mRecorder.setListener(message -> {
                try {
                    if (message.equals(Recorder.MSG_RECORDING)) {
                        callback.beginningOfSpeech();
                        callback.rmsChanged(10);
                    } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                        HapticFeedback.vibrate(this);
                        callback.rmsChanged(-20.0f);
                        startTranscription();
                    } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                        callback.error(ERROR_CLIENT);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "callback failed", e);
                }
            });

            if (mWhisper != null && !mWhisper.isInProgress()) {
                HapticFeedback.vibrate(this);
                startRecording();
                try {
                    callback.readyForSpeech(new Bundle());
                } catch (RemoteException e) {
                    Log.e(TAG, "callback.readyForSpeech failed", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "onStartListening failed", e);
            try {
                callback.error(SpeechRecognizer.ERROR_CLIENT);
            } catch (RemoteException re) {
                Log.e(TAG, "error callback failed too", re);
            }
        }
    }

    private void stopRecording() {
        if (mRecorder != null && mRecorder.isInProgress()) {
            mRecorder.stop();
        }
    }

    @Override
    protected void onCancel(Callback callback) {
        Log.d(TAG, "cancel");
        stopRecording();
        recognitionCancelled = true;
    }

    @Override
    protected void onStopListening(Callback callback) {
        Log.d(TAG, "StopListening");
        stopRecording();
    }

    private void initWhisper(Callback callback) {
        mWhisper = new Whisper(this);
        mWhisper.setLanguage("auto");
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) { }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                try {
                    if (whisperResult.getResult().trim().length() > 0) {
                        Log.d(TAG, whisperResult.getResult().trim());
                        callback.endOfSpeech();
                        Bundle results = new Bundle();
                        ArrayList<String> resultList = new ArrayList<>();
                        resultList.add(whisperResult.getResult().trim());
                        results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, resultList);
                        callback.results(results);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "onResult callback failed", e);
                }
            }
        });
    }

    private void startRecording() {
        mRecorder.initVad();
        mRecorder.start();
        recognitionCancelled = false;
    }

    private void startTranscription() {
        if (!recognitionCancelled) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> Toast.makeText(this, R.string.processing, Toast.LENGTH_SHORT).show());
            mWhisper.start();
            Log.d(TAG, "Start Transcription");
        }
    }

    private void checkRecordPermission(Callback callback) {
        int permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            try {
                callback.error(ERROR_INSUFFICIENT_PERMISSIONS);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
