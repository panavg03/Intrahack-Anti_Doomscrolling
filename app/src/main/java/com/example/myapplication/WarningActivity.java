package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class WarningActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warning);

        SharedPreferences prefs = PrefsManager.getPrefs(this);
        String appName =
                getIntent().getStringExtra("app_name");

        int usesLeft = prefs.getInt(
                PrefsManager.EMERGENCY_USES,
                3
        );

        TextView countText = findViewById(R.id.emergency_count);

        countText.setText(
                "Emergency Uses Left: " + usesLeft
        );

        Button emergencyBtn =
                findViewById(R.id.emergency_button);

        Button dismissBtn =
                findViewById(R.id.dismiss_button);

        if (usesLeft <= 0) {
            emergencyBtn.setEnabled(false);
            emergencyBtn.setText("No Uses Remaining");
        }

        emergencyBtn.setOnClickListener(v -> {

            int currentUses =
                    prefs.getInt(
                            PrefsManager.EMERGENCY_USES,
                            3
                    );

            if (currentUses > 0) {

                prefs.edit()
                        .putInt(
                                PrefsManager.EMERGENCY_USES,
                                currentUses - 1
                        )
                        .putBoolean(
                                PrefsManager.BONUS_PENDING,
                                true
                        )
                        .putString(
                                PrefsManager.BONUS_APP,
                                appName
                        )
                        .apply();

                finish();
            }
        });

        dismissBtn.setOnClickListener(v -> finish());
    }
}