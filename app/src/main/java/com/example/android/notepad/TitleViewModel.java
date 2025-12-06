package com.example.android.notepad;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

public class TitleViewModel extends ViewModel {
    private MutableLiveData<String> titleData;

    // 获取标题的公共方法
    public LiveData<String> getTitle(Context context, Uri uri) {
        if (titleData == null) {
            titleData = new MutableLiveData<>();
            loadTitle(context, uri);
        }
        return titleData;
    }

    // 使用CursorLoader在后台加载数据
    private void loadTitle(Context context, Uri uri) {
        // 创建一个CursorLoader
        CursorLoader loader = new CursorLoader(
                context,
                uri,
                new String[]{NotePad.Notes.COLUMN_NAME_TITLE}, // 只查询标题列
                null,
                null,
                null
        );

        // 定义一个LoaderCallback来处理加载结果
        Loader.OnLoadCompleteListener<Cursor> listener = (loader1, cursor) -> {
            if (cursor != null && cursor.moveToFirst()) {
                // 从Cursor中获取标题
                String title = cursor.getString(0);
                // 将结果设置到LiveData中，这将自动通知Activity更新UI
                titleData.setValue(title);
                cursor.close(); // 查询结束，关闭cursor
            }
        };

        // 注册监听器并开始加载
        loader.registerListener(0, listener);
        loader.startLoading();
    }
}
