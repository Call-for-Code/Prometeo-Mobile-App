package com.prometeo;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.prometeo.ble.BluetoothLeService;
import com.prometeo.io.RetrofitAdapter;
import com.prometeo.io.RetrofitService;
import com.prometeo.io.StatusCloud;
import com.prometeo.iot.IoTClient;
import com.prometeo.utils.Constants;
import com.prometeo.utils.MessageFactory;
import com.prometeo.utils.MyIoTActionListener;
import com.prometeo.utils.PrometeoEvent;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

import javax.net.SocketFactory;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class DeviceDashboard extends AppCompatActivity {

    private final static String TAG = DeviceDashboard.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String USER_ID = "USER_ID";

    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private String user_id;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic mGattCharacteristic;
    private BluetoothGattCharacteristic mGattDateTime;
    private BluetoothGattCharacteristic mGattStatusCloud;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private UUID uuidService = UUID.fromString("2c32fd5f-5082-437e-8501-959d23d3d2fb");
    private UUID uuidCharacteristic = UUID.fromString("dcaaccb4-c1d1-4bc4-b406-8f6f45df0208");
    private UUID uuidDateTime = UUID.fromString("e39c34e9-d574-47fc-a66e-425cec812aab");
    private UUID uuidStatusCloud = UUID.fromString("125ad2af-97cd-4f7a-b1e2-5109561f740d");

    private BluetoothGattService mGattService;

    Button valueTemperature;
    Button valueHumidity;
    Button valueCO;
    Button valueNO2;

    Context context;
    IoTStarterApplication app;
    BroadcastReceiver iotBroadCastReceiver;

    Call<StatusCloud> callStatus;
    Retrofit retrofit;
    RetrofitService retrofitService;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                updateDateTime();
                Handler handler = new Handler();

                handler.postDelayed(new Runnable() {
                    public void run() {
                        displayGattService();
                    }
                }, 10000); // 10 seconds of "delay"


            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                Handler handler = new Handler();

                handler.postDelayed(new Runnable() {
                    public void run() {
                        displayGattService();
                    }
                }, 10000); // 10 seconds of "delay"

                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));

            }
        }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_dashboard);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        user_id = intent.getStringExtra(USER_ID);


        valueTemperature = findViewById(R.id.btTemperature);
        valueHumidity = findViewById(R.id.btHumidity);
        valueCO = findViewById(R.id.btCO);
        valueNO2 = findViewById(R.id.btNO2);


        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

        context = this.getApplicationContext();
        // connect to the IoT platform and send random readings for now
        // how are these variables entered? How are they updated in the Application?
        app = (IoTStarterApplication) this.getApplication();
        app.setCurrentRunningActivity(TAG);

        if (iotBroadCastReceiver == null) {
            Log.d(TAG, ".onResume() - Registering iotBroadcastReceiver");
            iotBroadCastReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, ".onReceive() - Received intent for iotBroadcastReceiver");
                }
            };
        }

        this.getApplicationContext().registerReceiver(iotBroadCastReceiver,
                new IntentFilter(Constants.APP_ID + Constants.INTENT_IOT));


        app.setDeviceType(Constants.DEVICE_TYPE);
        app.setDeviceId(user_id.replace("@", "-"));
        app.setOrganization("p0g2ka");
        app.setAuthToken("prometeopriegoholaquetal");

        Log.d(TAG, "We are going to create the iotClient");
        IoTClient iotClient = IoTClient.getInstance(context, app.getOrganization(), app.getDeviceId(), app.getDeviceType(), app.getAuthToken());

        try {
            SocketFactory factory = null;
            // need to implement ssl here
            Log.d(TAG, "We are going to creat the listener");

            MyIoTActionListener listener = new MyIoTActionListener(context, Constants.ActionStateStatus.CONNECTING);
            Log.d(TAG, "Listener created");

            //start connection - if this method returns, connection has not yet happened
            iotClient.connectDevice(app.getMyIoTCallbacks(), listener, factory);
            Log.d(TAG, ".start connection");

            // iotClient.disconnectDevice(listener);
            Log.d(TAG, ".connectDevice finished");

        } catch (MqttException e) {
            if (e.getReasonCode() == (Constants.ERROR_BROKER_UNAVAILABLE)) {
                // error while connecting to the broker - send an intent to inform the user
                Intent actionIntent = new Intent(Constants.ACTION_INTENT_CONNECTIVITY_MESSAGE_RECEIVED);
                actionIntent.putExtra(Constants.CONNECTIVITY_MESSAGE, Constants.ERROR_BROKER_UNAVAILABLE);
                context.sendBroadcast(actionIntent);
            }
        }

        // We use retrofit to call the api res
        retrofit = new RetrofitAdapter().getAdapter();
        retrofitService = retrofit.create(RetrofitService.class);


    }

