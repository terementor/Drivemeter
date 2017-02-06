package com.github.terementor.drivemeter.net;

import android.os.AsyncTask;
import android.util.Log;

import com.github.terementor.drivemeter.activity.MainActivity;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

/**
 * Created by admin on 30.01.2017.
 */



public class NTPTime extends AsyncTask<Integer, Void, Long> {
    //NTP server list: http://tf.nist.gov/tf-cgi/servers.cgi
    public static final String TIME_SERVER = "time-a.nist.gov";
    public static final String LOCAL_SERVER = "192.168.43.1";
    private static final String TAG = MainActivity.class.getName();

    //public interface NTPTimeInterface2 {
    //    void processFinish(Long output);
    //}

    public NTPTimeInterface delegate = null;

    //public NTPTime(NTPTimeInterface2 delegate) {
    //    this.delegate = delegate;
    //}
    protected void onPostExecute(Long result) {
        delegate.processFinish(result);
    }


    @Override
    protected Long doInBackground(Integer... urls) {
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
        try {
            //timeInfo = timeClient.getTime(inetAddress);
            timeInfo2 = timeClient.getTime(inetAddress2, 40000);
        } catch (IOException er) {
            Log.e(TAG, er.toString());
        }


        //long returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();   //server time
        long returnTime = timeInfo2.getMessage().getTransmitTimeStamp().getTime();   //server time
        long sysreturnTime = timeInfo2.getReturnTime();   //local device time

        long timediff = sysreturnTime - returnTime;
        Date time = new Date(returnTime);

        return timediff;
    }
}
