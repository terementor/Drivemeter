package com.github.terementor.drivemeter;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;

import com.github.terementor.drivemeter.shared.DataMapKeys;
import com.github.terementor.drivemeter.shared.NTPTime;
import com.github.terementor.drivemeter.shared.NTPTimeInterface;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.sql.Time;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SensorService extends Service implements SensorEventListener, LocationListener, GpsStatus.Listener, NTPTimeInterface {
    private static final String TAG = "SensorDashboard/SensorService";

    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER; //1
    private final static int SENS_MAGNETIC_FIELD = Sensor.TYPE_MAGNETIC_FIELD; //2
    // 3 = @Deprecated Orientation
    private final static int SENS_GYROSCOPE = Sensor.TYPE_GYROSCOPE; //4
    private final static int SENS_LIGHT = Sensor.TYPE_LIGHT; //5
    private final static int SENS_PRESSURE = Sensor.TYPE_PRESSURE; //6
    // 7 = @Deprecated Temperature
    private final static int SENS_PROXIMITY = Sensor.TYPE_PROXIMITY; //8
    private final static int SENS_GRAVITY = Sensor.TYPE_GRAVITY; //9
    private final static int SENS_LINEAR_ACCELERATION = Sensor.TYPE_LINEAR_ACCELERATION; //10
    private final static int SENS_ROTATION_VECTOR = Sensor.TYPE_ROTATION_VECTOR; //11
    private final static int SENS_HUMIDITY = Sensor.TYPE_RELATIVE_HUMIDITY; //12
    // TODO: there's no Android Wear devices yet with a body temperature monitor
    private final static int SENS_AMBIENT_TEMPERATURE = Sensor.TYPE_AMBIENT_TEMPERATURE; //13
    private final static int SENS_MAGNETIC_FIELD_UNCALIBRATED = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED; //14
    private final static int SENS_GAME_ROTATION_VECTOR = Sensor.TYPE_GAME_ROTATION_VECTOR; //15
    private final static int SENS_GYROSCOPE_UNCALIBRATED = Sensor.TYPE_GYROSCOPE_UNCALIBRATED; //16
    private final static int SENS_SIGNIFICANT_MOTION = Sensor.TYPE_SIGNIFICANT_MOTION; //17
    private final static int SENS_STEP_DETECTOR = Sensor.TYPE_STEP_DETECTOR; //18
    private final static int SENS_STEP_COUNTER = Sensor.TYPE_STEP_COUNTER; //19
    private final static int SENS_GEOMAGNETIC = Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR; //20
    private final static int SENS_HEARTRATE = Sensor.TYPE_HEART_RATE; //21

    private static boolean sendingstatus = false;
    private LocationManager mLocService;
    private LocationProvider mLocProvider;
    private Location mLastLocation;
    boolean mGpsIsStarted = false;


    SensorManager mSensorManager;

    private Sensor mHeartrateSensor;

    private DeviceClient client;
    private ScheduledExecutorService mScheduler;

    @Override
    public void onCreate() {
        super.onCreate();

        client = DeviceClient.getInstance(this);
        //TODO Anderes Logo ruhig mal klar machen
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle("Drivemeter");
        builder.setContentText("Collecting sensor data..");
        builder.setSmallIcon(R.drawable.ic_launcher);

        startForeground(1, builder.build());

        gpsInit();
        startMeasurement();
        Calendar c = Calendar.getInstance();
        int seconds = c.get(Calendar.SECOND);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopMeasurement();

        if (mLocService != null) {
            mLocService.removeGpsStatusListener(this);
            mLocService.removeUpdates(this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void processFinish(Long asyncresult) {
        Log.d(TAG, "eiertanz " + asyncresult);
    }

    protected void startMeasurement() {
        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));

        //StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        //StrictMode.setThreadPolicy(policy);
        //gettime();

        //Get time from local ntpserver
        //NTPTime ntptime = new NTPTime();
        //ntptime.delegate = this;
        //ntptime.execute(2);

        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);
        //Sensor ambientTemperatureSensor = mSensorManager.getDefaultSensor(SENS_AMBIENT_TEMPERATURE);
        //Sensor gameRotationVectorSensor = mSensorManager.getDefaultSensor(SENS_GAME_ROTATION_VECTOR);
        //Sensor geomagneticSensor = mSensorManager.getDefaultSensor(SENS_GEOMAGNETIC);
        //Sensor gravitySensor = mSensorManager.getDefaultSensor(SENS_GRAVITY);
        Sensor gyroscopeSensor = mSensorManager.getDefaultSensor(SENS_GYROSCOPE);
        //Sensor gyroscopeUncalibratedSensor = mSensorManager.getDefaultSensor(SENS_GYROSCOPE_UNCALIBRATED);
        //mHeartrateSensor = mSensorManager.getDefaultSensor(SENS_HEARTRATE);
        //Sensor heartrateSamsungSensor = mSensorManager.getDefaultSensor(65562);
        //Sensor lightSensor = mSensorManager.getDefaultSensor(SENS_LIGHT);
        //Sensor linearAccelerationSensor = mSensorManager.getDefaultSensor(SENS_LINEAR_ACCELERATION);
        Sensor magneticFieldSensor = mSensorManager.getDefaultSensor(SENS_MAGNETIC_FIELD);
        //Sensor magneticFieldUncalibratedSensor = mSensorManager.getDefaultSensor(SENS_MAGNETIC_FIELD_UNCALIBRATED);
        //Sensor pressureSensor = mSensorManager.getDefaultSensor(SENS_PRESSURE);
        //Sensor proximitySensor = mSensorManager.getDefaultSensor(SENS_PROXIMITY);
        //Sensor humiditySensor = mSensorManager.getDefaultSensor(SENS_HUMIDITY);
        //Sensor rotationVectorSensor = mSensorManager.getDefaultSensor(SENS_ROTATION_VECTOR);
        //Sensor significantMotionSensor = mSensorManager.getDefaultSensor(SENS_SIGNIFICANT_MOTION);
        //Sensor stepCounterSensor = mSensorManager.getDefaultSensor(SENS_STEP_COUNTER);
        //Sensor stepDetectorSensor = mSensorManager.getDefaultSensor(SENS_STEP_DETECTOR);

        Sensor ambientTemperatureSensor = null;
        Sensor gameRotationVectorSensor = null;
        Sensor geomagneticSensor = null;
        Sensor gravitySensor = null;
        Sensor gyroscopeUncalibratedSensor =null;
        Sensor heartrateSamsungSensor = null;
        Sensor lightSensor =null;
        Sensor linearAccelerationSensor =null;
        Sensor magneticFieldUncalibratedSensor = null;
        Sensor pressureSensor = null;
        Sensor proximitySensor = null;
        Sensor humiditySensor = null;
        Sensor rotationVectorSensor = null;
        Sensor significantMotionSensor = null;
        Sensor stepCounterSensor =null;
        Sensor stepDetectorSensor = null;

        // Register the listener
        if (mSensorManager != null) {
            if (accelerometerSensor != null) {
                //The default data delay is suitable for monitoring typical screen orientation changes and uses a delay of 200,000 microseconds.
                // You can specify other data delays, such as SENSOR_DELAY_GAME (20,000 microsecond delay), SENSOR_DELAY_UI (60,000 microsecond delay), or SENSOR_DELAY_FASTEST (0 microsecond delay)
                mSensorManager.registerListener(this, accelerometerSensor, MessageReceiverService.speed); //SensorManager.SENSOR_DELAY_GAME
                Log.w(TAG, "AccSensor found with speed: " +MessageReceiverService.speed);
            } else {
                Log.w(TAG, "No Accelerometer found");
            }

            if (ambientTemperatureSensor != null) {
                mSensorManager.registerListener(this, ambientTemperatureSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "Ambient Temperature Sensor not found");
            }

            if (gameRotationVectorSensor != null) {
                mSensorManager.registerListener(this, gameRotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "Gaming Rotation Vector Sensor not found");
            }

            if (geomagneticSensor != null) {
                mSensorManager.registerListener(this, geomagneticSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "No Geomagnetic Sensor found");
            }

            if (gravitySensor != null) {
                mSensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "No Gravity Sensor");
            }

            if (gyroscopeSensor != null) {
                mSensorManager.registerListener(this, gyroscopeSensor, MessageReceiverService.speed); //SensorManager.SENSOR_DELAY_GAME
                Log.w(TAG, "No Gyroscope Sensor found");
            }

            if (gyroscopeUncalibratedSensor != null) {
                mSensorManager.registerListener(this, gyroscopeUncalibratedSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "No Uncalibrated Gyroscope Sensor found");
            }

            if (mHeartrateSensor != null) {
                final int measurementDuration   = 10;   // Seconds
                final int measurementBreak      = 5;    // Seconds

                mScheduler = Executors.newScheduledThreadPool(1);
                mScheduler.scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "register Heartrate Sensor");
                                mSensorManager.registerListener(SensorService.this, mHeartrateSensor, SensorManager.SENSOR_DELAY_NORMAL);

                                try {
                                    Thread.sleep(measurementDuration * 1000);
                                } catch (InterruptedException e) {
                                    Log.e(TAG, "Interrupted while waitting to unregister Heartrate Sensor");
                                }

                                Log.d(TAG, "unregister Heartrate Sensor");
                                mSensorManager.unregisterListener(SensorService.this, mHeartrateSensor);
                            }
                        }, 3, measurementDuration + measurementBreak, TimeUnit.SECONDS);

            } else {
                Log.d(TAG, "No Heartrate Sensor found");
            }

            if (heartrateSamsungSensor != null) {
                mSensorManager.registerListener(this, heartrateSamsungSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "Samsungs Heartrate Sensor not found");
            }

            if (lightSensor != null) {
                mSensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Light Sensor found");
            }

            if (linearAccelerationSensor != null) {
                mSensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Linear Acceleration Sensor found");
            }

            if (magneticFieldSensor != null) {
                mSensorManager.registerListener(this, magneticFieldSensor, MessageReceiverService.speed); //SensorManager.SENSOR_DELAY_GAME
            } else {
                Log.d(TAG, "No Magnetic Field Sensor found");
            }

            if (magneticFieldUncalibratedSensor != null) {
                mSensorManager.registerListener(this, magneticFieldUncalibratedSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No uncalibrated Magnetic Field Sensor found");
            }

            if (pressureSensor != null) {
                mSensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Pressure Sensor found");
            }

            if (proximitySensor != null) {
                mSensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Proximity Sensor found");
            }

            if (humiditySensor != null) {
                mSensorManager.registerListener(this, humiditySensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Humidity Sensor found");
            }

            if (rotationVectorSensor != null) {
                mSensorManager.registerListener(this, rotationVectorSensor, MessageReceiverService.speed);
            } else {
                Log.d(TAG, "No Rotation Vector Sensor found");
            }

            if (significantMotionSensor != null) {
                mSensorManager.registerListener(this, significantMotionSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Significant Motion Sensor found");
            }

            if (stepCounterSensor != null) {
                mSensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Step Counter Sensor found");
            }

            if (stepDetectorSensor != null) {
                mSensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.d(TAG, "No Step Detector Sensor found");
            }
        }
        //Wait with sending data to phone, to have enough time to initialize the phone

        gpsStart();

        ScheduledExecutorService pool = Executors.newScheduledThreadPool(2);
        pool.schedule(new Runnable() {
            @Override
            public void run() {
                client.sendreadyflag(); //Start recording of Phone sensors
            }
        }, 3000000, TimeUnit.MICROSECONDS );

        pool.schedule(new Runnable() {
            @Override
            public void run() {
                SensorService.startsending(); //Start recording of Wear sensors
            }
        }, 3230000, TimeUnit.MICROSECONDS );
        pool.shutdown();

    }

    public void stopMeasurement() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        if (mScheduler != null && !mScheduler.isTerminated()) {
            mScheduler.shutdown();
        }
        gpsStop();
    }

    /*public static void setdelay (){
        istimesend = false;
        Log.d(TAG, "issetffalse");
    }*/

    public static void startsending(){
        sendingstatus = true;
        Log.d(TAG, "startsending()");
    }
    public static void stopsending(){
        sendingstatus = false;
        Log.d(TAG, "delaytrue cause of stop message");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //only startsending, when everything is initalized and message with startsending was send to phone
        if (!sendingstatus){
            Log.d(TAG, "isfalse");
            return;
        }

        //client.sendSensorData(event.sensor.getType(), event.accuracy, event.timestamp, event.values, event.sensor.getMinDelay(), event.sensor.getName());
        if (event.sensor.getType() == 1 || event.sensor.getType() == 2 || event.sensor.getType() == 4) {
        //if (event.sensor.getType() == 1 ) {
            client.sendSensorData(event);
            if (event.sensor.getType() == 1 ) {
                //Log.d(TAG, "Type" + event.sensor.getType() + " "+ "Time "+ Long.toString(event.timestamp) + " Values "+ event.values[0] + " " + event.values[1]+ " "+ event.values[2]);
            }
            //Log.d(TAG, "Type" + event.sensor.getType() + " "+ "Time "+ Long.toString(event.timestamp) + " Values "+ event.values[0] + " " + event.values[1]+ " "+ event.values[2]);
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private boolean gpsInit() {
        mLocService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (mLocService != null) {
            mLocProvider = mLocService.getProvider(LocationManager.GPS_PROVIDER);
            if (mLocProvider != null) {
                mLocService.addGpsStatusListener(this);
                if (mLocService.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    return true;
                }
            }
        }
        Log.e(TAG, "Unable to get GPS PROVIDER");
        // todo disable gps controls into Preferences
        return false;
    }

    public void onLocationChanged(Location location) {

        mLastLocation = location;
        double lat = mLastLocation.getLatitude();
        double lon = mLastLocation.getLongitude();
        double alt = mLastLocation.getAltitude();
        double nanos = mLastLocation.getElapsedRealtimeNanos();
        Log.d(TAG, "GPS Location: ");
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    public void onProviderEnabled(String provider) {
    }

    public void onProviderDisabled(String provider) {
    }

    public void onGpsStatusChanged(int event) {

        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                Log.d(TAG, "GPS is started: ");
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                Log.d(TAG, "GPS is stopped: ");
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                Log.d(TAG, "GPS event first fix: ");
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                Log.d(TAG, "GPS event satelite status: ");
                break;
        }
    }

    private synchronized void gpsStart() {
        if (!mGpsIsStarted && mLocProvider != null && mLocService != null && mLocService.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mLocService.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5, 0, this); //TODO von den prefs rüber geben handy - mLocProvider.getName()
            mGpsIsStarted = true;
            //Log.d(TAG, "GPS is started");
        } else {
            Log.e(TAG, "Unable to start GPS");
        }
    }

    private synchronized void gpsStop() {
        if (mGpsIsStarted) {
            mLocService.removeUpdates(this);
            mGpsIsStarted = false;
        }
    }


    public static final String TIME_SERVER = "time-a.nist.gov";
    public static final String LOCAL_SERVER = "192.168.43.1";
    private void gettime (){
        Log.d(TAG, "Enter TimeClass");
        InetAddress inetAddress = null;
        InetAddress inetAddress2 = null;
        TimeInfo timeInfo = null;
        TimeInfo timeInfo2 = null;
        NTPUDPClient timeClient = new NTPUDPClient();
        try {
            //inetAddress = InetAddress.getByName(TIME_SERVER);
            inetAddress2 = InetAddress.getByName(LOCAL_SERVER);

        } catch (UnknownHostException er) {
            Log.e(TAG, er.toString());
        }
        Log.d(TAG, "got inet adress");
        try {
            //timeInfo = timeClient.getTime(inetAddress);
            timeInfo2 = timeClient.getTime(inetAddress2, 40000);
        } catch (IOException er) {
            Log.e(TAG, er.toString());
        }

        long sysreturnTime = timeInfo2.getReturnTime();   //local device time
        //long returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();   //server time
        long returnTime = timeInfo2.getMessage().getTransmitTimeStamp().getTime();   //server time

        long timediff = sysreturnTime - returnTime;

        Date time = new Date(returnTime);
        //Log.d(TAG, "Time from " + TIME_SERVER + ": " + time);
        Log.d(TAG, "Returntime " + LOCAL_SERVER + ": " + returnTime);
        Log.d(TAG, "Systemtime " + ": " + sysreturnTime);
        Log.d(TAG, "Differenz" + timediff);


    }
}
