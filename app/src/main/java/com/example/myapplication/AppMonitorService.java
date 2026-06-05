package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

public class AppMonitorService extends Service {

    private static final String TAG = "INTRAHACK";
    private static final String CHANNEL_ID = "intrahack_monitor";
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int NOTIFICATION_ID = 1;

    private Handler handler;
    private Runnable pollRunnable;
    private UsageTracker usageTracker;

    private int instagramSeconds = 0;
    private int youtubeSeconds = 0;
    
    private int instagramLimitSec;
    private int youtubeLimitSec;

    private boolean isOnReels = false;
    private boolean isOverlayShowing = false;

    private WindowManager windowManager;
    private View overlayView;

    // Receives updates from IntraAccessibilityService about current screen type
    private final BroadcastReceiver screenTypeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (IntraAccessibilityService.ACTION_SCREEN_TYPE.equals(intent.getAction())) {
                String typeStr = intent.getStringExtra(IntraAccessibilityService.EXTRA_SCREEN_TYPE);
                isOnReels = IntraAccessibilityService.ScreenType.INSTAGRAM_REELS.name().equals(typeStr);
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        usageTracker = new UsageTracker(this);
        handler = new Handler(Looper.getMainLooper());
        
        loadLimits();

        IntentFilter filter = new IntentFilter(IntraAccessibilityService.ACTION_SCREEN_TYPE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenTypeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenTypeReceiver, filter);
        }
        
        startForegroundWithNotification();
        startPolling();
    }

    private void loadLimits() {
        SharedPreferences prefs = PrefsManager.getPrefs(this);
        // Load minutes from SharedPreferences and convert to seconds
        instagramLimitSec = prefs.getInt("instagram_limit", 60) * 60;
        youtubeLimitSec = prefs.getInt("youtube_limit", 60) * 60;
        Log.d(TAG, "Limits Updated: Insta=" + instagramLimitSec + "s, YT=" + youtubeLimitSec + "s");
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Intrahack Monitor", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Intrahack Active")
                .setContentText("Monitoring brainrot levels...")
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
        
        if ("com.instagram.android".equals(pkg)) {
            // BrainRot Mechanic: Only count if scrolling Reels
            if (isOnReels) {
                instagramSeconds++;
                Log.d(TAG, "Reels Progress: " + instagramSeconds + "s / " + instagramLimitSec + "s");
                if (instagramSeconds >= instagramLimitSec && !isOverlayShowing) {
                    showOverlay("BRAINROT DETECTED!\nStop scrolling Reels.");
                }
            } else {
                Log.d(TAG, "Instagram Open (DMs/Profile) - Timer Paused");
            }
        } else if ("com.google.android.youtube".equals(pkg)) {
            youtubeSeconds++;
            if (youtubeSeconds >= youtubeLimitSec && !isOverlayShowing) {
                showOverlay("YOUTUBE LIMIT REACHED!");
            }
        }
    }

    private void showOverlay(String messageText) {
        if (!Settings.canDrawOverlays(this)) return;

        handler.post(() -> {
            if (isOverlayShowing) return;
            
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            overlayView = inflater.inflate(R.layout.overlay_warning, null);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP;
            params.y = 100;

            Button dismissBtn = overlayView.findViewById(R.id.btn_dismiss_overlay);
            TextView message = overlayView.findViewById(R.id.tv_overlay_message);
            message.setText(messageText);

            dismissBtn.setOnClickListener(v -> {
                instagramSeconds = 0;
                youtubeSeconds = 0;
                hideOverlay();
            });

            try {
                windowManager.addView(overlayView, params);
                isOverlayShowing = true;
            } catch (Exception e) {
                Log.e(TAG, "Error adding overlay: " + e.getMessage());
            }
        });
    }

    private void hideOverlay() {
        if (isOverlayShowing && windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay: " + e.getMessage());
            }
            isOverlayShowing = false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadLimits(); // Refresh limits if the user updated them in Settings
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenTypeReceiver);
        if (handler != null) handler.removeCallbacks(pollRunnable);
        hideOverlay();
    }
}
