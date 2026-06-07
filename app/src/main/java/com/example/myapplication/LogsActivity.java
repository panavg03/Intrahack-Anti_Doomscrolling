package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class LogsActivity extends AppCompatActivity {

    private TextView tvLogs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        tvLogs = findViewById(R.id.tv_logs);
        Button btnClear = findViewById(R.id.btn_clear_logs);
        Button btnRefresh = findViewById(R.id.btn_refresh_logs);

        refreshLogs();

        btnClear.setOnClickListener(v -> {
            LogManager.clearLogs(this);
            refreshLogs();
        });

        btnRefresh.setOnClickListener(v -> refreshLogs());
    }

    private void refreshLogs() {
        List<String> logs = LogManager.getLogs(this);
        if (logs.isEmpty()) {
            tvLogs.setText("No logs yet...");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String log : logs) {
                sb.append(log).append("\n\n");
            }
            tvLogs.setText(sb.toString());
        }
    }
}
