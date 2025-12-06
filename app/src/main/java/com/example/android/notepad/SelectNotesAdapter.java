package com.example.android.notepad;

import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

public class SelectNotesAdapter extends RecyclerView.Adapter<SelectNotesAdapter.NoteViewHolder> {

    private Cursor mCursor;
    private int mIdColumnIndex;
    private int mTitleColumnIndex;
    private OnNoteSelectionChangedListener mListener;
    private final Set<Long> mSelectedNoteIds = new HashSet<>();

    public interface OnNoteSelectionChangedListener {
        void onSelectionChanged(int count);
    }

    public SelectNotesAdapter(OnNoteSelectionChangedListener listener) {
        this.mListener = listener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.noteslist_item_selectable, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        if (mCursor == null) return;
        mCursor.moveToPosition(position);
        holder.bind(mCursor);
    }

    @Override
    public int getItemCount() {
        return mCursor != null ? mCursor.getCount() : 0;
    }

    public void swapCursor(Cursor newCursor) {
        if (mCursor == newCursor) return;
        mCursor = newCursor;
        if (newCursor != null) {
            mIdColumnIndex = newCursor.getColumnIndexOrThrow(NotePad.Notes._ID);
            mTitleColumnIndex = newCursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE);
            notifyDataSetChanged();
        }
    }

    public Set<Long> getSelectedNoteIds() {
        return mSelectedNoteIds;
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteTitle;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            noteTitle = itemView.findViewById(android.R.id.text1);
        }

        public void bind(Cursor cursor) {
            final long noteId = cursor.getLong(mIdColumnIndex);
            noteTitle.setText(cursor.getString(mTitleColumnIndex));

            // 更新选中状态的视觉效果
            itemView.setActivated(mSelectedNoteIds.contains(noteId));

            itemView.setOnClickListener(v -> {
                if (mSelectedNoteIds.contains(noteId)) {
                    mSelectedNoteIds.remove(noteId);
                } else {
                    mSelectedNoteIds.add(noteId);
                }
                // 更新视觉效果并通知 Activity
                itemView.setActivated(mSelectedNoteIds.contains(noteId));
                if (mListener != null) {
                    mListener.onSelectionChanged(mSelectedNoteIds.size());
                }
            });
        }
    }
}