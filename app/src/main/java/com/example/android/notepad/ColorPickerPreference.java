package com.example.android.notepad;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.card.MaterialCardView;

public class ColorPickerPreference extends Preference {
    public interface OnResetClickListener {
        void onResetClick();
    }
    private OnResetClickListener resetClickListener;
    private MaterialCardView colorPreview;
    private ImageButton resetButton;
    private int currentColor = Color.BLUE; // 默认颜色，或者从SharedPreferences加载

    // 构造函数链，确保 setLayoutResource 被调用
    public ColorPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.preference_color_picker); // 指定自定义布局
    }

    public ColorPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ColorPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerPreference(@NonNull Context context) {
        this(context, null);
    }

    // 当Preference的View被创建或重新绑定时调用
    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        colorPreview = (MaterialCardView) holder.findViewById(R.id.color_preview);
        resetButton = (ImageButton) holder.findViewById(R.id.reset_color_button);
        updateColorPreview(); // 更新颜色预览块的颜色
        resetButton.setOnClickListener(v -> {
            // 1. 获取应用主题中定义的默认颜色
            int defaultColor = ContextCompat.getColor(getContext(), R.color.default_seed_color);

            // 2. 更新当前颜色状态并保存
            setCurrentColor(defaultColor);

            // 3. 通知 Fragment (如果监听器被设置了)
            if (resetClickListener != null) {
                resetClickListener.onResetClick();
            }
        });
    }

    public void setOnResetClickListener(OnResetClickListener listener) {
        this.resetClickListener = listener;
    }

    // 设置当前颜色，并保存到SharedPreferences
    public void setCurrentColor(int color) {
        if (currentColor != color) {
            currentColor = color;
            persistInt(color); // Preference自带的方法，自动保存到SharedPreferences
            updateColorPreview(); // 更新UI
        }
    }

    // 获取当前保存的颜色
    public int getCurrentColor() {
        // 从SharedPreferences中获取颜色，如果没有则使用currentColor的当前值（通常是初始默认值）
        return getPersistedInt(currentColor);
    }

    // 实际更新UI上颜色预览块的方法
    private void updateColorPreview() {
        if (colorPreview != null) {
            colorPreview.setCardBackgroundColor(currentColor);
        }
    }

    // 覆盖这个方法来在Preference加载时设置初始值
    @Override
    protected void onSetInitialValue(Object defaultValue) {
        // Preference会根据XML中的android:defaultValue属性传递过来
        // 或者如果没有指定，则传递null。这里确保传入的是一个int
        if (defaultValue == null) {
            setCurrentColor(getPersistedInt(Color.BLUE)); // 如果没有默认值，使用蓝色
        } else {
            setCurrentColor(getPersistedInt((int) defaultValue));
        }
    }
}
