package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogManager {
    // Exact tag for Logcat filtering as requested
    public static final String TAG = "INTRAHACK";
    private static final String PREF_LOGS = "app_logs";
    private static final String KEY_LOG_LIST = "log_list";
    private static final int MAX_LOGS = 100;

    /**
     * Logs to both Logcat (with tag INTRAHACK) and persistent storage.
     */
    public static void log(Context context, String message) {
        if (message == null) message = "null";
        
        // Log to Logcat with multiple levels to ensure it shows up in most filters
        Log.e(TAG, "LOG: " + message);
        Log.i(TAG, "LOG: " + message);
        Log.d(TAG, "LOG: " + message);
        
        if (context == null) return;
        
        try {
            Context appContext = context.getApplicationContext();
            if (appContext == null) appContext = context;

            SharedPreferences prefs = appContext.getSharedPreferences(PREF_LOGS, Context.MODE_PRIVATE);
            String logsStr = prefs.getString(KEY_LOG_LIST, "");
            
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String newEntry = "[" + timestamp + "] " + message;
            
            // Store as a single string separated by delimiters for speed
            String updatedLogs = newEntry + ";;;" + (logsStr != null ? logsStr : "");
            
            // Keep string size reasonable (approx 50KB)
            if (updatedLogs.length() > 50000) {
                updatedLogs = updatedLogs.substring(0, 50000);
            }
            
            prefs.edit().putString(KEY_LOG_LIST, updatedLogs).apply();
        } catch (Exception e) {
            Log.e(TAG, "LogManager failed to save to disk: " + e.getMessage());
        }
    }

    public static List<String> getLogs(Context context) {
        List<String> list = new ArrayList<>();
        if (context == null) return list;
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_LOGS, Context.MODE_PRIVATE);
            String logsStr = prefs.getString(KEY_LOG_LIST, "");
            if (logsStr != null && !logsStr.isEmpty()) {
                String[] parts = logsStr.split(";;;");
                for (String p : parts) {
                    if (p != null && !p.trim().isEmpty()) {
                        list.add(p);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "LogManager failed to read logs: " + e.getMessage());
        }
        return list;
    }

    public static void clearLogs(Context context) {
        if (context == null) return;
        try {
            context.getApplicationContext().getSharedPreferences(PREF_LOGS, Context.MODE_PRIVATE).edit().clear().apply();
            Log.e(TAG, "Persistent logs cleared");
        } catch (Exception e) {
            Log.e(TAG, "LogManager failed to clear logs");
        }
    }
}
