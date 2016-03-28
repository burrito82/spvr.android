package de.root13.smartvr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Created by Somebody on 26.03.2016.
 */
public class SensorForwarder implements SensorEventListener, Runnable
{
    private DatagramSocket mSocket;
    private DatagramPacket mPacket;
    private InetAddress mInetAddress;

    private SensorManager mSensorManager;
    private Context mContext;
    private TextView mTxtLabel;

    public SensorForwarder(Context context, SensorManager sensorManager, TextView txtLabel) {
        mContext = context;
        mSensorManager = sensorManager;
        mTxtLabel = txtLabel;
        final byte buffer[] = ByteBuffer.allocate(4 * 4).array();
        mPacket = new DatagramPacket(buffer, buffer.length, mInetAddress, 4321);
        m_afPreviousValues = new float[3];
    }

    public void InitNetwork()
    {
        try {
            mInetAddress = InetAddress.getByName("192.168.192.57");
            //mInetAddress = InetAddress.getByName("192.168.42.20");
            mSocket = new DatagramSocket();//4321, mInetAddress);
            mSocket.setSendBufferSize(4 * 4);
            mSocket.setReuseAddress(true);
        } catch (IOException e) {
            Toast.makeText(mContext, "Could not initialize connection!", Toast.LENGTH_SHORT);
            Log.d(MainActivity.SMARTVR_TAG, "Could not initialize connection!");
            e.printStackTrace();
        }
    }

    public void SendDirection(float[] afValues) {
        if (mSocket != null && mPacket != null) {// && mSocket.isConnected()) {
            final byte buffer[] = ByteBuffer.allocate(4 * 4)
                    .putFloat(afValues[0])
                    .putFloat(afValues[1])
                    .putFloat(afValues[2])
                    .putFloat(afValues[3])
                    .array();

            try {
                /*final OutputStream os = mSocket.getOutputStream();
                os.write(buffer);
                os.flush();*/
                //mSocket.send(new DatagramPacket(buffer, buffer.length, mInetAddress, 4321));
                mPacket.setData(buffer, 0, buffer.length);
                mPacket.setAddress(mInetAddress);
                mPacket.setPort(4321);
                mSocket.send(mPacket);
            } catch (IOException e) {
                Toast.makeText(mContext, "Could not send direction!", Toast.LENGTH_SHORT);
                Log.d(MainActivity.SMARTVR_TAG, "Could not send direction!");
                e.printStackTrace();
            }
        }
    }

