package com.github.terementor.drivemeter;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.github.terementor.drivemeter.shared.ClientPaths;
import com.github.terementor.drivemeter.shared.DataMapKeys;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Calendar;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Timer;

public class MessageReceiverService extends WearableListenerService {
    private static final String TAG = "SensorDashboard/MessageReceiverService";
    public static int speed = 20000;

    private DeviceClient deviceClient;

    @Override
    public void onCreate() {
        super.onCreate();

        deviceClient = DeviceClient.getInstance(this);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();

                if (path.startsWith("/filter")) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    int filterById = dataMap.getInt(DataMapKeys.FILTER);
                    deviceClient.setSensorFilter(filterById);
                }
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "Received message: " + messageEvent.getPath());

        //check for startmessage with sensorspeed settings
        if (messageEvent.getPath().startsWith(ClientPaths.START_MEASUREMENT)) {
            startService(new Intent(this, SensorService.class));

            if (messageEvent.getPath().endsWith("20000")){
                speed = 20000;
            } else if (messageEvent.getPath().endsWith("60000")) {
                speed = 60000;
            } else if (messageEvent.getPath().endsWith("200000")){
                speed = 200000;
            } else {
                speed = 0;
            }
        }

        if (messageEvent.getPath().equals(ClientPaths.STOP_MEASUREMENT)) {
            SensorService.stopsending();
            stopService(new Intent(this, SensorService.class));
            deviceClient.clearSensorDeques();
        }
    }
}
