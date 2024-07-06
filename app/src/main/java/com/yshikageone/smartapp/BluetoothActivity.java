package com.yshikageone.smartapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class BluetoothActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothActivity";
    private static final String DEVICE_MAC_ADDRESS = "00:23:00:00:5F:1A";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final String CHANNEL_ID = "SoilMoistureNotificationChannel";
    private static final int SOIL_MOISTURE_HIGH_THRESHOLD = 700;
    private static final int SOIL_MOISTURE_LOW_THRESHOLD = 300;
    private static final long CHECK_INTERVAL = 5000;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private RelativeLayout bluetoothStatusContainer;
    private TextView bluetoothStatusText;
    private TextView soilMoistureValue;
    private TextView rawDataText;
    private Handler handler;
    private boolean isConnected = false;
    private Button buttonHighMoisture;
    private Button buttonLowMoisture;

    private ActivityResultLauncher<Intent> enableBluetoothLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        bluetoothStatusContainer = findViewById(R.id.bluetoothStatusContainer);
        bluetoothStatusText = findViewById(R.id.bluetoothStatusText);
        soilMoistureValue = findViewById(R.id.soilMoistureValue);
        rawDataText = findViewById(R.id.rawDataText);
        buttonHighMoisture = findViewById(R.id.buttonHighMoisture);
        buttonLowMoisture = findViewById(R.id.buttonLowMoisture);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler();

        //initialize the ActivityResultLauncher
        enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        //Bluetooth is enabled
                        connectToBluetoothDevice();
                    } else {
                        //Bluetooth is not enabled
                        Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        //check if Bluetooth is supported on the device
        if (bluetoothAdapter == null) {
            bluetoothStatusText.setText("Bluetooth not supported");
            return;
        }

        //request Bluetooth permissions if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            //permissions are already granted
            initializeBluetooth();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }

        //notification channel
        createNotificationChannel();

        //loop
        handler.postDelayed(checkConnectionRunnable, CHECK_INTERVAL);

        // Set up button click listeners
        buttonHighMoisture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNotification("The Soil Moisture is High, please checkup on the system.");
            }
        });

        buttonLowMoisture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNotification("The Soil Moisture is Low, please checkup on the system.");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //permissions granted
                initializeBluetooth();
            } else {
                //permissions denied
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeBluetooth() {
        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
        } else {
            // Bluetooth is already enabled
            connectToBluetoothDevice();

        }
    }

    private void connectToBluetoothDevice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
            return;
        }

        if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
            Log.d(TAG, "Bluetooth is already connected");
            return;
        }

        bluetoothDevice = bluetoothAdapter.getRemoteDevice(DEVICE_MAC_ADDRESS);

        int counter = 0;
        do {
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                System.out.println(bluetoothSocket);
                bluetoothSocket.connect();
                System.out.println(bluetoothSocket.isConnected());
                isConnected = true;
                updateUI(true);
                startListeningForData();
            } catch (IOException e) {
                e.printStackTrace();
                updateUI(false);
            }
            counter++;
        } while (!isConnected && counter < 3);
    }


    private void updateUI(boolean connected) {
        isConnected = connected;
        if (isConnected) {
            bluetoothStatusContainer.setBackgroundResource(R.drawable.shape_background_green);
            bluetoothStatusText.setText("Bluetooth: Connected");
        } else {
            bluetoothStatusContainer.setBackgroundResource(R.drawable.shape_background_red);
            bluetoothStatusText.setText("Bluetooth: Not Connected");
        }
    }

    private void startListeningForData() {
        System.out.println("Listening for data");
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                inputStream.skip(inputStream.available());
                byte[] buffer = new byte[1024];
                int bytes;
                while (true) {
                    bytes = inputStream.read(buffer);
                    String data = new String(buffer, 0, bytes);
                    Log.d(TAG, "Received data: " + data); // Log received data
                    runOnUiThread(() -> {
                        rawDataText.setText("Raw Data: " + data); // Update raw data TextView
                        handler.postDelayed(() -> updateSoilMoistureValue(data.trim()), 8000);
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateSoilMoistureValue(String data) {
        try {
            int soilMoisture = Integer.parseInt(data);
            soilMoistureValue.setText(String.valueOf(soilMoisture));
            if (soilMoisture > SOIL_MOISTURE_HIGH_THRESHOLD) {
                sendNotification("The Soil Moisture is High, please checkup on the system.");
            } else if (soilMoisture < SOIL_MOISTURE_LOW_THRESHOLD) {
                sendNotification("The Soil Moisture is Low, please checkup on the system.");
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse soil moisture value: " + data, e);
        }
    }

    private void sendNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Soil Moisture Alert")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);


        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());

        handler.postDelayed(() -> notificationManager.cancel(1), 5000);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Soil Moisture Notification Channel";
            String description = "Channel for soil moisture alerts";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private final Runnable checkConnectionRunnable = new Runnable() {
        @Override
        public void run() {
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                updateUI(true);
            } else {
                updateUI(false);
                connectToBluetoothDevice();
            }
            handler.postDelayed(this, CHECK_INTERVAL);
        }
    };
}