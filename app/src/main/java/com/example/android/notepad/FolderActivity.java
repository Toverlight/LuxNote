package com.example.android.notepad;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class FolderActivity extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int FOLDER_LIST_LOADER_ID = 0;
    private static final int UNCLASSIFIED_NOTES_COUNT_LOADER_ID = 1;
    private FolderAdapter mAdapter;
    private TextView mFolderCountTextView;
    private TextView mSearchResultsCountTextView;
    private EditText mSearchEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);

        Toolbar toolbar = findViewById(R.id.toolbar_folder);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.folders_recycler_view);
        mAdapter = new FolderAdapter();
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3)); // 3列网格布局

        registerForContextMenu(recyclerView); // 为RecyclerView注册上下文菜单，以便长按弹出

        mFolderCountTextView = findViewById(R.id.folder_count);
        mSearchResultsCountTextView = findViewById(R.id.folder_search_results_count);
        mSearchEditText = findViewById(R.id.search_folders_edit_text);

        LoaderManager.getInstance(this).initLoader(FOLDER_LIST_LOADER_ID, null, this);
        LoaderManager.getInstance(this).initLoader(UNCLASSIFIED_NOTES_COUNT_LOADER_ID, null, this);

        FloatingActionButton fab = findViewById(R.id.fab_add_folder);
        fab.setOnClickListener(v -> showCreateFolderDialog()); // 点击FAB创建文件夹

        setupSearch();
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
                handler.removeCallbacks(workRunnable);
                workRunnable = () -> performSearch(s.toString());
                handler.postDelayed(workRunnable, 300); // 延迟搜索
            }
        });
    }

    private void performSearch(String query) {
        Bundle args = new Bundle();
        args.putString("query", query);
        LoaderManager.getInstance(this).restartLoader(FOLDER_LIST_LOADER_ID, args, this);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        long folderId = mAdapter.getContextMenuFolderId();
        String folderName = mAdapter.getContextMenuFolderName();
        int noteCount = mAdapter.getContextMenuFolderNoteCount();

        final int itemId = item.getItemId();
        if (itemId == R.id.context_rename_folder) {
            showRenameFolderDialog(folderId, folderName);
            return true;
        } else if (itemId == R.id.context_delete_folder) {
            handleDeleteFolder(folderId, noteCount);
            return true;
        } else {
            return super.onContextItemSelected(item);
        }
    }

    private void showRenameFolderDialog(long folderId, String currentName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.menu_rename_folder);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(currentName);
        input.setSelection(currentName.length());
        // 限制不能输入换行符
        input.setFilters(new InputFilter[] { (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                if (source.charAt(i) == '\n') {
                    return "";
                }
            }
            return null;
        }});
        builder.setView(input);

        builder.setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(currentName)) {
                ContentValues values = new ContentValues();
                values.put(NotePad.Folders.COLUMN_NAME_NAME, newName);
                Uri folderUri = ContentUris.withAppendedId(NotePad.Folders.CONTENT_URI, folderId);
                getContentResolver().update(folderUri, values, null, null);
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void handleDeleteFolder(long folderId, int noteCount) {
        if (noteCount > 0) {
            // 有笔记，需要二次确认
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_folder_dialog_title)
                    .setMessage(getString(R.string.delete_folder_dialog_message, noteCount))
                    .setPositiveButton(R.string.dialog_ok, (dialog, which) -> deleteFolder(folderId))
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
        } else {
            // 空文件夹，直接删除
            deleteFolder(folderId);
        }
    }

    private void deleteFolder(long folderId) {
        Uri folderUri = ContentUris.withAppendedId(NotePad.Folders.CONTENT_URI, folderId);
        getContentResolver().delete(folderUri, null, null);
        Toast.makeText(this, R.string.folder_deleted_toast, Toast.LENGTH_SHORT).show();
    }

    private void showCreateFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建文件夹");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入文件夹名称");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (!folderName.isEmpty()) {
                createNewFolder(folderName);
            } else {
                Toast.makeText(this, "文件夹名称不能为空", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void createNewFolder(String name) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Folders.COLUMN_NAME_NAME, name);
        try {
            // getContentResolver().insert() 可能会抛出 SQLException，最好捕获
            Uri newFolderUri = getContentResolver().insert(NotePad.Folders.CONTENT_URI, values);
            if (newFolderUri != null) {
                Toast.makeText(this, "文件夹 '" + name + "' 已创建", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "创建文件夹失败", Toast.LENGTH_SHORT).show();
            }
        } catch (SQLException e) {
            Log.e("FolderActivity", "Error creating new folder: " + e.getMessage());
            Toast.makeText(this, "创建文件夹失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        switch (id) {
            case FOLDER_LIST_LOADER_ID:
                String selection = null;
                String[] selectionArgs = null;
                if (args != null && args.containsKey("query")) {
                    String query = args.getString("query");
                    if (query != null && !query.isEmpty()) {
                        selection = NotePad.Folders.COLUMN_NAME_NAME + " LIKE ?";
                        selectionArgs = new String[]{"%" + query + "%"};
                    }
                }
                String[] projection = {
                        NotePad.Folders._ID,
                        NotePad.Folders.COLUMN_NAME_NAME,
                        NotePad.Folders.COLUMN_NAME_CREATE_DATE,
                        NotePad.Folders.COLUMN_NAME_MODIFICATION_DATE,
                        NotePad.NOTE_COUNT_ALIAS
                };
                return new CursorLoader(
                        this,
                        NotePad.Folders.CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        NotePad.Folders.DEFAULT_SORT_ORDER
                );
            case UNCLASSIFIED_NOTES_COUNT_LOADER_ID:
                // 查询 folder_id 为 0 或 NULL 的笔记
                String unclassifiedSelection = NotePad.Notes.COLUMN_NAME_FOLDER_ID + " = 0 OR " + NotePad.Notes.COLUMN_NAME_FOLDER_ID + " IS NULL";
                return new CursorLoader(this, NotePad.Notes.CONTENT_URI, new String[]{NotePad.Notes._ID}, unclassifiedSelection, null, null);

            default:
                throw new IllegalArgumentException("Unknown loader id: " + id);
        }
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {
            case FOLDER_LIST_LOADER_ID:
                mAdapter.swapCursor(data);

                // 如果在搜索模式，更新搜索结果计数
                String query = mSearchEditText.getText().toString();
                if (!query.isEmpty()) {
                    mSearchResultsCountTextView.setVisibility(View.VISIBLE);
                    mSearchResultsCountTextView.setText(getString(R.string.search_results_format, data.getCount()));
                } else {
                    mSearchResultsCountTextView.setVisibility(View.GONE);
                }

                // 更新总文件夹数（不包括“未分类”）
                mFolderCountTextView.setText(String.format("(%d)", data.getCount()));
                break;

            case UNCLASSIFIED_NOTES_COUNT_LOADER_ID:
                mAdapter.setUnclassifiedNotesCount(data.getCount());
                break;
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        if (loader.getId() == FOLDER_LIST_LOADER_ID) {
            mAdapter.swapCursor(null);
        }
    }
}