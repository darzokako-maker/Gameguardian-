package com.nova.stealth;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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
    private LinearLayout rootContainer; // Kapsayıcı ana katman
    private LinearLayout menuContentView; // İçerik katmanı (Gizlenip açılabilen)
    private Button toggleButton; // Menüyü küçültme/büyütme butonu
    private WindowManager.LayoutParams params;
    
    private boolean isMinimized = false;
    private int currentPid = -1;
    private int currentType = 1; // 1: DWORD, 2: FLOAT

    static {
        System.loadLibrary("stealth");
    }

    // C++ Tarafındaki Yeni Yapıya Uygun JNI Metot Tanımları
    public native int getPidByName(String packageName);
    public native int firstScan(int pid, int type, float value);
    public native int nextScan(int pid, int type, int mode, float value);
    public native boolean writeAll(int pid, int type, float value);
    public native boolean writeIndex(int pid, int index, int type, float value);
    public native String getResultsString();
    
    // C++ kodundaki yeni pointer bulma mekanizmasının JNI karşılığı
    public native String otomatikPointerBul(int pid, long long targetAddress);

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 1. ANA KAPLAYICI (Root)
        rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);

        // 2. KÜÇÜLTME / BÜYÜTME BUTONU (Her zaman görünür)
        toggleButton = new Button(this);
        toggleButton.setText("▼ MENÜYÜ GİZLE / GÖSTER");
        toggleButton.setBackgroundColor(Color.parseColor("#FF0057"));
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setPadding(15, 15, 15, 15);
        toggleButton.setOnClickListener(v -> toggleMenuVisibility());
        rootContainer.addView(toggleButton);

        // 3. AŞAĞI KAYDIRILABİLİR İÇERİK ALANI (ScrollView)
        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);

        // 4. MENÜ İÇERİK PANELİ
        menuContentView = new LinearLayout(this);
        menuContentView.setOrientation(LinearLayout.VERTICAL);
        menuContentView.setBackgroundColor(Color.parseColor("#FA121212")); // Çok koyu şık arka plan
        menuContentView.setPadding(40, 30, 40, 30);

        // Başlık
        TextView tvTitle = new TextView(this);
        tvTitle.setText("🔥 NovaMem Ultra v3.5");
        tvTitle.setTextColor(Color.parseColor("#00FFC4"));
        tvTitle.setTextSize(22);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 25);
        menuContentView.addView(tvTitle);

        // Paket Adı Girişi
        final EditText etPackage = new EditText(this);
        etPackage.setHint("Uygulama Paket Adı (Örn: com.target.app)");
        etPackage.setHintTextColor(Color.GRAY);
        etPackage.setTextColor(Color.WHITE);
        etPackage.setTextSize(16);
        setupKeyboardBehavior(etPackage);
        menuContentView.addView(etPackage);

        final TextView tvPidStatus = new TextView(this);
        tvPidStatus.setText("Durum: Bağlantı Yok");
        tvPidStatus.setTextColor(Color.YELLOW);
        tvPidStatus.setTextSize(14);
        tvPidStatus.setPadding(0, 10, 0, 15);
        menuContentView.addView(tvPidStatus);

        Button btnFindPid = new Button(this);
        btnFindPid.setText("Bağlan (PID Bul)");
        btnFindPid.setPadding(20, 25, 20, 25);
        btnFindPid.setOnClickListener(v -> {
            closeKeyboard(v);
            String pkg = etPackage.getText().toString().trim();
            currentPid = getPidByName(pkg);
            if (currentPid > 0) {
                tvPidStatus.setText("✔ Bağlanıldı! PID: " + currentPid);
                tvPidStatus.setTextColor(Color.GREEN);
            } else {
                tvPidStatus.setText("❌ Hata: Süreç Bulunamadı!");
                tvPidStatus.setTextColor(Color.RED);
            }
        });
        menuContentView.addView(btnFindPid);

        // Değer Girişi
        final EditText etValue = new EditText(this);
        etValue.setHint("Aranacak / Yazılacak Değer");
        etValue.setHintTextColor(Color.GRAY);
        etValue.setTextColor(Color.WHITE);
        etValue.setTextSize(16);
        setupKeyboardBehavior(etValue);
        menuContentView.addView(etValue);

        // Veri Türü Seçimi
        final Button btnType = new Button(this);
        btnType.setText("Tür: DWORD (Integer)");
        btnType.setPadding(20, 25, 20, 25);
        btnType.setOnClickListener(v -> {
            closeKeyboard(v);
            if (currentType == 1) {
                currentType = 2;
                btnType.setText("Tür: FLOAT (Ondalıklı)");
            } else {
                currentType = 1;
                btnType.setText("Tür: DWORD (Integer)");
            }
        });
        menuContentView.addView(btnType);

        // Tarama ve Filtreleme Buton Satırı
        LinearLayout rowScan = new LinearLayout(this);
        rowScan.setOrientation(LinearLayout.HORIZONTAL);
        rowScan.setWeightSum(2);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams.setMargins(5, 10, 5, 10);

        Button btnFirstScan = new Button(this);
        btnFirstScan.setText("İlk Tarama");
        btnFirstScan.setLayoutParams(btnParams);
        btnFirstScan.setPadding(10, 25, 10, 25);
        btnFirstScan.setOnClickListener(v -> {
            closeKeyboard(v);
            if (currentPid <= 0) { Toast.makeText(this, "Önce PID Alın!", Toast.LENGTH_SHORT).show(); return; }
            try {
                float val = Float.parseFloat(etValue.getText().toString());
                int count = firstScan(currentPid, currentType, val);
                Toast.makeText(this, "Bulunan Adres: " + count, Toast.LENGTH_LONG).show();
            } catch (Exception e) { Toast.makeText(this, "Geçersiz değer!", Toast.LENGTH_SHORT).show(); }
        });
        rowScan.addView(btnFirstScan);

        Button btnNextScan = new Button(this);
        btnNextScan.setText("Filtrele");
        btnNextScan.setLayoutParams(btnParams);
        btnNextScan.setPadding(10, 25, 10, 25);
        btnNextScan.setOnClickListener(v -> {
            closeKeyboard(v);
            if (currentPid <= 0) return;
            try {
                float val = Float.parseFloat(etValue.getText().toString());
                int count = nextScan(currentPid, currentType, 1, val);
                Toast.makeText(this, "Kalan Adres: " + count, Toast.LENGTH_LONG).show();
            } catch (Exception e) { Toast.makeText(this, "Geçersiz değer!", Toast.LENGTH_SHORT).show(); }
        });
        rowScan.addView(btnNextScan);
        menuContentView.addView(rowScan);

        // Toplu İşlemler
        Button btnWriteAll = new Button(this);
        btnWriteAll.setText("Hepsini Değiştir");
        btnWriteAll.setPadding(20, 25, 20, 25);
        btnWriteAll.setOnClickListener(v -> {
            closeKeyboard(v);
            if (currentPid <= 0) return;
            try {
                float val = Float.parseFloat(etValue.getText().toString());
                boolean ok = writeAll(currentPid, currentType, val);
                Toast.makeText(this, ok ? "Tümü Değiştirildi!" : "Başarısız!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "Hatalı değer!", Toast.LENGTH_SHORT).show(); }
        });
        menuContentView.addView(btnWriteAll);

        // Sonuç Gösterim Alanı (Yenileme Butonlu)
        final TextView tvResults = new TextView(this);
        tvResults.setText("Sonuçlar burada listelenir...");
        tvResults.setTextColor(Color.WHITE);
        tvResults.setTextSize(14);
        tvResults.setBackgroundColor(Color.parseColor("#22000000"));
        tvResults.setPadding(15, 15, 15, 15);
        
        Button btnRefresh = new Button(this);
        btnRefresh.setText("Sonuç Listesini Yenile");
        btnRefresh.setPadding(10, 15, 10, 15);
        btnRefresh.setOnClickListener(v -> {
            closeKeyboard(v);
            tvResults.setText(getResultsString());
        });
        menuContentView.addView(btnRefresh);
        menuContentView.addView(tvResults);

        // İndeks ve Pointer Arama Alanı
        final EditText etIndex = new EditText(this);
        etIndex.setHint("İşlem Yapılacak İndeks (Örn: 0)");
        etIndex.setHintTextColor(Color.GRAY);
        etIndex.setTextColor(Color.WHITE);
        etIndex.setTextSize(16);
        setupKeyboardBehavior(etIndex);
        menuContentView.addView(etIndex);

        LinearLayout rowSingle = new LinearLayout(this);
        rowSingle.setOrientation(LinearLayout.HORIZONTAL);
        rowSingle.setWeightSum(2);

        Button btnWriteSingle = new Button(this);
        btnWriteSingle.setText("Tekli Yaz");
        btnWriteSingle.setLayoutParams(btnParams);
        btnWriteSingle.setPadding(10, 25, 10, 25);
        btnWriteSingle.setOnClickListener(v -> {
            closeKeyboard(v);
            try {
                int idx = Integer.parseInt(etIndex.getText().toString());
                float val = Float.parseFloat(etValue.getText().toString());
                writeIndex(currentPid, idx, currentType, val);
            } catch (Exception e) { Toast.makeText(this, "Hata!", Toast.LENGTH_SHORT).show(); }
        });
        rowSingle.addView(btnWriteSingle);

        Button btnPointerScanner = new Button(this);
        btnPointerScanner.setText("Oto Pointer Bul");
        btnPointerScanner.setLayoutParams(btnParams);
        btnPointerScanner.setPadding(10, 25, 10, 25);
        btnPointerScanner.setOnClickListener(v -> {
            closeKeyboard(v);
            try {
                int idx = Integer.parseInt(etIndex.getText().toString());
                // C++ tarafındaki pointer bulma mantığı tetikleniyor
                // Bu örnekte seçilen indeksteki adresi bir long değer varsayarak C++ fonksiyonuna aktarıyoruz
                String res = otomatikPointerBul(currentPid, idx); 
                tvResults.setText(res);
            } catch (Exception e) { Toast.makeText(this, "Geçerli bir indeks girin!", Toast.LENGTH_SHORT).show(); }
        });
        rowSingle.addView(btnPointerScanner);
        menuContentView.addView(rowSingle);

        // Katmanları Birleştir
        scrollView.addView(menuContentView);
        rootContainer.addView(scrollView);

        // 5. WINDOW MANAGER AYARLARI (Boyut Optimizasyonu)
        updateMenuDimensions();

        // Sürükleme Mekanizması (Sadece en üstteki buton ile taşınabilir)
        toggleButton.setOnTouchListener(new View.OnTouchListener() {
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
                        windowManager.updateViewLayout(rootContainer, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(rootContainer, params);
    }

    // 🚨 YATAY / DİKEY EKRAN DEĞİŞİMİ KONTROLÜ
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Ekran döndüğünde boyutları yeniden hesapla ve güncelle
        updateMenuDimensions();
    }

    // 🚨 DİNAMİK BOYUTLANDIRMA FONKSİYONU (Yatayda ve Dikeyde Taşmayı Önler)
    private void updateMenuDimensions() {
        int orientation = getResources().getConfiguration().orientation;
        int menuWidth;
        int menuHeight;

        if (isMinimized) {
            menuWidth = WindowManager.LayoutParams.WRAP_CONTENT;
            menuHeight = WindowManager.LayoutParams.WRAP_CONTENT;
        } else {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Ekran yatayken menüyü yana doğru genişlet, yüksekliği sınırla
                menuWidth = 800; 
                menuHeight = 500; 
            } else {
                // Ekran dikeyken standart geniş ve ferah görünüm
                menuWidth = 720; 
                menuHeight = 950; 
            }
        }

        if (params == null) {
            params = new WindowManager.LayoutParams(
                    menuWidth, menuHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 50; params.y = 50;
        } else {
            params.width = menuWidth;
            params.height = menuHeight;
            windowManager.updateViewLayout(rootContainer, params);
        }
    }

    // 🚨 TEK BUTONLA GİZLE / AÇ (Minimize Mekanizması)
    private void toggleMenuVisibility() {
        if (isMinimized) {
            menuContentView.setVisibility(View.VISIBLE);
            toggleButton.setText("▼ MENÜYÜ GİZLE / GÖSTER");
            isMinimized = false;
        } else {
            menuContentView.setVisibility(View.GONE);
            toggleButton.setText("▲ NovaMem");
            isMinimized = true;
        }
        updateMenuDimensions();
    }

    // 🚨 KLAVYE ODAKLAMA YARDIMCISI
    private void setupKeyboardBehavior(final EditText editText) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(rootContainer, params);
                editText.requestFocus();
                
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                }
            }
            return false;
        });
    }

    private void closeKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(rootContainer, params);
        rootContainer.clearFocus();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (rootContainer != null) windowManager.removeView(rootContainer);
    }
}
