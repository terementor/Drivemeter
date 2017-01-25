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

        /*if (messageEvent.getPath().startsWith("/PhoneTime/")) {
            long watchtime = Calendar.getInstance().getTimeInMillis();
            String path = messageEvent.getPath();
            String idStr = path.substring(path.lastIndexOf('/') + 1);
            long phonetime = Long.parseLong(idStr);
            Log.d(TAG, "Received message: " + messageEvent.getPath()+ "Phonetime " + phonetime + " Watchtime " + watchtime);

            watchtime = watchtime + 2000;

            //Timer timer = new Timer();
            //timer.schedule();

            ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
            pool.schedule(new Runnable() {
                @Override
                public void run() {
                    SensorService.setdelay();
                    Log.d(TAG, "Später: " );
                }
            }, 10, TimeUnit.MILLISECONDS ); //Warten bis Ausführung
            pool.shutdown();
        }*/



        if (messageEvent.getPath().equals(ClientPaths.START_MEASUREMENT)) {
            startService(new Intent(this, SensorService.class));
        }

        if (messageEvent.getPath().equals(ClientPaths.STOP_MEASUREMENT)) {
            SensorService.stopsending();
            stopService(new Intent(this, SensorService.class));
            deviceClient.clearSensorDeques();
        }
    }
}
