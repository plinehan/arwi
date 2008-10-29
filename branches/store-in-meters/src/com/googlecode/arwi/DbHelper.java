/*
 * Copyright (C) 2008  Patrick F. Linehan
 * 
 * See the LICENSE file for details.
 */
package com.googlecode.arwi;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class DbHelper extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "wakeum.db";
    private static final int DATABASE_VERSION = 3;

    public DbHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE alarms (id INTEGER PRIMARY KEY, name TEXT, latitudeE6 INTEGER, longitudeE6 INTEGER, radiusMeters FLOAT);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        Log.w(Arwi.TAG, "Upgrading database from version "
                        + oldVersion
                        + " to "
                        + newVersion
                        + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS alarms");
        onCreate(db);
    }
}
