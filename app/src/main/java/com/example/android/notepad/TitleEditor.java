/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

/**
 * This Activity allows the user to edit a note's title. It displays a floating window
 * containing an EditText.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class TitleEditor extends BaseActivity {

    /**
     * This is a special intent action that means "edit the title of a note".
     */
    public static final String EDIT_TITLE_ACTION = "com.android.notepad.action.EDIT_TITLE";

    // Creates a projection that returns the note ID and the note contents.
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
    };

    // The position of the title column in a Cursor returned by the provider.
    private static final int COLUMN_INDEX_TITLE = 1;

    // An EditText object for preserving the edited title.
    private EditText mText;

    // A URI object for the note whose title is being edited.
    private Uri mUri;

    // 用于判断是否真的修改了内容
    private String mOriginalTitle;

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the View for this Activity object's UI.
        setContentView(R.layout.title_editor);

        // Get the Intent that activated this Activity, and from it get the URI of the note whose
        // title we need to edit.
        mUri = getIntent().getData();

        // Gets the View ID for the EditText box
        mText = this.findViewById(R.id.title);

        // 1. 过滤换行符
        InputFilter noNewLineFilter = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                if (source.charAt(i) == '\n') {
                    return ""; // Return an empty string to block the newline character
                }
            }
            return null; // Accept the original replacement
        };

        // 2. 限制长度
        InputFilter lengthFilter = new InputFilter.LengthFilter(NotePad.Notes.TITLE_MAX_LENGTH);

        // 3. 将过滤器应用到 EditText
        mText.setFilters(new InputFilter[]{noNewLineFilter, lengthFilter});

        // 使用ViewModelProvider获取与此Activity关联的ViewModel
        TitleViewModel viewModel = new ViewModelProvider(this).get(TitleViewModel.class);

        // 观察ViewModel中的LiveData。当数据加载完成时，这个lambda表达式会被调用
        viewModel.getTitle(this, mUri).observe(this, title -> {
            // LiveData的数据回来了
            if (title != null && mOriginalTitle == null) {
                mOriginalTitle = title;
                mText.setText(title);
                // 将光标移动到文本末尾，方便编辑
                mText.setSelection(title.length());
            }
        });
    }

    /**
     * This method is called when the Activity loses focus.
     *
     * For Activity objects that edit information, onPause() may be the one place where changes are
     * saved. The Android application model is predicated on the idea that "save" and "exit" aren't
     * required actions. When users navigate away from an Activity, they shouldn't have to go back
     * to it to complete their work. The act of going away should save everything and leave the
     * Activity in a state where Android can destroy it if necessary.
     *
     * Updates the note with the text currently in the text box.
     */
    @Override
    protected void onPause() {
        super.onPause();

        String newTitle = mText.getText().toString();

        if (!TextUtils.equals(newTitle, mOriginalTitle)) {
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, newTitle);

            getContentResolver().update(mUri, values, null, null);

        }

    }

    public void onClickOk(View v) {
        finish();
    }
}
