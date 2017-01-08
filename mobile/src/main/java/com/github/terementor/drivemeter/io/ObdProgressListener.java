package com.github.terementor.drivemeter.io;

public interface ObdProgressListener {

    void stateUpdate(final ObdCommandJob job);

}