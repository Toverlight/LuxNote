package com.example.android.notepad;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        Preference themePreference = findPreference("theme");
        if (themePreference != null) {
            themePreference.setFragment("com.example.android.notepad.ThemeSettingsFragment");
        }
    }
}
