// SelectNotesActivity.java
package com.example.android.notepad;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Set;

public class SelectNotesActivity extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor>, SelectNotesAdapter.OnNoteSelectionChangedListener {

    public static final String EXTRA_CURRENT_FOLDER_ID = "com.example.android.notepad.CURRENT_FOLDER_ID";
    private static final int SELECT_NOTES_LOADER_ID = 1;

    private long mCurrentFolderId;
    private SelectNotesAdapter mAdapter;
    private Toolbar mToolbar;
    private EditText mSearchEditText;
    private FloatingActionButton mFabConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_notes);

        mCurrentFolderId = getIntent().getLongExtra(EXTRA_CURRENT_FOLDER_ID, -1);
        if (mCurrentFolderId == -1) {
            finish(); // 无法确定目标文件夹，退出
            return;
        }

        mToolbar = findViewById(R.id.toolbar_select_notes);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mToolbar.setNavigationOnClickListener(v -> finish());
        mToolbar.setSubtitle(null); // 初始时没有副标题

        mSearchEditText = findViewById(R.id.search_select_notes_edit_text);
        mFabConfirm = findViewById(R.id.fab_confirm_selection);

        RecyclerView recyclerView = findViewById(R.id.select_notes_recycler_view);
        mAdapter = new SelectNotesAdapter(this);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        LoaderManager.getInstance(this).initLoader(SELECT_NOTES_LOADER_ID, null, this);

        setupSearch();
        setupFab();
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
                handler.postDelayed(workRunnable, 300);
            }
        });
    }

    private void performSearch(String query) {
        Bundle args = new Bundle();
        args.putString("query", query);
        LoaderManager.getInstance(this).restartLoader(SELECT_NOTES_LOADER_ID, args, this);
    }

    private void setupFab() {
        mFabConfirm.setOnClickListener(v -> addSelectedNotesToFolder());
    }

    private void addSelectedNotesToFolder() {
        Set<Long> selectedIds = mAdapter.getSelectedNoteIds();
        if (selectedIds.isEmpty()) return;

        // 构建 WHERE 子句: _id IN (?, ?, ...)
        StringBuilder whereClause = new StringBuilder(NotePad.Notes._ID + " IN (");
        String[] whereArgs = new String[selectedIds.size()];
        int i = 0;
        for (Long id : selectedIds) {
            whereClause.append("?");
            if (i < selectedIds.size() - 1) {
                whereClause.append(",");
            }
            whereArgs[i++] = String.valueOf(id);
        }
        whereClause.append(")");

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_FOLDER_ID, mCurrentFolderId);

        // 批量更新
        int updatedRows = getContentResolver().update(
                NotePad.Notes.CONTENT_URI,
                values,
                whereClause.toString(),
                whereArgs
        );

        if (updatedRows > 0) {
            Toast.makeText(this, R.string.notes_added_successfully, Toast.LENGTH_SHORT).show();
        }
        finish(); // 完成后关闭界面
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        // 查询条件：folder_id 不等于当前文件夹 ID (包括未分类的)
        // 未分类的 folder_id 是 0
        String selection = NotePad.Notes.COLUMN_NAME_FOLDER_ID + " != ?";
        String[] selectionArgs = new String[]{String.valueOf(mCurrentFolderId)};

        if (args != null && args.containsKey("query")) {
            String query = args.getString("query");
            if (query != null && !query.isEmpty()) {
                selection += " AND " + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?";
                selectionArgs = new String[]{String.valueOf(mCurrentFolderId), "%" + query + "%"};
            }
        }

        return new CursorLoader(
                this,
                NotePad.Notes.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onSelectionChanged(int count) {
        if (count > 0) {
            mToolbar.setSubtitle(getString(R.string.subtitle_selected_notes, count));
            mFabConfirm.setVisibility(View.VISIBLE);
        } else {
            mToolbar.setSubtitle(null);
            mFabConfirm.setVisibility(View.GONE);
        }
    }
}