package com.example.myapplication;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    EditText instagramLimit;
    EditText youtubeLimit;

    Button saveBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }


        instagramLimit =
                findViewById(R.id.instagramLimit);

        youtubeLimit =
                findViewById(R.id.youtubeLimit);

        saveBtn =
                findViewById(R.id.saveBtn);

        SharedPreferences prefs =
                PrefsManager.getPrefs(this);

        instagramLimit.setText(
                String.valueOf(
                        prefs.getInt(
                                "instagram_limit",
                                60)));

        youtubeLimit.setText(
                String.valueOf(
                        prefs.getInt(
                                "youtube_limit",
                                60)));

        saveBtn.setOnClickListener(v -> {
            String instaText =
                    instagramLimit
                            .getText()
                            .toString()
                            .trim();

            if(instaText.isEmpty()) {
                instaText = "60";
            }

            int insta =
                    Integer.parseInt(instaText);

            String ytText =
                    youtubeLimit
                            .getText()
                            .toString()
                            .trim();

            if(ytText.isEmpty()) {
                ytText = "60";
            }

            int yt =
                    Integer.parseInt(ytText);

            prefs.edit()
                    .putInt(
                            "instagram_limit",
                            insta)
                    .putInt(
                            "youtube_limit",
                            yt)
                    .apply();

            Toast.makeText(
                            this,
                            "Settings Saved",
                            Toast.LENGTH_SHORT)
                    .show();

            finish();
        });
    }
}