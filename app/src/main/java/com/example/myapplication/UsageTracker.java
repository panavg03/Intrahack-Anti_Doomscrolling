package com.example.myapplication;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.util.Log;

public class UsageTracker {

    private static final String TAG = "INTRAHACK";
    private final UsageStatsManager usageStatsManager;
    private String lastKnownPackage = null; // CACHE last known app

    public UsageTracker(Context context) {
        usageStatsManager = (UsageStatsManager)
                context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    public String getForegroundApp() {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - 30_000; // 30 second window

        String latestPackage = null;
        long latestTimestamp = 0;

        try {
            UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (event.getTimeStamp() >= latestTimestamp) {
                        latestTimestamp = event.getTimeStamp();
                        latestPackage = event.getPackageName();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "UsageTracker error: " + e.getMessage());
        }

        // KEY FIX: if we got a real result, update cache
        if (latestPackage != null) {
            lastKnownPackage = latestPackage;
        }

        // Return cached value if current query returned null
        return lastKnownPackage;
    }
}