    private float m_afValues[];
    private float m_afPreviousValues[];
    private float m_afYrp[] = new float[3];
    private float m_afEuler[] = new float[3];
    private boolean m_bNewValue;
    final StringBuilder mStringBuilder = new StringBuilder();

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //m_afValues = sensorEvent.values;
        m_afValues = m_afEuler;
        m_afYrp[0] = (sensorEvent.values[0] + 180.0f) % 360.0f;
        m_afYrp[1] = sensorEvent.values[1];
        m_afYrp[2] = sensorEvent.values[2];
        Yrp2Euler(m_afYrp, m_afEuler);
        mStringBuilder.setLength(0);
        mStringBuilder.append("{\n").append(m_afValues[0]).append(",\n")
                .append(m_afValues[1]).append(",\n")
                .append(m_afValues[2]).append(",\n")
                //.append(sensorEvent.values[3]).append("\n")
                .append('}');
        //SendDirection(m_afValues);
        mTxtLabel.setText(mStringBuilder.toString());
        m_bNewValue = true;
    }

    private static void Yrp2Euler(float yrp[], float euler[])
    {
        final double conv = Math.PI / 180.0;

        final double yaw = conv * yrp[0];
        double roll = conv * yrp[1];
        double pitch = conv * (90.0 - yrp[2]);

        final boolean bLookingDown = (-90.0 < yrp[1]) && (yrp[1] < 90.0);
        if (bLookingDown)
        {
            pitch = conv * (yrp[2] - 90.0);
        }
        else
        {
            if (roll > 0.0)
            {
                roll = conv * (180.0 - yrp[1]);
            }
            else
            {
                roll = conv * -(180.0 + yrp[1]);
            }
        }

        /*
        roll < -90 || roll > 90 => looking up
        -90 < roll < 90 => looking down
        roll > 0 => left down right up
        roll = 0 == -0 == 180 == -180
         */

        euler[0] = (float) -yaw;
        euler[1] = (float) roll;
        euler[2] = (float) pitch;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public static void Euler2Quaternion(final float euler[], final float quat[])
    {
        final double yaw = euler[0];
        final double roll = euler[1];
        final double pitch = euler[2];

        final double cyaw = Math.cos(yaw * 0.5);
        final double croll = Math.cos(roll * 0.5);
        final double cpitch = Math.cos(pitch * 0.5);

        final double syaw = Math.sin(yaw * 0.5);
        final double sroll = Math.sin(roll * 0.5);
        final double spitch = Math.sin(pitch * 0.5);

        final double cyaw_cpitch = cyaw * cpitch;
        final double syaw_spitch = syaw * spitch;
        final double cyaw_spitch = cyaw * spitch;
        final double syaw_cpitch = syaw * cpitch;

        final double w = cyaw_cpitch * croll + syaw_spitch * sroll;
        final double x = cyaw_spitch * croll + syaw_cpitch * sroll;
        final double y = syaw_cpitch * croll - cyaw_spitch * sroll;
        final double z = cyaw_cpitch * sroll - syaw_spitch * croll;

        quat[0] = (float)w;
        quat[1] = (float)x;
        quat[2] = (float)y;
        quat[3] = (float)z;
    }

    public static void Euler2Quaternion2(final float euler[], final float quat[])
    {
        final float yaw = euler[0];
        final float roll = euler[1];
        final float pitch = euler[2];
        double phi = (yaw) / 180.0 * Math.PI; // a, [0, 2pi], azimuth/yaw
        double theta = roll / 180.0 * Math.PI; // b, [-pi/2, pi/2], roll {0}
        double psi = (90.0 - pitch) / 180.0 * Math.PI; // g, [-pi/2, pi/2], pitch {90}
        // looking up:
        if (Math.abs(roll) > 90.0)
        {
            if (roll > 0.0)
            {
                theta = (180.0 - roll) / 180.0 * Math.PI;
            }
            else
            {
                theta = (180.0 + roll) / 180.0 * Math.PI;
            }
            psi = (pitch - 90.0) / 180.0 * Math.PI;
        }
        else
        {
            if (yaw > 180.0 && Math.abs(roll) > 90.0)
            {
                psi = (pitch - 90.0) / 180.0 * Math.PI;
            }
        }
        final double rphi = -psi;
        final double rtheta = -phi;
        final double rpsi = theta;
        final double cphi2 = Math.cos(rphi / 2.0);
        final double ctheta2 = Math.cos(rtheta / 2.0);
        final double cpsi2 = Math.cos(rpsi / 2.0);
        final double sphi2 = Math.sin(rphi / 2.0);
        final double stheta2 = Math.sin(rtheta / 2.0);
        final double spsi2 = Math.sin(rpsi / 2.0);
        final double w = cphi2 * ctheta2 * cpsi2 + sphi2 * stheta2 * spsi2;
        final double x = sphi2 * ctheta2 * cpsi2 - cphi2 * stheta2 * spsi2;
        final double y = cphi2 * stheta2 * cpsi2 + sphi2 * ctheta2 * spsi2;
        final double z = cphi2 * ctheta2 * spsi2 - sphi2 * stheta2 * cpsi2;
        /*quat[0] = (float)w;
        quat[1] = (float)x;
        quat[2] = (float)y;
        quat[3] = (float)z;

        quat[0] = (float)cphi2;
        quat[1] = (float)0.0f;
        quat[2] = (float)-sphi2;
        quat[3] = (float)0.0f;

        quat[0] = (float)cpsi2;
        quat[1] = (float)-spsi2;
        quat[2] = (float)0.0f;
        quat[3] = (float)0.0f;

        quat[0] = (float)ctheta2;
        quat[1] = (float)0.0f;
        quat[2] = (float)0.0f;
        quat[3] = (float)stheta2;

        quat[0] = (float)w;
        quat[1] = (float)-z;
        quat[2] = (float)-x;
        quat[3] = (float)y;*/

        quat[0] = (float)w;
        quat[1] = (float)x;
        quat[2] = (float)y;
        quat[3] = (float)z;
    }

    @Override
    public void run() {
        InitNetwork();
        final float qRotation[] = new float[4];
        while (true)
        {
            if (m_afValues != null && m_bNewValue)
            {
                m_bNewValue = false;
                /*if (m_afValues[0] != m_afPreviousValues[0]
                    || m_afValues[1] != m_afPreviousValues[1]
                    || m_afValues[2] != m_afPreviousValues[2])*/
                {
                    Euler2Quaternion(m_afValues, qRotation);
                    SendDirection(qRotation);
                    //m_afPreviousValues = m_afValues.clone();
                }
                try {
                    Thread.currentThread().sleep(1, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
