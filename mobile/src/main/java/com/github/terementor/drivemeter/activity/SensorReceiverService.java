package com.github.terementor.drivemeter.activity;

import android.content.ContentValues;
import android.net.Uri;
import android.util.Log;

import com.github.terementor.drivemeter.data.DataDeques;
import com.github.terementor.drivemeter.shared.DataMapKeys;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.ArrayList;


public class SensorReceiverService extends WearableListenerService {
    private static final String TAG = "SensorDashboard/SensorReceiverService";
    private final static int SENS_ACCELEROMETER = 1;
    private final static int SENS_MAGNETIC_FIELD = 2;
    // 3 = @Deprecated Orientation
    private final static int SENS_GYROSCOPE = 4;
    private final static int SENS_LIGHT = 5;
    private final static int SENS_PRESSURE = 6;
    // 7 = @Deprecated Temperature
    private final static int SENS_PROXIMITY = 8;
    private final static int SENS_GRAVITY = 9;
    private final static int SENS_LINEAR_ACCELERATION = 10;
    private final static int SENS_ROTATION_VECTOR = 11;
    private final static int SENS_HUMIDITY = 12;
    private final static int SENS_AMBIENT_TEMPERATURE = 13;
    private final static int SENS_MAGNETIC_FIELD_UNCALIBRATED = 14;
    private final static int SENS_GAME_ROTATION_VECTOR = 15;
    private final static int SENS_GYROSCOPE_UNCALIBRATED = 16;
    private final static int SENS_SIGNIFICANT_MOTION = 17;
    private final static int SENS_STEP_DETECTOR = 18;
    private final static int SENS_STEP_COUNTER = 19;
    private final static int SENS_GEOMAGNETIC = 20;
    private final static int SENS_HEARTRATE = 21;
    private RemoteSensorManager sensorManager;

    @Override
    public void onCreate() {
        super.onCreate();

        sensorManager = RemoteSensorManager.getInstance(this);
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);

        Log.i(TAG, "Connected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);

        Log.i(TAG, "Disconnected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged()");

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();
                //Log.d(TAG, "Path" + path);

                //Die Methode muss schnell frei werden
                if (path.startsWith("/sensors/")) {
                    long t0 = System.nanoTime();
                    unpackSensorData(
                            Integer.parseInt(uri.getLastPathSegment()),
                            DataMapItem.fromDataItem(dataItem).getDataMap()
                    );
                    long t1 = System.nanoTime();
                    Log.d(TAG, "UnpackSensorData took " + ((t1 - t0) / 1_000_000) + "ms" + DataMapItem.fromDataItem(dataItem).getDataMap().size());
                }

                if (path.startsWith("/Ready/")) {
                    MainActivity.setOutputsensorstrue();
                    Log.d(TAG, "Received message: Ready");
                }
                if (path.startsWith("/Counter")) {
                    DataMap countermap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    MainActivity.setWearCounter(countermap.getIntegerArrayList(DataMapKeys.COUNTER));
                    Log.d(TAG, "Received message: Counter");
                }
            }
        }
    }

    private void unpackSensorData(int sensorType, DataMap dataMap) {
        //Wahl der der richtigen Dequeque

        final DataDeques dataDeques = DataDeques.getInstance();

        ArrayList<Integer> accuracylist = dataMap.getIntegerArrayList(DataMapKeys.ACCURACY);
        ArrayList<Integer> typelist = dataMap.getIntegerArrayList(DataMapKeys.TYPE);
        ArrayList<Integer> counterlist = dataMap.getIntegerArrayList(DataMapKeys.COUNTER);
        long[] timearray = dataMap.getLongArray(DataMapKeys.TIMESTAMP);
        long[] systimearray = dataMap.getLongArray(DataMapKeys.SYSTIME);
        float[] valuesxarray = dataMap.getFloatArray(DataMapKeys.VALUESX);
        float[] valuesyarray = dataMap.getFloatArray(DataMapKeys.VALUESY);
        float[] valueszarray = dataMap.getFloatArray(DataMapKeys.VALUESZ);
        ArrayList<Integer> counterarray = dataMap.getIntegerArrayList(DataMapKeys.COUNTER);
        Log.d(TAG, "ArrayList " + counterarray);
        Log.d(TAG, "Length " + typelist.size());

        for (int i = 0; i < timearray.length && i < typelist.size(); i++) {

            //Log.d(TAG, "SensorEvent Nummer: " +" timestamp: " + Long.toString(timearray[i]));

            //Log.d(TAG, "Sensortyp: " + typelist.toString());

            if (MainActivity.getoutputsensors2()) {
                switch (typelist.get(i)) {
                    case 1: //Acceleration
                        ContentValues daten = new ContentValues();
                        daten.put("time", timearray[i]);
                        daten.put("systime", systimearray[i]);
                        daten.put("x", valuesxarray[i]);
                        daten.put("y", valuesyarray[i]);
                        daten.put("z", valueszarray[i]);
                        dataDeques.addtoWearaccdeque(daten);
                        //Log.d(TAG, "Received sensor data Type" +  "_" + typelist.get(i));
                        break;
                    case 2: //Magenticfield
                        ContentValues magdaten = new ContentValues();
                        magdaten.put("time", timearray[i]);
                        magdaten.put("systime", systimearray[i]);
                        magdaten.put("x", valuesxarray[i]);
                        magdaten.put("y", valuesyarray[i]);
                        magdaten.put("z", valueszarray[i]);
                        dataDeques.addtoWearmagdeque(magdaten);
                        //Log.d(TAG, "Received sensor data Type" +  "_" + typelist.get(i));
                        break;
                    case 4: //Gyroskop
                        ContentValues gyrodaten = new ContentValues();
                        gyrodaten.put("time", timearray[i]);
                        gyrodaten.put("systime", systimearray[i]);
                        gyrodaten.put("x", valuesxarray[i]);
                        gyrodaten.put("y", valuesyarray[i]);
                        gyrodaten.put("z", valueszarray[i]);
                        dataDeques.addtoWeargyrodeque(gyrodaten);
                        //Log.d(TAG, "Received sensor data Type" +  "_" + typelist.get(i));
                        break;
                }
            }
        }
    }
}
