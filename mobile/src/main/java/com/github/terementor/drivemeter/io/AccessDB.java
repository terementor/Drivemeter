package com.github.terementor.drivemeter.io;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by Michael on 22.12.2016.
 */

public class AccessDB extends SQLiteOpenHelper{

    private SQLiteDatabase db;

    //Konstruktor
    public AccessDB (Context activity, String dbName) {
        super(activity, dbName, null, 1);
        db = getWritableDatabase();
        Log.d("#", db.toString());
    }

    public void onCreate(SQLiteDatabase db) {
        try {
            String sql = "CREATE TABLE IF NOT EXITS Gyroskop (time TEXT, x TEXT, y TEXT, z TEXT)";
            db.execSQL(sql);
        }
        catch (Exception ex) {
            Log.e("obdreader", ex.getMessage());
        }
    }

    public void  onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
        //do nothing
    }

}
