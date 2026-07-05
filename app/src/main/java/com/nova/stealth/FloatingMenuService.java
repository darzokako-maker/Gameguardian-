package com.nova.stealth;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
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

        // Ana Konteyner (Daha Büyük ve Ferah Tasarım)
        floatingView = new LinearLayout(this);
        floatingView.setOrientation(LinearLayout.VERTICAL);
        floatingView.setBackgroundColor(Color.parseColor("#F2141414")); // Yarı saydam net siyah teması
        floatingView.setPadding(35, 35, 35, 35); // İç boşluklar genişletildi

        // Başlık
        TextView tvTitle = new TextView(this);
        tvTitle.setText("🚀 NovaMem Engine Pro v5.0");
        tvTitle.setTextColor(Color.parseColor("#00FFC4"));
        tvTitle.setTextSize(20); // Yazı boyutu büyütüldü
        tvTitle.setPadding(0, 0, 0, 20);
        tvTitle.setGravity(Gravity.CENTER);
        floatingView.addView(tvTitle);

        // 1. OYUN PAKET ADI GİRİŞİ
        final EditText etPackage = new EditText(this);
        etPackage.setHint("com.tencent.ig (Paket Adı)");
        etPackage.setHintTextColor(Color.GRAY);
        etPackage.setTextColor(Color.WHITE);
        etPackage.setTextSize(16);
        setupInputKeyboardBehavior(etPackage); // Klavyeyi otomatik tetikleyen fonksiyon
        floatingView.addView(etPackage);

        final TextView tvPidStatus = new TextView(this);
        tvPidStatus.setText("Durum: Oyuna Bağlanılmadı");
        tvPidStatus.setTextColor(Color.YELLOW);
        tvPidStatus.setTextSize(14);
        tvPidStatus.setPadding(0, 10, 0, 10);
        floatingView.addView(tvPidStatus);

        Button btnFindPid = new Button(this);
        btnFindPid.setText("Oyuna Otomatik Bağlan (PID)");
        btnFindPid.setPadding(20, 25, 20, 25); // Parmakla rahat basılması için buton büyütüldü
        btnFindPid.setOnClickListener(v -> {
            closeKeyboard(v);
            String pkg = etPackage.getText().toString().trim();
            currentPid = getPidByName(pkg);
            if (currentPid > 0) {
                tvPidStatus.setText("✔ Bağlanıldı! PID: " + currentPid);
                tvPidStatus.setTextColor(Color.GREEN);
            } else {
                tvPidStatus.setText("❌ Hata: Oyun Açık Değil!");
                tvPidStatus.setTextColor(Color.RED);
            }
        });
        floatingView.addView(btnFindPid);

        // 2. VERİ TÜRÜ VE DEĞER ARAMA ALANI
        final EditText etValue = new EditText(this);
        etValue.setHint("Aranacak / Yazılacak Değer");
        etValue.setHintTextColor(Color.GRAY);
        etValue.setTextColor(Color.WHITE);
        etValue.setTextSize(16);
        setupInputKeyboardBehavior(etValue);
        floatingView.addView(etValue);

        final Button btnType = new Button(this);
        btnType.setText("Tür: DWORD (Integer)");
        btnType.setPadding(20, 25, 20, 25);
        final int[] currentType = {1};
        btnType.setOnClickListener(v -> {
            closeKeyboard(v);
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
        rowScan.setWeightSum(2);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        buttonParams.setMargins(5, 10, 5, 10);

        Button btnFirstScan = new Button(this);
        btnFirstScan.setText("İlk Tarama");
        btnFirstScan.setLayoutParams(buttonParams);
        btnFirstScan.setPadding(10, 25, 10, 25);
        btnFirstScan.setOnClickListener(v -> {
            closeKeyboard(v);
            if (currentPid == -1) { Toast.makeText(this, "Önce PID bulun!", Toast.LENGTH_SHORT).show(); return; }
            try {
                float val = Float.parseFloat(etValue.getText().toString());
                int count = firstScan(currentPid, currentType[0], val);
                Toast.makeText(this, "Bulunan Adres: " + count, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Geçerli bir sayı girin!", Toast.LENGTH_SHORT).show();
            }
        });
        rowScan.addView(btnFirstScan);

        Button btnNextScan = new Button(this);
        btnNextScan.setText("Filtrele");
        btnNextScan.setLayoutParams(buttonParams);
        btnNextScan.setPadding(10, 25, 10, 25);
        btnNextScan.setOnClickListener(v -> {
            closeKeyboard(v);
            if (currentPid == -1) return;
            try {
                float val = Float.parseFloat(etValue.getText().toString());
                int count = nextScan(currentPid, currentType[0], 1, val);
                Toast.makeText(this, "Kalan Adres: " + count, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Geçerli bir sayı girin!", Toast.LENGTH_SHORT).show();
            }
        });
        rowScan.addView(btnNextScan);
        floatingView.addView(rowScan);

        // 3. TOPLU İŞLEMLER
        Button btnWriteAll = new Button(this);
        btnWriteAll.setText("Hepsini Birden Değiştir");
        btnWriteAll.setPadding(20, 25, 20, 25);
        btnWriteAll.setBackgroundColor(Color.parseColor("#CC222222"));
        btnWriteAll.setTextColor(Color.WHITE);
        btnWriteAll.setOnClickListener(v -> {
            closeKeyboard(v);
            if (currentPid == -1) return;
            try {
                float val = Float.parseFloat(etValue.getText().toString());
                boolean ok = writeAll(currentPid, currentType[0], val);
                Toast.makeText(this, ok ? "Tümü Güncellendi!" : "Hata!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Hatalı Değer!", Toast.LENGTH_SHORT).show();
            }
        });
        floatingView.addView(btnWriteAll);

        // 4. CANLI ADRES GÖSTERİM ALANI
        TextView tvResultsTitle = new TextView(this);
        tvResultsTitle.setText("\n=== CANLI ADRES LİSTESİ ===");
        tvResultsTitle.setTextColor(Color.MAGENTA);
        tvResultsTitle.setTextSize(14);
        floatingView.addView(tvResultsTitle);

        ScrollView scrollView = new ScrollView(this);
        // Liste görünüm kutusu parmakla rahat kaydırılsın diye büyütüldü (Genişlik: 550, Yükseklik: 300)
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(550, 300);
        scrollView.setLayoutParams(scrollParams);
        scrollView.setBackgroundColor(Color.parseColor("#33000000"));

        final TextView tvResultsList = new TextView(this);
        tvResultsList.setText("Tarama yapıldığında adresler burada listelenir...");
        tvResultsList.setTextColor(Color.WHITE);
        tvResultsList.setTextSize(14);
        tvResultsList.setPadding(10, 10, 10, 10);
        scrollView.addView(tvResultsList);
        floatingView.addView(scrollView);

        Button btnRefreshList = new Button(this);
        btnRefreshList.setText("Listeyi Yenile");
        btnRefreshList.setPadding(20, 20, 20, 20);
        btnRefreshList.setOnClickListener(v -> {
            closeKeyboard(v);
            tvResultsList.setText(getResultsString());
        });
        floatingView.addView(btnRefreshList);

        // Tekil İndeks ve Ofset Alanı
        final EditText etIndex = new EditText(this);
        etIndex.setHint("İşlem Yapılacak İndeks No (Örn: 0)");
        etIndex.setHintTextColor(Color.GRAY);
        etIndex.setTextColor(Color.WHITE);
        etIndex.setTextSize(16);
        setupInputKeyboardBehavior(etIndex);
        floatingView.addView(etIndex);

        LinearLayout rowSingle = new LinearLayout(this);
        rowSingle.setOrientation(LinearLayout.HORIZONTAL);
        rowSingle.setWeightSum(2);

        Button btnWriteSingle = new Button(this);
        btnWriteSingle.setText("Tekli Değiştir");
        btnWriteSingle.setLayoutParams(buttonParams);
        btnWriteSingle.setPadding(10, 25, 10, 25);
        btnWriteSingle.setOnClickListener(v -> {
            closeKeyboard(v);
            try {
                int idx = Integer.parseInt(etIndex.getText().toString());
                float val = Float.parseFloat(etValue.getText().toString());
                writeIndex(currentPid, idx, currentType[0], val);
            } catch (Exception e) {
                Toast.makeText(this, "İndeks veya Değer Geçersiz!", Toast.LENGTH_SHORT).show();
            }
        });
        rowSingle.addView(btnWriteSingle);

        Button btnAnalyze = new Button(this);
        btnAnalyze.setText("Ofset Bul");
        btnAnalyze.setLayoutParams(buttonParams);
        btnAnalyze.setPadding(10, 25, 10, 25);
        btnAnalyze.setOnClickListener(v -> {
            closeKeyboard(v);
            try {
                int idx = Integer.parseInt(etIndex.getText().toString());
                String report = analyzePointer(currentPid, idx);
                Toast.makeText(this, report, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "İndeks Hatalı!", Toast.LENGTH_SHORT).show();
            }
        });
        rowSingle.addView(btnAnalyze);
        floatingView.addView(rowSingle);

        // Menü Konumlandırma Ayarları (Kapsayıcı Genişlik Artırıldı)
        params = new WindowManager.LayoutParams(
                650, // Sabit ve rahat genişlik (Geniş menü)
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Başlangıçta klavye engeli yok
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100; params.y = 100;

        // Sürükleme Mekanizması
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

    // 🚨 AKILLI YAZI KUTUSU DAVRANIŞI: Kutuya dokunulduğu an ekranı klavyeye odaklar
    private void setupInputKeyboardBehavior(final EditText editText) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // Odaklanmayı aç
                params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(floatingView, params);
                editText.requestFocus();
                
                // Klavyeyi zorla göster
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                }
            }
            return false;
        });
    }

    // 🚨 KLAVYE KAPATMA YARDIMCISI: İşlem butonlarına basıldığında odağı bırakır ve klavyeyi kapatır
    private void closeKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        // Oyunu engellememek için odağı tekrar kapat
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(floatingView, params);
        floatingView.clearFocus();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}
