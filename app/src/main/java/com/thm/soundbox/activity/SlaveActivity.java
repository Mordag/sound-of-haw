package com.thm.soundbox.activity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;

import com.thm.soundbox.R;
import com.thm.soundbox.Util;
import com.thm.soundbox.logic.AccelerationLogic;
import com.thm.soundbox.logic.BeaconLogic;
import com.thm.soundbox.logic.BeaconSlaveLogic;
import com.thm.soundbox.logic.BluetoothLogic;

public final class SlaveActivity extends Activity {

    private AccelerationLogic mAcceleration;
    private BeaconLogic mBeaconLogic;
    public BluetoothLogic mBluetoothLogic;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.slave_activity);

        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                handleData(msg);
            }
        };

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                mBluetoothLogic = new BluetoothLogic(mHandler);
                mBluetoothLogic.startConnection(Util.SLAVE);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        mAcceleration = new AccelerationLogic();
        mAcceleration.startLogic(SlaveActivity.this);
        mAcceleration.onResume();

        mBeaconLogic = new BeaconSlaveLogic();
        mBeaconLogic.startLogic(SlaveActivity.this);

        findViewById(R.id.slave_parent_layout).setBackgroundColor(Util.DEFAULT_BACKGROUND_COLOR);
    }

    private void handleData(Message msg) {
        Object content = ((Object[]) msg.obj)[0];
        byte[] aData = (byte[]) content;
        String data = new String(aData);
        String[] aSplitData = data.split("%");
        String identifier = aSplitData[0];

        Log.i(SlaveActivity.class.getName(), "Identifier: " + identifier);

        switch (identifier) {
            case "ERROR":
            case "LOGOUT_SLAVE":
                String beacon = aSplitData[1];
                if (beacon.equals(Util.connectedBeacon)) {
                    Util.isLogin = false;
                    Util.connectedBeacon = null;
                    Util.isLoggingOut = false;
                    findViewById(R.id.slave_parent_layout).setBackgroundColor(Util.DEFAULT_BACKGROUND_COLOR);
                    Util.currentColor = Util.DEFAULT_BACKGROUND_COLOR;
                    Util.gravity = false;
                } else {
                    Log.w(SlaveActivity.class.getName(), "Tried to disconnect an old connection. " +
                            "Beacon = " + beacon);
                }
                break;
            case "LOGIN_SLAVE":
                beacon = aSplitData[1];
                if (beacon.equals(Util.connectedBeacon) && !Util.isLoggingOut) {
                    Util.currentColor = Color.parseColor(aSplitData[2].trim());
                    Util.gravity = aSplitData[3].trim().equals("true");
                    findViewById(R.id.slave_parent_layout).setBackgroundColor(Util.currentColor);
                    Util.isLogin = true;
                } else {
                    if (Util.isLoggingOut) {
                        Log.i(SlaveActivity.class.getName(), "Device is currently in logout process.");
                    } else {
                        String deviceAddress = BluetoothAdapter.getDefaultAdapter().getAddress();
                        String logoutData = "Logout%" + beacon + "%" + deviceAddress + "%";
                        sendData("Logout", logoutData);

                        Log.w(SlaveActivity.class.getName(), "Wrong login. Beacon = " + beacon);
                    }
                }
                break;
        }
    }

    public void sendData(final String type, final String value) {
        if (mBluetoothLogic != null && mBluetoothLogic.isMasterAvailable()) {
            if (type.equals("Login") || type.equals("Logout")) {
                mBluetoothLogic.sendDataToMaster(value);
            } else {
                if (Util.isLogin) {
                    mBluetoothLogic.sendDataToMaster(value);
                } else {
                    Log.w(SlaveActivity.class.getName(), "This device is not in range of a beacon.");
                }
            }
        } else {
            Util.connectedBeacon = null;

            if (mBluetoothLogic == null) {
                Log.w(SlaveActivity.class.getName(), "Bluetooth logic = " + mBluetoothLogic);
            } else {
                Log.w(SlaveActivity.class.getName(), "Data could not be sent. " +
                        "Master = " + mBluetoothLogic.isMasterAvailable());
            }
        }
    }

    @Override
    public void onBackPressed() {
        //Back button of this activity; depending on the dev mode if the application
        //is going to be closed or only jumping back to the menu

        if (!Util.DEV_MODE) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mAcceleration.onPause();
        mBeaconLogic.onPause();

        if (Util.connectedBeacon != null) {
            Util.isLoggingOut = true;

            Log.i(SlaveActivity.class.getName(), "Device trying to logout.");
            String logoutData = "Logout%" + Util.connectedBeacon + "%";
            if(mBluetoothLogic != null && mBluetoothLogic.isMasterAvailable()) {
                mBluetoothLogic.sendDataToMaster(logoutData);
                mBluetoothLogic.close();
            }

            Util.isLogin = false;
            Util.connectedBeacon = null;
            Util.isLoggingOut = false;
            Util.gravity = false;
            findViewById(R.id.slave_parent_layout).setBackgroundColor(Util.DEFAULT_BACKGROUND_COLOR);
            Util.currentColor = Util.DEFAULT_BACKGROUND_COLOR;
        } else {
            mBluetoothLogic.close();
        }
    }
}
