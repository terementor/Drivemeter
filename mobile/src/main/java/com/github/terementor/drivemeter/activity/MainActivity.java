package com.github.terementor.drivemeter.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.RuntimeCommand;
import com.github.pires.obd.enums.AvailableCommandNames;
import com.github.terementor.drivemeter.R;
import com.github.terementor.drivemeter.config.ObdConfig;
import com.github.terementor.drivemeter.data.DataDeques;
import com.github.terementor.drivemeter.io.AbstractGatewayService;
import com.github.terementor.drivemeter.io.LogCSVWriter;
import com.github.terementor.drivemeter.io.MockObdGatewayService;
import com.github.terementor.drivemeter.io.ObdCommandJob;
import com.github.terementor.drivemeter.io.ObdGatewayService;
import com.github.terementor.drivemeter.io.ObdProgressListener;
import com.github.terementor.drivemeter.io.SensorCSVWriter;
import com.github.terementor.drivemeter.net.ObdReading;
import com.github.terementor.drivemeter.net.ObdService;
import com.github.terementor.drivemeter.trips.TripLog;
import com.github.terementor.drivemeter.trips.TripRecord;
import com.google.android.gms.wearable.DataEvent;
import com.google.inject.Inject;
import com.github.terementor.drivemeter.activity.SensorReceiverService;

//Wear inports
//import com.github.terementor.drivemeter.data.Sensor;
import com.github.terementor.drivemeter.events.BusProvider;
import com.github.terementor.drivemeter.events.NewSensorEvent;
import com.github.terementor.drivemeter.ui.ExportActivity;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.squareup.otto.Subscribe;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import roboguice.RoboGuice;
import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

//test
import android.support.v7.appcompat.*;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.ActivityCompat;
import android.database.sqlite.*;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
//

import static com.github.terementor.drivemeter.activity.ConfigActivity.getGpsDistanceUpdatePeriod;
import static com.github.terementor.drivemeter.activity.ConfigActivity.getGpsUpdatePeriod;

// Some code taken from https://github.com/barbeau/gpstest

@ContentView(R.layout.main)
@TargetApi(22)
public class MainActivity extends RoboActivity implements ObdProgressListener, LocationListener, GpsStatus.Listener {

    private static boolean outputsensors = false;
    private static boolean outputsensordetails = true;

