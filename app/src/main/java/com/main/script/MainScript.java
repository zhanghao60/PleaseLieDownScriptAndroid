package com.main.script;

import android.content.Context;
import android.util.Log;

import com.app.pldscript.PLDScript;

public class MainScript {
    private static final String TAG = "MainScript";
    private static volatile boolean running = false;
    private static volatile Thread workerThread;

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context) {
        if (running) {
            Log.d(TAG, "Script already running");
            return;
        }
        if (com.app.pldscript.PLDScript.getInstance() == null) {
            android.widget.Toast.makeText(context, "请先在系统设置中开启无障碍服务", android.widget.Toast.LENGTH_LONG).show();
            try {
                context.startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
            return;
        }
        running = true;
        Log.d(TAG, "Script started");

        // 示例脚本：通过中断直接终止（不做运行标志检查）
        workerThread = new Thread(() -> {
            try {
                Thread.sleep(300);
                PLDScript.Click(500, 500, 100);
                Log.d(TAG, "点击(500, 500)，时长 100ms");
                Thread.sleep(2000);
                PLDScript.Click(500, 500, 100);
                Log.d(TAG, "点击(500, 500)，时长 100ms");
                Thread.sleep(1000);
                //滑动，从500,2000到600,1500，时间是1.5秒
                PLDScript.Swipe(500, 2000, 550, 1000, 1500);
            } catch (InterruptedException e) {
                // 直接退出线程
            } finally {
                running = false;
                Log.d(TAG, "Script finished or interrupted");
            }
        }, "PLD-MainScript-Worker");
        workerThread.start();
    }

    public static void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (workerThread != null) {
            try {
                workerThread.interrupt();
            } catch (Exception ignored) {}
            workerThread = null;
        }
        Log.d(TAG, "Script stopped");
    }
}
