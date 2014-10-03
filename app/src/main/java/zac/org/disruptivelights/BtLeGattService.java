package zac.org.disruptivelights;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.UUID;


/**
 * This class is for connecting and communicating with a BLE device once
 * scanning has identified it.
 */
public class BtLeGattService extends Service {
    private static final String TAG = "BtLeGattService";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService mBluetoothGattService;
    private String mBluetoothDeviceAddress;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "zac.org.disruptivelights.BtLeGattService.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "zac.org.disruptivelights.BtLeGattService.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "zac.org.disruptivelights.BtLeGattService.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "zac.org.disruptivelights.BtLeGattService.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "zac.org.disruptivelights.BtLeGattService.EXTRA_DATA";

    public final static UUID UUID_SERVICE = sixteenBitUuid(0x2220);
    public final static UUID UUID_RECEIVE = sixteenBitUuid(0x2221);
    public final static UUID UUID_SEND = sixteenBitUuid(0x2222);
    public final static UUID UUID_DISCONNECT = sixteenBitUuid(0x2223);
    public final static UUID UUID_CLIENT_CONFIGURATION = sixteenBitUuid(0x2902);


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange(" + status + ", " + newState + ")");

            if(newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(ACTION_GATT_CONNECTED);
                mBluetoothGatt.discoverServices();
            } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered(" + status + ")");

            if(status == BluetoothGatt.GATT_SUCCESS) {
                mBluetoothGattService = gatt.getService(UUID_SERVICE);
                if (mBluetoothGattService == null) {
                    Log.e(TAG, "mGattCallback.onServicesDiscovered() - Failed to hook UUID_SERVICE!");
                    return;
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered(" + status + ") - Unhandled");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead(" + status + ")");

            if(status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } else {
                Log.e(TAG, "onCharacteristicRead() failed to read!");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged()");

            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        Log.d(TAG, "broadcastUpdate(" + action + ")");

        final Intent intent = new Intent(action);
        sendBroadcast(intent);
        //TODO: Consider LocalBroadcastManager?
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "broadcastUpdate(" + action + ", characteristic)");

        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, characteristic.getValue());
        sendBroadcast(intent, Manifest.permission.BLUETOOTH);
        //TODO: Consider LocalBroadcastManager?
    }

    public class BtLeGattBinder extends Binder {
        public BtLeGattService getService() {
            return BtLeGattService.this;
        }
    }

    private final IBinder mBinder = new BtLeGattBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind(" + (intent == null || intent.getAction() == null ? "null" : intent.getAction()) + ")");

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbound(" + (intent == null || intent.getAction() == null ? "null" : intent.getAction()) + ")");

        close();
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        Log.d(TAG, "initialize()");

        if(mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if(mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter");
            return false;
        }

        return true;
    }

    public boolean connect(final String address) {
        Log.d(TAG, "connect(" + address + ")");

        if(mBluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is null");
            return false;
        }

        if(address == null || address.isEmpty()) {
            Log.e(TAG, "Address is null or empty");
            return false;
        }

        //Try to reconnect if possible:
        if(mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(address) && mBluetoothGatt != null) {
            if(mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                //TODO - ? - mConnectionState = STATE_DISCONNECTED;
                return false;
            }
        }

        //New device:
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if(device == null) {
            Log.e(TAG, "Device " + address + "not found!");
            return false;
        }

        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to connect to " + address);
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;

        return true;
    }

    public void disconnect() {
        Log.d(TAG, "disconnect()");

        if(mBluetoothAdapter == null) {
            Log.e(TAG, "disconnect() - mBluetoothAdapter is null");
            return;
        }
        if(mBluetoothGatt == null) {
            Log.e(TAG, "disconnect() - mBluetoothGatt is null");
            return;
        }

        mBluetoothGatt.disconnect();
    }

    public void close() {
        Log.d(TAG, "close()" + (mBluetoothGatt == null ? "(mBluetoothGatt is null)" : ""));

        if(mBluetoothGatt == null) {
            return;
        }

        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public boolean send(byte[] data) {
        Log.d(TAG, "send()");

        if(mBluetoothGatt == null || mBluetoothGattService == null) {
            Log.e(TAG, "send() - mBluetoothGatt or mBluetoothGattService is null");
        }

        BluetoothGattCharacteristic characteristic = mBluetoothGattService.getCharacteristic(UUID_SEND);
        if(characteristic == null) {
            Log.e(TAG, "Failed to get UUID_SEND characteristic");
            return false;
        }

        characteristic.setValue(data);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }


    public static UUID sixteenBitUuid(long shortUuid) {
        final String shortUuidFormat = "0000%04X-0000-1000-8000-00805F9B34FB";
        assert shortUuid >= 0 && shortUuid <= 0xFFFF;
        return UUID.fromString(String.format(shortUuidFormat, shortUuid & 0xFFFF));
    }
}
