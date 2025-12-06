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

import static com.example.android.notepad.NotesList.COLUMN_INDEX_FOLDER_NAME;
import static com.example.android.notepad.NotesList.COLUMN_INDEX_MODIFICATION_DATE;
import static com.example.android.notepad.NotesList.COLUMN_INDEX_NOTE;

import com.example.android.notepad.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * Provides access to a database of notes. Each note has a title, the note
 * itself, a creation date and a modified data.
 */
public class NotePadProvider extends ContentProvider {
    // Used for debugging and logging
    private static final String TAG = "NotePadProvider";

    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "note_pad.db";

    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 4;

    /**
     * A projection map used to select columns from the database
     */
    private static HashMap<String, String> sNotesProjectionMap;
    private static HashMap<String, String> sFoldersProjectionMap;

    /**
     * Standard projection for the interesting columns of a normal note.
     */
    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,               // Projection position 0, the note's id
            NotePad.Notes.COLUMN_NAME_NOTE,  // Projection position 1, the note's content
            NotePad.Notes.COLUMN_NAME_TITLE, // Projection position 2, the note's title
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    /*
     * Constants used by the Uri matcher to choose an action based on the pattern
     * of the incoming URI
     */
    // The incoming URI matches the Notes URI pattern
    private static final int NOTES = 1;

    // The incoming URI matches the Note ID URI pattern
    private static final int NOTE_ID = 2;
    private static final int FOLDERS = 3;
    private static final int FOLDER_ID = 4;

    /**
     * A UriMatcher instance
     */
    private static final UriMatcher sUriMatcher;

    // Handle to a new DatabaseHelper.
    private DatabaseHelper mOpenHelper;


    /**
     * A block that instantiates and sets static objects
     */
    static {

        /*
         * Creates and initializes the URI matcher
         */
        // Create a new instance
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // Add a pattern that routes URIs terminated with "notes" to a NOTES operation
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);

