package zac.org.disruptivelights;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * Called to scan for BLE devices and if the target is available, connect and send data.
 */
public class AutoConnectBLEService extends Service {
    public final static String TAG = "AutoConnectBLEService";

    private final static String TARGET_BLE_ADDRESS = "D2:86:6A:06:04:83";   //Test RFduino Device. Has to be uppercase dumbass

    private BtLeScanService mBtLeScanService;
    private BtLeGattService mBtLeGattService;
    private boolean mIsConnectedAndDiscovered;

    private boolean mWantToSendCommand;


    //For BtLeScanService
    private final ServiceConnection mBtLeScanServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "mBtLeScanServiceConnection.onServiceConnected()");
            mBtLeScanService = ((BtLeScanService.BtLeScanBinder) service).getService();
            if(!mBtLeScanService.initialize()) {
                Log.e(TAG, "Failed to initialize BtLeScanService");
                stopSelf(); //TODO!
            } else {
                if(mBtLeScanService.startScan()) {
                    Log.d(TAG, "mBtLeScanService Started scan");
                } else {
                    Log.e(TAG, "Failed to start scan");
                    stopSelf(); //TODO!
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "mBtLeScanServiceConnection.onServiceDisconnected()");
            mBtLeScanService = null;
        }
    };

    //For BtLeScanService
    private final BroadcastReceiver mBtLeScanUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "mBtLeScanUpdateReceiver.onReceive(): " + action);

            if(BtLeScanService.ACTION_DEVICE_NEW.equals(action)) {
                final String address = intent.getStringExtra(BtLeScanService.EXTRA_DEVICE_ADDRESS);
                Log.d(TAG, "New device: " + address);

                if(address.equals(TARGET_BLE_ADDRESS)) {
                    //Found the target, stop the device scan:
                    Log.d(TAG, "Found target device, stopping scanning");
                    mBtLeScanService.stopScanning();

                    connectToTargetDevice();
                } else {
                    Log.d(TAG, "Address " + address + " isn't " + TARGET_BLE_ADDRESS);
                }
            }
        }
    };


    //For BtLeScanService
    private final ServiceConnection mBtLeGattServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "mBtLeGattServiceConnection.onServiceConnected()");
            mBtLeGattService = ((BtLeGattService.BtLeGattBinder) service).getService();
            if(!mBtLeGattService.initialize()) {
                Log.e(TAG, "Failed to initialize BtLeGattService");
                stopSelf(); //TODO!
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "mBtLeGattServiceConnection.onServiceDisconnected()");
            mBtLeGattService = null;
        }
    };

    //For BtLeScanService
    private final BroadcastReceiver mBtLeGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "mBtLeGattUpdateReceiver.onReceive(): " + action);

            if(BtLeGattService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "Gatt connected");
                mIsConnectedAndDiscovered = false; //not discovered
            }
            else if(BtLeGattService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "Gatt disconnected");
                mIsConnectedAndDiscovered = false;
            }
            else if(BtLeGattService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "Gatt services discovered");

                mIsConnectedAndDiscovered = true;
                if(mWantToSendCommand) {
                    Log.d(TAG, "Sending message");
                    mBtLeGattService.send(new byte[] {'H', 'I'});
                } else {
                    Log.d(TAG, "Can't send message, not connected & discovered");
                }
            }
        }
    };



    public class AutoConnectBLEBinder extends Binder {
        public AutoConnectBLEService getService() {
            return AutoConnectBLEService.this;
        }
    }

    private final IBinder mBinder = new AutoConnectBLEBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        unbindService(mBtLeScanServiceConnection);
        mBtLeScanService = null;
        unregisterReceiver(mBtLeScanUpdateReceiver);

        unbindService(mBtLeGattServiceConnection);
        mBtLeGattService = null;
        unregisterReceiver(mBtLeGattUpdateReceiver);

    }

    public boolean start() {
        Log.d(TAG, "start()");

        //Register intents we want to receive from the Scan service:
        final IntentFilter scanIntentFilter = new IntentFilter();
        scanIntentFilter.addAction(BtLeScanService.ACTION_SCAN_STARTED);
        scanIntentFilter.addAction(BtLeScanService.ACTION_SCAN_STOPPED);
        scanIntentFilter.addAction(BtLeScanService.ACTION_DEVICE_NEW);
        scanIntentFilter.addAction(BtLeScanService.ACTION_DEVICE_UPDATE);
        scanIntentFilter.addAction(BtLeScanService.ACTION_DEVICE_GONE);
        registerReceiver(mBtLeScanUpdateReceiver, scanIntentFilter);

        //Bind the Scan service:
        final Intent scanIntent = new Intent(getApplicationContext(), BtLeScanService.class);
        if(!bindService(scanIntent, mBtLeScanServiceConnection, BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to bindService!");
            return false;
        }

        //Register intents from the Gatt service we want:
        final IntentFilter gattIntentFilter = new IntentFilter();
        gattIntentFilter.addAction(BtLeGattService.ACTION_GATT_CONNECTED);
        gattIntentFilter.addAction(BtLeGattService.ACTION_GATT_DISCONNECTED);
        gattIntentFilter.addAction(BtLeGattService.ACTION_GATT_SERVICES_DISCOVERED);
        //gattIntentFilter.addAction(BtLeGattService.ACTION_DATA_AVAILABLE);
        registerReceiver(mBtLeGattUpdateReceiver, gattIntentFilter);

        //Bind the Gatt service:
        final Intent gattIntent = new Intent(getApplicationContext(), BtLeGattService.class);
        if(!bindService(gattIntent, mBtLeGattServiceConnection, BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to bind Gatt service");
            return false;
        }

        mWantToSendCommand = true;

        return true;
    }

    public void stop() {
        stopSelf();
    }


    //TODO: Reading on forums the BLE device is less likely to screw up if we don't use it in the same thread as the OnLeScan() callback.
    private void connectToTargetDevice() {
        Log.d(TAG, "conncetToTargetDevice()");

        if(!mBtLeGattService.connect(TARGET_BLE_ADDRESS)) {
            Log.e(TAG, "Failed to start connecting to target device");
            return;
        }

        mWantToSendCommand = true;
        //Now we wait for broadcasted intents from the Gatt service
    }
}
