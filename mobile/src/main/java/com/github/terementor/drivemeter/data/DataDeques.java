package com.github.terementor.drivemeter.data;

import android.content.ContentValues;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DataDeques {
    private static final String TAG = "SensorDashboardDataDeques";
    private static final DataDeques INSTANCE = new DataDeques();
    private static int m = 0;
    private Deque<ContentValues> weargyrodeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> wearaccdeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> wearrotdeque = new ConcurrentLinkedDeque<ContentValues>();
    private Deque<ContentValues> wearmagdeque = new ConcurrentLinkedDeque<ContentValues>();

    //Singleton
    private DataDeques() {
    }

    public static DataDeques getInstance() {
        return INSTANCE;
    }

    public ContentValues pollfromWeargyrodeque() {
        return this.weargyrodeque.pollFirst();
    }

    public ContentValues pollfromWearaccdeque() {
        return this.wearaccdeque.pollFirst();
    }

    public ContentValues pollfromWearrotdeque() {
        return this.wearrotdeque.pollFirst();
    }

    public ContentValues pollfromWearmagdeque() {
        return this.wearmagdeque.pollFirst();
    }

    public void addtoWeargyrodeque(ContentValues content) {
        this.weargyrodeque.addLast(content);
    }

    public void addtoWearaccdeque(ContentValues content) {
        //Log.d("DataDeques ",Integer.toString(m) + content.toString());
        //m++;
        this.wearaccdeque.addLast(content);
    }

    public void addtoWearrotdeque(ContentValues content) {
        this.wearrotdeque.addLast(content);
    }

    public void addtoWearmagdeque(ContentValues content) {
        this.wearmagdeque.addLast(content);
    }

    public void clearWearmagdeque() {
        this.wearmagdeque.clear();
    }

    public void clearWearaccdeque() {
        this.wearaccdeque.clear();
    }

    public void clearWearrotdeque() {
        this.wearrotdeque.clear();
    }

    public void clearWeargyrodeque() {
        this.weargyrodeque.clear();
    }

    public Boolean WearmagdequeisEmpty() {
        return this.wearmagdeque.isEmpty();
    }

    public Boolean WearaccdequeisEmpty() {
        return this.wearaccdeque.isEmpty();
    }

    public Boolean WearrotdequeisEmpty() {
        return this.wearrotdeque.isEmpty();
    }

    public Boolean WeargyrodequeisEmpty() {
        return this.weargyrodeque.isEmpty();
    }
}
