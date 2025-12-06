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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends BaseActivity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /*
     * Creates a projection that returns the note ID and the note contents.
     */
    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE
    };

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;

    /**
     * Defines a custom EditText View that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends AppCompatEditText {
        private Rect mRect;
        private Paint mPaint;

        // This constructor is used by LayoutInflater
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // Creates a Rect and a Paint object, and sets the style and color of the Paint object.
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        /**
         * This is called to draw the LinedEditText object
         * @param canvas The canvas on which the background is drawn.
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // Gets the number of lines of text in the View.
            int count = getLineCount();

            // Gets the global Rect and Paint objects
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * Draws one line in the rectangle for every line of text in the EditText
             */
            for (int i = 0; i < count; i++) {

                // Gets the baseline coordinates for the current line of text
                int baseline = getLineBounds(i, r);

                /*
                 * Draws a line in the background from the left of the rectangle to the right,
                 * at a vertical position one dip below the baseline, using the "paint" object
                 * for details.
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // Finishes up by calling the parent method
            super.onDraw(canvas);
        }
    }

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_editor);
        Toolbar toolbar = findViewById(R.id.toolbar_note_editor);
        if (toolbar != null) {
            setSupportActionBar(toolbar);

            toolbar.setOnClickListener(v -> {
                // 只有在编辑状态下才允许修改标题
                if (mState == STATE_EDIT && mCursor != null && mCursor.moveToFirst()) {
                    showTitleEditorDialog();
                }
            });
            Log.d(TAG, "Toolbar found and set as SupportActionBar. Has ActionBar: " + (getSupportActionBar() != null));
        } else {
            Log.e(TAG, "Toolbar with ID R.id.toolbar_note_editor NOT FOUND!");
        }
        mText = findViewById(R.id.note);

        // 【新增代码】添加文本变化监听器
        mText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要处理
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 不需要处理
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                // 关键：当文本变化后，通知系统菜单选项可能需要更新
                // 这会触发 onPrepareOptionsMenu 方法再次执行
                invalidateOptionsMenu();
            }
        });

        /*
         * Creates an Intent to use when the Activity object's result is sent back to the
         * caller.
         */
        final Intent intent = getIntent();

        /*
         *  Sets up for the edit, based on the action specified for the incoming Intent.
         */

        // Gets the action that triggered the intent filter for this Activity
        final String action = intent.getAction();

        // For an edit action:
        if (Intent.ACTION_EDIT.equals(action)) {

            // Sets the Activity state to EDIT, and gets the URI for the data to be edited.
            mState = STATE_EDIT;
            mUri = intent.getData();

            // For an insert or paste action:
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            // Sets the Activity state to INSERT, gets the general note URI, and inserts an
            // empty record in the provider
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            /*
             * If the attempt to insert the new note fails, shuts down this Activity. The
             * originating Activity receives back RESULT_CANCELED if it requested a result.
             * Logs that the insert failed.
             */
            if (mUri == null) {

                // Writes the log identifier, a message, and the URI that failed.
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                // Closes the activity.
                finish();
                return;
            }

            // Since the new entry was created, this sets the result to be returned
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        // If the action was other than EDIT or INSERT:
        } else {

            // Logs an error that the action was not understood, finishes the Activity, and
            // returns RESULT_CANCELED to an originating Activity.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * Using the URI passed in with the triggering Intent, gets the note or notes in
         * the provider.
         * Note: This is being done on the UI thread. It will block the thread until the query
         * completes. In a sample app, going against a simple provider based on a local database,
         * the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        mCursor = getContentResolver().query(
            mUri,         // The URI that gets multiple notes from the provider.
            PROJECTION,   // A projection that returns the note ID and note content for each note.
            null,         // No "where" clause selection criteria.
            null,         // No "where" clause selection values.
            null          // Use the default sort order (modification date, descending)
        );

        // For a paste, initializes the data from clipboard.
        // (Must be done after mCursor is initialized.)
        if (Intent.ACTION_PASTE.equals(action)) {
            // Does the paste
            performPaste();
            // Switches the state to EDIT so the title can be modified.
            mState = STATE_EDIT;
        }

        /*
         * If this Activity had stopped previously, its state was written the ORIGINAL_CONTENT
         * location in the saved Instance state. This gets the state.
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    private void showTitleEditorDialog() {
        if (getSupportActionBar() == null) return;

        String currentTitle = getSupportActionBar().getTitle().toString();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_edit_title); // 需要在 strings.xml 中添加

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(currentTitle);
        input.setSelection(currentTitle.length());

        // 应用输入过滤器
        InputFilter noNewLineFilter = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                if (source.charAt(i) == '\n') {
                    return ""; // 过滤掉换行符
                }
            }
            return null;
        };
        InputFilter lengthFilter = new InputFilter.LengthFilter(NotePad.Notes.TITLE_MAX_LENGTH);
        input.setFilters(new InputFilter[]{noNewLineFilter, lengthFilter});

        builder.setView(input);

        builder.setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
            String newTitle = input.getText().toString().trim();
            if (!newTitle.isEmpty() && !newTitle.equals(currentTitle)) {
                // 更新标题
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, newTitle);
                // 直接使用 updateNote 方法，传入 null 作为 text，这样它只会更新标题和修改时间
                updateNote(mText.getText().toString(), newTitle);
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * This method is called when the Activity is about to come to the foreground. This happens
     * when the Activity comes to the top of the task stack, OR when it is first starting.
     *
     * Moves to the first note in the list, sets an appropriate title for the action chosen by
     * the user, puts the note contents into the TextView, and saves the original text as a
     * backup.
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor != null) {
            if (mCursor.moveToFirst()) {
                if (mState == STATE_EDIT) {
                    // 从Cursor中获取标题列的索引
                    int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);

                    // 获取标题内容
                    String title = mCursor.getString(colTitleIndex);

                    // 将其设置为Toolbar的标题
                    // 安全检查
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(title);
                    }

                    // 从Cursor中获取笔记内容的索引
                    int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                    // 获取笔记内容
                    String note = mCursor.getString(colNoteIndex);

                    // 填充到编辑器中
                    mText.setText(note);

                    // 修正光标位置到最后
                    if (note != null) {
                        mText.setSelection(note.length());
                    }

                    // 备份原始内容，用于“撤销”等功能
                    mOriginalContent = note;

                } else if (mState == STATE_INSERT) {
                    // 如果是新建状态，设置一个通用标题
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(getText(R.string.title_create));
                    }
                    // 对于新插入的笔记，其“原始内容”默认为空，以便与后续输入比较
                    mOriginalContent = "";
                }
            } else {
                // 如果 Cursor 为空，说明笔记可能已经被删除了，或者 URI 错误
                // 这里可以处理异常，比如 finish()
                Log.e(TAG, "Cursor is empty in onResume");
                setTitle("Error loading note");
                finish(); // 无法加载笔记，关闭Activity
            }
        } else {
            // 如果 mCursor 为 null，这本身就是一个问题，应该关闭Activity
            Log.e(TAG, "mCursor is null in onResume");
            finish();
        }
    }

    /**
     * This method is called when an Activity loses focus during its normal operation, and is then
     * later on killed. The Activity has a chance to save its state so that the system can restore
     * it.
     *
     * Notice that this method isn't a normal part of the Activity lifecycle. It won't be called
     * if the user simply navigates away from the Activity.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        super.onSaveInstanceState(outState);
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
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
     * If the user hasn't done anything, then this deletes or clears out the note, otherwise it
     * writes the user's work to the provider.
     */
    @Override
    protected void onPause() {
        super.onPause();

        /*
         * Tests to see that the query operation didn't fail (see onCreate()). The Cursor object
         * will exist, even if no records were returned, unless the query failed because of some
         * exception or error.
         *
         */
        if (mCursor != null) {

            // Get the current note text.
            String currentText = mText.getText().toString();
            // 场景一：正在编辑一个已存在的笔记 (STATE_EDIT)
            if (mState == STATE_EDIT) {
                // 【关键检查】只有当当前文本与原始文本不同时，才执行更新
                // TextUtils.equals 可以安全地处理 mOriginalContent 可能为 null 的情况
                if (!TextUtils.equals(currentText, mOriginalContent)) {
                    // 笔记内容已更改，执行更新
                    updateNote(currentText, null); // 更新笔记内容，标题不在这里更新
                }
                // 如果内容没有变化，则什么也不做，不执行任何数据库操作。

            }
            // 场景二：正在创建一个新笔记 (STATE_INSERT)
            else if (mState == STATE_INSERT) {
                // 【新规则】不再检查内容是否为空并删除。
                // 无论用户是否输入内容，我们都保存这个新笔记。
                // 如果用户什么都没写，就会创建一个内容和标题都为空的笔记。
                updateNote(currentText, currentText); // 传入currentText作为标题的源

                // 笔记一旦被创建和保存，其状态就应变为“编辑”，以便后续操作
                mState = STATE_EDIT;
            }
        }
    }

    /**
     * This method is called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Builds the menus for editing and inserting, and adds in alternative actions that
     * registered themselves to handle the MIME types for this application.
     *
     * @param menu A Menu object to which items should be added.
     * @return True to display the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);
        Log.d(TAG, "onCreateOptionsMenu called, menu inflated.");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 获取撤销按钮
        MenuItem revertItem = menu.findItem(R.id.menu_revert);

        // 检查 Cursor 是否有效
        if (mCursor != null && !mCursor.isClosed()) {
            // 为了安全，重新检查一下 Cursor 是否在正确位置，或者直接用 mOriginalContent 对比
            // 这里我们用 mOriginalContent 对比效率更高，因为它在 onResume 里已经存好了

            String currentNote = mText.getText().toString();

            // 如果 mOriginalContent 为 null (新建笔记时)，视为空字符串处理
            String savedContent = (mOriginalContent == null) ? "" : mOriginalContent;

            // 核心逻辑：对比当前内容和原始内容
            boolean isChanged = !TextUtils.equals(currentNote, savedContent);

            // 设置按钮状态
            revertItem.setEnabled(isChanged);

            // 也可以手动设置图标透明度增强视觉反馈 (可选)
            if (revertItem.getIcon() != null) {
                revertItem.getIcon().setAlpha(isChanged ? 255 : 130);
            }

            // 确保按钮始终可见
            revertItem.setVisible(true);
        } else {
            // 如果数据还没准备好，先禁用按钮
            revertItem.setEnabled(false);
            revertItem.setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This method is called when a menu item is selected. Android passes in the selected item.
     * The switch statement in this method calls the appropriate method to perform the action the
     * user chose.
     *
     * @param item The selected MenuItem
     * @return True to indicate that the item was processed, and no further work is necessary. False
     * to proceed to further processing as indicated in the MenuItem object.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the possible menu actions.
        int id = item.getItemId();
        if(id== R.id.menu_save) {
            String currentText = mText.getText().toString();
            if (!TextUtils.equals(currentText, mOriginalContent)) {
                updateNote(currentText, null);
            }
            finish();
            return true;
        } else if (id == R.id.menu_delete) {
            // 【重点修改】显示二次确认对话框
            showDeleteConfirmationDialog(mUri);
            return true;
        } else if (id == R.id.menu_revert) {
            // 【新增】显示撤销二次确认对话框
            showRevertConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 显示删除确认对话框。
     * @param noteUri 要删除的笔记的URI。
     */
    private void showDeleteConfirmationDialog(final Uri noteUri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_note_dialog_title) // "删除笔记"
                .setMessage(R.string.delete_note_dialog_message) // "您确定要删除这个笔记吗？该操作不可恢复！"
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    // 用户点击了确认，执行删除操作
                    deleteNote(noteUri);
                    Toast.makeText(NoteEditor.this, R.string.note_deleted_toast, Toast.LENGTH_SHORT).show();
                    finish(); // 删除后关闭 NoteEditor 界面
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
                    // 用户点击了取消，什么也不做
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * 显示撤销更改的二次确认对话框。
     */
    private void showRevertConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.revert_dialog_title) // "放弃更改"
                .setMessage(R.string.revert_dialog_message) // "您确定要放弃最近编辑的所有更改吗？"
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    // 用户点击了确认，执行撤销操作
                    cancelNote(); // 调用现有的撤销逻辑
                    Toast.makeText(NoteEditor.this, R.string.revert_toast, Toast.LENGTH_SHORT).show();
                    // 不需要 finish()，因为要求是留在编辑界面
                    // 撤销后，mText的内容会恢复，mOriginalContent也会更新，需要刷新菜单状态
                    invalidateOptionsMenu();
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
                    // 用户点击了取消，什么也不做
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * 执行实际的笔记删除操作。
     * @param noteUri 要删除的笔记的URI。
     */
    private void deleteNote(Uri noteUri) {
        if (noteUri != null) {
            getContentResolver().delete(
                    noteUri,
                    null,
                    null
            );
        }
    }

//BEGIN_INCLUDE(paste)
    /**
     * A helper method that replaces the note's data with the contents of the clipboard.
     */
    private final void performPaste() {

        // Gets a handle to the Clipboard Manager
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // Gets a content resolver instance
        ContentResolver cr = getContentResolver();

        // Gets the clipboard data from the clipboard
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            // Gets the first item from the clipboard data
            ClipData.Item item = clip.getItemAt(0);

            // Tries to get the item's contents as a URI pointing to a note
            Uri uri = item.getUri();

            // Tests to see that the item actually is an URI, and that the URI
            // is a content URI pointing to a provider whose MIME type is the same
            // as the MIME type supported by the Note pad provider.
            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                // The clipboard holds a reference to data with a note MIME type. This copies it.
                Cursor orig = cr.query(
                        uri,            // URI for the content provider
                        PROJECTION,     // Get the columns referred to in the projection
                        null,           // No selection variables
                        null,           // No selection variables, so no criteria are needed
                        null            // Use the default sort order
                );

                // If the Cursor is not null, and it contains at least one record
                // (moveToFirst() returns true), then this gets the note data from it.
                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    // Closes the cursor.
                    orig.close();
                }
            }

            // If the contents of the clipboard wasn't a reference to a note, then
            // this converts whatever it is to text.
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // Updates the current note with the retrieved title and text.
            updateNote(text, title);
        }
    }
