package com.example.android.notepad;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyDynamicColors();
        super.onCreate(savedInstanceState);
    }

    private void applyDynamicColors() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        int defaultColor = ContextCompat.getColor(this, R.color.default_seed_color);
        int customColor = sharedPreferences.getInt("custom_theme_color", defaultColor);

        DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                .setContentBasedSource(customColor)
                .build();

        DynamicColors.applyToActivityIfAvailable(this, options);
    }
}