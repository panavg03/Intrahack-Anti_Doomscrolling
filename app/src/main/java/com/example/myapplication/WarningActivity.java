package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;

public class WarningActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warning);

        Button dismissBtn = findViewById(R.id.dismiss_button);
        if (dismissBtn != null) {
            dismissBtn.setOnClickListener(v -> finish());
        }
    }
}