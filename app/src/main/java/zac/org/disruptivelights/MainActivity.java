package zac.org.disruptivelights;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;



public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";
    private BtLeScanService mBtLeScanService;
    private AutoConnectBLEService mAutoConnectBLEService;
    private TextView mLogTextView;
    private final Handler mHandler = new Handler();


    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "mServiceConnection.onServiceConnected()");
            addLog("BT scan service connected");

            mBtLeScanService = ((BtLeScanService.BtLeScanBinder) service).getService();
            if(!mBtLeScanService.initialize()) {
                Log.e(TAG, "Failed to get Bluetooth scanning service!");
                addLog("BT scan service failed to initialize");
                finish();
            } else {
                addLog("BT scan service initialized");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "mServiceConnection.onServiceDisconnected()");
            addLog("BT scan service disconnected");
            mBtLeScanService = null;
        }
    };

    private final BroadcastReceiver mBtLeScanUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive(" + action + ")");

            if(BtLeScanService.ACTION_SCAN_STARTED.equals(action)) {
                addLog("Scan started");
            }
            else if(BtLeScanService.ACTION_SCAN_STOPPED.equals(action)) {
                addLog("Scan stopped");
            }
            else if(BtLeScanService.ACTION_DEVICE_NEW.equals(action)) {
                final String deviceName = intent.getStringExtra(BtLeScanService.EXTRA_DEVICE_NAME);
                final String deviceAddress = intent.getStringExtra(BtLeScanService.EXTRA_DEVICE_ADDRESS);
                final int deviceRssi = intent.getIntExtra(BtLeScanService.EXTRA_DEVICE_RSSI, -1);

                addLog("New device " + deviceAddress + (deviceName == null || deviceName.equals("null") ? "" : " (" + deviceName + ")") + " RSSI: " + deviceRssi);
            }
            else if(BtLeScanService.ACTION_DEVICE_UPDATE.equals(action)) {
                final String deviceName = intent.getStringExtra(BtLeScanService.EXTRA_DEVICE_NAME);
                final String deviceAddress = intent.getStringExtra(BtLeScanService.EXTRA_DEVICE_ADDRESS);
                final int deviceRssi = intent.getIntExtra(BtLeScanService.EXTRA_DEVICE_RSSI, -1);

                addLog("Update device " + deviceAddress + (deviceName == null || deviceName.equals("null") ? "" : " (" + deviceName + ")") + " RSSI: " + deviceRssi);
            }
            else if(BtLeScanService.ACTION_DEVICE_GONE.equals(action)) {
                final String deviceAddress = intent.getStringExtra(BtLeScanService.EXTRA_DEVICE_ADDRESS);

                addLog("Device gone " + deviceAddress);
            }

        }
    };


    //For BtLeScanService
    private final ServiceConnection mAutoConnectBLEServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "mAutoConnectBLEServiceConnection.onServiceConnected");
            addLog("mAutoConnectBLEServiceConnection.onServiceConnected");

            mAutoConnectBLEService = ((AutoConnectBLEService.AutoConnectBLEBinder)service).getService();
            if(mAutoConnectBLEService.start()) {
                Log.d(TAG, "Starting AutoConnectBLEService");
                addLog("Starting AutoConnectBLEService");
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Auto stopping AutoConnectBLEService after 5 seconds");
                        mAutoConnectBLEService.stop();
                        unbindService(mAutoConnectBLEServiceConnection); //from it's own handler? TODO
                    }
                }, 5000);
            } else {
                Log.d(TAG, "Failed to start AutoConnectBLEService");
                addLog("Failed to start AutoConnectBLEService");
                mAutoConnectBLEService.stop(); //??? TODO
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "mAutoConnectBLEServiceConnection.onServiceDisconnected");
            addLog("mAutoConnectBLEServiceConnection.onServiceDisconnected");
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLogTextView = (TextView)findViewById(R.id.logTextView);

        Log.d(TAG, "Binding scan service");
        Intent btLeScanServiceIntent = new Intent(this, BtLeScanService.class);
        bindService(btLeScanServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BtLeScanService.ACTION_SCAN_STARTED);
        intentFilter.addAction(BtLeScanService.ACTION_SCAN_STOPPED);
        intentFilter.addAction(BtLeScanService.ACTION_DEVICE_NEW);
        intentFilter.addAction(BtLeScanService.ACTION_DEVICE_UPDATE);
        intentFilter.addAction(BtLeScanService.ACTION_DEVICE_GONE);
        registerReceiver(mBtLeScanUpdateReceiver, intentFilter);

        if(mBtLeScanService != null) {
            final boolean result = mBtLeScanService.initialize();
            if(result) {
                Log.d(TAG, "onResume() BT Scan initialize succeeded");
                addLog("onResume() BT Scan initialize succeeded");
            } else {
                Log.d(TAG, "onResume() BT Scan initialize failed");
                addLog("onResume() BT Scan initialize failed");
            }
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();

        unregisterReceiver(mBtLeScanUpdateReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBtLeScanService = null;

        //TODO: May not be hooked up? Matter?
        unbindService(mAutoConnectBLEServiceConnection);
        mAutoConnectBLEService = null;
    }

    private void addLog(String s) {
        mLogTextView.append("\n" + s);
    }

    public void onScanButtonClick(View view) {
        Log.d(TAG, "onScanButtonClick()");

        if(mBtLeScanService == null) {
            Log.w(TAG, "mBtLeScanService is null");
            Toast.makeText(getApplicationContext(), "BT scan service is unavailable", Toast.LENGTH_LONG).show();
        } else {
            mBtLeScanService.startScan();
        }
    }

    public void onConnectButtonClick(View view) {
        Log.d(TAG, "onConnectButtonClick()");
        addLog("Trying auto search & connect");

        //Bind the Gatt service:
        final Intent gattIntent = new Intent(getApplicationContext(), AutoConnectBLEService.class);
        if(!bindService(gattIntent, mAutoConnectBLEServiceConnection, BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to bind Gatt service");
            addLog("Failed to bind AutoConnectBLEService");
        }
        addLog("Bound AutoConnectBLEService, waiting on ServiceConnection");
    }
}
