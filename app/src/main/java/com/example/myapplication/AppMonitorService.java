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
import com.example.myapplication.bluetooth.BLEManager;

import androidx.core.app.NotificationCompat;

public class AppMonitorService extends Service {

    private static final String TAG = "INTRAHACK";
    private static final String CHANNEL_ID = "intrahack_monitor";
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int NOTIFICATION_ID = 1;

    private int instagramBonusSeconds = 0;
    private int youtubeBonusSeconds = 0;

    private Handler handler;
    private Runnable pollRunnable;
    private UsageTracker usageTracker;

    private int instagramSeconds = 0;
    private int youtubeSeconds = 0;
    private boolean warningLaunched = false;

    private int instagramLimitSec = 0;
    private int youtubeLimitSec = 0;

    private String currentInstagramScreen = "OTHER";
    private boolean isScrolling = false;
    private boolean isOverlayShowing = false;
    private String lastDetectedPkg = "";

    private WindowManager windowManager;
    private View overlayView;
    private BLEManager bleManager;

    private final BroadcastReceiver screenTypeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (IntraAccessibilityService.ACTION_SCREEN_TYPE.equals(intent.getAction())) {
                String oldScreen = currentInstagramScreen;
                currentInstagramScreen = intent.getStringExtra(IntraAccessibilityService.EXTRA_SCREEN_TYPE);
                isScrolling = intent.getBooleanExtra(IntraAccessibilityService.EXTRA_IS_SCROLLING, false);
                
                if (currentInstagramScreen != null && !currentInstagramScreen.equals(oldScreen)) {
                    LogManager.log(context, "Accessibility: Section changed to " + currentInstagramScreen);
                }
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        LogManager.log(this, "Monitor Service: Initializing");
        usageTracker = new UsageTracker(this);
        handler = new Handler(Looper.getMainLooper());

        loadLimits();
        loadTotalTime();

        IntentFilter filter = new IntentFilter(IntraAccessibilityService.ACTION_SCREEN_TYPE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenTypeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenTypeReceiver, filter);
        }

        startForegroundWithNotification();
        
        try {
            bleManager = new BLEManager(this);
            bleManager.setCallback(new BLEManager.BLECallback() {
                @Override
                public void onConnected() {
                    LogManager.log(AppMonitorService.this, "BLE: Ring connected");
                }

                @Override
                public void onDisconnected() {
                    LogManager.log(AppMonitorService.this, "BLE: Ring disconnected");
                }

                @Override
                public void onAccelDataReceived(short x, short y, short z, long timestamp) {}

                @Override
                public void onError(String message) {
                    LogManager.log(AppMonitorService.this, "BLE Error: " + message);
                }
            });
        } catch (SecurityException e) {
            LogManager.log(this, "BLE Error: Permission denied in service");
        }

