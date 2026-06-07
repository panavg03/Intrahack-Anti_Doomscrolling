package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.bluetooth.BLEManager;

public class BluetoothActivity extends AppCompatActivity {

    private BLEManager bleManager;
    private TextView statusText;
    private Button scanBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        scanBtn = findViewById(R.id.scanBtn);
        statusText = findViewById(R.id.statusText);

        bleManager = new BLEManager(this);
        bleManager.setCallback(new BLEManager.BLECallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    statusText.setText("Status: Ring Connected");
                    LogManager.log(BluetoothActivity.this, "BLE: Connected to Ring");
                    Toast.makeText(BluetoothActivity.this, "Connected!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    statusText.setText("Status: Disconnected");
                    LogManager.log(BluetoothActivity.this, "BLE: Disconnected");
                });
            }

            @Override
            public void onAccelDataReceived(short x, short y, short z, long timestamp) {
                // Not showing raw data on UI to keep it clean
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    statusText.setText("Status: Error - " + message);
                    LogManager.log(BluetoothActivity.this, "BLE Error: " + message);
                    Toast.makeText(BluetoothActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });

        scanBtn.setOnClickListener(v -> {
            statusText.setText("Status: Scanning for Ring...");
            LogManager.log(this, "BLE: Manual scan started");
            bleManager.startScan();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // We might want to keep it connected for the service, but if we close activity
        // and it was only for connecting, we can decide.
        // For now, let's not disconnect here so the background service can use it.
    }
}
