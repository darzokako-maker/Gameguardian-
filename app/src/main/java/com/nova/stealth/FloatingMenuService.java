package com.nova.stealth;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingMenuService extends Service {
    private WindowManager windowManager;
    private LinearLayout floatingView;
    private WindowManager.LayoutParams params;

    static {
        System.loadLibrary("stealth");
    }
    public native boolean writeMemorySyscall(int pid, long address, int value);

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // GameGuardian Tarzı Siyah/Yarı Saydam Arayüz Tasarımı
        floatingView = new LinearLayout(this);
        floatingView.setOrientation(LinearLayout.VERTICAL);
        floatingView.setBackgroundColor(Color.parseColor("#CC111111"));
        floatingView.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText(" NovaMem GG Menu v4.0 ");
        title.setTextColor(Color.GREEN);
        title.setGravity(Gravity.CENTER);
        floatingView.addView(title);

        final EditText inputPid = new EditText(this);
        inputPid.setHint("Hedef PID");
        inputPid.setHintTextColor(Color.GRAY);
        inputPid.setTextColor(Color.WHITE);
        floatingView.addView(inputPid);

        final EditText inputAddress = new EditText(this);
        inputAddress.setHint("Bellek Adresi (Örn: 2131904224)");
        inputAddress.setHintTextColor(Color.GRAY);
        inputAddress.setTextColor(Color.WHITE);
        floatingView.addView(inputAddress);

        final EditText inputValue = new EditText(this);
        inputValue.setHint("Yeni Değer");
        inputValue.setHintTextColor(Color.GRAY);
        inputValue.setTextColor(Color.WHITE);
        floatingView.addView(inputValue);

        Button btnInject = new Button(this);
        btnInject.setText("Syscall Enjekte Et");
        btnInject.setBackgroundColor(Color.parseColor("#FF333333"));
        btnInject.setTextColor(Color.WHITE);
        
        btnInject.setOnClickListener(v -> {
            try {
                int pid = Integer.parseInt(inputPid.getText().toString());
                // Hex formatında değil, düz uzun sayı formatında adresi parse eder
                long addr = Long.parseLong(inputAddress.getText().toString()); 
                int val = Integer.parseInt(inputValue.getText().toString());

                boolean res = writeMemorySyscall(pid, addr, val);
                Toast.makeText(this, res ? "Değer Değiştirildi!" : "Enjeksiyon Başarısız!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Lütfen alanları doğru doldurun!", Toast.LENGTH_SHORT).show();
            }
        });
        floatingView.addView(btnInject);

        // Menünün ekranda serbestçe sürüklenebilmesini sağlayan Touch mekanizması
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 200;
        params.y = 200;

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}

