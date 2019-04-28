/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.google.wearable.app;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.wearable.view.DelayedConfirmationView;
import android.support.wearable.view.DismissOverlayView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.nio.ByteBuffer;

public class MainActivity extends Activity
        implements DelayedConfirmationView.DelayedConfirmationListener,
        SensorEventListener, CapabilityClient.OnCapabilityChangedListener{
    private static final String TAG = "MainActivity";

    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_REQUEST_CODE = 1;
    private static final int NUM_SECONDS = 5;

    private GestureDetectorCompat mGestureDetector;
    private DismissOverlayView mDismissOverlayView;

    private SensorManager sensorManager;
    private boolean record;
    private int seconds;
    private int accCount = 0, rotCount = 0;
    private long lastUpdate;
    private long lastUpdateR;
    private long startTime;
    private long otherTime;
    private long systemTime;
    private ArrayList<ArrayList<Float>> accList = new ArrayList<ArrayList<Float>>();
    private ArrayList<ArrayList<Float>> rotList = new ArrayList<ArrayList<Float>>();
    static final int COUNT = 32;
    static float[] AcceleratorBuffer = new float[4*COUNT];
    static float[] GyroBuffer = new float[4*COUNT];
    private DataClient mDataClient;
    private Node phone;
    private static final String
            WISDM_CAPABILITY = "phone";
    Set<Node> nodes;
    ByteArrayOutputStream output;

    private boolean last = false;
    private boolean lastacc = false;
    private boolean lastrot = false;
    private Vibrator vibrator;
    private long[] vibrationPattern = {0, 500, 50, 300};

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        Log.d("createstart", "" + System.currentTimeMillis());
        setContentView(R.layout.main_activity);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        record = false;
        Log.d(TAG, " START ");
        mDismissOverlayView = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
        mDismissOverlayView.setIntroText(R.string.intro_text);
        mDismissOverlayView.showIntroIfNecessary();
        mGestureDetector = new GestureDetectorCompat(this, new LongPressListener());
        seconds = 1;
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);
        lastUpdate = 0;
        lastUpdateR = 0;
        Log.d("createend", "" + System.currentTimeMillis());
    }
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        Log.d("capabilitychangeStart", "" + System.currentTimeMillis());
        Log.d(TAG, "onCapabilityChanged(): " + capabilityInfo);

        phone = pickBestNodeId(capabilityInfo.getNodes());
        //verifyNodeAndUpdateUI();
        Log.d("capabilitychangeEnd", "" + System.currentTimeMillis());
    }
    private Node pickBestNodeId(Set<Node> nodes) {
        Log.d("picknodeStart", "" + System.currentTimeMillis());
        Log.d(TAG, "pickBestNodeId(): " + nodes);

        Node bestNodeId = null;
        // Find a nearby node/phone or pick one arbitrarily. Realistically, there is only one phone.
        for (Node node : nodes) {
            bestNodeId = node;
        }
        Log.d("picknodeEnd", "" + System.currentTimeMillis());
        return bestNodeId;
    }
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event) || super.dispatchTouchEvent(event);
    }

    private class LongPressListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent event) {
            mDismissOverlayView.show();
        }
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        //Log.d("sensor", "Value: " + Integer.toString(event.sensor.getType()));
        //systemTime = System.currentTimeMillis();
        Log.d("sensorchange", "" + System.currentTimeMillis());
        if(!record)
            return;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event);
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            getGyroscope(event);
        }
    }
    private void getAccelerometer(SensorEvent event) {
        Log.d("hit", "" + System.currentTimeMillis());
        long difference = System.currentTimeMillis() - otherTime;
        //Log.d("acceleration", "" + record + "  " + difference + " " + lastUpdate + " " + System.currentTimeMillis());
        if (!record || ((difference - lastUpdate) <= 10 && lastUpdate != 0) || (difference < 1000)) {
            //otherTime = systemTime;
            //Log.d("acceleration", "hereerrerrr");
            return;
        }
        Log.d("recording", "" + System.currentTimeMillis());
        //Log.d("acceleration", "here2222");
        //Log.d("difference",  difference + " " + otherTime);
        accCount++;
        lastUpdate = difference;
        //otherTime = systemTime;
        //accList.add(new ArrayList<> (Arrays.asList(x, y, z, (float) difference)));
        AcceleratorBuffer[4*(accCount-1)] = event.values[0];
        AcceleratorBuffer[4*(accCount-1)+1] = event.values[1];
        AcceleratorBuffer[4*(accCount-1)+2] = event.values[2]-9.6f;
        AcceleratorBuffer[4*(accCount-1)+3] = (float)difference;
        Log.d("recorded", "" + System.currentTimeMillis());
        if (accCount % COUNT == 0) {
            Log.d("sending", "" + System.currentTimeMillis());
            Wearable.getMessageClient(this).sendMessage(
                    phone.getId(), "/wacc", FloatArray2ByteArray(AcceleratorBuffer));
            AcceleratorBuffer = new float[4*COUNT];
            accCount = 0;
            Log.d("sent", "" + System.currentTimeMillis());
        }
    }
    private void getGyroscope(SensorEvent event) {
        long actualTimeR = event.timestamp;
        long difference = System.currentTimeMillis() - otherTime;
        if (!record || ((difference - lastUpdateR) <= 10 && lastUpdateR != 0) || (difference < 1000)) {
            return;
        }
        rotCount++;
        lastUpdateR = difference;
        //rotList.add(new ArrayList<> (Arrays.asList(x, y, z, (float) difference)));
        //Log.d("rotation", "Value: " + Float.toString(x) + " " + Float.toString(y) + " " + Float.toString(z) + " " + Float.toString((float)(System.currentTimeMillis() - startTime)) + " rotcount: " + rotCount);
        /*if (rotCount > 100) {
            Log.d("accsize", " sent ");
            sendRot();
        }*/
        GyroBuffer[4*(rotCount-1)] = event.values[0];
        GyroBuffer[4*(rotCount-1)+1] = event.values[1];
        GyroBuffer[4*(rotCount-1)+2] = event.values[2];
        GyroBuffer[4*(rotCount-1)+3] = (float)difference;
        if (rotCount % COUNT == 0) {
            Wearable.getMessageClient(this).sendMessage(
                    phone.getId(), "/wgyro", FloatArray2ByteArray(GyroBuffer));
            GyroBuffer = new float[4*COUNT];
            //GyroBuffer.clear();
            rotCount = 0;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    @Override
    protected void onResume() {
        super.onResume();
        // register this class as a listener for the orientation and
        // accelerometer sensors
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
        Wearable.getCapabilityClient(this).addListener(this, WISDM_CAPABILITY);
        mDataClient = Wearable.getDataClient(this);
        checkIfPhoneHasApp();
    }
    private void checkIfPhoneHasApp() {
        Log.d(TAG, "checkIfPhoneHasApp()");

        Task<CapabilityInfo> capabilityInfoTask = Wearable.getCapabilityClient(this)
                .getCapability(WISDM_CAPABILITY, CapabilityClient.FILTER_ALL);
        Task<CapabilityInfo> addLocal = Wearable.getCapabilityClient(this)
                .getCapability(WISDM_CAPABILITY, CapabilityClient.FILTER_ALL);
        capabilityInfoTask.addOnCompleteListener(new OnCompleteListener<CapabilityInfo>() {
            @Override
            public void onComplete(Task<CapabilityInfo> task) {

                if (task.isSuccessful()) {
                    Log.d(TAG, "Capability request succeeded.");
                    CapabilityInfo capabilityInfo = task.getResult();
                    phone = pickBestNodeId(capabilityInfo.getNodes());
                    Log.d("Nodes: ", "Got node: " + phone);
                } else {
                    Log.d(TAG, "Capability request failed to return any results.");
                }

                //verifyNodeAndUpdateUI();
            }
        });
    }
    @Override
    protected void onPause() {
        // unregister listener
        super.onPause();
        Wearable.getCapabilityClient(this).removeListener(this, WISDM_CAPABILITY);
        sensorManager.unregisterListener(this);
    }
    /**
     * Handles the button to launch a notification.
     */
    public void showNotification(View view) {
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_title))
                .setSmallIcon(R.drawable.ic_launcher)
                .addAction(R.drawable.ic_launcher,
                        getText(R.string.action_launch_activity),
                        PendingIntent.getActivity(this, NOTIFICATION_REQUEST_CODE,
                                new Intent(this, GridExampleActivity.class),
                                PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        finish();
    }


    /**
     * Handles the button press to finish this activity and take the user back to the Home.
     */
    public void onFinishActivity(View view) {
        setResult(RESULT_OK);
        finish();
    }

    /**
     * Handles the button to start a DelayedConfirmationView timer.
     */
    public void onStartTimer(View view) {
        seconds = 6;
        timed(view);
    }
    public void onStartTimer1(View view) {
        seconds = 61;
        timed(view);
    }
    public void onStartTimer5(View view) {
        seconds = 301;
        timed(view);
    }
    public void onStartTimer10(View view) {
        seconds = 601;
        timed(view);
    }
    public void onStartTimer15(View view) {
        seconds = 901;
        timed(view);
    }
    public void onStartStopTimer(View view){
        seconds = 10801;
        if(!record)
            timed(view);
        else
            onTimerFinished(view);
    }
    public void timed(View view){
        DelayedConfirmationView delayedConfirmationView = (DelayedConfirmationView)
                findViewById(R.id.timer);
        vibrator.vibrate(vibrationPattern, -1);
        accList.clear();
        Log.d("system", "accList cleared, size: " + accList.size());
        rotList.clear();
        accCount = 0;
        rotCount = 0;
        AcceleratorBuffer = new float[4*COUNT];
        GyroBuffer = new float[4*COUNT];
        delayedConfirmationView.setTotalTimeMs(seconds * 1000);
        delayedConfirmationView.setListener(this);
        delayedConfirmationView.start();
        scroll(View.FOCUS_DOWN);
        startTime = System.currentTimeMillis();
        otherTime = System.currentTimeMillis();
        record = true;
        Log.d("system", "Sent message to " + phone);
        Wearable.getMessageClient(this).sendMessage(
                phone.getId(), "/seconds", ByteBuffer.allocate(4).putInt(seconds).array());
    }

    @Override
    public void onTimerFinished(View v) {
        Log.d(TAG, "onTimerFinished is called.");
        scroll(View.FOCUS_UP);
        record = false;
        lastUpdate = 0;
        lastUpdateR = 0;
        last = true;
        vibrator.vibrate(vibrationPattern, -1);
        Wearable.getMessageClient(this).sendMessage(
                phone.getId(), "/wacc", FloatArray2ByteArray(AcceleratorBuffer));
        AcceleratorBuffer = new float[4*COUNT];
        accCount = 0;
        Wearable.getMessageClient(this).sendMessage(
                phone.getId(), "/wgyro", FloatArray2ByteArray(GyroBuffer));
        GyroBuffer = new float[4*COUNT];
        rotCount = 0;
        Log.d("system", "Sent end message to " + phone);
        Wearable.getMessageClient(this).sendMessage(
                phone.getId(), "/end", ByteBuffer.allocate(4).putInt(-1).array());
    }
    public void sendStringAsEmail(String content){
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");

        // send it to yourself for testing
        // i.putExtra(Intent.EXTRA_EMAIL , new
        // String[]{"myemailaddress@fortesting.com"});

        i.putExtra(Intent.EXTRA_SUBJECT, "Sent for WISDM lab");
        i.putExtra(Intent.EXTRA_TEXT, content);

        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(MainActivity.this,
                    "There are no email clients installed.",
                    Toast.LENGTH_SHORT).show();
        }
    }
    public static byte[] FloatArray2ByteArray(float[] values){
        Log.d("floats2bytesStart", "" + System.currentTimeMillis());
        ByteArrayOutputStream bas = new ByteArrayOutputStream();
        DataOutputStream ds = new DataOutputStream(bas);
        for (float f : values) {
            try {
                ds.writeFloat(f);
            }catch(IOException ie) {
                ie.printStackTrace();
            }
        }
        byte[] bytes = bas.toByteArray();
        Log.d("floats2bytesEnd", "" + System.currentTimeMillis());
        return bytes;
    }
    @Override
    public void onTimerSelected(View v) {
        Log.d(TAG, "onTimerSelected is called.");
        scroll(View.FOCUS_UP);
    }

    private void scroll(final int scrollDirection) {
        final ScrollView scrollView = (ScrollView) findViewById(R.id.scroll);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(scrollDirection);
            }
        });
    }
}
