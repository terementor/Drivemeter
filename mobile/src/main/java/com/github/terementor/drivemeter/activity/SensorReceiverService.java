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

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import java.util.Arrays;

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

    private Deque<ContentValues> tmpdeque = new ConcurrentLinkedDeque<>();


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


                if (path.startsWith("/sensors/")) {
                    unpackSensorData(
                            Integer.parseInt(uri.getLastPathSegment()),
                            DataMapItem.fromDataItem(dataItem).getDataMap()
                    );
                }
            }
        }
    }

    private void unpackSensorData(int sensorType, DataMap dataMap) {
        //Wahl der der richtigen Dequeque
        int accuracy = dataMap.getInt(DataMapKeys.ACCURACY);
        long timestamp = dataMap.getLong(DataMapKeys.TIMESTAMP);
        float[] values = dataMap.getFloatArray(DataMapKeys.VALUES);

        final DataDeques dataDeques = DataDeques.getInstance();

        switch (sensorType) {
            case 1: //Acceleration
                float a = values[0];
                float b = values[1];
                float c = values[2];
                long y = timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);

                dataDeques.addtoWearaccdeque(daten);
                break;
            case 2: //Magenticfield
                float maga = values[0];
                float magb = values[1];
                float magc = values[2];
                long magy = timestamp;

                ContentValues magdaten = new ContentValues();
                magdaten.put("time", magy);
                magdaten.put("x", maga);
                magdaten.put("y", magb);
                magdaten.put("z", magc);
                dataDeques.addtoWearmagdeque(magdaten);
                break;
            case 4: //Gyroskop
                float gyroa = values[0];
                float gyrob = values[1];
                float gyroc = values[2];
                long gyroy = timestamp;

                ContentValues gyrodaten = new ContentValues();
                gyrodaten.put("time", gyroy);
                gyrodaten.put("x", gyroa);
                gyrodaten.put("y", gyrob);
                gyrodaten.put("z", gyroc);
                dataDeques.addtoWeargyrodeque(gyrodaten);
                break;
        }


        Log.d(TAG, "Received sensor data " + sensorType + " = " + Arrays.toString(values));

        sensorManager.addSensorData(sensorType, accuracy, timestamp, values);
    }

}