    private static final String TAG = MainActivity.class.getName();
    private static final int NO_BLUETOOTH_ID = 0;
    private static final int BLUETOOTH_DISABLED = 1;
    private static final int START_LIVE_DATA = 2;
    private static final int STOP_LIVE_DATA = 3;
    private static final int SETTINGS = 4;
    private static final int GET_DTC = 5;
    private static final int TABLE_ROW_MARGIN = 7;
    private static final int NO_ORIENTATION_SENSOR = 8;
    private static final int NO_GPS_SUPPORT = 9;
    private static final int TRIPS_LIST = 10;
    private static final int SAVE_TRIP_NOT_AVAILABLE = 11;
    private static final int REQUEST_ENABLE_BT = 1234;
    private static final int START_SENSORS = 55;
    private static final int STOP_SENSORS = 56;
    private static boolean bluetoothDefaultIsEnable = false;

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }

    public Map<String, String> commandResult = new HashMap<String, String>();
    boolean mGpsIsStarted = false;
    private LocationManager mLocService;
    private LocationProvider mLocProvider;
    private LogCSVWriter myCSVWriter;
    private SensorCSVWriter mySensorCSVWriter;
    private Location mLastLocation;
    /// the trip log
    private TripLog triplog;
    private TripRecord currentTrip;

    @InjectView(R.id.compass_text)
    private TextView compass;

    private Deque<SensorEvent> d = new ConcurrentLinkedDeque<SensorEvent>();
    private Deque<ContentValues> gyrodeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> accdeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> rotdeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> magdeque = new ConcurrentLinkedDeque<ContentValues>();
    //private SQLiteDatabase mydatabase;


    //WearCode
    private RemoteSensorManager remoteSensorManager;

    /*public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }*/


    //@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private final SensorEventListener gyroListener = new SensorEventListener() {

        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            String dir = "";
            if (x >= 337.5 || x < 22.5) {
                dir = "N";
            } else if (x >= 22.5 && x < 67.5) {
                dir = "NE";
            } else if (x >= 67.5 && x < 112.5) {
                dir = "E";
            } else if (x >= 112.5 && x < 157.5) {
                dir = "SE";
            } else if (x >= 157.5 && x < 202.5) {
                dir = "S";
            } else if (x >= 202.5 && x < 247.5) {
                dir = "SW";
            } else if (x >= 247.5 && x < 292.5) {
                dir = "W";
            } else if (x >= 292.5 && x < 337.5) {
                dir = "NW";
            }
            updateTextView(compass, dir);

            if (outputsensors) {
                //d.addLast(event);
                float a = event.values[0];
                float b = event.values[1];
                float c = event.values[2];
                long y = event.timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);

                gyrodeque.addLast(daten);
            }

        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };

    private final SensorEventListener accListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (outputsensors) {
                float a = event.values[0];
                float b = event.values[1];
                float c = event.values[2];
                long y = event.timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);
                accdeque.addLast(daten);
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };

    private final SensorEventListener rotListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (outputsensors) {
                float a = event.values[0];
                float b = event.values[1];
                float c = event.values[2];
                long y = event.timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);
                rotdeque.addLast(daten);
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };

    private final SensorEventListener magListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (outputsensors) {
                float a = event.values[0];
                float b = event.values[1];
                float c = event.values[2];
                long y = event.timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);
                magdeque.addLast(daten);
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };


    @InjectView(R.id.BT_STATUS)
    private TextView btStatusTextView;
    @InjectView(R.id.OBD_STATUS)
    private TextView obdStatusTextView;
    @InjectView(R.id.GPS_POS)
    private TextView gpsStatusTextView;
    @InjectView(R.id.vehicle_view)
    private LinearLayout vv;
    @InjectView(R.id.data_table)
    private TableLayout tl;
    @Inject
    private SensorManager sensorManager;
    @Inject
    private PowerManager powerManager;
    @Inject
    private SharedPreferences prefs;
    private boolean isServiceBound;
    private AbstractGatewayService service;
    private final Runnable mQueueCommands = new Runnable() {
        public void run() {
            if (service != null && service.isRunning() && service.queueEmpty()) {
                queueCommands();

                double lat = 0;
                double lon = 0;
                double alt = 0;
                final int posLen = 7;
                if (mGpsIsStarted && mLastLocation != null) {
                    lat = mLastLocation.getLatitude();
                    lon = mLastLocation.getLongitude();
                    alt = mLastLocation.getAltitude();

                    StringBuilder sb = new StringBuilder();
                    sb.append("Lat: ");
                    sb.append(String.valueOf(mLastLocation.getLatitude()).substring(0, posLen));
                    sb.append(" Lon: ");
                    sb.append(String.valueOf(mLastLocation.getLongitude()).substring(0, posLen));
                    sb.append(" Alt: ");
                    sb.append(String.valueOf(mLastLocation.getAltitude()));
                    gpsStatusTextView.setText(sb.toString());
                }
                if (prefs.getBoolean(ConfigActivity.UPLOAD_DATA_KEY, false)) {
                    // Upload the current reading by http
                    final String vin = prefs.getString(ConfigActivity.VEHICLE_ID_KEY, "UNDEFINED_VIN");
                    Map<String, String> temp = new HashMap<String, String>();
                    temp.putAll(commandResult);
                    ObdReading reading = new ObdReading(lat, lon, alt, System.currentTimeMillis(), vin, temp);
                    new UploadAsyncTask().execute(reading);

                } else if (prefs.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, false)) {
                    // Write the current reading to CSV
                    final String vin = prefs.getString(ConfigActivity.VEHICLE_ID_KEY, "UNDEFINED_VIN");
                    Map<String, String> temp = new HashMap<String, String>();
                    temp.putAll(commandResult);
                    ObdReading reading = new ObdReading(lat, lon, alt, System.currentTimeMillis(), vin, temp);
                    if (reading != null) myCSVWriter.writeLineCSV(reading);
                }
                commandResult.clear();
            }
            // run again in period defined in preferences
            new Handler().postDelayed(mQueueCommands, ConfigActivity.getObdUpdatePeriod(prefs));
        }
    };
    private Sensor gyroSensor = null;
    private Sensor accSensor = null;
    private Sensor rotSensor = null;
    private Sensor magSensor = null;
    private PowerManager.WakeLock wakeLock = null;
    private boolean preRequisites = true;
    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d(TAG, className.toString() + " service is bound");
            isServiceBound = true;
            service = ((AbstractGatewayService.AbstractGatewayServiceBinder) binder).getService();
            service.setContext(MainActivity.this);
            Log.d(TAG, "Starting live data");
            try {
                service.startService();
                if (preRequisites)
                    btStatusTextView.setText(getString(R.string.status_bluetooth_connected));
            } catch (IOException ioe) {
                Log.e(TAG, "Failure Starting live data");
                btStatusTextView.setText(getString(R.string.status_bluetooth_error_connecting));
                doUnbindService();
            }
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        // This method is *only* called when the connection to the service is lost unexpectedly
        // and *not* when the client unbinds (http://developer.android.com/guide/components/bound-services.html)
        // So the isServiceBound attribute should also be set to false when we unbind from the service.
        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, className.toString() + " service is unbound");
            isServiceBound = false;
        }
    };

    public static String LookUpCommand(String txt) {
        for (AvailableCommandNames item : AvailableCommandNames.values()) {
            if (item.getValue().equals(txt)) return item.name();
        }
        return txt;
    }

    public void updateTextView(final TextView view, final String txt) {
        new Handler().post(new Runnable() {
            public void run() {
                view.setText(txt);
            }
        });
    }

    public void stateUpdate(final ObdCommandJob job) {
        final String cmdName = job.getCommand().getName();
        String cmdResult = "";
        final String cmdID = LookUpCommand(cmdName);

        if (job.getState().equals(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR)) {
            cmdResult = job.getCommand().getResult();
            if (cmdResult != null && isServiceBound) {
                obdStatusTextView.setText(cmdResult.toLowerCase());
            }
        } else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.BROKEN_PIPE)) {
            if (isServiceBound)
                stopLiveData();
        } else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.NOT_SUPPORTED)) {
            cmdResult = getString(R.string.status_obd_no_support);
        } else {
            cmdResult = job.getCommand().getFormattedResult();
            if (isServiceBound)
                obdStatusTextView.setText(getString(R.string.status_obd_data));
        }

        if (vv.findViewWithTag(cmdID) != null) {
            TextView existingTV = (TextView) vv.findViewWithTag(cmdID);
            existingTV.setText(cmdResult);
        } else addTableRow(cmdID, cmdName, cmdResult);
        commandResult.put(cmdID, cmdResult);
        updateTripStatistic(job, cmdID);
    }

    private boolean gpsInit() {
        mLocService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (mLocService != null) {
            mLocProvider = mLocService.getProvider(LocationManager.GPS_PROVIDER);
            if (mLocProvider != null) {
                mLocService.addGpsStatusListener(this);
                if (mLocService.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    gpsStatusTextView.setText(getString(R.string.status_gps_ready));
                    return true;
                }
            }
        }
        gpsStatusTextView.setText(getString(R.string.status_gps_no_support));
        showDialog(NO_GPS_SUPPORT);
        Log.e(TAG, "Unable to get GPS PROVIDER");
        // todo disable gps controls into Preferences
        return false;
    }

    private void updateTripStatistic(final ObdCommandJob job, final String cmdID) {

        if (currentTrip != null) {
            if (cmdID.equals(AvailableCommandNames.SPEED.toString())) {
                SpeedCommand command = (SpeedCommand) job.getCommand();
                currentTrip.setSpeedMax(command.getMetricSpeed());
            } else if (cmdID.equals(AvailableCommandNames.ENGINE_RPM.toString())) {
                RPMCommand command = (RPMCommand) job.getCommand();
                currentTrip.setEngineRpmMax(command.getRPM());
            } else if (cmdID.endsWith(AvailableCommandNames.ENGINE_RUNTIME.toString())) {
                RuntimeCommand command = (RuntimeCommand) job.getCommand();
                currentTrip.setEngineRuntime(command.getFormattedResult());
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null)
            bluetoothDefaultIsEnable = btAdapter.isEnabled();


        //WearCode
        remoteSensorManager = RemoteSensorManager.getInstance(this);

        // get Acceleration sensor
        List<Sensor> accsensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
        if (accsensors.size() > 0)
            accSensor = accsensors.get(0);
        else
            showDialog(NO_ORIENTATION_SENSOR);

        // get Rotation sensor
        List<Sensor> rotsensors = sensorManager.getSensorList(Sensor.TYPE_ROTATION_VECTOR);
        if (rotsensors.size() > 0)
            rotSensor = rotsensors.get(0);
        else
            showDialog(NO_ORIENTATION_SENSOR);

        // get Magneticfield sensor
        List<Sensor> magsensors = sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
        if (magsensors.size() > 0)
            magSensor = magsensors.get(0);
        else
            showDialog(NO_ORIENTATION_SENSOR);

        // get Gyroskop sensor
        List<Sensor> gyrosensors = sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE);
        if (magsensors.size() > 0)
            gyroSensor = gyrosensors.get(0);
        else
            showDialog(NO_ORIENTATION_SENSOR);

        // create a log instance for use by this application
        triplog = TripLog.getInstance(this.getApplicationContext());

        obdStatusTextView.setText(getString(R.string.status_obd_disconnected));

        //SensorReceiverService sss = ((SensorReceiverService) this.back("com.github.terementor.drivemeter.activity.SensorReceiverService"));
        //sss.toString();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Entered onStart...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mLocService != null) {
            mLocService.removeGpsStatusListener(this);
            mLocService.removeUpdates(this);
        }

        releaseWakeLockIfHeld();
        if (isServiceBound) {
            doUnbindService();
        }

        endTrip();

        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null && btAdapter.isEnabled() && !bluetoothDefaultIsEnable)
            btAdapter.disable();
    }

    @Override
    protected void onPause() {
        super.onPause();

        //WearCode
        BusProvider.getInstance().unregister(this);

        remoteSensorManager.stopMeasurement();

        Log.d(TAG, "Pausing..");
        releaseWakeLockIfHeld();
    }

    /**
     * If lock is held, release. Lock will be held when the service is running.
     */
    private void releaseWakeLockIfHeld() {
        if (wakeLock.isHeld())
            wakeLock.release();
    }

    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resuming..");
        sensorManager.registerListener(gyroListener, gyroSensor,
                SensorManager.SENSOR_DELAY_FASTEST); // 200Hz, beim Nexus 5x 400hz
        sensorManager.registerListener(accListener, accSensor,
                SensorManager.SENSOR_DELAY_FASTEST); // 200Hz
        sensorManager.registerListener(rotListener, rotSensor,
                SensorManager.SENSOR_DELAY_FASTEST); // 100Hz
        sensorManager.registerListener(magListener, magSensor,
                SensorManager.SENSOR_DELAY_FASTEST); // 100Hz
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "ObdReader");


        //WearCode
        BusProvider.getInstance().register(this);
        List<com.github.terementor.drivemeter.data.Sensor> wearsensors = RemoteSensorManager.getInstance(this).getSensors();
        Log.d(TAG, "Wear" + "");
        //remoteSensorManager.startMeasurement();


        // get Bluetooth device
        final BluetoothAdapter btAdapter = BluetoothAdapter
                .getDefaultAdapter();

        preRequisites = btAdapter != null && btAdapter.isEnabled();
        if (!preRequisites && prefs.getBoolean(ConfigActivity.ENABLE_BT_KEY, false)) {
            preRequisites = btAdapter != null && btAdapter.enable();
        }

        gpsInit();

        if (!preRequisites) {
            showDialog(BLUETOOTH_DISABLED);
            btStatusTextView.setText(getString(R.string.status_bluetooth_disabled));
        } else {
            btStatusTextView.setText(getString(R.string.status_bluetooth_ok));
        }
    }

    private void updateConfig() {
        startActivity(new Intent(this, ConfigActivity.class));
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, START_LIVE_DATA, 0, getString(R.string.menu_start_live_data));
        menu.add(0, STOP_LIVE_DATA, 0, getString(R.string.menu_stop_live_data));
        menu.add(0, START_SENSORS, 0, "Start Sensors");
        menu.add(0, STOP_SENSORS, 0, "Stop Sensors");
        menu.add(0, GET_DTC, 0, getString(R.string.menu_get_dtc));
        menu.add(0, TRIPS_LIST, 0, getString(R.string.menu_trip_list));
        menu.add(0, SETTINGS, 0, getString(R.string.menu_settings));
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case START_LIVE_DATA:
                startLiveData();
                return true;
            case START_SENSORS:
                startsensors();
                return true;
            case STOP_SENSORS:
                stopsensors();
                remoteSensorManager.stopMeasurement();
                return true;
            case STOP_LIVE_DATA:
                stopLiveData();
                return true;
            case SETTINGS:
                updateConfig();
                return true;
            case GET_DTC:
                getTroubleCodes();
                return true;
            case TRIPS_LIST:
                startActivity(new Intent(this, TripListActivity.class));
                return true;
        }
        return false;
    }

    private void getTroubleCodes() {
        startActivity(new Intent(this, TroubleCodesActivity.class));
    }

    private void startLiveData() {
        Log.d(TAG, "Starting live data..");

        tl.removeAllViews(); //start fresh
        doBindService();

        currentTrip = triplog.startTrip();
        if (currentTrip == null)
            showDialog(SAVE_TRIP_NOT_AVAILABLE);

        // start command execution
        new Handler().post(mQueueCommands);

        if (prefs.getBoolean(ConfigActivity.ENABLE_GPS_KEY, false))
            gpsStart();
        else
            gpsStatusTextView.setText(getString(R.string.status_gps_not_used));

        // screen won't turn off until wakeLock.release()
        wakeLock.acquire();

        if (prefs.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, false)) {

            // Create the CSV Logger
            long mils = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("_dd_MM_yyyy_HH_mm_ss");

            try {
                myCSVWriter = new LogCSVWriter("Log" + sdf.format(new Date(mils)).toString() + ".csv",
                        prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                getString(R.string.default_dirname_full_logging))
                );
            } catch (FileNotFoundException | RuntimeException e) {
                Log.e(TAG, "Can't enable logging to file.", e);
            }
        }
    }


    private void startsensors() {
        outputsensors = true;
        remoteSensorManager.startMeasurement();

        if (prefs.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, false)) {
            //// TODO: 30.12.2016 Wenn in den Einstellungen nicht "logging" aktiviert ist, stürzt die app ab. Das muss besser programmiert werden


            // Create the CSV Logger
            long mils = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("_dd_MM_yyyy_HH_mm_ss");

            try {
                mySensorCSVWriter = new SensorCSVWriter("SensorLog" + sdf.format(new Date(mils)).toString(),
                        prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                getString(R.string.default_dirname_full_logging))
                );
                Log.d(TAG, "Create logfile");

                //String filename = "DB" + sdf.format(new Date(mils)).toString()+".db";
                //String pathname = prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                //        getString(R.string.default_dirname_full_logging));

                //mydatabase = new AccessDB(this, pathname + filename);


            } catch (FileNotFoundException | RuntimeException e) {
                Log.e(TAG, "Can't enable logging to file.", e);
            }


        }
        Log.d(TAG, "Starting sensors..");

        Runnable runnable = new Runnable() {
            public void run() {

                /*TODO get sensor meta data for later CSV
                name = sensor.getName();
                vendor = sensor.getVendor();
                version = sensor.getVersion();
                type = sensor.getType();
                resolution = sensor.getResolution();
                power = sensor.getPower();
                int otype = orientSensor.getType();
                String oname = orientSensor.getName();
                Log.d(TAG, "di#" + oname);*/

                DataDeques dataDeques = DataDeques.getInstance();
                dataDeques.clearWearaccdeque();
                dataDeques.clearWeargyrodeque();
                dataDeques.clearWearmagdeque();
                dataDeques.clearWearrotdeque();
                gyrodeque.clear();
                accdeque.clear();
                magdeque.clear();
                rotdeque.clear();

                //Write Sensordata in Database
                while (outputsensors) {
                    //Smartphonesensors
                    ContentValues gyrodata = gyrodeque.pollFirst();
                    if (gyrodata != null) {
                        SensorCSVWriter.mydatabase.insert("Gyroskop", null, gyrodata);
                    }
                    ContentValues accdata = accdeque.pollFirst();
                    if (accdata != null) {
                        SensorCSVWriter.mydatabase.insert("Accelerometer", null, accdata);
                    }
                    ContentValues rotdata = rotdeque.pollFirst();
                    if (rotdata != null) {
                        SensorCSVWriter.mydatabase.insert("Rotation", null, rotdata);
                    }
                    ContentValues magdata = magdeque.pollFirst();
                    if (magdata != null) {
                        SensorCSVWriter.mydatabase.insert("Magnetic", null, magdata);
                    }
                    //Wearsensors
                    ContentValues weargyrodata = dataDeques.pollfromWeargyrodeque();
                    if (weargyrodata != null) {
                        SensorCSVWriter.mydatabase.insert("WearGyroskop", null, weargyrodata);
                    }
                    ContentValues wearaccdata = dataDeques.pollfromWearaccdeque();
                    if (wearaccdata != null) {
                        SensorCSVWriter.mydatabase.insert("WearAccelerometer", null, wearaccdata);
                    }
                    ContentValues wearrotdata = dataDeques.pollfromWearrotdeque();
                    if (wearrotdata != null) {
                        SensorCSVWriter.mydatabase.insert("WearRotation", null, wearrotdata);
                    }
                    ContentValues wearmagdata = dataDeques.pollfromWearmagdeque();
                    if (wearmagdata != null) {
                        Log.d(TAG, "wearmagdata" + wearmagdata.toString());
                        SensorCSVWriter.mydatabase.insert("WearMagnetic", null, wearmagdata);
                    }


                    //Write Sensordetails to CSV
                    if (outputsensordetails) {
                        String[] details = new String[6];
                        details[0] = gyroSensor.getName();
                        details[1] = gyroSensor.getVendor();
                        details[2] = Integer.toString(gyroSensor.getType());
                        details[3] = Float.toString(gyroSensor.getResolution());
                        details[4] = Integer.toString(gyroSensor.getMinDelay());
                        details[5] = Integer.toString(gyroSensor.getMaxDelay());
                        mySensorCSVWriter.writeLineSensorCSV(details); //write headerline
                        mySensorCSVWriter.writeLineSensorCSV(details);


                        details[0] = accSensor.getName();
                        details[1] = accSensor.getVendor();
                        details[2] = Integer.toString(accSensor.getType());
                        details[3] = Float.toString(accSensor.getResolution());
                        details[4] = Integer.toString(accSensor.getMinDelay());
                        details[5] = Integer.toString(accSensor.getMaxDelay());
                        mySensorCSVWriter.writeLineSensorCSV(details);

                        details[0] = rotSensor.getName();
                        details[1] = rotSensor.getVendor();
                        details[2] = Integer.toString(rotSensor.getType());
                        details[3] = Float.toString(rotSensor.getResolution());
                        details[4] = Integer.toString(rotSensor.getMinDelay());
                        details[5] = Integer.toString(rotSensor.getMaxDelay());
                        mySensorCSVWriter.writeLineSensorCSV(details);

                        details[0] = magSensor.getName();
                        details[1] = magSensor.getVendor();
                        details[2] = Integer.toString(magSensor.getType());
                        details[3] = Float.toString(magSensor.getResolution());
                        details[4] = Integer.toString(magSensor.getMinDelay());
                        details[5] = Integer.toString(magSensor.getMaxDelay());
                        mySensorCSVWriter.writeLineSensorCSV(details);

                        outputsensordetails = false;
                    }
                    //updateTextView(compass, di);

                }

            }


            private void threadMsg(SensorEvent msg) {
                Message msgObj = handler.obtainMessage();
            }

            private final Handler handler = new Handler() {
                public void handleMessage(Message msg) {
                    String aResponse = msg.getData().getString("message");
                    updateTextView(compass, aResponse);
                    Log.d(TAG, "#" + aResponse);
                }
            };


        };
        Thread mythread = new Thread(runnable);
        mythread.start();

    }

    private void stopsensors() {
        outputsensors = false;
        if (mySensorCSVWriter != null) {
            mySensorCSVWriter.closeSensorCSVWriter();
        }
    }


    private void stopLiveData() {
        Log.d(TAG, "Stopping live data..");

        gpsStop();

        doUnbindService();
        endTrip();

        releaseWakeLockIfHeld();

        final String devemail = prefs.getString(ConfigActivity.DEV_EMAIL_KEY, null);
        if (devemail != null && !devemail.isEmpty()) {
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            ObdGatewayService.saveLogcatToFile(getApplicationContext(), devemail);
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            //No button clicked
                            break;
                    }
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Where there issues?\nThen please send us the logs.\nSend Logs?").setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener).show();
        }

        if (myCSVWriter != null) {
            myCSVWriter.closeLogCSVWriter();
        }
    }

    protected void endTrip() {
        if (currentTrip != null) {
            currentTrip.setEndDate(new Date());
            triplog.updateRecord(currentTrip);
        }
    }

    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        switch (id) {
            case NO_BLUETOOTH_ID:
                build.setMessage(getString(R.string.text_no_bluetooth_id));
                return build.create();
            case BLUETOOTH_DISABLED:
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return build.create();
            case NO_ORIENTATION_SENSOR:
                build.setMessage(getString(R.string.text_no_orientation_sensor));
                return build.create();
            case NO_GPS_SUPPORT:
                build.setMessage(getString(R.string.text_no_gps_support));
                return build.create();
            case SAVE_TRIP_NOT_AVAILABLE:
                build.setMessage(getString(R.string.text_save_trip_not_available));
                return build.create();
        }
        return null;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem startItem = menu.findItem(START_LIVE_DATA);
        MenuItem stopItem = menu.findItem(STOP_LIVE_DATA);
        MenuItem settingsItem = menu.findItem(SETTINGS);
        MenuItem getDTCItem = menu.findItem(GET_DTC);

        if (service != null && service.isRunning()) {
            getDTCItem.setEnabled(false);
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            settingsItem.setEnabled(false);
        } else {
            getDTCItem.setEnabled(true);
            stopItem.setEnabled(false);
            startItem.setEnabled(true);
            settingsItem.setEnabled(true);
        }

        return true;
    }

    private void addTableRow(String id, String key, String val) {

        TableRow tr = new TableRow(this);
        MarginLayoutParams params = new MarginLayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(TABLE_ROW_MARGIN, TABLE_ROW_MARGIN, TABLE_ROW_MARGIN,
                TABLE_ROW_MARGIN);
        tr.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setGravity(Gravity.RIGHT);
        name.setText(key + ": ");
        TextView value = new TextView(this);
        value.setGravity(Gravity.LEFT);
        value.setText(val);
        value.setTag(id);
        tr.addView(name);
        tr.addView(value);
        tl.addView(tr, params);
    }

    /**
     *
     */
    private void queueCommands() {
        if (isServiceBound) {
            for (ObdCommand Command : ObdConfig.getCommands()) {
                if (prefs.getBoolean(Command.getName(), true))
                    service.queueJob(new ObdCommandJob(Command));
            }
        }
    }

    private void doBindService() {
        if (!isServiceBound) {
            Log.d(TAG, "Binding OBD service..");
            if (preRequisites) {
                btStatusTextView.setText(getString(R.string.status_bluetooth_connecting));
                Intent serviceIntent = new Intent(this, ObdGatewayService.class);
                bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
            } else {
                btStatusTextView.setText(getString(R.string.status_bluetooth_disabled));
                Intent serviceIntent = new Intent(this, MockObdGatewayService.class);
                bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
            }
        }
    }

    private void doUnbindService() {
        if (isServiceBound) {
            if (service.isRunning()) {
                service.stopService();
                if (preRequisites)
                    btStatusTextView.setText(getString(R.string.status_bluetooth_ok));
            }
            Log.d(TAG, "Unbinding OBD service..");
            unbindService(serviceConn);
            isServiceBound = false;
            obdStatusTextView.setText(getString(R.string.status_obd_disconnected));
        }
    }

    public void onLocationChanged(Location location) {
        mLastLocation = location;
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
                gpsStatusTextView.setText(getString(R.string.status_gps_started));
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                gpsStatusTextView.setText(getString(R.string.status_gps_stopped));
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                gpsStatusTextView.setText(getString(R.string.status_gps_fix));
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                btStatusTextView.setText(getString(R.string.status_bluetooth_connected));
            } else {
                Toast.makeText(this, R.string.text_bluetooth_disabled, Toast.LENGTH_LONG).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private synchronized void gpsStart() {
        if (!mGpsIsStarted && mLocProvider != null && mLocService != null && mLocService.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mLocService.requestLocationUpdates(mLocProvider.getName(), getGpsUpdatePeriod(prefs), getGpsDistanceUpdatePeriod(prefs), this);
            mGpsIsStarted = true;
        } else {
            gpsStatusTextView.setText(getString(R.string.status_gps_no_support));
        }
    }

    private synchronized void gpsStop() {
        if (mGpsIsStarted) {
            mLocService.removeUpdates(this);
            mGpsIsStarted = false;
            gpsStatusTextView.setText(getString(R.string.status_gps_stopped));
        }
    }

    /**
     * Uploading asynchronous task
     */
    private class UploadAsyncTask extends AsyncTask<ObdReading, Void, Void> {

        @Override
        protected Void doInBackground(ObdReading... readings) {
            Log.d(TAG, "Uploading " + readings.length + " readings..");
            // instantiate reading service client
            final String endpoint = prefs.getString(ConfigActivity.UPLOAD_URL_KEY, "");
            RestAdapter restAdapter = new RestAdapter.Builder()
                    .setEndpoint(endpoint)
                    .build();
            ObdService service = restAdapter.create(ObdService.class);
            // upload readings
            for (ObdReading reading : readings) {
                try {
                    Response response = service.uploadReading(reading);
                    assert response.getStatus() == 200;
                } catch (RetrofitError re) {
                    Log.e(TAG, re.toString());
                }

            }
            Log.d(TAG, "Done");
            return null;
        }

    }
}
