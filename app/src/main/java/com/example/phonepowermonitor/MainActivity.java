package com.example.phonepowermonitor;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import java.util.Locale;

public class MainActivity extends Activity {
    private BatteryManager batteryManager;
    private TextView levelText, statusText, tempText, voltageText, currentText,
            powerText, estimateText, healthText, noteText;
    private ProgressBar progress;
    private int previousLevel = -1;
    private long previousTime = 0;
    private double drainRatePerHour = 0;
    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            refreshBattery();
            handler.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        buildScreen();
    }

    private TextView makeText(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 7, 0, 7);
        return t;
    }

    private void buildScreen() {
        int bg = Color.rgb(14, 25, 21);
        int panel = Color.rgb(27, 43, 36);
        int white = Color.WHITE;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 28, 24, 20);
        page.setBackgroundColor(bg);

        TextView title = makeText("手機電源監控", 26, white);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        TextView subtitle = makeText("即時電池狀態 · 每 2 秒更新", 13, Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle);

        levelText = makeText("--%", 44, Color.rgb(103, 224, 158));
        levelText.setGravity(Gravity.CENTER);
        page.addView(levelText);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        page.addView(progress, new LinearLayout.LayoutParams(-1, 12));

        statusText = makeText("狀態：讀取中", 16, white);
        page.addView(statusText);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18, 12, 18, 12);
        card.setBackgroundColor(panel);
        tempText = makeText("電池溫度：--", 18, white);
        voltageText = makeText("電池電壓：--", 18, white);
        currentText = makeText("電池電流：--", 18, white);
        powerText = makeText("估算功率：--", 18, white);
        estimateText = makeText("剩餘時間：資料累積中", 18, white);
        healthText = makeText("電池狀態：--", 18, white);
        card.addView(tempText); card.addView(voltageText); card.addView(currentText);
        card.addView(powerText); card.addView(estimateText); card.addView(healthText);
        page.addView(card);

        noteText = makeText("提示：部分手機不提供即時電流資料；耗電時間需先累積使用紀錄。", 12, Color.LTGRAY);
        page.addView(noteText);
        setContentView(page);
    }

    private void refreshBattery() {
        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return;

        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        int health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);

        String state = status == BatteryManager.BATTERY_STATUS_CHARGING ? "充電中" :
                status == BatteryManager.BATTERY_STATUS_FULL ? "已充滿" :
                status == BatteryManager.BATTERY_STATUS_DISCHARGING ? "使用電池中" :
                status == BatteryManager.BATTERY_STATUS_NOT_CHARGING ? "未充電" : "狀態未知";

        long now = System.currentTimeMillis();
        if (pct >= 0) {
            if (previousLevel >= 0 && now > previousTime && pct < previousLevel) {
                double hours = (now - previousTime) / 3600000.0;
                double rate = (previousLevel - pct) / hours;
                if (rate > 0 && rate < 100) drainRatePerHour =
                        drainRatePerHour == 0 ? rate : (drainRatePerHour * 0.7 + rate * 0.3);
            }
            previousLevel = pct;
            previousTime = now;
            levelText.setText(pct + "%");
            progress.setProgress(pct);
        }

        long currentUa = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        double currentMa = Math.abs(currentUa) / 1000.0;
        double powerW = (voltage > 0 && currentUa != Long.MIN_VALUE)
                ? (voltage / 1000.0) * (currentMa / 1000.0) : 0;

        statusText.setText("狀態：" + state);
        tempText.setText("電池溫度：" + (temp >= 0 ? String.format(Locale.getDefault(), "%.1f °C", temp / 10.0) : "不支援"));
        voltageText.setText("電池電壓：" + (voltage > 0 ? String.format(Locale.getDefault(), "%.3f V", voltage / 1000.0) : "不支援"));
        currentText.setText("電池電流：" + (currentUa != Long.MIN_VALUE && currentUa != 0
                ? String.format(Locale.getDefault(), "%.0f mA（絕對值）", currentMa) : "手機未提供資料"));
        powerText.setText("估算功率：" + (powerW > 0
                ? String.format(Locale.getDefault(), "%.2f W（電池端估算）", powerW) : "無法取得"));

        if (state.equals("充電中") || state.equals("已充滿")) {
            estimateText.setText("剩餘時間：充電中，未估算充滿時間");
        } else if (drainRatePerHour > 0 && pct > 0) {
            double hoursLeft = pct / drainRatePerHour;
            estimateText.setText(String.format(Locale.getDefault(),
                    "預估剩餘使用：%.1f 小時（依近期電量變化）", hoursLeft));
        } else {
            estimateText.setText("預估剩餘使用：資料累積中，請持續使用一段時間");
        }

        String h = health == BatteryManager.BATTERY_HEALTH_GOOD ? "良好" :
                health == BatteryManager.BATTERY_HEALTH_OVERHEAT ? "過熱" :
                health == BatteryManager.BATTERY_HEALTH_DEAD ? "電池故障" :
                health == BatteryManager.BATTERY_HEALTH_COLD ? "過冷" : "未知／未提供";
        healthText.setText("電池健康狀態：" + h);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(updater);
        handler.post(updater);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(updater);
        super.onPause();
    }
}
