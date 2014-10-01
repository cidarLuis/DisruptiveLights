package zac.org.disruptivelights;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.Date;
import java.util.HashMap;

public class BtLeScanService extends Service {
    public final static String TAG = "BtLeScanService";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private int mScanState = STATE_STOPPED;

    private static final int STATE_STOPPED = 1;
    private static final int STATE_RUNNING = 2;

    public final static String ACTION_SCAN_STARTED = "zac.org.disruptivelights.BtLeGattService.ACTION_SCAN_STARTED";
    public final static String ACTION_SCAN_STOPPED = "zac.org.disruptivelights.BtLeGattService.ACTION_SCAN_STOPPED";
    public final static String ACTION_DEVICE_NEW= "zac.org.disruptivelights.BtLeGattService.ACTION_DEVICE_NEW";
    public final static String ACTION_DEVICE_UPDATE = "zac.org.disruptivelights.BtLeGattService.ACTION_DEVICE_UPDATE";
    public final static String ACTION_DEVICE_GONE = "zac.org.disruptivelights.BtLeGattService.ACTION_DEVICE_GONE";
    public final static String EXTRA_DEVICE_ADDRESS = "zac.org.disruptivelights.BtLeGattService.EXTRA_DEVICE_ADDRESS";
    public final static String EXTRA_DEVICE_NAME = "zac.org.disruptivelights.BtLeGattService.EXTRA_DEVICE_NAME";
    public final static String EXTRA_DEVICE_RSSI  = "zac.org.disruptivelights.BtLeGattService.EXTRA_DEVICE_RSSI";

    private final HashMap<String, Date> mDevices = new HashMap<String, Date>();

    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(TAG, "onLeScan(" + (device != null ? device.getAddress() : "null") + ", " + rssi + ")");

            Date lastSeen = mDevices.put(device.getAddress(), new Date());
            if(lastSeen == null) {
                final Intent intent = new Intent(ACTION_DEVICE_NEW);
                intent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
                intent.putExtra(EXTRA_DEVICE_NAME, device.getName());
                intent.putExtra(EXTRA_DEVICE_RSSI, rssi);
                sendBroadcast(intent);
            } else {
                final Intent intent = new Intent(ACTION_DEVICE_UPDATE);
                intent.putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress());
                intent.putExtra(EXTRA_DEVICE_NAME, device.getName());
                intent.putExtra(EXTRA_DEVICE_RSSI, rssi);
                sendBroadcast(intent);
            }
        }
    };


    public class BtLeScanBinder extends Binder {
        public BtLeScanService getService() {
            return BtLeScanService.this;
        }
    }

    public final IBinder mBinder = new BtLeScanBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind(" + (intent == null || intent.getAction() == null ? "null" : intent.getAction()) + ")");

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind(" + (intent == null || intent.getAction() == null ? "null" : intent.getAction()) + ")");

        //TODO: What to do on unbind?
        return super.onUnbind(intent);
    }

    public boolean startScanning() {
        //TODO!
        return false;
    }

    public boolean stopScanning() {
        //TODO!
        return false;
    }

    private boolean getBluetoothManager() {
        if(mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager == null) {
                Log.e(TAG, "mBluetoothManager is null");
                return false;
            }
        }
        return true;
    }

    private boolean getBluetoothAdapter() {
        if(!getBluetoothManager()) {
            return false;
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if(mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to get BluetoothAdapter");
            return false;
        }

        return true;
    }
}
