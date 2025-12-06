package com.example.android.notepad;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.github.dhaval2404.colorpicker.ColorPickerDialog;
import com.github.dhaval2404.colorpicker.model.ColorShape;

public class ThemeSettingsFragment extends PreferenceFragmentCompat {
    private SharedPreferences sharedPreferences;
    private ColorPickerPreference colorPickerPreference;
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.theme_preferences, rootKey);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        // 1. 处理“明暗策略”
        setupDarkModePreference();
        // 2. 处理“主体配色”
        setupColorPickerPreference();
    }

    private void setupDarkModePreference() {
        ListPreference darkModePreference = findPreference(getString(R.string.theme_dark_mode_strategy_key));
        if (darkModePreference != null) {
            darkModePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                ((SettingsActivity) requireActivity()).onThemeChanged();
                ThemeUtil.applyDarkMode((String) newValue);
                return true; // 返回 true 表示接受这个新值，系统会自动保存它
            });
        }
    }

    private void setupColorPickerPreference() {
        colorPickerPreference = findPreference("custom_theme_color");
        if (colorPickerPreference != null) {
            // 当 Fragment 视图创建好后，更新颜色预览
            colorPickerPreference.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog();
                return true;
            });

            colorPickerPreference.setOnResetClickListener(() -> {
                // 当重置按钮被点击时，ColorPickerPreference 内部已经
                // 处理了颜色值的重置和保存。只需要重启 Activity 来应用主题。
                ((SettingsActivity) requireActivity()).onThemeChanged();
                requireActivity().recreate();
            });

            int defaultColor = ContextCompat.getColor(requireContext(), R.color.default_seed_color);
            int savedColor = sharedPreferences.getInt("custom_theme_color", defaultColor);
            colorPickerPreference.setCurrentColor(savedColor);
        }
    }

    private void showColorPickerDialog() {
        int defaultColor = colorPickerPreference.getCurrentColor();

        new ColorPickerDialog
                .Builder(requireContext())
                .setTitle("选择主体颜色")
                .setColorShape(ColorShape.SQAURE) // Or CIRCLE
                .setDefaultColor(defaultColor)
                .setColorListener((color, colorHex) -> {
                    ((SettingsActivity) requireActivity()).onThemeChanged();
                    // 1. 更新自定义的Preference，它内部会保存到SharedPreferences并更新UI
                    colorPickerPreference.setCurrentColor(color);
                    // 2. 为了让整个应用的主题色生效，需要重启 Activity
                    requireActivity().recreate();
                })
                .show();
    }

}
