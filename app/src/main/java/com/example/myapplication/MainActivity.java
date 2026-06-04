package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    Button startBtn;
    Button permissionBtn;
    Button settingsBtn;
    Button bluetoothBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.startBtn);
        permissionBtn = findViewById(R.id.permissionBtn);
        settingsBtn = findViewById(R.id.settingsBtn);
        bluetoothBtn = findViewById(R.id.bluetoothBtn);

        bluetoothBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BluetoothActivity.class);
            startActivity(intent);
        });

        settingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        permissionBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        });

        // Start button now goes through overlay permission check first
        startBtn.setOnClickListener(v -> checkOverlayPermission());
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, 1234);
                Toast.makeText(this,
                        "Please grant 'Display over other apps' permission, then press Start again",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }
        // Permission already granted — start service directly
        startMonitoringService();
    }

    private void startMonitoringService() {
        Intent serviceIntent = new Intent(MainActivity.this, AppMonitorService.class);
        startService(serviceIntent);
        Toast.makeText(MainActivity.this, "Monitoring Started", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Called after user returns from the overlay permission settings screen
        if (requestCode == 1234) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    // Permission was granted — start service
                    startMonitoringService();
                } else {
                    // Still not granted
                    Toast.makeText(this,
                            "Overlay permission is required for the warning screen to appear",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}