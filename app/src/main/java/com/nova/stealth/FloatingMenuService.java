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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingMenuService extends Service {
    private WindowManager windowManager;
    private LinearLayout floatingView;
    private WindowManager.LayoutParams params;
    private int currentPid = -1;

    static {
        System.loadLibrary("stealth");
    }

    // Native Metot Tanımlamaları
    public native int getPidByName(String packageName);
    public native int firstScan(int pid, int type, float value);
    public native int nextScan(int pid, int type, int mode, float value);
    public native boolean writeAll(int pid, int type, float value);
    public native boolean writeIndex(int pid, int index, int type, float value);
    public native String getResultsString();
    public native String analyzePointer(int pid, int index);

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Ana Konteyner (Yüzen Panel)
        floatingView = new LinearLayout(this);
        floatingView.setOrientation(LinearLayout.VERTICAL);
        floatingView.setBackgroundColor(Color.parseColor("#EE1A1A1A")); // Şeffaf Siyah GG Teması
        floatingView.setPadding(25, 25, 25, 25);

        // Başlık
        TextView tvTitle = new TextView(this);
        tvTitle.setText(" NovaMem Engine Pro v4.5");
        tvTitle.setTextColor(Color.CYAN);
        tvTitle.setTextSize(16);
        tvTitle.setGravity(Gravity.CENTER);
        floatingView.addView(tvTitle);

        // 1. OYUN PAKET ADI GİRİŞİ VE PID BULUCU
        final EditText etPackage = new EditText(this);
        etPackage.setHint("com.tencent.ig (Paket Adı)");
        etPackage.setHintTextColor(Color.GRAY);
        etPackage.setTextColor(Color.WHITE);
        floatingView.addView(etPackage);

        final TextView tvPidStatus = new TextView(this);
        tvPidStatus.setText("Durum: Oyuna Bağlanılmadı");
        tvPidStatus.setTextColor(Color.YELLOW);
        floatingView.addView(tvPidStatus);

        Button btnFindPid = new Button(this);
        btnFindPid.setText("Oyuna Otomatik Bağlan (PID)");
        btnFindPid.setOnClickListener(v -> {
            String pkg = etPackage.getText().toString().trim();
            currentPid = getPidByName(pkg);
            if (currentPid > 0) {
                tvPidStatus.setText("Bağlanıldı! PID: " + currentPid);
                tvPidStatus.setTextColor(Color.GREEN);
            } else {
                tvPidStatus.setText("Hata: Oyun Arka Planda Açık Değil!");
                tvPidStatus.setTextColor(Color.RED);
            }
        });
        floatingView.addView(btnFindPid);

        // 2. VERİ TÜRÜ VE DEĞER ARAMA ALANI
        final EditText etValue = new EditText(this);
        etValue.setHint("Aranacak / Yazılacak Değer");
        etValue.setHintTextColor(Color.GRAY);
        etValue.setTextColor(Color.WHITE);
        floatingView.addView(etValue);

        // Tür Seçimi (Basitlik adına: Tıklayınca Değişen Buton)
        final Button btnType = new Button(this);
        btnType.setText("Tür: DWORD (Integer)");
        final int[] currentType = {1}; // 1 = Dword, 2 = Float
        btnType.setOnClickListener(v -> {
            if (currentType[0] == 1) {
                currentType[0] = 2;
                btnType.setText("Tür: FLOAT (Ondalıklı)");
            } else {
                currentType[0] = 1;
                btnType.setText("Tür: DWORD (Integer)");
            }
        });
        floatingView.addView(btnType);

        // Tarama Butonları Satırı
        LinearLayout rowScan = new LinearLayout(this);
        rowScan.setOrientation(LinearLayout.HORIZONTAL);

        Button btnFirstScan = new Button(this);
        btnFirstScan.setText("İlk Tarama");
        btnFirstScan.setOnClickListener(v -> {
            if (currentPid == -1) { Toast.makeText(this, "Önce PID bulun!", Toast.LENGTH_SHORT).show(); return; }
            float val = Float.parseFloat(etValue.getText().toString());
            int count = firstScan(currentPid, currentType[0], val);
            Toast.makeText(this, "Bulunan Adres: " + count, Toast.LENGTH_LONG).show();
        });
        rowScan.addView(btnFirstScan);

        Button btnNextScan = new Button(this);
        btnNextScan.setText("Filtrele (Tam)");
        btnNextScan.setOnClickListener(v -> {
            if (currentPid == -1) return;
            float val = Float.parseFloat(etValue.getText().toString());
            int count = nextScan(currentPid, currentType[0], 1, val);
            Toast.makeText(this, "Kalan Adres: " + count, Toast.LENGTH_LONG).show();
        });
        rowScan.addView(btnNextScan);
        floatingView.addView(rowScan);

        // 3. TOPLU VE TEKLİ İŞLEMLER
        LinearLayout rowModify = new LinearLayout(this);
        rowModify.setOrientation(LinearLayout.HORIZONTAL);

        Button btnWriteAll = new Button(this);
        btnWriteAll.setText("Hepsini Değiştir");
        btnWriteAll.setOnClickListener(v -> {
            if (currentPid == -1) return;
            float val = Float.parseFloat(etValue.getText().toString());
            boolean ok = writeAll(currentPid, currentType[0], val);
            Toast.makeText(this, ok ? "Tümü Güncellendi!" : "Hata!", Toast.LENGTH_SHORT).show();
        });
        rowModify.addView(btnWriteAll);
        floatingView.addView(rowModify);

        // 4. SONUÇLARI GÖRME VE POINTER AYIKLAMA ALANI
        TextView tvResultsTitle = new TextView(this);
        tvResultsTitle.setText("=== CANLI ADRES LİSTESİ ===");
        tvResultsTitle.setTextColor(Color.MAGENTA);
        floatingView.addView(tvResultsTitle);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(400, 200);
        scrollView.setLayoutParams(scrollParams);

        final TextView tvResultsList = new TextView(this);
        tvResultsList.setText("Tarama yapıldığında adresler burada listelenir...");
        tvResultsList.setTextColor(Color.WHITE);
        scrollView.addView(tvResultsList);
        floatingView.addView(scrollView);

        Button btnRefreshList = new Button(this);
        btnRefreshList.setText("Listeyi Yenile");
        btnRefreshList.setOnClickListener(v -> tvResultsList.setText(getResultsString()));
        floatingView.addView(btnRefreshList);

        // Tekil İndeks ve Pointer İşlem Alanı
        final EditText etIndex = new EditText(this);
        etIndex.setHint("İşlem Yapılacak İndeks No (Örn: 0)");
        etIndex.setHintTextColor(Color.GRAY);
        etIndex.setTextColor(Color.WHITE);
        floatingView.addView(etIndex);

        LinearLayout rowSingle = new LinearLayout(this);
        rowSingle.setOrientation(LinearLayout.HORIZONTAL);

        Button btnWriteSingle = new Button(this);
        btnWriteSingle.setText("Sadece Bunu Değiştir");
        btnWriteSingle.setOnClickListener(v -> {
            int idx = Integer.parseInt(etIndex.getText().toString());
            float val = Float.parseFloat(etValue.getText().toString());
            writeIndex(currentPid, idx, currentType[0], val);
        });
        rowSingle.addView(btnWriteSingle);

        Button btnAnalyze = new Button(this);
        btnAnalyze.setText("Pointer/Ofset Bul");
        btnAnalyze.setOnClickListener(v -> {
            int idx = Integer.parseInt(etIndex.getText().toString());
            String report = analyzePointer(currentPid, idx);
            Toast.makeText(this, report, Toast.LENGTH_LONG).show();
        });
        rowSingle.addView(btnAnalyze);
        floatingView.addView(rowSingle);

        // Menü Sürükleme Mantığı
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 150; params.y = 150;

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
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