//    @Override
//    protected void onPause() {
//        super.onPause();
//        unregisterReceiver(mGattUpdateReceiver);
//    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        unbindService(mServiceConnection);
//        mBluetoothLeService = null;
//    }


    private void displayData(String data) {
        if (data != null) {

            // We display the data in the mobile screen
            String[] parts = data.split(" ");
            valueTemperature.setText(parts[0]+"\n celsius");
            valueHumidity.setText(parts[1]+"\n %");
            valueCO.setText(parts[2]+"\n ppm");
            valueNO2.setText(parts[3]+"\n ppm");

            // We send the data to the cloud through IOT Platform
            sendData(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));

            // We get the status from the cloud
            getStatus();

        }

    }

    private void getStatus() {
        callStatus = retrofitService.get_status(user_id, Calendar.getInstance().getTime().toString()); // TO-DO: timestamp of the device
        Log.d(TAG, "callStatus created");
        callStatus.enqueue(new Callback<StatusCloud>() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void onResponse(Call<StatusCloud> callStatus, Response<StatusCloud> response) {
                if (!response.isSuccessful()) {
                    Log.d(TAG, "Error when calling to get_status: "+response.code());
                    return;
                }
                else {
                    System.out.println(response.body().getFirefighter_id());
                    System.out.println(response.body().getStatus());
                    System.out.println(response.body().getTimestamp_mins());

                    updateStatusCloud(response.body().getStatus());

                    Log.d(TAG, "ha ido bien");
                }

            }

            @Override
            public void onFailure(Call<StatusCloud> callStatus, Throwable t) {
                System.out.println(t.getCause().toString());
                Log.d(TAG, "ha fallado");
            }
        });


    }


    private void sendData(float temperature, float humidity, float co, float no2) {
        try {
            Random random = new Random();
            // create a random PrometeoEvent
            PrometeoEvent pe = new PrometeoEvent();
            pe.setAcroleine((float) 0.0);
            pe.setBenzene((float) 0.0);
            pe.setCO(co);
            pe.setFormaldehyde((float) 0.0);
            pe.setNo2(no2);
            pe.setTemp(temperature);
            pe.setHumidity(humidity);

            // create ActionListener to handle message published results
            MyIoTActionListener listener = new MyIoTActionListener(context, Constants.ActionStateStatus.PUBLISH);
            IoTClient iotClient = IoTClient.getInstance(context);
            String messageData = MessageFactory.getPrometeoDeviceMessage(getMacAddress(this.getApplicationContext()), pe);


//            messageData = "{\"firefighter_id\": \"4fba5700ofifkf4404949499\"," +
//                    "\"device_id\": \"Prometeo93930293kdf0203\"," +
//                    "\"device_battery_level\": \"0\"," +
//                    "\"temperature\": 18.0," +
//                    "\"humidity\": 69.0," +
//                    "\"carbon_monoxide\": 2.0," +
//                    "\"nitrogen_dioxide\": 10.01," +
//                    "\"formaldehyde\": 23.22," +
//                    "\"acrolein\": 12," +
//                    "\"benzene\": 1.5," +
//                    "\"device_timestamp\": \"2020-09-10 09:32:38\"}"; //utc time


            iotClient.publishEvent(Constants.TEXT_EVENT, "json", messageData, 0, false, listener);

            int count = app.getPublishCount();
            app.setPublishCount(++count);

            String runningActivity = app.getCurrentRunningActivity();
            if (runningActivity != null && runningActivity.equals(this.getClass().getName())) {
                Intent actionIntent = new Intent(Constants.APP_ID + Constants.INTENT_IOT);
                actionIntent.putExtra(Constants.INTENT_DATA, Constants.INTENT_DATA_PUBLISHED);
                context.sendBroadcast(actionIntent);
            }
        } catch (MqttException e) {
            // Publish failed
        }
    }

    public String getMacAddress(Context context) {
        WifiManager wimanager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        String macAddress = wimanager.getConnectionInfo().getMacAddress();
        if (macAddress == null) {
            macAddress = "Device don't have mac address or wi-fi is disabled";
        }
        return macAddress;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void displayGattService() {

//        mGattService = mBluetoothLeService.getGattService(uuidService);
        mGattCharacteristic = mBluetoothLeService.getGattService(uuidService).getCharacteristic(uuidCharacteristic);

        if (mGattCharacteristic != null) {
            final BluetoothGattCharacteristic characteristic = mGattCharacteristic;
            final int charaProp = characteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                // If there is an active notification on a characteristic, clear
                // it first so it doesn't update the data field on the user interface.
                if (mNotifyCharacteristic != null) {
                    mBluetoothLeService.setCharacteristicNotification(
                            mNotifyCharacteristic, false);
                    mNotifyCharacteristic = null;

                }
                mBluetoothLeService.readCharacteristic(characteristic);
            }
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                mNotifyCharacteristic = characteristic;
                mBluetoothLeService.setCharacteristicNotification(
                        characteristic, true);
            }
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void updateDateTime() {

//        mGattService = mBluetoothLeService.getGattService(uuidService);
        mGattDateTime = mBluetoothLeService.getGattService(uuidService).getCharacteristic(uuidDateTime);

        if (mGattDateTime != null) {
            Date currentTime = Calendar.getInstance().getTime(); // convert into utc
            mGattDateTime.setValue(currentTime.toString());
            mBluetoothLeService.writeCharacteristic(mGattDateTime);
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void updateStatusCloud(Integer status_choice) {

//        mGattService = mBluetoothLeService.getGattService(uuidService);
        mGattStatusCloud = mBluetoothLeService.getGattService(uuidService).getCharacteristic(uuidStatusCloud);


        if (status_choice == -1) {
//            Integer[] status_choices = new Integer[3];
//            Random ga = new Random();

//            status_choices[0] = 3;   // red
//            status_choices[1] = 2;    // yellow
//            status_choices[2] = 1;    // green

//            int random_number = ga.nextInt(3);

//            status_choice = status_choices[random_number];

            status_choice = 3;  // red

        }
        if (mGattStatusCloud != null) {
            mGattStatusCloud.setValue(status_choice.toString());
            mBluetoothLeService.writeCharacteristic(mGattStatusCloud);
        }

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }



    public void scanClicked(View view) {
        Intent intent;

        intent = new Intent(DeviceDashboard.this, DeviceScanActivity.class);
        intent.putExtra(DeviceScanActivity.USER_ID, user_id);

        startActivity(intent);

    }
}