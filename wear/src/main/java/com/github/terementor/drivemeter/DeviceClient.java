package com.github.terementor.drivemeter;

import android.content.ContentValues;
import android.content.Context;
import android.hardware.SensorEvent;
import android.util.Log;
import android.util.SparseLongArray;

import com.github.terementor.drivemeter.shared.DataMapKeys;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class DeviceClient {
    private static final String TAG = "SensorDashboard/DeviceClient";
    private static final int CLIENT_CONNECTION_TIMEOUT = 15000;
    public static DeviceClient instance;
    private Deque<ContentValues> sensordeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> sensordeque2 = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<Long> testdeque = new ConcurrentLinkedDeque<Long>();
    private Boolean changedeque = true;
    private AtomicInteger i = new AtomicInteger(0);
    private int j = 0;
    private Context context;
    private GoogleApiClient googleApiClient;
    private ExecutorService executorService;
    private int filterId;
    private SparseLongArray lastSensorData;
    private long time2 = 0;

    private DeviceClient(Context context) {
        this.context = context;

        googleApiClient = new GoogleApiClient.Builder(context).addApi(Wearable.API).build();

        executorService = Executors.newCachedThreadPool();
        lastSensorData = new SparseLongArray();
    }

    public static DeviceClient getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceClient(context.getApplicationContext());
        }

        return instance;
    }


    public void setSensorFilter(int filterId) {
        Log.d(TAG, "Now filtering by sensor: "); //+ filterId

        this.filterId = filterId;
    }

    public void sendtime(long time) {
        time2 = time;
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                PutDataMapRequest dataMap = PutDataMapRequest.create("/time/" + time2);
                PutDataRequest putDataRequest = dataMap.asPutDataRequest();
                send(putDataRequest);
                Log.d(TAG, "Sedning WatchTime " + Long.toString(time2));
            }
        });
    }

    public void clearSensorDeques() {
        this.sensordeque.clear();
        this.sensordeque2.clear();
        Log.d(TAG, "Clearing Deques");
    }

    public void sendreadyflag() {
        executorService.submit(new Runnable() {
            @Override
            public void run() {

                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                Calendar cal = Calendar.getInstance();
                PutDataMapRequest dataMap = PutDataMapRequest.create("/Ready/" + dateFormat.format(cal.getTime()));

                PutDataRequest putDataRequest = dataMap.asPutDataRequest();
                if (validateConnection()) {
                    Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            Log.d(TAG, "Sending ready signal: " + dataItemResult.getStatus().isSuccess());
                        }
                    });
                }
            }
        });
    }

    public void sendSensorData(SensorEvent event) {
        if (changedeque) {
            ContentValues daten = new ContentValues();
            daten.put("time", event.timestamp);
            daten.put("x", event.values[0]);
            daten.put("y", event.values[1]);
            daten.put("z", event.values[2]);
            daten.put("type", event.sensor.getType());
            daten.put("accurancy", event.accuracy);

            sensordeque.addLast(daten);

            //Log.d(TAG, "addLast "+Long.toString(event.timestamp));
        } else {
            ContentValues daten2 = new ContentValues();
            daten2.put("time", event.timestamp);
            daten2.put("x", event.values[0]);
            daten2.put("y", event.values[1]);
            daten2.put("z", event.values[2]);
            daten2.put("type", event.sensor.getType());
            daten2.put("accurancy", event.accuracy);

            sensordeque2.addLast(daten2);
        }

        if (sensordeque.size() == DataMapKeys.BATCHSIZE) {
            changedeque = false;
            Log.d(TAG, "Start mit sensordeque1 " + Integer.toString(sensordeque.size()));
            {
                long t0 = System.nanoTime();
                sendSensorDataInBackground(new ArrayDeque<ContentValues>(sensordeque));
                long t1 = System.nanoTime();
                Log.d(TAG, "Backgroundsending Queu1 took " + ((t1 - t0) / 1_000_000) + "ms");
            }
            sensordeque.clear();
        }

        if (sensordeque2.size() == DataMapKeys.BATCHSIZE) {
            changedeque = true;
            Log.d(TAG, "Start mit sensordeque2 " + Integer.toString(sensordeque2.size()));
            {
                long t0 = System.nanoTime();
                sendSensorDataInBackground(new ArrayDeque<ContentValues>(sensordeque2));
                long t1 = System.nanoTime();
                Log.d(TAG, "Backgroundsending Queu2 took " + ((t1 - t0) / 1_000_000) + "ms");
            }
            sensordeque2.clear();
        }
    }

    private void sendSensorDataInBackground(final Deque<ContentValues> deque) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                ArrayList<Integer> accuracylist = new ArrayList<Integer>();
                ArrayList<Integer> typelist = new ArrayList<Integer>();
                ArrayList<Integer> counterlist = new ArrayList<Integer>();
                long[] timearray = new long[DataMapKeys.BATCHSIZE];
                float[] valuesxarray = new float[DataMapKeys.BATCHSIZE];
                float[] valuesyarray = new float[DataMapKeys.BATCHSIZE];
                float[] valueszarray = new float[DataMapKeys.BATCHSIZE];
                int l = 0;
                int k = 0;

                Log.d(TAG, "Start While Loop, dequesize: " + Integer.toString(deque.size()));
                while (!deque.isEmpty()) {
                    ContentValues tmp = deque.pollFirst();
                    //Log.d(TAG, "Time "+ Long.toString(tmp.timestamp) );

                    accuracylist.add(tmp.getAsInteger("accurancy"));
                    timearray[l] = tmp.getAsLong("time");
                    valuesxarray[l] = tmp.getAsFloat("x");
                    valuesyarray[l] = tmp.getAsFloat("y");
                    valueszarray[l] = tmp.getAsFloat("z");
                    typelist.add(tmp.getAsInteger("type"));
                    counterlist.add(i.intValue());

                    i.getAndAdd(1);
                    l++;
                    k++;

                    if (k == DataMapKeys.BATCHSIZE) {
                        Log.d(TAG, "StartSending " + i.toString() + " WhileLoopPosition " + Integer.toString(l));

                        int sensorType = 1;
                        PutDataMapRequest dataMap = PutDataMapRequest.create("/sensors/" + sensorType);

                        dataMap.getDataMap().putIntegerArrayList(DataMapKeys.TYPE, typelist);
                        dataMap.getDataMap().putIntegerArrayList(DataMapKeys.ACCURACY, accuracylist);
                        dataMap.getDataMap().putIntegerArrayList(DataMapKeys.COUNTER, counterlist);
                        dataMap.getDataMap().putLongArray(DataMapKeys.TIMESTAMP, timearray);
                        dataMap.getDataMap().putFloatArray(DataMapKeys.VALUESX, valuesxarray);
                        dataMap.getDataMap().putFloatArray(DataMapKeys.VALUESY, valuesyarray);
                        dataMap.getDataMap().putFloatArray(DataMapKeys.VALUESZ, valueszarray);

                        PutDataRequest putDataRequest = dataMap.asPutDataRequest();

                        {
                            long t0 = System.nanoTime();
                            send(putDataRequest);
                            long t1 = System.nanoTime();
                            Log.d(TAG, "Sending Batches took " + ((t1 - t0) / 1_000_000) + "ms");
                        }

                        //Log.d(TAG, "Fertig mit Senden der Batches");
                        k = 0;
                    }
                }
            }
        });
    }

    private boolean validateConnection() {
        if (googleApiClient.isConnected()) {
            return true;
        }

        ConnectionResult result = googleApiClient.blockingConnect(CLIENT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);

        return result.isSuccess();
    }

    private void send(final PutDataRequest putDataRequest) {
        if (validateConnection()) {
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    Log.d(TAG, "Sending sensor data: " + j + "_" + dataItemResult.getStatus().isSuccess());
                    j++;
                }
            });
        }
    }
}
