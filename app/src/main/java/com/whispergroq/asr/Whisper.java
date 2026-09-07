package com.whispergroq.asr;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Whisper {

    public interface WhisperListener {
        void onUpdateReceived(String message);
        void onResultReceived(WhisperResult result);
        default void onStatusChanged(String status, String detailMessage) {}
    }

    private static final String TAG = "Whisper";
    public static final String MSG_PROCESSING = "Processing...";
    public static final String MSG_PROCESSING_DONE = "Processing done...!";
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final MediaType WAV = MediaType.get("audio/wav");

    private final AtomicBoolean mInProgress = new AtomicBoolean(false);
    private final Context mContext;
    private final SharedPreferences sp;
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private WhisperListener mUpdateListener;
    private long startTime;
    private volatile okhttp3.Call activeCall;

    public Whisper(Context context) {
        mContext = context;
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        executor = Executors.newSingleThreadExecutor();
    }

    public void setListener(WhisperListener listener) {
        this.mUpdateListener = listener;
    }

    private void updateStatus(String status, String detail) {
        if (mUpdateListener != null) mUpdateListener.onStatusChanged(status, detail);
    }

    public void start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Execution already in progress");
            return;
        }
        executor.execute(this::processRecordBuffer);
    }

    public void stop() {
        mInProgress.set(false);
        okhttp3.Call c = activeCall;
        if (c != null) c.cancel();
    }

    public boolean isInProgress() {
        return mInProgress.get();
    }

    public void shutdown() {
        executor.shutdown();
    }

    private void processRecordBuffer() {
        try {
            byte[] pcmData = RecordBuffer.getOutputBuffer();
            if (pcmData == null || pcmData.length == 0) {
                updateStatus("ERROR", "Nenhum áudio gravado");
                sendUpdate("No audio recorded");
                return;
            }

            String apiKey = com.whispergroq.utils.SecurePrefs.getApiKey(mContext);
            if (apiKey.isEmpty()) {
                updateStatus("ERROR", "API key não configurada");
                sendUpdate("ERROR: Groq API key not configured. Open settings.");
                return;
            }

            String model = sp.getString("groq_model", "whisper-large-v3-turbo");

            startTime = System.currentTimeMillis();
            sendUpdate(MSG_PROCESSING);
            updateStatus("BUSY", null);

            byte[] wavData = WavEncoder.encodePcmToWav(pcmData);
            WhisperResult result = transcribeAudioWithFallback(wavData, apiKey, model);
            sendResult(result);

            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Transcription in " + elapsed + "ms");
            sendUpdate(MSG_PROCESSING_DONE);
            updateStatus("OK", "Transcrição em " + elapsed + "ms");

        } catch (IOException e) {
            if (activeCall != null && activeCall.isCanceled()) {
                Log.d(TAG, "Call cancelled by user");
                updateStatus("IDLE", null);
            } else {
                Log.e(TAG, "Network error", e);
                updateStatus("ERROR", "Rede: " + e.getMessage());
                sendUpdate("Error: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Transcription error", e);
            updateStatus("ERROR", e.getMessage());
            sendUpdate("Error: " + e.getMessage());
        } finally {
            activeCall = null;
            mInProgress.set(false);
        }
    }

    private WhisperResult transcribeAudioWithFallback(byte[] wavData, String apiKey, String model) throws Exception {
        int attempts = 0;
        int maxAttempts = com.whispergroq.utils.SecurePrefs.getKeyCount(mContext);
        Exception lastError = null;

        for (; attempts < maxAttempts; attempts++) {
            try {
                return transcribeAudio(wavData, apiKey, model);
            } catch (Exception e) {
                lastError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("401") || msg.contains("403")) {
                    Log.w(TAG, "Auth error on key " + com.whispergroq.utils.SecurePrefs.getActiveKeyIndex(mContext) + "; rotating");
                    com.whispergroq.utils.SecurePrefs.rotateKey(mContext);
                    String nextKey = com.whispergroq.utils.SecurePrefs.getCurrentApiKey(mContext);
                    if (nextKey == null || nextKey.isEmpty()) break;
                    apiKey = nextKey;
                    updateStatus("ERROR", "Chave inválida, alternando...");
                } else {
                    throw e;
                }
            }
        }
        throw lastError != null ? lastError : new Exception("All keys exhausted");
    }

    /**
     * HTTP call to Groq Whisper endpoint. Encapsulates the multipart logic.
     */
    private WhisperResult transcribeAudio(byte[] wavData, String apiKey, String model) throws Exception {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav",
                        RequestBody.create(wavData, WAV))
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "json")
                .build();

        Request request = new Request.Builder()
                .url(GROQ_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        okhttp3.Call call = httpClient.newCall(request);
        activeCall = call;

        try (Response response = call.execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Groq API error " + response.code() + ": " + responseBody);
            }
            JSONObject json = new JSONObject(responseBody);
            String text = json.optString("text", "");
            String detectedLang = json.optString("language", "auto");
            return new WhisperResult(text, detectedLang);
        }
    }

    private void sendUpdate(String message) {
        if (mUpdateListener != null) mUpdateListener.onUpdateReceived(message);
    }

    private void sendResult(WhisperResult r) {
        if (mUpdateListener != null) mUpdateListener.onResultReceived(r);
    }
}
