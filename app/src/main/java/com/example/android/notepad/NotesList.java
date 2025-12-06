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

import com.example.android.notepad.NotePad;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.transition.TransitionManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    // For logging and debugging
    private static final String TAG = "NotesList";
    private ListView mListView;
    private SimpleCursorAdapter mAdapter; // 将 adapter 声明为成员变量，方便在 LoaderCallbacks 中访问

    // Loader 的唯一 ID
    private static final int NOTES_LIST_LOADER_ID = 0;
    private static final int FOLDER_COUNT_LOADER_ID = 2;

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes.TABLE_NAME + "." + NotePad.Notes._ID, // 0
            NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2
            NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_NOTE, // 3 复制需要获取笔记内容
            NotePad.Folders.TABLE_NAME + "." + NotePad.Folders.COLUMN_NAME_NAME + " AS folder_name"
    };

    private static final String[] INTERNAL_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_FOLDER_ID
    };

    /** The index of the title column */
    public static final int COLUMN_INDEX_TITLE = 1;
    /** The index of the modification date column */
    public static final int COLUMN_INDEX_MODIFICATION_DATE = 2;
    /** The index of the note content column */
    public static final int COLUMN_INDEX_NOTE = 3;
    public static final int COLUMN_INDEX_FOLDER_NAME = 4;
    private static final int INTERNAL_COLUMN_INDEX_TITLE = 1;
    private static final int INTERNAL_COLUMN_INDEX_NOTE = 2;
    private static final int INTERNAL_COLUMN_INDEX_FOLDER_ID = 3;

    // 搜索相关
    private boolean isSearchMode = false;
    private EditText mSearchEditText;
    private ViewGroup mRootView;
    private LinearLayout mSearchOptionsContainer;
    private TextView mSearchResultsCount;
    private RadioGroup mSearchRadioGroup;
    private MenuItem mSearchMenuItem, mPasteMenuItem;
    private FloatingActionButton mFabAddNote;
    private Toolbar mToolbar;
    private ImageView mToolbarLogo;
    private LinearLayout mNavFolderTodoContainer;
    private TextView mNavFolderCountTextView;

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        switch (id) {
            case NOTES_LIST_LOADER_ID:
                String selection = null;
                String[] selectionArgs = null;

                if (args != null && args.containsKey("query")) {
                    String query = args.getString("query");
                    String searchType = args.getString("search_type");

                    if (query != null && !query.isEmpty() && searchType != null) {
                        selection = searchType + " LIKE ?";
                        selectionArgs = new String[]{"%" + query + "%"};
                    }
                }

                Uri queryUri = getIntent().getData().buildUpon().appendQueryParameter("join", "folders").build();

                return new CursorLoader(
                        this,
                        queryUri,
                        PROJECTION,
                        selection, // 使用动态生成的 selection
                        selectionArgs, // 使用动态生成的 selectionArgs
                        NotePad.Notes.DEFAULT_SORT_ORDER
                );
            case FOLDER_COUNT_LOADER_ID:
                // 只查询 ID 列以获取数量
                return new CursorLoader(
                        this,
                        NotePad.Folders.CONTENT_URI,
                        new String[]{NotePad.Folders._ID},
                        null,
                        null,
                        null
                );
            default:
                throw new IllegalArgumentException("Unknown loader id: " + id);
        }
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {
            case NOTES_LIST_LOADER_ID:
                mAdapter.swapCursor(data);

                // 如果处于搜索模式，更新并显示搜索结果计数
                if (isSearchMode && mSearchEditText.length() > 0) {
                    int count = (data != null) ? data.getCount() : 0;
                    mSearchResultsCount.setText(String.format(getString(R.string.search_results_format), count));

                    // 如果是第一次显示，添加淡入动画
                    if (mSearchResultsCount.getVisibility() == View.GONE) {
                        mSearchResultsCount.setVisibility(View.VISIBLE);
                        mSearchResultsCount.setAlpha(0f);
                        mSearchResultsCount.animate().alpha(1f).setDuration(300).start();
                    }
                } else if (isSearchMode && mSearchEditText.length() == 0) {
                    // 如果在搜索模式但搜索框为空，则隐藏计数
                    mSearchResultsCount.setVisibility(View.GONE);
                }
                break;
            case FOLDER_COUNT_LOADER_ID:
                if (data != null && mNavFolderCountTextView != null) {
                    mNavFolderCountTextView.setText(String.format("(%d)", data.getCount()));
                }
                break;
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        if (loader.getId() == NOTES_LIST_LOADER_ID) {
            mAdapter.swapCursor(null);
        }
    }

    // Helper method to refresh the loader (e.g., after an insert/delete/update outside of NoteEditor)
    // In NoteEditor, the ContentProvider notifies changes automatically, which CursorLoader picks up.
    // If you explicitly needed to restart a query, you'd use this.
    public void restartLoader() {
        LoaderManager.getInstance(this).restartLoader(NOTES_LIST_LOADER_ID, null, this);
    }

    // 关闭 Cursor 的最佳实践是在 onDestroy()
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 如果 mCursor (旧的 Cursor，现在由 Loader 管理) 还没有关闭，确保它关闭。
        // 然而，使用 CursorLoader 后，mAdapter.swapCursor(data) 和 mAdapter.swapCursor(null)
        // 会自动管理 Cursor 的生命周期，通常不需要手动在这里关闭 mCursor。
        // 确保 mCursor 在旧的代码中不再被直接持有和管理。
    }

    private long getSelectedItemId() {
        if (mListView != null) {
            return mListView.getSelectedItemId();
        }
        return AdapterView.INVALID_ROW_ID; // 或者抛出异常，或者返回一个默认值
    }

    public static class NoteListCursorAdapter extends SimpleCursorAdapter {
        private final SimpleDateFormat sdf;
        private final String unclassified;
        private final String wordCountFormat;

        public NoteListCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
            // 初始化一个日期格式化工具
            this.sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            // 使用系统默认时区
            this.sdf.setTimeZone(TimeZone.getDefault());
            this.unclassified = context.getString(R.string.unclassified_folder_name);
            // 确保 wordCountFormat 已经获取了实际的字符串
            this.wordCountFormat = context.getString(R.string.word_count_format);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {

            TextView titleTextView = view.findViewById(android.R.id.text1);
            TextView timeTextView = view.findViewById(R.id.note_modification_date);
            TextView folderTextView = view.findViewById(R.id.note_folder_name);
            TextView wordCountTextView = view.findViewById(R.id.note_word_count);

            titleTextView.setText(cursor.getString(COLUMN_INDEX_TITLE));

            long millis = cursor.getLong(COLUMN_INDEX_MODIFICATION_DATE);
            timeTextView.setText(sdf.format(new Date(millis)));

            String folderName = cursor.getString(COLUMN_INDEX_FOLDER_NAME);
            if (folderName == null || folderName.isEmpty()) {
                folderTextView.setText(unclassified);
            } else {
                folderTextView.setText(folderName);
            }

            String noteContent = cursor.getString(COLUMN_INDEX_NOTE);
            int wordCount = (noteContent != null) ? noteContent.length() : 0;
            wordCountTextView.setText(String.format(wordCountFormat, wordCount));
        }
    }


    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notes_list);

        // 2. 设置 Toolbar
        mToolbar = findViewById(R.id.toolbar_notes_list);
        setSupportActionBar(mToolbar);
        mToolbarLogo = findViewById(R.id.toolbar_logo);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false); // 初始显示标题
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // 确保 Navigation Icon 槽位启用
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu_settings); // 设置初始设置为图标
        }

        // 获取UI组件引用
        mRootView = findViewById(R.id.notes_list_root);
        mSearchEditText = findViewById(R.id.search_edit_text);
        mSearchOptionsContainer = findViewById(R.id.search_options_container);
        mSearchResultsCount = findViewById(R.id.search_results_count);
        mSearchRadioGroup = findViewById(R.id.search_radio_group);

        mNavFolderTodoContainer = findViewById(R.id.nav_folder_todo_container);
        mNavFolderCountTextView = findViewById(R.id.folder_count_text);

        mListView = findViewById(R.id.list);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setOnItemClickListener(this::onListItemClick); // 手动绑定点击事件

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        String[] dataColumns = { NotePad.Notes._ID } ;
        int[] viewIDs = { android.R.id.text1 };

        mAdapter = new NoteListCursorAdapter(
                this,
                R.layout.noteslist_item,
                null,
                dataColumns,
                viewIDs,
                0
        );

        // 5. 将 Adapter 设置给 ListView
        mListView.setAdapter(mAdapter);

        // 初始化 LoaderManager 并启动 Loader
        // 这将触发 onCreateLoader() 方法
        LoaderManager.getInstance(this).initLoader(NOTES_LIST_LOADER_ID, null, this);
        LoaderManager.getInstance(this).initLoader(FOLDER_COUNT_LOADER_ID, null, this);

        // 处理返回按钮的逻辑
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if (isSearchMode) {
                    exitSearchMode();
                } else {
                    finish();
                }
            }
        });

        // 为搜索框添加监听
        setupSearch();

        mSearchRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (isSearchMode) { // 只有在搜索模式下才响应切换
                String currentQuery = mSearchEditText.getText().toString();
                // 触发一次搜索，使用当前文本和新的搜索类型
                performSearch(currentQuery);
                Log.d(TAG, "Search type changed to: " + (checkedId == R.id.radio_search_title ? "Title" : "Content") + ", re-performing search for: " + currentQuery);
            }
        });

        mFabAddNote = findViewById(R.id.fab_add_note);
        mFabAddNote.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData())
                    .setClassName(getPackageName(), "com.example.android.notepad.NoteEditor"));
        });

        findViewById(R.id.nav_folder_button).setOnClickListener(v -> {
            startActivity(new Intent(NotesList.this, FolderActivity.class));
        });
