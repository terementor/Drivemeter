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
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import com.google.inject.Inject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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

import static com.github.terementor.drivemeter.activity.ConfigActivity.getGpsDistanceUpdatePeriod;
import static com.github.terementor.drivemeter.activity.ConfigActivity.getGpsUpdatePeriod;
// Some code taken from https://github.com/barbeau/gpstest

@ContentView(R.layout.main)
@TargetApi(22)
public class MainActivity extends RoboActivity implements ObdProgressListener, LocationListener, GpsStatus.Listener, OnItemSelectedListener {

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
    private static final int START_BOTH = 57;
    private static final int STOP_BOTH = 58;
    private static boolean outputsensors = false;
    private static boolean outputsensors2 = false;
    private static boolean bluetoothDefaultIsEnable = false;
    private static boolean alreadyExecuted = false;
    private static boolean watchtextviewupdate = false;
    private static boolean phonetextviewupdate = false;
    private static boolean obdtextviewupdate = false;
    private static long gyrocounter = 0;
    private static long acccounter = 0;
    private static long magcounter = 0;
    private static ContentValues metadata = new ContentValues();

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }

    public Map<String, String> commandResult = new HashMap<String, String>();
    boolean mGpsIsStarted = false;
    private Spinner driver_spinner = null;
    private Spinner situation_spinner = null;
    private LocationManager mLocService;
    private LocationProvider mLocProvider;
    private SensorCSVWriter sensorCSVWriter;
    private LogCSVWriter detailsCSV;
    private LogCSVWriter accCSV;
    private LogCSVWriter waccCSV;
    private LogCSVWriter gyroCSV;
    private LogCSVWriter magCSV;
    private LogCSVWriter wgyroCSV;
    private LogCSVWriter wmagCSV;
    private LogCSVWriter gpsCSV;
    private LogCSVWriter rotCSV;
    private LogCSVWriter wrotCSV;
    private LogCSVWriter obdCSV;
    private LogCSVWriter metaCSV;

    private Location mLastLocation;
    /// the trip log
    private TripLog triplog;
    private TripRecord currentTrip;
    @InjectView(R.id.compass_text)
    private TextView compass;
    private Deque<ContentValues> gyrodeque = new ConcurrentLinkedDeque<ContentValues>();
    //@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private final SensorEventListener gyroListener = new SensorEventListener() {

        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            String dir = "";
            /*if (x >= 337.5 || x < 22.5) {
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
            updateTextView(compass, dir);*/

            if (outputsensors2) {
                float a = event.values[0];
                float b = event.values[1];
                float c = event.values[2];
                long y = event.timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);
                daten.put("systime", System.nanoTime()); //System.currentTimeMillis()

                gyrodeque.addLast(daten);
                gyrocounter++;
            }

        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };
    private Deque<ContentValues> obddeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> gpsdeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> accdeque = new ConcurrentLinkedDeque<ContentValues>();
    private final SensorEventListener accListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (outputsensors2) {
                float a = event.values[0];
                float b = event.values[1];
                float c = event.values[2];
                long y = event.timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);
                daten.put("systime", System.nanoTime());
                accdeque.addLast(daten);
                acccounter++;
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };
    private Deque<ContentValues> rotdeque = new ConcurrentLinkedDeque<ContentValues>();
    private final SensorEventListener rotListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (outputsensors2) {
                float a = event.values[0];
                float b = event.values[1];
                float c = event.values[2];
                long y = event.timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);
                daten.put("systime", System.nanoTime());
                rotdeque.addLast(daten);
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };

    private Deque<ContentValues> magdeque = new ConcurrentLinkedDeque<ContentValues>();
    private int z = 0;
    private final SensorEventListener magListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (outputsensors2) {
                float a = event.values[0];
                float b = event.values[1];
                float c = event.values[2];
                long y = event.timestamp;

                ContentValues daten = new ContentValues();
                daten.put("time", y);
                daten.put("x", a);
                daten.put("y", b);
                daten.put("z", c);
                daten.put("systime", System.nanoTime());
                magdeque.addLast(daten);
                magcounter++;
                //Log.d(TAG, "Zaehler " + z);
                z++;
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };
    private RemoteSensorManager remoteSensorManager;
    @InjectView(R.id.BT_STATUS)
    private TextView btStatusTextView;
    @InjectView(R.id.OBD_STATUS)
    private TextView obdStatusTextView;
    @InjectView(R.id.GPS_POS)
    private TextView gpsStatusTextView;
    @InjectView(R.id.PHONE_STATUS)
    private TextView phoneStatusTextView;
    @InjectView(R.id.WATCH_STATUS)
    private TextView watchStatusTextView;
    @InjectView(R.id.OBDOUTPUT_STATUS)
    private TextView obdoutputStatusTextView;
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
                if (mGpsIsStarted && mLastLocation != null && outputsensors2) {
                    lat = mLastLocation.getLatitude();
                    lon = mLastLocation.getLongitude();
                    alt = mLastLocation.getAltitude();
                    Long nanos = mLastLocation.getElapsedRealtimeNanos();
                    Long systemnanos = System.nanoTime();

                    //put gpsdata in deque
                    ContentValues gpsdata = new ContentValues();
                    gpsdata.put("lat", lat);
                    gpsdata.put("lon", lon);
                    gpsdata.put("alt", alt);
                    gpsdata.put("time", nanos);
                    gpsdata.put("systemtime", systemnanos);
                    gpsdeque.addLast(gpsdata);
                }
                if (prefs.getBoolean(ConfigActivity.UPLOAD_DATA_KEY, false)) {
                    // Upload the current reading by http
                    final String vin = prefs.getString(ConfigActivity.VEHICLE_ID_KEY, "UNDEFINED_VIN");
                    Map<String, String> temp = new HashMap<String, String>();
                    temp.putAll(commandResult);
                    ObdReading reading = new ObdReading(lat, lon, alt, System.currentTimeMillis(), vin, temp);
                    new UploadAsyncTask().execute(reading);

                } else if (prefs.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, true)) {
                    // Write the current reading to CSV
                    final String vin = prefs.getString(ConfigActivity.VEHICLE_ID_KEY, "UNDEFINED_VIN");
                    Map<String, String> temp = new HashMap<String, String>();
                    temp.putAll(commandResult);

                    //Write the current reading to DB
                    if (outputsensors2) {
                        Log.d(TAG, "OBD Hashmap" + temp.toString());
                        ContentValues obddata = new ContentValues();
                        obddata.put("speed", temp.get("SPEED"));
                        obddata.put("rpm", temp.get("ENGINE_RPM"));
                        obddata.put("time", temp.get("time"));
                        obddeque.addLast(obddata);
                    }
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

    public static void setOutputsensorstrue() {
        outputsensors2 = true;
        Log.d(TAG, "SsetOutputsensors true..");
    }

    public static void setOutputsensorsfalse() {
        outputsensors = false;
        outputsensors2 = false;
        Log.d(TAG, "setOutputsensors false..");
    }

    public static boolean getoutputsensors2() {
        return outputsensors2;
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
        commandResult.put("time", Long.toString(System.nanoTime()));
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

        driver_spinner = (Spinner) findViewById(R.id.driver_spinner);
        driver_spinner.setOnItemSelectedListener(this);
        situation_spinner = (Spinner) findViewById(R.id.situation_spinner);
        situation_spinner.setOnItemSelectedListener(this);

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

        //StopWearService
        //BusProvider.getInstance().unregister(this);
        remoteSensorManager.stopMeasurement();

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
        //BusProvider.getInstance().unregister(this);
        //remoteSensorManager.stopMeasurement();

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

        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "ObdReader");


        //WearCode
        //BusProvider.getInstance().register(this);
        //List<com.github.terementor.drivemeter.data.Sensor> wearsensors = RemoteSensorManager.getInstance(this).getSensors();
        //Log.d(TAG, "Wear" + "");
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
        menu.add(0, START_BOTH, 0, "Start Sensors and OBD");
        menu.add(0, STOP_BOTH, 0, "Stop Sensors and OBD");

        //Create Spinners for Driver and Situation
        situation_spinner = (Spinner) findViewById(R.id.situation_spinner);
        driver_spinner = (Spinner) findViewById(R.id.driver_spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> driveradapter = ArrayAdapter.createFromResource(this,
                R.array.driver_array, android.R.layout.simple_spinner_dropdown_item);
        ArrayAdapter<CharSequence> situationadapter = ArrayAdapter.createFromResource(this,
                R.array.situation_array, android.R.layout.simple_spinner_item);
// Specify the layout to use when the list of choices appears
        driveradapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        situationadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
// Apply the adapter to the spinner
        driver_spinner.setAdapter(driveradapter);
        situation_spinner.setAdapter(situationadapter);
        return true;
    }

    public void onItemSelected(AdapterView<?> driver_spinner, View view,
                               int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
    }

    public void onNothingSelected(AdapterView<?> driver_spinner) {
        // Another interface callback
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case START_LIVE_DATA:
                startLiveData();
                return true;
            case STOP_LIVE_DATA:
                stopLiveData();
                return true;
            case START_SENSORS:
                startsensors();
                return true;
            case STOP_SENSORS:
                stopsensors();
                return true;
            case START_BOTH:
                startLiveData();
                startsensors();
                return true;
            case STOP_BOTH:
                stopLiveData();
                stopsensors();
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
    }

    private void startsensors() {
        Log.d(TAG, "startsensors()");

        outputsensors = true;
        phonetextviewupdate = false;
        watchtextviewupdate = false;
        obdtextviewupdate = false;

        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "ObdReader");
        wakeLock.acquire();


        //Start the Measurement on the watch WearCode
        Log.d(TAG, "Wear" + "");
        remoteSensorManager.startMeasurement(prefs.getString(ConfigActivity.SMARTWATCH_SPEED, "20000"));

        //Register Listener
        sensorManager.registerListener(gyroListener, gyroSensor, Integer.parseInt(prefs.getString(ConfigActivity.SMARTPHONE_SPEED, "0"))); // 200Hz, beim Nexus 5x 400hz
        sensorManager.registerListener(accListener, accSensor, Integer.parseInt(prefs.getString(ConfigActivity.SMARTPHONE_SPEED, "0"))); // 200Hz
        //sensorManager.registerListener(rotListener, rotSensor, Integer.parseInt(prefs.getString(ConfigActivity.SMARTPHONE_SPEED, "0"))); // 100Hz
        sensorManager.registerListener(magListener, magSensor, Integer.parseInt(prefs.getString(ConfigActivity.SMARTPHONE_SPEED, "0"))); // 100Hz


        if (prefs.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, true)) {
            //// TODO: 30.12.2016 Wenn in den Einstellungen nicht "logging" aktiviert ist, stürzt die app ab. Das muss besser programmiert werden -> logging ist nun standard als true

            // Create the CSV Logger
            accCSV = null;
            waccCSV = null;
            gyroCSV = null;
            magCSV = null;
            wgyroCSV = null;
            wmagCSV = null;
            gpsCSV = null;
            rotCSV = null;
            wrotCSV = null;
            obdCSV = null;
            metaCSV = null;
            sensorCSVWriter.mydatabase = null;

            long mils = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss_");

            try {
                detailsCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "details.csv",
                        prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                getString(R.string.default_dirname_full_logging))
                );
                Log.d(TAG, "Create logfile");
            } catch (FileNotFoundException | RuntimeException e) {
                Log.e(TAG, "Can't enable logging to file.", e);
            }

            if (prefs.getString(ConfigActivity.LOGGING_TYPES_KEY, "CSV").equals("CSV")) { //TODO hier könnte sich ne methode lohnen
                try {
                    accCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "acc.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created accCSV logfiles");
                    waccCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "wacc.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created waccCSV logfiles");
                    gyroCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "gyro.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created gyroCSV logfiles");
                    wgyroCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "wgyro.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created wgyroCSV logfiles");
                    magCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "mag.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created magCSV logfiles");
                    wmagCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "wmag.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created wmagCSV logfiles");
                    rotCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "rot.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created rotCSV logfiles");
                    wrotCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "wrot.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created wrotCSV logfiles");
                    gpsCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "gps.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created gpsCSV logfiles");
                    obdCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "obd.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created obdCSV logfiles");
                    metaCSV = new LogCSVWriter(sdf.format(new Date(mils)).toString() + "meta.csv",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging))
                    );
                    Log.d(TAG, "Created metaCSV logfiles");
                } catch (FileNotFoundException | RuntimeException e) {
                    Log.e(TAG, "Can't enable logging to file.", e);
                }
            }
            if (prefs.getString(ConfigActivity.LOGGING_TYPES_KEY, "CSV").equals("SQLite3")) {
                try {
                    sensorCSVWriter = new SensorCSVWriter(sdf.format(new Date(mils)).toString() + "Drivemeter",
                            prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                    getString(R.string.default_dirname_full_logging)));
                } catch (FileNotFoundException | RuntimeException e) {
                    Log.e(TAG, "Can't enable logging to database.", e);
                }


            }

            Log.d(TAG, "Starting sensors..");

            Runnable runnable = new Runnable() {
                /*private final Handler handler = new Handler() {
                    public void handleMessage(Message msg) {
                        String aResponse = msg.getData().getString("message");
                        updateTextView(compass, aResponse);
                        Log.d(TAG, "#" + aResponse);
                    }
                };*/

                public void run() {
                    //TODO get sensor meta data for later CSV

                    DataDeques dataDeques = DataDeques.getInstance();
                    dataDeques.clearWearaccdeque();
                    dataDeques.clearWeargyrodeque();
                    dataDeques.clearWearmagdeque();
                    dataDeques.clearWearrotdeque();
                    gyrodeque.clear();
                    accdeque.clear();
                    magdeque.clear();
                    rotdeque.clear();
                    obddeque.clear();
                    gpsdeque.clear();

                    //Write Sensordetails to CSV
                    writeSensorDetails(gyroSensor, detailsCSV);
                    writeSensorDetails(accSensor, detailsCSV);
                    writeSensorDetails(rotSensor, detailsCSV);
                    writeSensorDetails(magSensor, detailsCSV);

                    int zaehler = 0;

                    //Start Handshake with Watch
                    //remoteSensorManager.sendTime();
                    //Log.d(TAG, "WatchTime " + WatchTime);

                    //Write Sensordata to csv or sql output
                    while (outputsensors) {
                        if (outputsensors2) {

                            if (!alreadyExecuted) {
                                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                                Calendar cal = Calendar.getInstance();
                                metadata.put("PhoneTime", dateFormat.format(cal.getTime()));
                                alreadyExecuted = true;

                                //Getting Driver and Situation choices
                                metadata.put("Driver", driver_spinner.getSelectedItem().toString().substring(7));
                                metadata.put("Situation", situation_spinner.getSelectedItem().toString().substring(10));
                            }
                            if ((!gyrodeque.isEmpty() || !rotdeque.isEmpty() || !accdeque.isEmpty() || !magdeque.isEmpty()) && !phonetextviewupdate) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        watchStatusTextView.setText(getString(R.string.status_phone_saving));
                                    }
                                });
                                phonetextviewupdate = true;
                            }
                            if ((!dataDeques.WearaccdequeisEmpty() || !dataDeques.WeargyrodequeisEmpty() || !dataDeques.WearmagdequeisEmpty() || !dataDeques.WearrotdequeisEmpty()) && !watchtextviewupdate) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        phoneStatusTextView.setText(getString(R.string.status_watch_saving));
                                    }
                                });
                                watchtextviewupdate = true;
                            }
                            if (!obddeque.isEmpty()&& !obdtextviewupdate) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        obdoutputStatusTextView.setText(getString(R.string.status_obdoutput_saving));
                                    }
                                });
                                obdtextviewupdate = true;
                            }


                            if (prefs.getString(ConfigActivity.LOGGING_TYPES_KEY, "CSV").equals("SQLite3")) {
                                //Smartphonesensors write to Database

                                if (!gyrodeque.isEmpty()) {
                                    ContentValues gyrodata = gyrodeque.pollFirst();
                                    sensorCSVWriter.mydatabase.insert("Gyroskop", null, gyrodata);
                                }
                                if (!accdeque.isEmpty()) {
                                    ContentValues accdata = accdeque.pollFirst();
                                    sensorCSVWriter.mydatabase.insert("Accelerometer", null, accdata);
                                }
                                if (!rotdeque.isEmpty()) {
                                    ContentValues rotdata = rotdeque.pollFirst();
                                    sensorCSVWriter.mydatabase.insert("Rotation", null, rotdata);
                                }
                                if (!magdeque.isEmpty()) {
                                    ContentValues magdata = magdeque.pollFirst();
                                    sensorCSVWriter.mydatabase.insert("Magnetic", null, magdata);
                                    Log.d(TAG, "Smartphonesensors are saving " + zaehler);
                                    zaehler++;
                                }
                                //Phone GPS
                                if (!gpsdeque.isEmpty()) {
                                    ContentValues gpsdata = gpsdeque.pollFirst();
                                    sensorCSVWriter.mydatabase.insert("GPS", null, gpsdata);
                                }

                                //Wearsensors write to Database
                                if (!dataDeques.WeargyrodequeisEmpty()) {
                                    ContentValues weargyrodata = dataDeques.pollfromWeargyrodeque();
                                    sensorCSVWriter.mydatabase.insert("WearGyroskop", null, weargyrodata);
                                }
                                if (!dataDeques.WearaccdequeisEmpty()) {
                                    ContentValues wearaccdata = dataDeques.pollfromWearaccdeque();
                                    sensorCSVWriter.mydatabase.insert("WearAccelerometer", null, wearaccdata);
                                }
                                if (!dataDeques.WearrotdequeisEmpty()) {
                                    ContentValues wearrotdata = dataDeques.pollfromWearrotdeque();
                                    sensorCSVWriter.mydatabase.insert("WearRotation", null, wearrotdata);
                                }
                                if (!dataDeques.WearmagdequeisEmpty()) {
                                    ContentValues wearmagdata = dataDeques.pollfromWearmagdeque();
                                    Log.d(TAG, "Weardata is saving" + wearmagdata.toString());
                                    sensorCSVWriter.mydatabase.insert("WearMagnetic", null, wearmagdata);
                                }

                                //OBD SPEED and RPM write to Database
                                if (!obddeque.isEmpty()) {
                                    ContentValues obddata = obddeque.pollFirst();
                                    Log.d(TAG, "Weardata is saving" + obddata.toString());
                                    sensorCSVWriter.mydatabase.insert("OBD", null, obddata);
                                }
                            }
                            if (prefs.getString(ConfigActivity.LOGGING_TYPES_KEY, "CSV").equals("CSV")) {
                                //Smartphonesensors write to CSV
                                if (!gyrodeque.isEmpty()) {
                                    writeSensorData(gyrodeque.pollFirst(), gyroCSV);
                                }
                                if (!accdeque.isEmpty()) {
                                    writeSensorData(accdeque.pollFirst(), accCSV);
                                }
                                if (!rotdeque.isEmpty()) {
                                    writeSensorData(rotdeque.pollFirst(), rotCSV);
                                }
                                if (!magdeque.isEmpty()) {
                                    writeSensorData(magdeque.pollFirst(), magCSV);
                                    ContentValues magdata = magdeque.pollFirst();
                                    Log.d(TAG, "Smartphonesensors are saving " + zaehler);
                                    zaehler++;
                                }
                                //Phone GPS
                                String[] gpstmp = new String[5];
                                if (!gpsdeque.isEmpty()) {
                                    ContentValues gpsdata = gpsdeque.pollFirst();
                                    gpstmp[0] = Long.toString(gpsdata.getAsLong("time"));
                                    gpstmp[1] = Long.toString(gpsdata.getAsLong("systemtime"));
                                    gpstmp[2] = Double.toString(gpsdata.getAsDouble("lat"));
                                    gpstmp[3] = Double.toString(gpsdata.getAsDouble("lon"));
                                    gpstmp[4] = Double.toString(gpsdata.getAsDouble("alt"));
                                    gpsCSV.writestringLineCSV(gpstmp);
                                }

                                //Wearsensors write to Database
                                if (!dataDeques.WeargyrodequeisEmpty()) {
                                    writeSensorData(dataDeques.pollfromWeargyrodeque(), wgyroCSV);
                                }
                                if (!dataDeques.WearaccdequeisEmpty()) {
                                    writeSensorData(dataDeques.pollfromWearaccdeque(), waccCSV);
                                }
                                if (!dataDeques.WearrotdequeisEmpty()) {
                                    writeSensorData(dataDeques.pollfromWearrotdeque(), wrotCSV);
                                }
                                if (!dataDeques.WearmagdequeisEmpty()) {
                                    writeSensorData(dataDeques.pollfromWearmagdeque(), wmagCSV);
                                }

                                //OBD SPEED and RPM write to Database
                                String[] obdtmp = new String[3];
                                if (!obddeque.isEmpty()) {
                                    ContentValues obddata = obddeque.pollFirst();
                                    Log.d(TAG, "Weardata is saving" + obddata.toString());
                                    obdtmp[0] = obddata.getAsString("time");
                                    obdtmp[1] = obddata.getAsString("speed");
                                    obdtmp[2] = obddata.getAsString("rpm");
                                    obdCSV.writestringLineCSV(obdtmp);
                                }
                            }
                        }
                    }
                }
            };
            Thread mythread = new Thread(runnable);
            mythread.start();
        }
    }

    private void stopsensors() {
        //Stop Sensors from Wear
        Long t0 = System.nanoTime();
        remoteSensorManager.stopMeasurement();

        DataDeques dataDeques = DataDeques.getInstance();

        //Stop Sensors from Phone
        sensorManager.unregisterListener(gyroListener, gyroSensor);
        sensorManager.unregisterListener(accListener, accSensor);
        sensorManager.unregisterListener(rotListener, rotSensor);
        sensorManager.unregisterListener(magListener, magSensor);

        while (true) {
            if (gyrodeque.isEmpty() && rotdeque.isEmpty() && accdeque.isEmpty() && magdeque.isEmpty() && dataDeques.WearaccdequeisEmpty() && dataDeques.WeargyrodequeisEmpty() && dataDeques.WearmagdequeisEmpty() && dataDeques.WearrotdequeisEmpty()) {
                setOutputsensorsfalse();

                //write Counters ins DB
                metadata.put("CountGyroskop", gyrocounter);
                metadata.put("CountAccelerometer", acccounter);
                metadata.put("CountMagnetic", magcounter);
                if (prefs.getString(ConfigActivity.LOGGING_TYPES_KEY, "CSV").equals("SQLite3") && sensorCSVWriter.mydatabase != null) {
                    sensorCSVWriter.mydatabase.insert("MetaData", null, metadata);
                }
                if (prefs.getString(ConfigActivity.LOGGING_TYPES_KEY, "CSV").equals("CSV") && metaCSV != null) {
                    String[] tmp = new String[7];
                    tmp[0] = metadata.getAsString("PhoneTime");
                    tmp[1] = "0";
                    tmp[2] = metadata.getAsString("Driver");
                    tmp[3] = metadata.getAsString("Situation");
                    tmp[4] = Long.toString(acccounter);
                    tmp[5] = Long.toString(gyrocounter);
                    tmp[6] = Long.toString(magcounter);
                    metaCSV.writestringLineCSV(tmp);
                    Log.d(TAG, "metaCSV written " + tmp);
                }
                gyrocounter = 0;
                acccounter = 0;
                magcounter = 0;
                alreadyExecuted = false;

                Log.d(TAG, "All data is written");

                if (detailsCSV != null) {
                    detailsCSV.closeLogCSVWriter();
                    Log.d(TAG, "CloseDB");
                }
                if (accCSV != null) {
                    accCSV.closeLogCSVWriter();
                    Log.d(TAG, "CloseaccCSV");
                }
                if (waccCSV != null) {
                    waccCSV.closeLogCSVWriter();
                }
                if (gyroCSV != null) {
                    gyroCSV.closeLogCSVWriter();
                }
                if (wgyroCSV != null) {
                    wgyroCSV.closeLogCSVWriter();
                }
                if (magCSV != null) {
                    magCSV.closeLogCSVWriter();
                }
                if (wmagCSV != null) {
                    wmagCSV.closeLogCSVWriter();
                }
                if (gpsCSV != null) {
                    gpsCSV.closeLogCSVWriter();
                }
                if (rotCSV != null) {
                    rotCSV.closeLogCSVWriter();
                }
                if (wrotCSV != null) {
                    wrotCSV.closeLogCSVWriter();
                }
                if (metaCSV != null) {
                    metaCSV.closeLogCSVWriter();
                }
                if (obdCSV != null) {
                    obdCSV.closeLogCSVWriter();
                }

                if (sensorCSVWriter.mydatabase != null) {
                    sensorCSVWriter.mydatabase.close();
                }

                releaseWakeLockIfHeld();
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException re) {
                Log.e(TAG, re.toString());
            }
        }
        Long t1 = System.nanoTime();
        Log.d(TAG, "Writing took " + (t1 - t0));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                phoneStatusTextView.setText(getString(R.string.status_phone_done));
                watchStatusTextView.setText(getString(R.string.status_watch_done));
                obdoutputStatusTextView.setText(getString(R.string.status_obdoutput_done));
            }
        });
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
        MenuItem startSensors = menu.findItem(START_SENSORS);
        MenuItem stopSensors = menu.findItem(STOP_SENSORS);

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
        if (outputsensors2) {
            startSensors.setEnabled(false);
            stopSensors.setEnabled(true);
            settingsItem.setEnabled(false);
        } else {
            startSensors.setEnabled(true);
            stopSensors.setEnabled(false);
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

    public void writeSensorDetails(Sensor sensor, LogCSVWriter csvfile) {
        if (sensor != null && csvfile != null) {
            String[] details = new String[6];
            details[0] = sensor.getName();
            details[1] = sensor.getVendor();
            details[2] = Integer.toString(sensor.getType());
            details[3] = Float.toString(sensor.getResolution());
            details[4] = Integer.toString(sensor.getMinDelay());
            details[5] = Integer.toString(sensor.getMaxDelay());
            csvfile.writestringLineCSV(details);
        } else {
            Log.e(TAG, "Cant write details to CSV");
        }
    }

    public void writeSensorData(ContentValues cv, LogCSVWriter csvfile) {
        if (csvfile != null) {
            String[] tmp = new String[4];
            tmp[0] = Long.toString(cv.getAsLong("time"));
            tmp[1] = Float.toString(cv.getAsFloat("x"));
            tmp[2] = Float.toString(cv.getAsFloat("y"));
            tmp[3] = Float.toString(cv.getAsFloat("z"));
            csvfile.writestringLineCSV(tmp);
        } else {
            Log.e(TAG, "Cant write data to CSV");
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
