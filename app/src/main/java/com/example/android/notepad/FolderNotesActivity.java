// FolderNotesActivity.java
package com.example.android.notepad;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class FolderNotesActivity extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String EXTRA_FOLDER_ID = "com.example.android.notepad.FOLDER_ID";
    public static final String EXTRA_FOLDER_NAME = "com.example.android.notepad.FOLDER_NAME";

    private static final int NOTES_IN_FOLDER_LOADER_ID = 1;

    private long mFolderId;
    private String mFolderName;
    private NoteGridAdapter mAdapter;
    private Toolbar mToolbar;
    private EditText mSearchEditText;
    private TextView mSearchResultsCountTextView;
    private FloatingActionButton mFabAddNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_notes);

        mFolderId = getIntent().getLongExtra(EXTRA_FOLDER_ID, -1);
        mFolderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);

        if (mFolderId == -1 || mFolderName == null) {
            finish(); // 如果没有有效的文件夹信息，则关闭 Activity
            return;
        }

        mFabAddNote = findViewById(R.id.fab_add_note_to_folder);

        mFabAddNote.setOnClickListener(v -> {
            Intent intent = new Intent(FolderNotesActivity.this, SelectNotesActivity.class);
            intent.putExtra(SelectNotesActivity.EXTRA_CURRENT_FOLDER_ID, mFolderId);
            startActivity(intent);
        });

        mToolbar = findViewById(R.id.toolbar_folder_notes);
        mToolbar.setTitle(mFolderName);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mToolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.folder_notes_recycler_view);
        mAdapter = new NoteGridAdapter(); // 我们需要创建一个 NoteGridAdapter
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        mSearchEditText = findViewById(R.id.search_folder_notes_edit_text);
        mSearchResultsCountTextView = findViewById(R.id.folder_notes_search_results_count);

        LoaderManager.getInstance(this).initLoader(NOTES_IN_FOLDER_LOADER_ID, null, this);

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
        LoaderManager.getInstance(this).restartLoader(NOTES_IN_FOLDER_LOADER_ID, args, this);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        String selection;
        String[] selectionArgs;
        // 基础查询条件：必须属于当前文件夹
        String baseSelection = NotePad.Notes.COLUMN_NAME_FOLDER_ID + " = ?";

        if (args != null && args.containsKey("query")) {
            String query = args.getString("query");
            if (query != null && !query.isEmpty()) {
                // 复合查询条件：属于当前文件夹 AND 标题匹配
                selection = baseSelection + " AND " + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?";
                selectionArgs = new String[]{String.valueOf(mFolderId), "%" + query + "%"};
            } else {
                selection = baseSelection;
                selectionArgs = new String[]{String.valueOf(mFolderId)};
            }
        } else {
            selection = baseSelection;
            selectionArgs = new String[]{String.valueOf(mFolderId)};
        }

        return new CursorLoader(
                this,
                NotePad.Notes.CONTENT_URI,
                null, // 所有列
                selection,
                selectionArgs,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
        // 更新 Toolbar 的副标题
        mToolbar.setSubtitle(getString(R.string.note_count_subtitle, data.getCount()));

        String query = mSearchEditText.getText().toString();
        if (!query.isEmpty()) {
            mSearchResultsCountTextView.setVisibility(View.VISIBLE);
            mSearchResultsCountTextView.setText(getString(R.string.search_results_format, data.getCount()));
        } else {
            mSearchResultsCountTextView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }
}