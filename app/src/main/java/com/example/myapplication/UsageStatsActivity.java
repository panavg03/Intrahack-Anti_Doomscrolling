package com.example.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class UsageStatsActivity extends AppCompatActivity {

    private TextView tvInstagramTime;
    private TextView tvYoutubeTime;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage_stats);

        tvInstagramTime = findViewById(R.id.tv_instagram_time);
        tvYoutubeTime = findViewById(R.id.tv_youtube_time);
        Button btnReset = findViewById(R.id.btn_reset_stats);

        prefs = PrefsManager.getPrefs(this);

        updateUI();

        btnReset.setOnClickListener(v -> {
            prefs.edit()
                .putInt(PrefsManager.INSTAGRAM_TOTAL_SECONDS, 0)
                .putInt(PrefsManager.YOUTUBE_TOTAL_SECONDS, 0)
                .apply();
            updateUI();
        });

        // Refresh stats every second while viewing
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                handler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    private void updateUI() {
        int instaSecs = prefs.getInt(PrefsManager.INSTAGRAM_TOTAL_SECONDS, 0);
        int ytSecs = prefs.getInt(PrefsManager.YOUTUBE_TOTAL_SECONDS, 0);

        tvInstagramTime.setText(formatTime(instaSecs));
        tvYoutubeTime.setText(formatTime(ytSecs));
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("Time: %dm %ds", minutes, seconds);
    }
}