        startPolling();
    }

    private void loadLimits() {
        SharedPreferences prefs = PrefsManager.getPrefs(this);
        int instaMin = prefs.getInt("instagram_limit", 60);
        int ytMin = prefs.getInt("youtube_limit", 60);
        instagramLimitSec = instaMin * 60;
        youtubeLimitSec = ytMin * 60;
        LogManager.log(this, "Config: IG Limit=" + instagramLimitSec + "s, YT Limit=" + youtubeLimitSec + "s");
    }

    private void loadTotalTime() {
        SharedPreferences prefs = PrefsManager.getPrefs(this);
        instagramSeconds = prefs.getInt(PrefsManager.INSTAGRAM_TOTAL_SECONDS, 0);
        youtubeSeconds = prefs.getInt(PrefsManager.YOUTUBE_TOTAL_SECONDS, 0);
        LogManager.log(this, "Cache: Loaded saved usage IG=" + instagramSeconds + "s, YT=" + youtubeSeconds + "s");
    }

    private void saveTotalTime() {
        SharedPreferences prefs = PrefsManager.getPrefs(this);
        prefs.edit()
                .putInt(PrefsManager.INSTAGRAM_TOTAL_SECONDS, instagramSeconds)
                .putInt(PrefsManager.YOUTUBE_TOTAL_SECONDS, youtubeSeconds)
                .apply();
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
        SharedPreferences prefs = PrefsManager.getPrefs(this);

        boolean pendingBonus = prefs.getBoolean(PrefsManager.BONUS_PENDING, false);
        if (pendingBonus) {
            String bonusApp = prefs.getString(PrefsManager.BONUS_APP, "");
            if ("instagram".equals(bonusApp)) {
                instagramBonusSeconds += 300;
            } else if ("youtube".equals(bonusApp)) {
                youtubeBonusSeconds += 300;
            }
            warningLaunched = false;
            prefs.edit().putBoolean(PrefsManager.BONUS_PENDING, false).apply();
            LogManager.log(this, "Bonus: 5m extension applied for " + bonusApp);
        }

        int usesLeft = prefs.getInt(PrefsManager.EMERGENCY_USES, 3);
        String pkg = usageTracker.getForegroundApp();

        if (pkg == null) return;

        if (!pkg.equals(lastDetectedPkg)) {
            LogManager.log(this, "Detection: App -> " + pkg);
            lastDetectedPkg = pkg;
        }

        if (pkg.equals("com.instagram.android")) {
            boolean isOnReels = "INSTAGRAM_REELS".equals(currentInstagramScreen);
            
            if (isOnReels) {
                instagramSeconds++;
                saveTotalTime();
                int totalAllowed = instagramLimitSec + instagramBonusSeconds;
                
                if (instagramSeconds % 10 == 0) {
                    LogManager.log(this, "Status: IG Reels usage " + instagramSeconds + "/" + totalAllowed + "s");
                }

                if (instagramSeconds >= totalAllowed) {
                    if (usesLeft <= 0 && !isOverlayShowing) {
                        LogManager.log(this, "Limit: IG Reels BLOCKED");
                        showOverlay("BRAINROT DETECTED!\nNo emergency uses left. STOP SCROLLING.");
                    } else if (!warningLaunched && !isOverlayShowing) {
                        LogManager.log(this, "Limit: IG Reels warning triggered");
                        launchWarning("instagram");
                    }
                }
            } else {
                if (isOverlayShowing) hideOverlay();
            }

        } else if (pkg.equals("com.google.android.youtube")) {
            youtubeSeconds++;
            saveTotalTime();
            int totalAllowed = youtubeLimitSec + youtubeBonusSeconds;
            
            if (youtubeSeconds % 10 == 0) {
                LogManager.log(this, "Status: YouTube usage " + youtubeSeconds + "/" + totalAllowed + "s");
            }

            if (youtubeSeconds >= totalAllowed && !warningLaunched && !isOverlayShowing) {
                LogManager.log(this, "Limit: YouTube warning triggered");
                launchWarning("youtube");
            }
        } else {
            if (warningLaunched && !pkg.equals(getPackageName()) && !pkg.equals("com.example.myapplication")) {
                warningLaunched = false;
                LogManager.log(this, "Monitor: Resetting warning flag (left restricted app)");
            }
        }
    }

    private void showOverlay(String messageText) {
        if (!Settings.canDrawOverlays(this)) {
            LogManager.log(this, "UI Error: Missing Overlay Permission");
            return;
        }

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

            dismissBtn.setOnClickListener(v -> hideOverlay());

            try {
                windowManager.addView(overlayView, params);
                isOverlayShowing = true;
                LogManager.log(this, "UI: Blocking overlay shown");
            } catch (Exception e) {
                Log.e(TAG, "Error adding overlay: " + e.getMessage());
            }
        });
    }

    private void launchWarning(String appName) {
        if (warningLaunched) return;
        warningLaunched = true;

        if (bleManager != null && bleManager.isDeviceConnected()) {
            bleManager.sendShockCommand(200, 150);
            LogManager.log(this, "Action: Shock command sent to ring");
        } else {
            LogManager.log(this, "Action: No ring connected for shock");
        }

        Intent intent = new Intent(this, WarningActivity.class);
        intent.putExtra("app_name", appName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "warn_channel", "Warnings", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification n = new NotificationCompat.Builder(this, "warn_channel")
                .setContentTitle("🚨 Time's up!")
                .setContentText("Stop scrolling " + appName + " and take a break!")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(99, n);
    }

    private void hideOverlay() {
        if (isOverlayShowing && windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
                LogManager.log(this, "UI: Warning overlay hidden");
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay: " + e.getMessage());
            }
            isOverlayShowing = false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadLimits();
        LogManager.log(this, "Monitor Service: onStartCommand executed");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        LogManager.log(this, "Monitor Service: Stopping");
        super.onDestroy();
        try {
            unregisterReceiver(screenTypeReceiver);
        } catch (Exception e) {}
        
        if (handler != null) handler.removeCallbacks(pollRunnable);
        hideOverlay();
        if (bleManager != null) {
            bleManager.disconnect();
        }
    }
}