//        findViewById(R.id.nav_todo_button).setOnClickListener(v -> {
//            Toast.makeText(this, "进入待办列表", Toast.LENGTH_SHORT).show();
//            ...ABANDONED
//        });
    }

    protected void onListItemClick(AdapterView<?> l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri)
                    .setClassName(this, "com.example.android.notepad.NoteEditor")); // 注意 context 改为 this
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 获取菜单项的引用，以便控制它们的可见性
        mSearchMenuItem = menu.findItem(R.id.menu_search);
        mPasteMenuItem = menu.findItem(R.id.menu_paste);
        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 根据是否处于搜索模式，控制菜单项的可见性
        mSearchMenuItem.setVisible(!isSearchMode);
        mPasteMenuItem.setVisible(!isSearchMode);

        if (mFabAddNote != null) {
            if (isSearchMode) {
                mFabAddNote.hide(); // 隐藏 FAB
            } else {
                mFabAddNote.show(); // 显示 FAB
            }
        }

        if (!isSearchMode) {
            // The paste menu item is enabled if there is data on the clipboard.
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

            if (clipboard == null) {
                Log.e(TAG, "ClipboardManager is null!");
                mPasteItem.setEnabled(false);
                return true;
            }

            // Variable to track if clipboard access was successfully checked and content is available
            boolean clipboardContentAvailable = false;

            try {
                boolean hasPrimaryClip = clipboard.hasPrimaryClip();
                Log.d(TAG, "Clipboard has primary clip: " + hasPrimaryClip);

                if (hasPrimaryClip) {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        ClipData.Item item = clip.getItemAt(0);
                        // 尝试将剪贴板内容转换为文本，检查是否非空
                        String pasteContent = item.coerceToText(this).toString();
                        Log.d(TAG, "First clip item content: '" + pasteContent + "' (length: " + pasteContent.length() + ")");

                        if (!pasteContent.isEmpty()) { // 只有当内容非空时才启用
                            clipboardContentAvailable = true;
                        } else {
                            Log.d(TAG, "Clip content is empty, disabling paste button.");
                        }
                    } else {
                        Log.d(TAG, "Primary clip is null or empty, disabling paste button.");
                    }
                } else {
                    // Clipboard has no primary clip (either genuinely empty or access denied due to focus)
                    Log.d(TAG, "No primary clip reported, disabling paste button. (Possibly due to focus restriction on Android 10+).");
                }
            } catch (SecurityException e) {
                // Android 10+ 特有的安全异常：应用程序不在焦点时访问剪贴板会被拒绝
                Log.e(TAG, "SecurityException: Cannot access clipboard as app is not in focus. Disabling paste button.", e);
                // 明确禁用
            } catch (Exception e) {
                // 捕获其他任何意外错误
                Log.e(TAG, "Error checking clipboard in onPrepareOptionsMenu", e);
                // 发生错误时禁用
            }

            mPasteItem.setEnabled(clipboardContentAvailable);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_paste) {
            pasteFromClipboard();
            return true;
        } else if (id == R.id.menu_search) {
            enterSearchMode();
            return true;
        } else if (id == android.R.id.home) {
            if (isSearchMode) {
                exitSearchMode();
            } else {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.stay);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupSearch() {
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            private final Handler handler = new Handler();
            private Runnable workRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 防抖动：延迟500毫秒执行搜索，避免用户每输入一个字符就查询一次
                handler.removeCallbacks(workRunnable);
                workRunnable = () -> performSearch(s.toString());
                handler.postDelayed(workRunnable, 500);
            }
        });
    }

    private void performSearch(String query) {
        if (!isSearchMode) return;

        Bundle args = new Bundle();
        if (query.isEmpty()) {
            // 如果查询为空，清除过滤器，恢复默认列表
            LoaderManager.getInstance(this).restartLoader(NOTES_LIST_LOADER_ID, null, this);
            mSearchResultsCount.setVisibility(View.GONE); // 隐藏计数
        } else {
            // 如果有查询内容，准备参数并重启 Loader
            args.putString("query", query);
            int checkedRadioId = mSearchRadioGroup.getCheckedRadioButtonId();
            if (checkedRadioId == R.id.radio_search_title) {
                args.putString("search_type", NotePad.Notes.COLUMN_NAME_TITLE);
            } else {
                args.putString("search_type", NotePad.Notes.COLUMN_NAME_NOTE);
            }
            LoaderManager.getInstance(this).restartLoader(NOTES_LIST_LOADER_ID, args, this);
        }
    }

    private void enterSearchMode() {
        if (isSearchMode) return;
        isSearchMode = true;

        if (mFabAddNote != null) {
            mFabAddNote.hide(); // Material FAB 提供 show() 和 hide() 方法带动画
        }

        mNavFolderTodoContainer.setVisibility(View.GONE);

        // 1. 开始场景转换动画
        TransitionManager.beginDelayedTransition((ViewGroup) mToolbar.getParent());
        mToolbarLogo.setVisibility(View.GONE);

        // 2. 显示返回按钮 (X)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close);
        }

        // 3. 隐藏菜单项
        invalidateOptionsMenu();

        // 4. 动态设置 EditText 宽度
        Toolbar.LayoutParams params = (Toolbar.LayoutParams) mSearchEditText.getLayoutParams();
        params.width = Toolbar.LayoutParams.MATCH_PARENT;
        mSearchEditText.setLayoutParams(params);

        // 5. 显示和动画化搜索框
        mSearchEditText.setVisibility(View.VISIBLE);
        mSearchEditText.setAlpha(0f);

        final CharSequence originalHint = mSearchEditText.getHint();
        mSearchEditText.setHint("");

        mSearchEditText.animate().alpha(1f).setDuration(100).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束后，请求焦点并弹出键盘
                mSearchEditText.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT);
                }

                mSearchEditText.postDelayed(() -> {
                    mSearchEditText.setHint(originalHint); // 恢复提示文本
                }, 100);
            }
        }).start();

        // 6. 动画显示搜索选项
        mSearchOptionsContainer.setVisibility(View.VISIBLE);
        mSearchOptionsContainer.setAlpha(0f);
        mSearchOptionsContainer.setTranslationY(-mSearchOptionsContainer.getHeight());
        mSearchOptionsContainer.animate().alpha(1f).translationY(0).setDuration(200).start();

        if (mFabAddNote != null) {
            mFabAddNote.hide();
        }
    }

    private void exitSearchMode() {
        if (!isSearchMode) return;

        // 1. 关闭键盘
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
        }

        // 2. 开始场景转换动画
        TransitionManager.beginDelayedTransition((ViewGroup) mToolbar.getParent());

        mNavFolderTodoContainer.setVisibility(View.VISIBLE);

        // 3. 动画隐藏搜索框
        mSearchEditText.animate().alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mSearchEditText.setVisibility(View.INVISIBLE);
                mSearchEditText.setText(""); // 清空搜索内容

                // 恢复 Navigation Icon
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu_settings);
                }

                mToolbarLogo.setVisibility(View.VISIBLE);

                Toolbar.LayoutParams params = (Toolbar.LayoutParams) mSearchEditText.getLayoutParams();
                params.width = 0; // 宽度设回 0
                mSearchEditText.setLayoutParams(params);

                isSearchMode = false;
                invalidateOptionsMenu(); // 恢复菜单项
                // 直接重启 Loader，加载所有笔记
                LoaderManager.getInstance(NotesList.this).restartLoader(NOTES_LIST_LOADER_ID, null, NotesList.this);
                // 隐藏搜索结果计数
                mSearchResultsCount.setVisibility(View.GONE); // 在动画结束后直接隐藏，无需再次动画

                Log.d(TAG, "Exited search mode and reset loader for all notes."); // 添加日志确认
            }
        }).start();

        // 6. 动画隐藏搜索选项和结果计数
        mSearchOptionsContainer.animate().alpha(0f).translationY(-mSearchOptionsContainer.getHeight()).setDuration(200).withEndAction(() -> {
            mSearchOptionsContainer.setVisibility(View.GONE);
        }).start();

        if (mSearchResultsCount.getVisibility() == View.VISIBLE) {
            mSearchResultsCount.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                mSearchResultsCount.setVisibility(View.GONE);
            }).start();
        }

        if (mFabAddNote != null) {
            mFabAddNote.show();
        }
    }

    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     *
     * The only available options are COPY and DELETE.
     *
     * Context-click is equivalent to long-press.
     *
     * @param menu A ContexMenu object to which items should be added.
     * @param view The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        Cursor cursor = (Cursor) mListView.getAdapter().getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(), 
                                        Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
        super.onCreateContextMenu(menu, view, menuInfo); // 确保调用父类方法
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);
            // Triggers default processing of the menu item.
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri)
                    .setClassName(getPackageName(), "com.example.android.notepad.NoteEditor"));
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // 调用新的复制逻辑
            copyNote(noteUri);
            return true;
        } else if (id == R.id.context_delete) {
            // 显示二次确认对话框
            showDeleteConfirmationDialog(noteUri);

            // Returns to the caller and skips further processing.
            return true;
        }
        return super.onContextItemSelected(item);
    }

    // 复制笔记的辅助方法
    private void copyNote(Uri originalNoteUri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    originalNoteUri,
                    INTERNAL_NOTE_PROJECTION,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String originalTitle = cursor.getString(INTERNAL_COLUMN_INDEX_TITLE);
                String originalContent = cursor.getString(INTERNAL_COLUMN_INDEX_NOTE);

                String newTitle = originalTitle + " - "  + getString(R.string.copy_suffix);;
                int copyCount = 1;
                // 检查是否存在同名副本，并生成递增的数字后缀
                while (checkIfNoteExists(newTitle)) {
                    copyCount++;
                    newTitle = originalTitle + " - "  + getString(R.string.copy_suffix) + copyCount;
                }

                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, newTitle);
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, originalContent);
                values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, System.currentTimeMillis());
                values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

                long originalFolderId = cursor.getLong(INTERNAL_COLUMN_INDEX_FOLDER_ID); // 使用内部索引
                values.put(NotePad.Notes.COLUMN_NAME_FOLDER_ID, originalFolderId);

                Uri newNoteUri = getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);

                if (newNoteUri != null) {
                    Toast.makeText(this, R.string.note_copied_toast, Toast.LENGTH_SHORT).show();
                    // CursorLoader 会自动检测到数据变化并刷新列表，无需手动 restartLoader
                } else {
                    Toast.makeText(this, R.string.note_copy_failed_toast, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying note: " + e.getMessage());
            Toast.makeText(this, R.string.note_copy_failed_toast, Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // 检查笔记标题是否已存在的辅助方法
    private boolean checkIfNoteExists(String title) {
        Cursor cursor = null;
        try {
            String selection = NotePad.Notes.COLUMN_NAME_TITLE + " = ?";
            String[] selectionArgs = { title };
            cursor = getContentResolver().query(
                    NotePad.Notes.CONTENT_URI,
                    new String[]{NotePad.Notes._ID}, // 只查询 ID，效率更高
                    selection,
                    selectionArgs,
                    null
            );
            return cursor != null && cursor.getCount() > 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // 处理剪贴板粘贴内容的辅助方法
    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, R.string.clipboard_empty_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        ContentResolver cr = getContentResolver();

        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            String text = null;
            String title = null;
            long folderId = 0; // 粘贴的笔记默认放在未分类

            ClipData.Item item = clip.getItemAt(0);
            Uri uri = item.getUri();

            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {
                Cursor orig = cr.query(
                        uri,            // URI for the content provider
                        INTERNAL_NOTE_PROJECTION, // 使用内部 PROJECTION
                        null,           // No selection variables
                        null,           // No selection variables, so no criteria are needed
                        null            // Use the default sort order
                );

                if (orig != null) {
                    if (orig.moveToFirst()) {
                        text = orig.getString(INTERNAL_COLUMN_INDEX_NOTE);
                        title = orig.getString(INTERNAL_COLUMN_INDEX_TITLE);
                        folderId = orig.getLong(INTERNAL_COLUMN_INDEX_FOLDER_ID);
                    }
                    orig.close();
                }
            }

            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // updateNote 方法需要修改来支持 folderId 参数
            // 或者在这里直接构建 ContentValues

            // 因为 updateNote 并没有 folderId 参数，我们在这里手动构建 ContentValues
            String baseTitle = getString(R.string.pasted_note_title);
            String newTitle = baseTitle;
            int copyCount = 1;
            while (checkIfNoteExists(newTitle)) {
                copyCount++;
                newTitle = baseTitle + copyCount;
            }

            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, newTitle);
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, System.currentTimeMillis());
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
            values.put(NotePad.Notes.COLUMN_NAME_FOLDER_ID, folderId); // 【新增】粘贴时保留文件夹ID，如果是从外部粘贴则为0

            Uri newNoteUri = getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);

            if (newNoteUri != null) {
                Toast.makeText(this, R.string.note_pasted_toast, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.note_paste_failed_toast, Toast.LENGTH_SHORT).show();
            }
        }
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
                    Toast.makeText(NotesList.this, R.string.note_deleted_toast, Toast.LENGTH_SHORT).show();
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
        getContentResolver().delete(
                noteUri,
                null,
                null
        );
        // 删除后，需要刷新列表。SimpleCursorAdapter 会自动监听数据变化并刷新。
        // 如果没有自动刷新，可能需要重新查询或调用 adapter.swapCursor(newCursor)
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 强制刷新菜单，以便 onPrepareOptionsMenu 能够更新粘贴按钮状态
        invalidateOptionsMenu();
    }


}
