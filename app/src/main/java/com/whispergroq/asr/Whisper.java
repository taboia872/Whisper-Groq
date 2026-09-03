package com.whispergroq.asr;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class Whisper {

    public interface WhisperListener {
        void onUpdateReceived(String message);
        void onResultReceived(WhisperResult result);
    }

    private static final String TAG = "Whisper";
    public static final String MSG_PROCESSING = "Processing...";
    public static final String MSG_PROCESSING_DONE = "Processing done...!";
    public static final String GROQ_URL = "https://api.groq.com/openai/v1/audio/transcriptions";

    private final AtomicBoolean mInProgress = new AtomicBoolean(false);
    private final Context mContext;
    private final SharedPreferences sp;
    private WhisperListener mUpdateListener;
    private String mLangCode = "auto";
    private long startTime;
    private Thread workerThread;

    public Whisper(Context context) {
        mContext = context;
        sp = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setListener(WhisperListener listener) {
        this.mUpdateListener = listener;
    }

    public void loadModel() {
        // No-op: cloud API doesn't need model loading
    }

    public void unloadModel() {
        // No-op
    }

    public void setLanguage(String language){
        this.mLangCode = language;
    }

    public void start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Execution is already in progress...");
            return;
        }
        workerThread = new Thread(this::processRecordBuffer);
        workerThread.start();
    }

    public void stop() {
        mInProgress.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public boolean isInProgress() {
        return mInProgress.get();
    }

    private void processRecordBuffer() {
        try {
            byte[] pcmData = RecordBuffer.getOutputBuffer();
            if (pcmData == null || pcmData.length == 0) {
                sendUpdate("No audio recorded");
                return;
            }

            String apiKey = sp.getString("groq_api_key", "");
            if (apiKey.isEmpty()) {
                sendUpdate("ERROR: Groq API key not configured. Open settings.");
                return;
            }

            String model = sp.getString("groq_model", "whisper-large-v3-turbo");

            startTime = System.currentTimeMillis();
            sendUpdate(MSG_PROCESSING);

            byte[] wavData = WavEncoder.encodePcmToWav(pcmData);
            String response = callGroqApi(wavData, apiKey, model, mLangCode);

            JSONObject json = new JSONObject(response);
            String text = json.optString("text", "");
            String detectedLang = json.optString("language", mLangCode);

            WhisperResult result = new WhisperResult(text, detectedLang);
            sendResult(result);

            long timeTaken = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Transcription completed in " + timeTaken + "ms");
            sendUpdate(MSG_PROCESSING_DONE);

        } catch (Exception e) {
            Log.e(TAG, "Error during transcription", e);
            sendUpdate("Error: " + e.getMessage());
        } finally {
            mInProgress.set(false);
        }
    }

    private String callGroqApi(byte[] wavData, String apiKey, String model, String langCode) throws Exception {
        String boundary = "----WhisperGroq" + System.currentTimeMillis();
        String CRLF = "\r\n";

        HttpURLConnection conn = (HttpURLConnection) new URL(GROQ_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());

        // file part
        out.writeBytes("--" + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"" + CRLF);
        out.writeBytes("Content-Type: audio/wav" + CRLF + CRLF);
        out.write(wavData);
        out.writeBytes(CRLF);

        // model
        out.writeBytes("--" + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"model\"" + CRLF + CRLF);
        out.writeBytes(model + CRLF);

        // language (skip if "auto")
        if (langCode != null && !langCode.equals("auto") && !langCode.isEmpty()) {
            out.writeBytes("--" + boundary + CRLF);
            out.writeBytes("Content-Disposition: form-data; name=\"language\"" + CRLF + CRLF);
            out.writeBytes(langCode + CRLF);
        }

        // response_format
        out.writeBytes("--" + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"response_format\"" + CRLF + CRLF);
        out.writeBytes("json" + CRLF);

        out.writeBytes("--" + boundary + "--" + CRLF);
        out.flush();

        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 200 && responseCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        if (responseCode >= 200 && responseCode < 300) {
            return sb.toString();
        } else {
            throw new Exception("Groq API error " + responseCode + ": " + sb);
        }
    }

    private void sendUpdate(String message) {
        if (mUpdateListener != null) {
            mUpdateListener.onUpdateReceived(message);
        }
    }

    private void sendResult(WhisperResult whisperResult) {
        if (mUpdateListener != null) {
            mUpdateListener.onResultReceived(whisperResult);
        }
    }

}
