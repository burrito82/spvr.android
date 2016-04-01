package de.root13.spvr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Somebody on 26.03.2016.
 */
public class SensorForwarder implements SensorEventListener, Runnable {
    private final float m_afYrp[] = new float[3];
    private final float m_afEulerUncalibrated[] = new float[3];
    private final float m_afEulerCalibrated[] = new float[3];
    private final float qRotation[] = new float[4];
    private final StringBuilder mStringBuilder = new StringBuilder();
    private DatagramSocket mSocket;
    private DatagramPacket mPacket;
    private InetAddress mInetAddress;
    private SensorManager mSensorManager;
    private Context mContext;
    private TextView mTxtLabel;
    // UDP => drop packets received too late
    private int m_iNextMsg = 0;
    private boolean m_bNewValue;
    private boolean m_bIsSending;
    private float m_afCalib[] = new float[3];
    private AtomicBoolean m_bIsCalibrated = new AtomicBoolean(false);
    public SensorForwarder(final Context context, final SensorManager sensorManager, final TextView txtLabel) {
        mContext = context;
        mSensorManager = sensorManager;
        mTxtLabel = txtLabel;
        final byte buffer[] = ByteBuffer.allocate(4 * 5).array();
        mPacket = new DatagramPacket(buffer, buffer.length, mInetAddress, 4321);
        m_bIsSending = false;
    }

    private static void Yrp2Euler(float yrp[], float euler[]) {
        final double conv = Math.PI / 180.0;

        final double yaw = conv * yrp[0];
        double roll = conv * yrp[1];
        double pitch = conv * (90.0 - yrp[2]);

        final boolean bLookingDown = (-90.0 < yrp[1]) && (yrp[1] < 90.0);
        if (bLookingDown) {
            pitch = conv * (yrp[2] - 90.0);
        } else {
            if (roll > 0.0) {
                roll = conv * (180.0 - yrp[1]);
            } else {
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

    public boolean GetIsSending() {
        return m_bIsSending;
    }

    public void SetIsSending(boolean bIsSending) {
        if (!m_bIsSending && bIsSending && mInetAddress != null) {
            InitNetwork();
        }
        m_bIsSending = bIsSending;
    }

    public void InitNetwork() {
        if (mSocket == null) {
            try {
                //mInetAddress = InetAddress.getByName("192.168.192.57");
                mSocket = new DatagramSocket();//4321, mInetAddress);
                mSocket.setSendBufferSize(4 * 5);
                mSocket.setReuseAddress(true);
            } catch (IOException e) {
                Log.d(MainActivity.SPVR_TAG, "Could not initialize connection!");
                e.printStackTrace();
            }
        }
    }

    public void SetIpAddress(final String strIpAddress) throws UnknownHostException {
        mInetAddress = InetAddress.getByName(strIpAddress);
    }

    public void SendDirection(final float afValues[]) throws IOException {
        if (m_iNextMsg < 0) {
            m_iNextMsg = 0;
        }
        if (mSocket != null && mPacket != null) {
            final byte buffer[] = ByteBuffer.allocate(4 * 5)
                    .putFloat(afValues[0])
                    .putFloat(afValues[1])
                    .putFloat(afValues[2])
                    .putFloat(afValues[3])
                    .putInt(m_iNextMsg++)
                    .array();

            mPacket.setData(buffer, 0, buffer.length);
            mPacket.setAddress(mInetAddress);
            mPacket.setPort(4321);
            mSocket.send(mPacket);
        }
    }

    public void ResetCalibration() {
        m_bIsCalibrated.set(false);
    }

    private void SetCalibration() {
        m_afCalib[0] = m_afEulerUncalibrated[0];
        m_afCalib[1] = 0.0f;//m_afEulerUncalibrated[1];
        m_afCalib[2] = 0.0f;//m_afEulerUncalibrated[2];
    }

    private void Calibrate() {
        m_afEulerCalibrated[0] = m_afEulerUncalibrated[0] - m_afCalib[0];
        m_afEulerCalibrated[1] = m_afEulerUncalibrated[1] - m_afCalib[1];
        m_afEulerCalibrated[2] = m_afEulerUncalibrated[2] - m_afCalib[2];
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        m_afYrp[0] = sensorEvent.values[0];
        m_afYrp[1] = sensorEvent.values[1];
        m_afYrp[2] = sensorEvent.values[2];

        m_bNewValue = true;

        if (m_bIsSending) {
            Yrp2Euler(m_afYrp, m_afEulerUncalibrated);
            if (m_bIsCalibrated.compareAndSet(false, true)) {
                SetCalibration();
            }
            Calibrate();
            Euler2Quaternion(m_afEulerCalibrated, qRotation);
            try {
                SendDirection(qRotation);
            } catch (IOException e) {
            }
        }

        mStringBuilder.setLength(0);
        mStringBuilder.append("{\n")
                .append(qRotation[0]).append(",\n")
                .append(qRotation[1]).append(",\n")
                .append(qRotation[2]).append(",\n")
                .append(qRotation[3]).append("\n")
                .append('}');
        mTxtLabel.setText(mStringBuilder.toString());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void Euler2Quaternion(final float euler[], final float quat[]) {
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

        quat[0] = (float) w;
        quat[1] = (float) x;
        quat[2] = (float) y;
        quat[3] = (float) z;
    }

    @Override
    public void run() {
        InitNetwork();
        while (true) {
            try {
                while (true) {
                    if (m_bNewValue) {
                        m_bNewValue = false;

                        Yrp2Euler(m_afYrp, m_afEulerUncalibrated);
                        Calibrate();
                        Euler2Quaternion(m_afEulerCalibrated, qRotation);
                        SendDirection(qRotation);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
