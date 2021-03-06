package de.root13.spvr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    public static final String SPVR_TAG = "spvr";
    private SensorForwarder mSensorForwarder;
    private SensorManager mSensorManager;

    private Sensor mSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        // TODO: sensor and network stuff should be moved into a Service
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        InitSensorListener();

        final EditText editTxtIp = (EditText) findViewById(R.id.editTxtIp);
        final Button btnStart = (Button) findViewById(R.id.btnStart);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mSensorForwarder.GetIsSending()) {
                    try {
                        mSensorForwarder.SetIpAddress(editTxtIp.getText().toString());
                        mSensorForwarder.SetIsSending(true);
                    } catch (UnknownHostException e) {
                        Toast.makeText(getApplicationContext(), "Could not set ip!", Toast.LENGTH_SHORT);
                    }
                }
            }
        });

        final Button btnCalibrate = (Button) findViewById(R.id.btnCalibrate);
        btnCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSensorForwarder.ResetCalibration();
            }
        });
    }

    private void InitSensorListener() {
        if (mSensorForwarder == null) {
            final TextView txtLabel = (TextView) findViewById(R.id.txtLabel);
            mSensorForwarder = new SensorForwarder(this, mSensorManager, txtLabel);
            mSensorManager.registerListener(mSensorForwarder, mSensor, 1);
        }
    }
}