        // Add a pattern that routes URIs terminated with "notes" plus an integer
        // to a note ID operation
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);

        sUriMatcher.addURI(NotePad.AUTHORITY, "folders", FOLDERS);
        sUriMatcher.addURI(NotePad.AUTHORITY, "folders/#", FOLDER_ID);

        /*
         * Creates and initializes a projection map that returns all columns
         */

        sNotesProjectionMap = new HashMap<>();
        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes._ID
                + " AS " + NotePad.Notes._ID);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_TITLE
                + " AS " + NotePad.Notes.COLUMN_NAME_TITLE);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_NOTE
                + " AS " + NotePad.Notes.COLUMN_NAME_NOTE);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_CREATE_DATE
                    + " AS " + NotePad.Notes.COLUMN_NAME_CREATE_DATE);
        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                        + " AS " + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);
        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_FOLDER_ID,
                NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_FOLDER_ID
                        + " AS " + NotePad.Notes.COLUMN_NAME_FOLDER_ID);

        sFoldersProjectionMap = new HashMap<>();
        sFoldersProjectionMap.put(NotePad.Folders._ID, NotePad.Folders.TABLE_NAME + "." + NotePad.Folders._ID + " AS " + NotePad.Folders._ID);
        sFoldersProjectionMap.put(NotePad.Folders.COLUMN_NAME_NAME, NotePad.Folders.TABLE_NAME + "." + NotePad.Folders.COLUMN_NAME_NAME + " AS " + NotePad.Folders.COLUMN_NAME_NAME);
        sFoldersProjectionMap.put(NotePad.Folders.COLUMN_NAME_CREATE_DATE, NotePad.Folders.TABLE_NAME + "." + NotePad.Folders.COLUMN_NAME_CREATE_DATE + " AS " + NotePad.Folders.COLUMN_NAME_CREATE_DATE);
        sFoldersProjectionMap.put(NotePad.Folders.COLUMN_NAME_MODIFICATION_DATE, NotePad.Folders.TABLE_NAME + "." + NotePad.Folders.COLUMN_NAME_MODIFICATION_DATE + " AS " + NotePad.Folders.COLUMN_NAME_MODIFICATION_DATE);
        sFoldersProjectionMap.put(NotePad.NOTE_COUNT_ALIAS, "COUNT(" + NotePad.Notes.TABLE_NAME + "." + NotePad.Notes._ID + ") AS " + NotePad.NOTE_COUNT_ALIAS);
    }

    /**
    *
    * This class helps open, create, and upgrade the database file. Set to package visibility
    * for testing purposes.
    */
   static class DatabaseHelper extends SQLiteOpenHelper {
       DatabaseHelper(Context context) {

           // calls the super constructor, requesting the default cursor factory.
           super(context, DATABASE_NAME, null, DATABASE_VERSION);
       }

       /**
        *
        * Creates the underlying database with table name and column names taken from the
        * NotePad class.
        */
       @Override
       public void onCreate(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                   + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_FOLDER_ID + " INTEGER DEFAULT 0"
                   + ");");
           db.execSQL("CREATE TABLE " + NotePad.Folders.TABLE_NAME + " ("
                   + NotePad.Folders._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Folders.COLUMN_NAME_NAME + " TEXT NOT NULL,"
                   + NotePad.Folders.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Folders.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                   + ");");
       }

       /**
        *
        * Demonstrates that the provider must consider what happens when the
        * underlying datastore is changed. In this sample, the database is upgraded the database
        * by destroying the existing data.
        * A real application should upgrade the database in place.
        */
       @Override
       public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

           // Logs that the database is being upgraded
           Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                   + newVersion + ", which will destroy all old data");

           // Kills the table and existing data
           db.execSQL("DROP TABLE IF EXISTS " + NotePad.Notes.TABLE_NAME);
           db.execSQL("DROP TABLE IF EXISTS " + NotePad.Folders.TABLE_NAME);

           // Recreates the database with a new version
           onCreate(db);
       }
   }

   /**
    *
    * Initializes the provider by creating a new DatabaseHelper. onCreate() is called
    * automatically when Android creates the provider in response to a resolver request from a
    * client.
    */
   @Override
   public boolean onCreate() {

       // Creates a new helper object. Note that the database itself isn't opened until
       // something tries to access it, and it's only created if it doesn't already exist.
       mOpenHelper = new DatabaseHelper(getContext());

       // Assumes that any failures will be reported by a thrown exception.
       return true;
   }

   /**
    * This method is called when a client calls
    * {@link android.content.ContentResolver#query(Uri, String[], String, String[], String)}.
    * Queries the database and returns a cursor containing the results.
    *
    * @return A cursor containing the results of the query. The cursor exists but is empty if
    * the query returns no results or an exception occurs.
    * @throws IllegalArgumentException if the incoming URI pattern is invalid.
    */
   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
           String sortOrder) {

       // Constructs a new query builder and sets its table name
       SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
       String orderBy;
       String groupBy = null;

       switch (sUriMatcher.match(uri)) {
           case NOTES:
               if ("folders".equals(uri.getQueryParameter("join"))) {
                   String tables = NotePad.Notes.TABLE_NAME + " LEFT JOIN " + NotePad.Folders.TABLE_NAME
                           + " ON (" + NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_FOLDER_ID
                           + " = " + NotePad.Folders.TABLE_NAME + "." + NotePad.Folders._ID + ")";
                   qb.setTables(tables);
               } else {
                   qb.setTables(NotePad.Notes.TABLE_NAME);
                   qb.setProjectionMap(sNotesProjectionMap);
               }
               orderBy = TextUtils.isEmpty(sortOrder) ? NotePad.Notes.DEFAULT_SORT_ORDER : sortOrder;
               break;

           case NOTE_ID:
               qb.setTables(NotePad.Notes.TABLE_NAME);
               qb.setProjectionMap(sNotesProjectionMap);
               qb.appendWhere(NotePad.Notes._ID + "=" + uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
               orderBy = TextUtils.isEmpty(sortOrder) ? NotePad.Notes.DEFAULT_SORT_ORDER : sortOrder;
               break;

           case FOLDERS:
               String tables = NotePad.Folders.TABLE_NAME + " LEFT JOIN " + NotePad.Notes.TABLE_NAME +
                       " ON (" + NotePad.Folders.TABLE_NAME + "." + NotePad.Folders._ID + " = " +
                       NotePad.Notes.TABLE_NAME + "." + NotePad.Notes.COLUMN_NAME_FOLDER_ID + ")";
               qb.setTables(tables);
               qb.setProjectionMap(sFoldersProjectionMap);
               groupBy = NotePad.Folders.TABLE_NAME + "." + NotePad.Folders._ID;
               orderBy = TextUtils.isEmpty(sortOrder) ? NotePad.Folders.DEFAULT_SORT_ORDER : sortOrder;
               break;

           case FOLDER_ID:
               qb.setTables(NotePad.Folders.TABLE_NAME);
               qb.setProjectionMap(sFoldersProjectionMap);
               qb.appendWhere(NotePad.Folders._ID + "=" + uri.getPathSegments().get(NotePad.Folders.FOLDER_ID_PATH_POSITION));
               orderBy = TextUtils.isEmpty(sortOrder) ? NotePad.Folders.DEFAULT_SORT_ORDER : sortOrder;
               break;

           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }

       SQLiteDatabase db = mOpenHelper.getReadableDatabase();
       Cursor c = qb.query(db, projection, selection, selectionArgs, groupBy, null, orderBy);
       c.setNotificationUri(getContext().getContentResolver(), uri);

       return c;
   }

   /**
    * This is called when a client calls {@link android.content.ContentResolver#getType(Uri)}.
    * Returns the MIME data type of the URI given as a parameter.
    *
    * @param uri The URI whose MIME type is desired.
    * @return The MIME type of the URI.
    * @throws IllegalArgumentException if the incoming URI pattern is invalid.
    */
   @Override
   public String getType(Uri uri) {

       /**
        * Chooses the MIME type based on the incoming URI pattern
        */
       switch (sUriMatcher.match(uri)) {

           // If the pattern is for notes or live folders, returns the general content type.
           case NOTES:
               return NotePad.Notes.CONTENT_TYPE;
           // If the pattern is for note IDs, returns the note ID content type.
           case NOTE_ID:
               return NotePad.Notes.CONTENT_ITEM_TYPE;
           case FOLDERS:
               return NotePad.Folders.CONTENT_TYPE;
           case FOLDER_ID:
               return NotePad.Folders.CONTENT_ITEM_TYPE;

           // If the URI pattern doesn't match any permitted patterns, throws an exception.
           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }
    }


    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#insert(Uri, ContentValues)}.
     * Inserts a new row into the database. This method sets up default values for any
     * columns that are not included in the incoming map.
     * If rows were inserted, then listeners are notified of the change.
     * @return The row ID of the inserted row.
     * @throws SQLException if the insertion fails.
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        if (sUriMatcher.match(uri) != NOTES && sUriMatcher.match(uri) != FOLDERS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = System.currentTimeMillis();

        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE)) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        }

        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId;
        Uri insertedUri;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                // 如果未提供标题和内容，插入默认值
                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE)) {
                    Resources r = Resources.getSystem();
                    values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled));
                }
                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE)) {
                    values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
                }
                // 如果未提供 folder_id，插入默认值 0 (代表未分类)
                if (!values.containsKey(NotePad.Notes.COLUMN_NAME_FOLDER_ID)) {
                    values.put(NotePad.Notes.COLUMN_NAME_FOLDER_ID, 0);
                }

                rowId = db.insert(NotePad.Notes.TABLE_NAME, NotePad.Notes.COLUMN_NAME_NOTE, values);
                if (rowId > 0) {
                    insertedUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);
                    getContext().getContentResolver().notifyChange(insertedUri, null);
                    // 额外通知 NOTES 集合 URI，以防有 Loader 监听
                    getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
                    return insertedUri;
                }
                break;

            case FOLDERS:
                // 如果文件夹名称为空，抛出异常
                if (!values.containsKey(NotePad.Folders.COLUMN_NAME_NAME) || values.getAsString(NotePad.Folders.COLUMN_NAME_NAME).trim().isEmpty()) {
                    throw new SQLException("Folder name cannot be empty");
                }
                rowId = db.insert(NotePad.Folders.TABLE_NAME, NotePad.Folders.COLUMN_NAME_NAME, values);
                if (rowId > 0) {
                    insertedUri = ContentUris.withAppendedId(NotePad.Folders.CONTENT_ID_URI_BASE, rowId);
                    // 【关键修复】这里要通知 Folder 集合 URI，以确保 FolderActivity 的 Loader 刷新
                    getContext().getContentResolver().notifyChange(NotePad.Folders.CONTENT_URI, null);
                    return insertedUri;
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#delete(Uri, String, String[])}.
     * Deletes records from the database. If the incoming URI matches the note ID URI pattern,
     * this method deletes the one record specified by the ID in the URI. Otherwise, it deletes a
     * a set of records. The record or records must also match the input selection criteria
     * specified by where and whereArgs.
     *
     * If rows were deleted, then listeners are notified of the change.
     * @return If a "where" clause is used, the number of rows affected is returned, otherwise
     * 0 is returned. To delete all rows and get a row count, use "1" as the where clause.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;
        int count;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.delete(NotePad.Notes.TABLE_NAME, where, whereArgs);
                break;

            case NOTE_ID:
                finalWhere =
                        NotePad.Notes._ID + " = " + uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.delete(NotePad.Notes.TABLE_NAME, finalWhere, whereArgs);
                break;

            case FOLDERS:
                // 在删除文件夹时，将该文件夹下的所有笔记的 folder_id 设为 0 (未分类)
                // 这是一个重要的业务逻辑，防止笔记“丢失”
                ContentValues updateValues = new ContentValues();
                updateValues.put(NotePad.Notes.COLUMN_NAME_FOLDER_ID, 0);
                db.update(NotePad.Notes.TABLE_NAME, updateValues, NotePad.Notes.COLUMN_NAME_FOLDER_ID + " = " + uri.getPathSegments().get(NotePad.Folders.FOLDER_ID_PATH_POSITION), null);

                count = db.delete(NotePad.Folders.TABLE_NAME, where, whereArgs);
                break;

            case FOLDER_ID:
                String folderIdToDelete = uri.getPathSegments().get(NotePad.Folders.FOLDER_ID_PATH_POSITION);

                // 在删除文件夹时，将该文件夹下的所有笔记的 folder_id 设为 0 (未分类)
                ContentValues valuesToUpdateNotes = new ContentValues();
                valuesToUpdateNotes.put(NotePad.Notes.COLUMN_NAME_FOLDER_ID, 0);
                db.update(NotePad.Notes.TABLE_NAME, valuesToUpdateNotes,
                        NotePad.Notes.COLUMN_NAME_FOLDER_ID + " = ?",
                        new String[]{folderIdToDelete});

                finalWhere = NotePad.Folders._ID + " = " + folderIdToDelete;
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.delete(NotePad.Folders.TABLE_NAME, finalWhere, whereArgs);

                // 通知笔记列表可能需要刷新
                getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 统一通知相关 URI
        if (uri.toString().startsWith(NotePad.Folders.CONTENT_URI.toString())) {
            getContext().getContentResolver().notifyChange(NotePad.Folders.CONTENT_URI, null);
        } else {
            getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
        }

        return count;
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#update(Uri,ContentValues,String,String[])}
     * Updates records in the database. The column names specified by the keys in the values map
     * are updated with new data specified by the values in the map. If the incoming URI matches the
     * note ID URI pattern, then the method updates the one record specified by the ID in the URI;
     * otherwise, it updates a set of records. The record or records must match the input
     * selection criteria specified by where and whereArgs.
     * If rows were updated, then listeners are notified of the change.
     *
     * @param uri The URI pattern to match and update.
     * @param values A map of column names (keys) and new values (values).
     * @param where An SQL "WHERE" clause that selects records based on their column values. If this
     * is null, then all records that match the URI pattern are selected.
     * @param whereArgs An array of selection criteria. If the "where" param contains value
     * placeholders ("?"), then each placeholder is replaced by the corresponding element in the
     * array.
     * @return The number of rows updated.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        // 【确保更新时间是添加在 ContentProvider 级别，而不是在每个 case 内部】
        // 但是对于文件夹更新，我们只更新其修改时间，不应该影响笔记
        // 最佳实践是让 ContentProvider 仅更新它负责的表的修改时间
        // 这里需要更精细的控制，我们只对特定表设置修改时间。
        Long now = System.currentTimeMillis();

        // 只有当 values 中没有明确指定修改时间时才自动设置
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) && sUriMatcher.match(uri) == NOTES || sUriMatcher.match(uri) == NOTE_ID) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }
        if (!values.containsKey(NotePad.Folders.COLUMN_NAME_MODIFICATION_DATE) && sUriMatcher.match(uri) == FOLDERS || sUriMatcher.match(uri) == FOLDER_ID) {
            values.put(NotePad.Folders.COLUMN_NAME_MODIFICATION_DATE, now);
        }

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.update(NotePad.Notes.TABLE_NAME, values, where, whereArgs);
                break;

            case NOTE_ID:
                String noteId = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);
                finalWhere = NotePad.Notes._ID + " = " + noteId;
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.update(NotePad.Notes.TABLE_NAME, values, finalWhere, whereArgs);
                break;

            case FOLDERS:
                count = db.update(NotePad.Folders.TABLE_NAME, values, where, whereArgs);
                break;

            case FOLDER_ID:
                String folderId = uri.getPathSegments().get(NotePad.Folders.FOLDER_ID_PATH_POSITION);
                finalWhere = NotePad.Folders._ID + " = " + folderId;
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.update(NotePad.Folders.TABLE_NAME, values, finalWhere, whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 统一通知相关 URI
        if (uri.toString().startsWith(NotePad.Folders.CONTENT_URI.toString())) {
            getContext().getContentResolver().notifyChange(NotePad.Folders.CONTENT_URI, null);
        } else {
            getContext().getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
        }

        return count;
    }

    /**
     * A test package can call this to get a handle to the database underlying NotePadProvider,
     * so it can insert test data into the database. The test case class is responsible for
     * instantiating the provider in a test context; does
     * this during the call to setUp()
     *
     * @return a handle to the database helper object for the provider's data.
     */
    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }

    public static class NoteListCursorAdapter extends SimpleCursorAdapter {
        private final SimpleDateFormat sdf;
        private final String unclassified;
        private final String wordCountFormat;
        public NoteListCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
            this.sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            this.unclassified = context.getString(R.string.unclassified_folder_name);
            this.wordCountFormat = context.getString(R.string.word_count_format);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);

            TextView timeTextView = view.findViewById(R.id.note_modification_date);
            TextView folderTextView = view.findViewById(R.id.note_folder_name);
            TextView wordCountTextView = view.findViewById(R.id.note_word_count);

            // 绑定修改时间
            long millis = cursor.getLong(COLUMN_INDEX_MODIFICATION_DATE);
            timeTextView.setText(sdf.format(new Date(millis)));

            // 绑定文件夹名称
            String folderName = cursor.getString(COLUMN_INDEX_FOLDER_NAME);
            if (folderName == null || folderName.isEmpty()) {
                folderTextView.setText(unclassified);
            } else {
                folderTextView.setText(folderName);
            }

            // 绑定字数
            String noteContent = cursor.getString(COLUMN_INDEX_NOTE);
            int wordCount = noteContent.length();
            wordCountTextView.setText(String.format(wordCountFormat, wordCount));
        }
    }
}
