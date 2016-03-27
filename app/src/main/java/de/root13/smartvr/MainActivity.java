package de.root13.smartvr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

import de.root13.smartvr.R;

public class MainActivity extends AppCompatActivity implements Runnable {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        //mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        mTxtLabel = (TextView) findViewById(R.id.txtLabel);

        //StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        //StrictMode.setThreadPolicy(policy);

        //mNetworkThread = new Thread(this);
        //mNetworkThread.start();
        InitSensorListener();
    }
    TextView mTxtLabel;

    public void InitSensorListener() {
        final TextView txtLabel = (TextView) findViewById(R.id.txtLabel);

        mSensorForwarder = new SensorForwarder(this, mSensorManager, mTxtLabel);
        mSensorManager.registerListener(mSensorForwarder, mSensor, 1);
        new Thread(mSensorForwarder).start();
    };

    SensorForwarder mSensorForwarder;

    public static final String SMARTVR_TAG = "smartvr";

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Socket mSocket;
    private Thread mNetworkThread;

    @Override
    public void run() {
        InitSensorListener();
    }
}
