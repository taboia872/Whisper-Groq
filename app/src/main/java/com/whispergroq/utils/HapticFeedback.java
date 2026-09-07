package com.whispergroq.utils;

import static android.content.Context.VIBRATOR_SERVICE;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;

public class HapticFeedback {

    public static void vibrateStart(Context context){
        vibrate(context, 20, 255);
    }

    public static void vibrateDone(Context context){
        vibrate(context, 30, 180);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        vibrate(context, 20, 255);
    }

    public static void vibrateError(Context context){
        vibrate(context, 100, 255);
        try { Thread.sleep(80); } catch (InterruptedException ignored) {}
        vibrate(context, 100, 255);
    }

    public static void vibrate(Context context){
        vibrate(context, 15, 200);
    }

    private static void vibrate(Context context, long ms, int amplitude){
        if (!hapticEnabled(context)) return;
        Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        }
    }

    private static boolean hapticEnabled(Context context){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null && vm.getDefaultVibrator().hasVibrator();
        }
        Vibrator v = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        return v != null && v.hasVibrator();
    }
}
