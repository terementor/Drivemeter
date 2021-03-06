package com.github.terementor.drivemeter.io;

import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;


public class SensorCSVWriter {

    private static final String TAG = SensorCSVWriter.class.getName();
    private static final String HEADER_CSV = "This is a logfile generated by pires.obd.reader";
    //private static final String[] NAMES_COLUMNS = {"TIME", "X", "Y", "Z"};
    private static final String[] NAMES_COLUMNS = {"Name", "Vendor", "Type", "Resolution", "MinDelay", "MaxDelay"};
    private static final String[] NAMES_COLUMNS_ONLY_READINGS = {
            "BAROMETRIC_PRESSURE", "ENGINE_COOLANT_TEMP", "FUEL_LEVEL", "ENGINE_LOAD", "AMBIENT_AIR_TEMP",
            "ENGINE_RPM", "INTAKE_MANIFOLD_PRESSURE", "MAF", "Term Fuel Trim Bank 1",
            "FUEL_ECONOMY", "Long Term Fuel Trim Bank 2", "FUEL_TYPE", "AIR_INTAKE_TEMP",
            "FUEL_PRESSURE", "SPEED", "Short Term Fuel Trim Bank 2",
            "Short Term Fuel Trim Bank 1", "ENGINE_RUNTIME", "THROTTLE_POS", "DTC_NUMBER",
            "TROUBLE_CODES", "TIMING_ADVANCE", "EQUIV_RATIO"};
    private boolean isFirstLine;
    private BufferedWriter buf;
    public static SQLiteDatabase mydatabase = null;

    public SensorCSVWriter(String filename, String dirname) throws FileNotFoundException, RuntimeException {
        try{
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + File.separator + dirname);
            if (!dir.exists()) dir.mkdirs();
            Log.d(TAG, "Path is " + sdCard.getAbsolutePath() + File.separator + dirname);
            Log.d(TAG, "Database" + dir + filename  + ".csv");
            Log.d(TAG, "Constructed the LogCSVWriter");


            mydatabase = SQLiteDatabase.openOrCreateDatabase(sdCard.getAbsolutePath() + File.separator + dirname + File.separator + filename + ".db", null, null);
            Log.d(TAG, "DB" + sdCard.getAbsolutePath() + File.separator + dirname + File.separator + filename +  ".db");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS MetaData (PhoneTime STRING, WearTime STRING, Driver INT, Situation INT, CountAccelerometer BIGINT, CountGyroskop BIGINT, CountMagnetic BIGINT," +
                    " CountWearAccelerometer BIGINT, CountWearGyroskp BIGINT, CountWearMagnetic BIGINT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS Gyroskop (time BIGINT, systime BIGINT, x FLOAT, y FLOAT, z FLOAT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS Accelerometer (time BIGINT, systime BIGINT, x FLOAT, y FLOAT, z FLOAT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS Rotation (time BIGINT, systime BIGINT, x FLOAT, y FLOAT, z FLOAT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS Magnetic (time BIGINT, systime BIGINT, x FLOAT, y FLOAT, z FLOAT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS WearGyroskop (time BIGINT, systime BIGINT, x FLOAT, y FLOAT, z FLOAT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS WearAccelerometer (time BIGINT, systime BIGINT, x FLOAT, y FLOAT, z FLOAT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS WearRotation (time BIGINT, systime BIGINT, x FLOAT, y FLOAT, z FLOAT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS WearMagnetic (time BIGINT, systime BIGINT, x FLOAT, y FLOAT, z FLOAT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS GPS (time BIGINT, systime BIGINT, latitude BIGINT, longitude BIGINT, altitude BIGINT);");
            mydatabase.execSQL("CREATE TABLE IF NOT EXISTS OBD (time BIGINT, systime BIGINT, BAROMETRIC_PRESSURE STRING, ENGINE_COOLANT_TEMP STRING, FUEL_LEVEL STRING, ENGINE_LOAD STRING, AMBIENT_AIR_TEMP STRING," +
                    "rpm STRING, INTAKE_MANIFOLD_PRESSURE STRING, MAF STRING, Term Fuel_Trim Bank_1 STRING, FUEL_ECONOMY STRING, Long_Term_Fuel_Trim Bank_2 STRING, FUEL_TYPE STRING, AIR_INTAKE_TEMP STRING," +
                    "FUEL_PRESSURE STRING, speed STRING, Short_Term_Fuel_Trim_Bank_2 STRING, Short_Term_Fuel_Trim_Bank_1 STRING, ENGINE_RUNTIME STRING, THROTTLE_POS STRING, DTC_NUMBER STRING, TROUBLE_CODES STRING," +
                    "TIMING_ADVANCE STRING, EQUIV_RATIO STRING);");

        }
        catch (Exception e) {
            Log.e(TAG, "Database constructor failed");
        }


    }
}
