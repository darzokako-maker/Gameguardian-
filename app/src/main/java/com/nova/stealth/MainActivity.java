package com.nova.stealth;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.DataOutputStream;

public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_PERMISSION_REQ_CODE = 5469;

    @Override
    protected void Bundle) {
        super.onCreate(savedInstanceState);
        
        // Basit bir ana ekran tasarımı oluşturuyoruz
        Button startButton = new Button(this);
        startButton.setText("Hile Menüsünü Başlat");
        setContentView(startButton);

        startButton.setOnClickListener(v -> {
            checkOverlayPermission();
            requestRootPermission();
        });
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            } else {
                startFloatingService();
            }
        } else {
            startFloatingService();
        }
    }

    private void requestRootPermission() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();
        } catch (Exception e) {
            Toast.makeText(this, "Root izni alınamadı! Cihaz rootlu mu?", Toast.LENGTH_LONG).show();
        }
    }

    private void startFloatingService() {
        Intent intent = new Intent(MainActivity.this, FloatingMenuService.class);
        startService(intent);
        finish(); // Arka planda kalmasın diye ana ekranı kapatıyoruz
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingService();
            }
        }
    }
          }