//END_INCLUDE(paste)

    /**
     * Replaces the current note contents with the text and title provided as arguments.
     * @param text The new note contents to use.
     * @param title The new note title to use
     */
    private final void updateNote(String text, String title) {

        // Sets up a map to contain values to be updated in the provider.
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        // If the action is to insert a new note, this creates an initial title for it.
        if (mState == STATE_INSERT) {

            // If no title was provided as an argument, create one from the note text.
            if (title == null || title.isEmpty()) { // 同时检查 title 是否为空字符串

                // 1. 查找第一个换行符
                int lineBreakIndex = text.indexOf('\n');

                // 2. 只取第一行内容
                String firstLine = (lineBreakIndex != -1) ? text.substring(0, lineBreakIndex) : text;

                // 3. 去除首尾空格
                title = firstLine.trim();

                // 4. 截取到最大长度
                title = title.substring(0, Math.min(NotePad.Notes.TITLE_MAX_LENGTH, title.length()));

                // 如果处理后标题为空，则设置为“无标题”
                if (title.isEmpty()) {
                    title = getString(R.string.untitled);
                }
            }
            // In the values map, sets the value of the title
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // In the values map, sets the value of the title
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else { // 如果 title 为 null (即不是在 STATE_INSERT 且没有提供新标题)，则尝试从当前 Cursor 中获取旧标题
            if (mCursor != null && mCursor.moveToFirst()) {
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String existingTitle = mCursor.getString(colTitleIndex);
                if (existingTitle != null) {
                    values.put(NotePad.Notes.COLUMN_NAME_TITLE, existingTitle);
                } else {
                    values.put(NotePad.Notes.COLUMN_NAME_TITLE, getString(R.string.untitled)); // 确保有一个标题
                }
            } else {
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, getString(R.string.untitled)); // 兜底，防止无标题
            }
        }

        // This puts the desired notes text into the map.
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        /*
         * Updates the provider with the new values in the map. The ListView is updated
         * automatically. The provider sets this up by setting the notification URI for
         * query Cursor objects to the incoming URI. The content resolver is thus
         * automatically notified when the Cursor for the URI changes, and the UI is
         * updated.
         * Note: This is being done on the UI thread. It will block the thread until the
         * update completes. In a sample app, going against a simple provider based on a
         * local database, the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        getContentResolver().update(
                mUri,    // The URI for the record to update.
                values,  // The map of column names and new values to apply to them.
                null,    // No selection criteria are used, so no where columns are necessary.
                null     // No where columns are used, so no where arguments are necessary.
            );

        // 更新 mOriginalContent，确保撤销按钮状态在保存后正确更新
        mOriginalContent = text;
        invalidateOptionsMenu(); // 更新菜单状态，撤销按钮会因为 mOriginalContent 更新而禁用

        // 如果标题发生了变化，也更新Toolbar的标题
        if (getSupportActionBar() != null && values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE)) {
            getSupportActionBar().setTitle(values.getAsString(NotePad.Notes.COLUMN_NAME_TITLE));
        }
    }

    /**
     * This helper method cancels the work done on a note.  It deletes the note if it was
     * newly created, or reverts to the original text of the note i
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // Put the original note text back into the database
                // 我们不直接操作数据库，而是恢复 EditText 的内容，并更新 mOriginalContent
                // 这意味着我们只在UI层面“撤销”了未保存的更改
                mText.setText(mOriginalContent);
                // 此时，currentText == mOriginalContent，onPause() 不会再触发数据库更新
                // 也不需要刷新 Cursor，因为它代表的是数据库的“原始”状态

                // 刷新标题，因为用户可能在TitleEditor中修改了标题，但这里我们只撤销内容。
                // 如果要撤销标题，TitleEditor也需要保存mOriginalTitle。
                // 目前只撤销内容，所以标题保持不变或从mCursor恢复
                if (getSupportActionBar() != null && mCursor.moveToFirst()) {
                    int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                    getSupportActionBar().setTitle(mCursor.getString(colTitleIndex));
                }

            } else if (mState == STATE_INSERT) {
                // 如果是新建状态，我们之前创建了一个空笔记。现在用户选择放弃，就删除它。
                // 注意：根据您新的需求“允许空内容的笔记存在”，这一段可能需要重新考虑。
                // 如果用户明确新建，写了东西又撤销，且不希望留下空笔记，则删除。
                // 如果希望留下空笔记，则不做删除，只是清空输入框内容。
                // 这里我们按照“放弃新建笔记”来处理，所以删除。
                deleteNote(); // 调用 deleteNote()，而不是 deleteNote(mUri)
                setResult(RESULT_CANCELED); // 返回取消结果
                finish(); // 关闭Activity
            }
        } else {
            // 如果 mCursor 为空，无法撤销，直接退出
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    /**
     * Take care of deleting a note.  Simply deletes the entry.
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
}
