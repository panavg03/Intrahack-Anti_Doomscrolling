package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class BluetoothActivity
        extends AppCompatActivity {

    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_bluetooth);

        Button scanBtn =
                findViewById(R.id.scanBtn);

        TextView status =
                findViewById(R.id.statusText);

        scanBtn.setOnClickListener(v -> {

            status.setText(
                    "Scanning...");

            new Handler().postDelayed(() -> {

                status.setText(
                        "ESP32 Ring Connected");

            }, 2000);
        });
    }
}