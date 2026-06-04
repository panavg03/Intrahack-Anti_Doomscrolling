package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class AppMonitorService extends Service {

    private static final String TAG = "INTRAHACK";
    private static final String CHANNEL_ID = "intrahack_monitor";
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int NOTIFICATION_ID = 1;

    // HARDCODED TO 10 SECONDS FOR DEMO — change later
    private static final int LIMIT_SECONDS = 60;

    private Handler handler;
    private Runnable pollRunnable;
    private UsageTracker usageTracker;

    private int instagramSeconds = 0;
    private int youtubeSeconds = 0;
    private boolean warningLaunched = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SERVICE CREATED");
        usageTracker = new UsageTracker(this);
        handler = new Handler(Looper.getMainLooper());
        startForegroundWithNotification();
        startPolling();
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Intrahack Monitor", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Intrahack Active")
                .setContentText("Monitoring your screen time")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                checkForegroundApp();
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        handler.post(pollRunnable);
    }

    private void checkForegroundApp() {
        String pkg = usageTracker.getForegroundApp();
        Log.d(TAG, "FOREGROUND = " + pkg);

        if (pkg == null) return;

        if (pkg.equals("com.instagram.android")) {
            instagramSeconds++;
            Log.d(TAG, "Instagram = " + instagramSeconds + "s / limit = " + LIMIT_SECONDS + "s");
            if (instagramSeconds >= LIMIT_SECONDS && !warningLaunched) {
                launchWarning();
            }

        } else if (pkg.equals("com.google.android.youtube")) {
            youtubeSeconds++;
            Log.d(TAG, "YouTube = " + youtubeSeconds + "s / limit = " + LIMIT_SECONDS + "s");
            if (youtubeSeconds >= LIMIT_SECONDS && !warningLaunched) {
                launchWarning();
            }
        }
    }

    private void launchWarning() {
        if (warningLaunched) return;
        warningLaunched = true;
        Log.d(TAG, "*** LIMIT REACHED — FIRING WARNING ***");

        // Try activity launch
        Intent intent = new Intent(this, WarningActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        // Also fire a high-priority notification as backup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "warn_channel", "Warnings", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification n = new NotificationCompat.Builder(this, "warn_channel")
                .setContentTitle("🚨 Time's up!")
                .setContentText("You've been scrolling too long. Take a break!")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(99, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "SERVICE onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacks(pollRunnable);
    }
}