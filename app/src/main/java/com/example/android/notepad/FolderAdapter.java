package com.example.android.notepad;

import android.content.Context; // 【新增】
import android.content.Intent; // 【新增】
import android.database.Cursor;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    private Cursor mCursor;
    private int mIdColumnIndex; // ID 列索引
    private int mNameColumnIndex;
    private int mNoteCountColumnIndex;

    private int mUnclassifiedNotesCount = 0;

    private long mContextMenuFolderId;
    private String mContextMenuFolderName;
    private int mContextMenuFolderNoteCount;

    public FolderAdapter() {
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.folder_grid_item, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        // 根据 position 绑定数据
        if (position == 0) {
            holder.bindUnclassified(); // 绑定“未分类”文件夹
        } else {
            if (mCursor == null) return;
            mCursor.moveToPosition(position - 1); // 真实的 cursor 的位置需要 -1
            holder.bind(mCursor);
        }
    }

    @Override
    public int getItemCount() {
        return (mCursor == null ? 0 : mCursor.getCount()) + 1;
    }

    public void swapCursor(Cursor newCursor) {
        if (mCursor == newCursor) {
            return;
        }
        mCursor = newCursor;
        if (newCursor != null) {
            // 获取列索引
            mIdColumnIndex = newCursor.getColumnIndexOrThrow(NotePad.Folders._ID);
            mNameColumnIndex = newCursor.getColumnIndexOrThrow(NotePad.Folders.COLUMN_NAME_NAME);
            mNoteCountColumnIndex = newCursor.getColumnIndexOrThrow(NotePad.NOTE_COUNT_ALIAS);
            mNoteCountColumnIndex = newCursor.getColumnIndexOrThrow(NotePad.NOTE_COUNT_ALIAS);
        }
        notifyDataSetChanged();
    }

    public void setUnclassifiedNotesCount(int count) {
        this.mUnclassifiedNotesCount = count;
        notifyItemChanged(0); // 只刷新第一个 item
    }

    class FolderViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnCreateContextMenuListener {
        ImageView folderIcon;
        TextView folderName;
        TextView noteCountBadge;
        Context context;

        public FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            context = itemView.getContext();
            folderIcon = itemView.findViewById(R.id.folder_icon);
            folderName = itemView.findViewById(R.id.folder_name);
            noteCountBadge = itemView.findViewById(R.id.folder_note_count_badge);
            itemView.setOnClickListener(this);
            itemView.setOnCreateContextMenuListener(this);
        }

        public void bindUnclassified() {
            folderName.setText(R.string.unclassified_folder_name);

            // 设置特殊颜色
            int specialColor = ContextCompat.getColor(context, com.google.android.material.R.color.material_on_surface_disabled);
            folderIcon.setColorFilter(specialColor);

            updateBadge(mUnclassifiedNotesCount);

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, FolderNotesActivity.class);
                intent.putExtra(FolderNotesActivity.EXTRA_FOLDER_ID, 0L); // 未分类的 ID 为 0
                intent.putExtra(FolderNotesActivity.EXTRA_FOLDER_NAME, context.getString(R.string.unclassified_folder_name));
                context.startActivity(intent);
            });
        }

        public void bind(Cursor cursor) {
            folderIcon.clearColorFilter();
            // final long folderId = cursor.getLong(mIdColumnIndex); // 获取 ID
            final String name = cursor.getString(mNameColumnIndex);
            folderName.setText(name);

            int count = cursor.getInt(mNoteCountColumnIndex);
            updateBadge(count);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            if (position == 0) {
                // "未分类" 文件夹的点击逻辑
                Intent intent = new Intent(context, FolderNotesActivity.class);
                intent.putExtra(FolderNotesActivity.EXTRA_FOLDER_ID, 0L);
                intent.putExtra(FolderNotesActivity.EXTRA_FOLDER_NAME, context.getString(R.string.unclassified_folder_name));
                context.startActivity(intent);
            } else {
                // 移动 cursor 到正确位置
                mCursor.moveToPosition(position - 1);

                // 从当前 cursor 位置获取数据
                long folderId = mCursor.getLong(mIdColumnIndex);
                String name = mCursor.getString(mNameColumnIndex);

                Intent intent = new Intent(context, FolderNotesActivity.class);
                intent.putExtra(FolderNotesActivity.EXTRA_FOLDER_ID, folderId);
                intent.putExtra(FolderNotesActivity.EXTRA_FOLDER_NAME, name);
                context.startActivity(intent);
            }
        }

        private void updateBadge(int count) {
            if (count > 0) {
                noteCountBadge.setVisibility(View.VISIBLE);
                if (count > 99) {
                    noteCountBadge.setText("99+");
                } else {
                    noteCountBadge.setText(String.valueOf(count));
                }
            } else {
                noteCountBadge.setVisibility(View.GONE);
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            // "未分类" 文件夹不允许有上下文菜单
            if (getAdapterPosition() == 0) {
                return;
            }

            mCursor.moveToPosition(getAdapterPosition() - 1);
            mContextMenuFolderId = mCursor.getLong(mIdColumnIndex);
            mContextMenuFolderName = mCursor.getString(mNameColumnIndex);
            mContextMenuFolderNoteCount = mCursor.getInt(mNoteCountColumnIndex);

            menu.setHeaderTitle(mContextMenuFolderName); // 设置菜单标题

            // 添加菜单项
            menu.add(Menu.NONE, R.id.context_rename_folder, Menu.NONE, R.string.menu_rename_folder);
            menu.add(Menu.NONE, R.id.context_delete_folder, Menu.NONE, R.string.menu_delete_folder);
        }
    }

    public long getContextMenuFolderId() { return mContextMenuFolderId; }
    public String getContextMenuFolderName() { return mContextMenuFolderName; }
    public int getContextMenuFolderNoteCount() { return mContextMenuFolderNoteCount; }
}
    