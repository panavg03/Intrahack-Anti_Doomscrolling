package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {

    private static final String PREF_NAME = "intrahack";

    public static final String EMERGENCY_USES = "emergency_uses";
    public static final String BONUS_APP = "bonus_app";
    public static final String BONUS_PENDING = "bonus_pending";
    
    public static final String INSTAGRAM_TOTAL_SECONDS = "instagram_total_seconds";
    public static final String YOUTUBE_TOTAL_SECONDS = "youtube_total_seconds";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );
    }
}
