package com.github.terementor.drivemeter.events;

import com.github.terementor.drivemeter.data.Sensor;

public class NewSensorEvent {
    private Sensor sensor;

    public NewSensorEvent(Sensor sensor) {
        this.sensor = sensor;
    }

    public Sensor getSensor() {
        return sensor;
    }
}
