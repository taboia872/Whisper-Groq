package com.whispergroq.asr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converte PCM 16kHz mono 16-bit (little-endian) para formato WAV em memória.
 */
public class WavEncoder {

    public static byte[] encodePcmToWav(byte[] pcmData) throws IOException {
        int sampleRate = 16000;
        int channels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int totalSize = 36 + dataSize;

        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
        DataOutputStream dos = new DataOutputStream(out);

        // RIFF header
        dos.writeBytes("RIFF");
        writeIntLE(dos, totalSize);
        dos.writeBytes("WAVE");

        // fmt chunk
        dos.writeBytes("fmt ");
        writeIntLE(dos, 16);              // chunk size
        writeShortLE(dos, (short) 1);     // PCM
        writeShortLE(dos, (short) channels);
        writeIntLE(dos, sampleRate);
        writeIntLE(dos, byteRate);
        writeShortLE(dos, (short) blockAlign);
        writeShortLE(dos, (short) bitsPerSample);

        // data chunk
        dos.writeBytes("data");
        writeIntLE(dos, dataSize);
        dos.write(pcmData);

        dos.flush();
        return out.toByteArray();
    }

    private static void writeIntLE(DataOutputStream dos, int value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(value);
        dos.write(bb.array());
    }

    private static void writeShortLE(DataOutputStream dos, short value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(value);
        dos.write(bb.array());
    }
}
