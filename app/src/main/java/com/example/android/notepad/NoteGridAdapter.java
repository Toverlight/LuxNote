// NoteGridAdapter.java
package com.example.android.notepad;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class NoteGridAdapter extends RecyclerView.Adapter<NoteGridAdapter.NoteViewHolder> {

    private Cursor mCursor;
    private int mIdColumnIndex;
    private int mTitleColumnIndex;

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.folder_note_grid_item, parent, false);
        return new NoteViewHolder(view, this);
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
        if (mCursor == newCursor) {
            return;
        }
        mCursor = newCursor;
        if (newCursor != null) {
            mIdColumnIndex = newCursor.getColumnIndexOrThrow(NotePad.Notes._ID);
            mTitleColumnIndex = newCursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE);
            notifyDataSetChanged();
        }
    }

    class NoteViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView noteName;
        private NoteGridAdapter mAdapter;

        public NoteViewHolder(@NonNull View itemView, NoteGridAdapter adapter) {
            super(itemView);
            noteName = itemView.findViewById(R.id.note_name);
            mAdapter = adapter;
            itemView.setOnClickListener(this);
        }

        public void bind(Cursor cursor) {
            noteName.setText(cursor.getString(mAdapter.mTitleColumnIndex));
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION || mAdapter.mCursor == null) {
                return;
            }

            mAdapter.mCursor.moveToPosition(position);
            long noteId = mAdapter.mCursor.getLong(mAdapter.mIdColumnIndex);

            Context context = v.getContext();
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, noteId);

            Intent intent = new Intent(Intent.ACTION_EDIT, noteUri);
            context.startActivity(intent);
        }
    }
}