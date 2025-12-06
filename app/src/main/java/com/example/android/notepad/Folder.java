package com.example.android.notepad;

public class Folder {
    private String name;
    private int noteCount;

    public Folder(String name, int noteCount) {
        this.name = name;
        this.noteCount = noteCount;
    }

    public String getName() {
        return name;
    }

    public int getNoteCount() {
        return noteCount;
    }
}
