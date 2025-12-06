package com.example.android.notepad;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class NotePadApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String darkModeValue = sharedPreferences.getString(
                getString(R.string.theme_dark_mode_strategy_key),
                "system" // 默认值
        );
        ThemeUtil.applyDarkMode(darkModeValue);
    }
}
