package com.example.phoneapp;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.WindowManager;
import android.os.Handler;
import android.net.Uri;
import android.widget.Toast;

import com.google.android.gms.common.util.IOUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements SensorEventListener,
        MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener,
        CapabilityClient.OnCapabilityChangedListener {
    private SensorManager sensorManager;
    private boolean record;
    private int seconds;
    private int accCount = 0, rotCount = 0;
    private boolean recievedAcc = false;
    private boolean recievedRot = false;
    private long lastUpdate = 0;
    private long lastUpdateR = 0;
    private long systemTime;
    private long otherTime;
    private long lastRecord = 0;
    private static ArrayList<ArrayList<Float>> accList = new ArrayList<ArrayList<Float>>();
    private static ArrayList<ArrayList<Float>> rotList = new ArrayList<ArrayList<Float>>();
    private ArrayList<ArrayList<Float>> waccList = new ArrayList<ArrayList<Float>>();
    private ArrayList<ArrayList<Float>> wrotList = new ArrayList<ArrayList<Float>>();
    Set<Node> nodes;
    List<Node> allNodes;
    private String watchId;
    private String watchString;
    private static final String
            WISDM_CAPABILITY = "watch";
    private float acc[];
    private float rot[];
    private boolean endRecieved = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        record = false;
        seconds = 1;

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);
    }
    public void findDevices(){
        Task<CapabilityInfo> capabilityInfoTask = Wearable.getCapabilityClient(this)
                .getCapability(WISDM_CAPABILITY, CapabilityClient.FILTER_REACHABLE);

        capabilityInfoTask.addOnCompleteListener(new OnCompleteListener<CapabilityInfo>() {
            @Override
            public void onComplete(Task<CapabilityInfo> task) {

                if (task.isSuccessful()) {
                    CapabilityInfo capabilityInfo = task.getResult();
                    nodes = capabilityInfo.getNodes();
                    Log.d("Nodes: ", "Got nodes: " + nodes);
                } else {
                    Log.d("Nodes: ", "Capability request failed to return any results.");
                }

            }
        });
    }
    private void findAllWearDevices() {
        Log.d("system" , "findAllWearDevices()");

        Task<List<Node>> NodeListTask = Wearable.getNodeClient(this).getConnectedNodes();

        NodeListTask.addOnCompleteListener(new OnCompleteListener<List<Node>>() {
            @Override
            public void onComplete(Task<List<Node>> task) {

                if (task.isSuccessful()) {
                    allNodes = task.getResult();
                    Log.d("nodes", "All node request succeeded, nodes: " + allNodes);

                } else {
                    Log.d("nodes", "Node request failed to return any results.");
                }

                //verifyNodeAndUpdateUI();
            }
        });
    }
    @Override
    protected void onResume() {
        Log.d("system ", "onResume()");
        super.onResume();
        //Wearable.getDataClient(this).addListener(this);
        Wearable.getCapabilityClient(this).addListener(this, WISDM_CAPABILITY);
        Wearable.getMessageClient(this).addListener(messageEvent ->
                handleMessage(messageEvent));
        // Initial request for devices with our capability, aka, our Wear app installed.
        findDevices();

        // Initial request for all Wear devices connected (with or without our capability).
        // Additional Note: Because there isn't a listener for ALL Nodes added/removed from network
        // that isn't deprecated, we simply update the full list when the Google API Client is
        // connected and when capability changes come through in the onCapabilityChanged() method.
        findAllWearDevices();
    }
    public void handleMessage(MessageEvent event){
        watchId = event.getSourceNodeId();
        if((event.getPath() != "/seconds") || (seconds != ByteBuffer.wrap(event.getData()).getInt()))
            switch (event.getPath()) {
                case "/seconds":
                   if(record)
                       break;
                   endRecieved = false;
                    //lastRecord = System.currentTimeMillis();
                    accList.clear();
                    rotList.clear();
                    waccList.clear();
                    wrotList.clear();
                  ByteBuffer wrapped = ByteBuffer.wrap(event.getData());
                    seconds = wrapped.getInt();
                    Log.d("message:", "message recieved---------------------------------------------------------------! " + seconds);
                    startRecording();
                    break;
                case "/wacc":
                    for(int i = 0; i < 32; i++) {
                        if(ByteArray2FloatArray(event.getData())[i * 4 + 3] > 0.1)
                            waccList.add(new ArrayList<>(Arrays.asList(ByteArray2FloatArray(event.getData())[i * 4],
                                    ByteArray2FloatArray(event.getData())[i * 4 + 1],
                                    ByteArray2FloatArray(event.getData())[i * 4 + 2],
                                    ByteArray2FloatArray(event.getData())[i * 4 + 3]-1000)));
                    }
                    Log.d("message:", "message recieved----------------acc");
                    break;
                case "/wgyro":
                    for(int i = 0; i < 32; i++) {
                        if(ByteArray2FloatArray(event.getData())[i * 4 + 3] > 0.1)
                            wrotList.add(new ArrayList<>(Arrays.asList(ByteArray2FloatArray(event.getData())[i * 4],
                                    ByteArray2FloatArray(event.getData())[i * 4 + 1],
                                    ByteArray2FloatArray(event.getData())[i * 4 + 2],
                                    ByteArray2FloatArray(event.getData())[i * 4 + 3]-1000)));
                    }
                    Log.d("message:", "message recieved----------------rot");
                    break;
                case "/end":
                    //if (recievedRot && recievedAcc) {
                    if(endRecieved)
                        break;
                    endRecieved = true;
                    Log.d("message:", "message recieved----------------end");
                        writeDataLineByLine();
                        record = false;
                        seconds = 0;
                        recievedRot = false;
                        recievedAcc = false;
                        lastUpdate = 0;
                        lastUpdateR = 0;
                        acc = null;
                        rot = null;
                    //}
                default:
            }
    }
    public void startRecording(){
        record = true;
        //seconds = 20;
        //startTime = System.currentTimeMillis();
        otherTime = System.currentTimeMillis();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                record = false;
                //Log.d("counters", "acceleration: " + Integer.toString(accCount) + " rotation: " + Integer.toString(rotCount));
                seconds = 0;
                //Log.d("counters", "acceleration: " + Integer.toString(accCount) + " rotation: " + Integer.toString(rotCount) + " entries: " + accList.size() + " " + rotList.size());
                //for(int i = 0; i< accList.size(); i++){
                //    List<Float> list = accList.get(i);
                    //Log.d("accArray", "x " + list.get(0) + " y " + list.get(1) + " z " + list.get(2) + " time " + list.get(3)+ " spot " + i);
                //}
                //for(int i = 0; i< rotList.size(); i++){
                //    List<Float> list = rotList.get(i);
                    //Log.d("rotArray", "x " + list.get(0) + " y " + list.get(1) + " z " + list.get(2) + " time " + list.get(3)+ " spot " + i);
                //}
                accCount = 0;
                rotCount = 0;
            }
        }, seconds * 1000);
    }
    public static float[] ByteArray2FloatArray(byte[] buffer){
        ByteArrayInputStream bas = new ByteArrayInputStream(buffer);
        DataInputStream ds = new DataInputStream(bas);
        float[] fArr = new float[buffer.length / 4];  // 4 bytes per float
        for (int i = 0; i < fArr.length; i++)
        {
            try{fArr[i] = ds.readFloat();}
            catch(IOException ie){
                ie.printStackTrace();
            }
        }
        return fArr;
    }
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        Log.d("system", "onCapabilityChanged(): " + capabilityInfo);

        nodes = capabilityInfo.getNodes();
        Log.d("Nodes: ", "Got nodes: " + nodes);
        // Because we have an updated list of devices with/without our app, we need to also update
        // our list of active Wear devices.
        //findAllWearDevices();

        //verifyNodeAndUpdateUI();
    }
    public void buttonClick(View view){
        startRecording();
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        ByteBuffer wrapped = ByteBuffer.wrap(messageEvent.getData());
        //seconds = wrapped.getInt();
        //Log.d("message:", "message recieved! " + seconds);
    }
    /*public float[] assettofloat(Asset asset, float[] floats){
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        // convert asset into a file descriptor and block until it's ready
        try {
            InputStream assetInputStream =
                    Tasks.await(Wearable.getDataClient(this).getFdForAsset(asset))
                            .getInputStream();

            if (assetInputStream == null) {
                Log.w("press F", "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            try{
                return byteToFloat(IOUtils.toByteArray(assetInputStream));
            }catch (IOException ie){
             ie.printStackTrace();
            }
        }catch(ExecutionException ie){
            ie.printStackTrace();
        }catch(InterruptedException ie){
            ie.printStackTrace();
        }
        Log.w("press F", "didnt function right");
        return floats;
    }*/
    //@Override
    public void onDataChanged(DataEventBuffer data){
        for (DataEvent event : data) {
            /*if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals("/acc")) {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Asset accAsset = dataMapItem.getDataMap().getAsset("watch_acceleration");
                acc = assettofloat(accAsset, acc);
                // Do something with the bitmap
            }
            else if(event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals("/rot")){
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Asset rotAsset = dataMapItem.getDataMap().getAsset("watch_acceleration");
                rot = assettofloat(rotAsset, rot);
            }*/
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals("/watch")) {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Asset accAsset = dataMapItem.getDataMap().getAsset("watch");
                watchString = accAsset.toString();
                sendStringAsEmail(watchString);
                Log.d("accArray", "first-------------------------------------------------------------------------------------------------------------------------");
                // Do something with the bitmap
            }
        }
        /*for(int i = 0; i < data.getCount(); i++)
        {
            DataItem item = data.get(i).getDataItem();
            String path = item.getUri().getPath();
            Log.d("message:", path);
            switch (path){
                case "/acc":
                    Log.d("message:", "acc recieved from watch!");
                    DataMap map = DataMapItem.fromDataItem(item).getDataMap();
                    if (acc == null || !recievedAcc) {
                        acc = map.getFloatArray("ACC_KEY");
                        Log.d("accArray", "first-------------------------------------------------------------------------------------------------------------------------");
                    }else {
                        acc = concatenate(acc, map.getFloatArray("ACC_KEY"));
                        Log.d("accArray", "concat-----------------------------------------------------------------------------------------------------------------------------");
                    }
                    recievedAcc = true;
                    Log.d("message:", "acc size: " + acc.length);
                    Log.d("accArray", "[0]" + acc[0] + "[1] " + acc[1] + " [2] " + acc[2] + " [3] " + acc[3]);
                    Log.d("accArray", "[4]" + acc[4] + "[5] " + acc[5] + " [6] " + acc[6] + " [7] " + acc[7] + " [8] " + acc[8] + " [last] " + acc[acc.length-1]);
                    Log.d("accArray", "----------------------------------------------------------------------------------------------------------------------------------");
                    break;
                case "/rot":
                    Log.d("message:", "rot recieved from watch!");
                    //float rot[] = byteToFloat2(item.getData());
                    DataMap map2 = DataMapItem.fromDataItem(item).getDataMap();
                    if (rot == null || !recievedRot) {
                        rot = map2.getFloatArray("ROT_KEY");
                        Log.d("rotArray", "first-----------------------------------------------------------------------------------------------------------------------------");
                    }else {
                        rot = concatenate(rot, map2.getFloatArray("ROT_KEY"));
                        Log.d("rotArray", "concat-----------------------------------------------------------------------------------------------------------------------------");
                    }
                    Log.d("message:", "rot size: " + rot.length);
                    Log.d("rotArray", "----------------------------------------------------------------------------------------------------------------------------------");
                    recievedRot = true;
                    break;
                default:
            }
        //if(recievedAcc && recievedRot){
        //    writeDataLineByLine("WISDM", acc, rot);
        //}
        }*/

    }
    public void writeDataLineByLine()
    {
        // first create file object for file placed at location
        // specified by filepath
        File file = new File(this.getFilesDir(),"dir");
        int counter = 2;
        while(file.exists()){
            file = new File(this.getFilesDir(),"dir"+counter);
            counter++;
        }
        file.mkdir();

        String contentWA = "wax,way,waz,time";
        String contentWR = "wrx,wry,wrz,time";
        String contentPA = "pax,pay,paz,time";
        String contentPR = "prx,pry,prz,time";
        String header = "wax,way,waz,wrx,wry,wrz,pax,pay,paz,prx,pry,prz,time";
        int j = 0;
        /*float[] rotPhone = new float[rotList.size()*rotList.get(0).size()];
        for (ArrayList<Float> f : rotList) {
            for(Float g : f)
                rotPhone[j++] = (g != null ? g : Float.NaN); // Or whatever default you want.
        }*/
        removeEmptyEntries(waccList);
        removeEmptyEntries(wrotList);
        removeEmptyEntries(accList);
        removeEmptyEntries(rotList);
        File waccFile = new File(file, "watch_acceleration.csv");
        try (PrintWriter pw = new PrintWriter(waccFile)) {
            pw.println(contentWA);
            for (ArrayList<Float> wacc : waccList) {
                for (int i = 0; i < wacc.size()-1; i++) {
                    pw.print(wacc.get(i));
                    pw.print(',');
                }
                pw.print(wacc.get(wacc.size()-1));
                pw.println();
            }
        }catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        File wrotFile = new File(file, "watch_rotation.csv");
        try (PrintWriter pw = new PrintWriter(wrotFile)) {
            pw.println(contentWR);
            for (ArrayList<Float> wrot : wrotList) {
                for (int i = 0; i < wrot.size()-1; i++) {
                    pw.print(wrot.get(i));
                    pw.print(',');
                }
                pw.print(wrot.get(wrot.size()-1));
                pw.println();
            }
        }catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        File paccFile = new File(file, "phone_acceleration.csv");
        try (PrintWriter pw = new PrintWriter(paccFile)) {
            pw.println(contentPA);
            for (ArrayList<Float> pacc : accList) {
                for (int i = 0; i < pacc.size()-1; i++) {
                    pw.print(pacc.get(i));
                    pw.print(',');
                }
                pw.print(pacc.get(pacc.size()-1));
                pw.println();
            }
        }catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        File protFile = new File(file, "phone_rotation.csv");
        try (PrintWriter pw = new PrintWriter(protFile)) {
            pw.println(contentPR);
            for (ArrayList<Float> prot : rotList) {
                for (int i = 0; i < prot.size()-1; i++) {
                    pw.print(prot.get(i));
                    pw.print(',');
                }
                pw.print(prot.get(prot.size()-1));
                pw.println();
            }
        }catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Log.d("system", "done------------------------------------------------------------------------------------------------------------");
        File csv = new File(file, "wisdm_recording.csv");
        try(PrintWriter pw = new PrintWriter(csv)){
            pw.println(header);
            int waIndex, wrIndex, paIndex;
            for(ArrayList<Float> flArr : rotList){
                waIndex = search(waccList,flArr.get(3));
                wrIndex = search(wrotList,flArr.get(3));
                paIndex = search(accList, flArr.get(3));
                for (int i = 0; i<waccList.get(waIndex).size()-1; i++){
                    pw.print(waccList.get(waIndex).get(i));
                    pw.print(',');
                }for(int i = 0; i<wrotList.get(wrIndex).size()-1; i++){
                    pw.print(wrotList.get(wrIndex).get(i));
                    pw.print(',');
                }for(int i = 0; i<accList.get(paIndex).size()-1; i++){
                    pw.print(accList.get(paIndex).get(i));
                    pw.print(',');
                }for (int i = 0; i < flArr.size()-1; i++) {
                    pw.print(flArr.get(i));
                    pw.print(',');
                }
                pw.print(flArr.get(flArr.size()-1));
                pw.println();
            }
        }catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        File csv2 = new File(file, "wisdm_recording_interpolated.csv");
        try(PrintWriter pw = new PrintWriter(csv2)){
            pw.println(header);
            int waIndex, paIndex, prIndex;
            for(ArrayList<Float> flArr : wrotList){
                waIndex = search(waccList,flArr.get(3));
                paIndex = search(accList, flArr.get(3));
                prIndex = search(rotList, flArr.get(3));
                for (int i = 0; i<waccList.get(waIndex).size()-1; i++){
                    pw.print(waccList.get(waIndex).get(i));
                    pw.print(',');
                }for(int i = 0; i<flArr.size()-1; i++){
                    pw.print(flArr.get(i));
                    pw.print(',');
                }for(int i = 0; i<accList.get(paIndex).size()-1; i++){
                    pw.print(accList.get(paIndex).get(i));
                    pw.print(',');
                }for (int i = 0; i < rotList.get(prIndex).size()-1; i++) {
                    pw.print(rotList.get(prIndex).get(i));
                    pw.print(',');
                }
                pw.print(flArr.get(flArr.size()-1));
                pw.println();
            }
        }catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Log.d("system", "done2222--------------------------------------------------------------------------------------------------------");
        Toast.makeText(MainActivity.this,
                "Saved files successfully.",
                Toast.LENGTH_LONG).show();
        //sendStringAsEmail(content);
        //writeFilesOnInternalStorage(this, contentWA, contentWR, contentPA, contentPR);
    }
    public void sendStringAsEmail(String content){
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");

        // send it to yourself for testing
         i.putExtra(Intent.EXTRA_EMAIL , new
         String[]{"mriadzaky@fordham.edu"});

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
    public void removeEmptyEntries(ArrayList<ArrayList<Float>> arr){
        for(int i = 0; i < arr.size(); i++){
            if(arr.get(i).get(3) < 0.1)
                arr.remove(i);
        }
    }
    public static int getClosestIndexByTime(ArrayList<ArrayList<Float>> arr, float time){
        float distance = Math.abs(arr.get(0).get(3) - time);
        int index = 0;
        for(int i = 0; i < (arr.size()); i++){
            float cdistance = Math.abs(arr.get(i).get(3) - time);
            if(cdistance < distance){
                index = i;
                distance = cdistance;
            }
        }
        //Log.d("time", " " + time + " distance " + distance);
        //Log.d("counting", "i = " + (index-3)/4 + " index = " + index + " arr.length, /4:" + arr.length + " " + arr.length/4);
        return (index);
    }
    public void writeFilesOnInternalStorage(Context mcoContext, String sBodyWA, String sBodyWR, String sBodyPA, String sBodyPR){
        File file = new File(mcoContext.getFilesDir(),"dir");
        int counter = 2;
        while(file.exists()){
            file = new File(mcoContext.getFilesDir(),"dir"+counter);
            counter++;
        }
        file.mkdir();
        try{
            File gpxfile = new File(file, "watch_acceleration.csv");
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(sBodyWA);
            writer.flush();
            writer.close();
            Log.d("filesystem", ""+mcoContext.getFilesDir());
        }catch (Exception e){
            e.printStackTrace();
        }
        try{
            File gpxfile = new File(file, "watch_rotation.csv");
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(sBodyWR);
            writer.flush();
            writer.close();
            Log.d("filesystem", ""+mcoContext.getFilesDir());
        }catch (Exception e){
            e.printStackTrace();
        }
        try{
            File gpxfile = new File(file, "phone_acceleration.csv");
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(sBodyPA);
            writer.flush();
            writer.close();
            Log.d("filesystem", ""+mcoContext.getFilesDir());
        }catch (Exception e){
            e.printStackTrace();
        }
        try{
            File gpxfile = new File(file, "phone_rotation.csv");
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(sBodyPR);
            writer.flush();
            writer.close();
            Log.d("filesystem", ""+mcoContext.getFilesDir());
        }catch (Exception e){
            e.printStackTrace();
        }
        Toast.makeText(MainActivity.this,
                "Files written to directory " + mcoContext.getFilesDir(),
                Toast.LENGTH_LONG).show();
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        //Log.d("sensor", "Value: " + Integer.toString(event.sensor.getType()));

        systemTime = System.currentTimeMillis();
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event);
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            getGyroscope(event);
        }
    }
    private void getAccelerometer(SensorEvent event) {
        //float[] values = event.values;
        // Movement
        //float x = values[0];
        //float y = values[1];
        //float z = values[2] - (9.6f);

        //float accelationSquareRoot = (x * x + y * y + z * z)
        //      / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);

        long difference = systemTime - otherTime;
        //Log.d("system", "differencce: " + difference + " lastupdate: " + lastUpdate);
        if (!record || ((difference - lastUpdate) < 10 && lastUpdate != 0) || (difference < 1000)) {
            return;
        }
        accCount++;
        lastUpdate = difference;
        //Log.d("acceleration", "Value: " + Float.toString(x) + " " + Float.toString(y) + " " + Float.toString(z));
        accList.add(new ArrayList<> (Arrays.asList(event.values[0], event.values[1], event.values[2]-(9.6f), (float) (difference)-1000)));
    }
    private void getGyroscope(SensorEvent event) {
        //float[] values = event.values;
        // Movement
        //float x = values[0];
        //float y = values[1];
        //float z = values[2];

        //float accelationSquareRoot = (x * x + y * y + z * z)
        //      / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        long difference = systemTime - otherTime;
        if (!record || ((difference - lastUpdateR) < 10 && lastUpdateR != 0) || (difference < 1000)) {
            return;
        }
        rotCount++;
        lastUpdateR = difference;
        //Log.d("rotation", "Value: " + Float.toString(x) + " " + Float.toString(y) + " " + Float.toString(z));
        rotList.add(new ArrayList<> (Arrays.asList(event.values[0], event.values[1], event.values[2], (float) (difference)-1000)));
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    public static <T> T concatenate(T a, T b) {
        if (!a.getClass().isArray() || !b.getClass().isArray()) {
            throw new IllegalArgumentException();
        }

        Class<?> resCompType;
        Class<?> aCompType = a.getClass().getComponentType();
        Class<?> bCompType = b.getClass().getComponentType();

        if (aCompType.isAssignableFrom(bCompType)) {
            resCompType = aCompType;
        } else if (bCompType.isAssignableFrom(aCompType)) {
            resCompType = bCompType;
        } else {
            throw new IllegalArgumentException();
        }

        int aLen = Array.getLength(a);
        int bLen = Array.getLength(b);

        @SuppressWarnings("unchecked")
        T result = (T) Array.newInstance(resCompType, aLen + bLen);
        System.arraycopy(a, 0, result, 0, aLen);
        System.arraycopy(b, 0, result, aLen, bLen);

        return result;
    }
    public static int search(ArrayList<ArrayList<Float>> a, Float value) {

        if(value < a.get(0).get(3)) {
            return 0;
        }
        if(value > a.get(a.size()-1).get(3)) {
            return a.size()-1;
        }

        int lo = 0;
        int hi = a.size() - 1;

        while (lo <= hi) {
            int mid = (hi + lo) / 2;

            if (value < a.get(mid).get(3)) {
                hi = mid - 1;
            } else if (value > a.get(mid).get(3)) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        // lo == hi + 1
        return (a.get(lo).get(3) - value) < (value - a.get(hi).get(3)) ? lo : hi;
    }
}
