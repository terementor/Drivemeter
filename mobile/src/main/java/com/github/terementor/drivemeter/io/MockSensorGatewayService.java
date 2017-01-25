package com.github.terementor.drivemeter.io;

import android.util.Log;

import com.github.terementor.drivemeter.activity.MainActivity;
import com.github.terementor.drivemeter.io.ObdCommandJob.ObdCommandJobState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * This service is primarily responsible for establishing and maintaining a
 * permanent connection between the device where the application runs and a more
 * OBD Bluetooth interface.
 * <p/>
 * Secondarily, it will serve as a repository of ObdCommandJobs and at the same
 * time the application state-machine.
 */
/*public class MockSensorGatewayService extends AbstractGatewaySensorService {

    private static final String TAG = MockSensorGatewayService.class.getName();

    public void startService() {
        Log.d(TAG, "Starting " + this.getClass().getName() + " service..");

        SensorqueueCounter = 0L;
        Log.d(TAG, "Initialization jobs queued.");

        isRunning = true;
    }



     //Runs the queue until the service is stopped

    protected void executeQueue() {
        Log.d(TAG, "Executing queue..");
        while (!Thread.currentThread().isInterrupted()) {
            ObdCommandJob job = null;
            try {
                job = SensorjobsQueue.take();

                Log.d(TAG, "Taking job[" + job.getId() + "] from queue..");

                if (job.getState().equals(ObdCommandJobState.NEW)) {
                    Log.d(TAG, "Job state is NEW. Run it..");
                    job.setState(ObdCommandJobState.RUNNING);
                    Log.d(TAG, job.getCommand().getName());
                    job.getCommand().run(new ByteArrayInputStream("41 00 00 00>41 00 00 00>41 00 00 00>".getBytes()), new ByteArrayOutputStream());
                } else {
                    Log.e(TAG, "Job state was not new, so it shouldn't be in queue. BUG ALERT!");
                }
            } catch (InterruptedException i) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
                if (job != null) {
                    job.setState(ObdCommandJobState.EXECUTION_ERROR);
                }
                Log.e(TAG, "Failed to run command. -> " + e.getMessage());
            }

            if (job != null) {
                Log.d(TAG, "Job is finished.");
                job.setState(ObdCommandJobState.FINISHED);
                final ObdCommandJob job2 = job;
                ((MainActivity) ctx).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((MainActivity) ctx).stateUpdate(job2);
                    }
                });

            }
        }
    }



     //Stop OBD connection and queue processing.

    public void stopService() {
        Log.d(TAG, "Stopping service..");

        notificationManager.cancel(NOTIFICATION_ID);
        SensorjobsQueue.clear();
        isRunning = false;

        // kill service
        stopSelf();
    }

}*/
