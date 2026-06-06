package com.example.myapplication.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.UUID;

public class BLEManager {

    private static final String TAG = "BLEManager";

    // UUIDs matching ESP32 firmware
    private static final String SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0";
    private static final String ACCEL_CHAR_UUID = "87654321-4321-8765-4321-210987654321";
    private static final String SHOCK_CMD_CHAR_UUID = "11111111-2222-3333-4444-555555555555";
    private static final String CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private static final String DEVICE_NAME = "DoomStop-Ring";

    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private BluetoothGattCharacteristic accelCharacteristic;
    private BluetoothGattCharacteristic shockCharacteristic;

    private Handler handler = new Handler(Looper.getMainLooper());
    private BLECallback callback;
    private boolean isScanning = false;
    private boolean isConnected = false;

    public interface BLECallback {
        void onConnected();
        void onDisconnected();
        void onAccelDataReceived(short x, short y, short z, long timestamp);
        void onError(String message);
    }

    public BLEManager(Context context) {
        this.context = context;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }

    public void setCallback(BLECallback callback) {
        this.callback = callback;
    }

    public void startScan() {
        if (isScanning) {
            Log.w(TAG, "Already scanning");
            return;
        }

        if (bluetoothAdapter == null) {
            if (callback != null) {
                callback.onError("Bluetooth adapter not available");
            }
            return;
        }

        isScanning = true;
        Log.i(TAG, "Starting BLE scan for DoomStop-Ring");

        android.bluetooth.le.BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            if (callback != null) {
                callback.onError("Bluetooth scanner not available");
            }
            return;
        }

        scanner.startScan(new android.bluetooth.le.ScanCallback() {
            @Override
            public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                if (result.getDevice().getName() != null &&
                        result.getDevice().getName().equals(DEVICE_NAME)) {
                    Log.i(TAG, "Found DoomStop ring: " + result.getDevice().getAddress());
                    stopScan();
                    connectToDevice(result.getDevice());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Scan failed: " + errorCode);
                isScanning = false;
                if (callback != null) {
                    callback.onError("Scan failed: " + errorCode);
                }
            }
        });

        // Auto-stop after 15 seconds
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    Log.w(TAG, "Scan timeout");
                    stopScan();
                    if (callback != null) {
                        callback.onError("Device not found");
                    }
                }
            }
        }, 15000);
    }

    public void stopScan() {
        if (bluetoothAdapter != null) {
            android.bluetooth.le.BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner != null) {
                scanner.stopScan(new android.bluetooth.le.ScanCallback() {});
            }
        }
        isScanning = false;
    }

    private void connectToDevice(BluetoothDevice device) {
        Log.i(TAG, "Connecting to " + device.getAddress());
        bluetoothGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
        );
    }

    public void disconnect() {
        Log.i(TAG, "Disconnecting");
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        isConnected = false;
    }

    public void sendShockCommand(int durationMs, int intensity) {
        if (!isConnected || bluetoothGatt == null) {
            Log.w(TAG, "Not connected to device");
            return;
        }

        if (shockCharacteristic == null) {
            Log.e(TAG, "Shock characteristic not found");
            return;
        }

        short duration = (short) (durationMs & 0xFFFF);
        byte intensity_byte = (byte) (intensity & 0xFF);

        byte[] value = new byte[3];
        value[0] = (byte) (duration & 0xFF);
        value[1] = (byte) ((duration >> 8) & 0xFF);
        value[2] = intensity_byte;

        shockCharacteristic.setValue(value);

        if (!bluetoothGatt.writeCharacteristic(shockCharacteristic)) {
            Log.e(TAG, "Failed to write shock command");
        } else {
            Log.i(TAG, "Shock sent: " + durationMs + "ms @ " + intensity);
        }
    }

    public boolean isDeviceConnected() {
        return isConnected;
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Connection failed: " + status);
                if (callback != null) {
                    callback.onError("Connection failed");
                }
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected, discovering services");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected");
                isConnected = false;
                if (callback != null) {
                    callback.onDisconnected();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed");
                if (callback != null) {
                    callback.onError("Service discovery failed");
                }
                return;
            }

            Log.i(TAG, "Services discovered");

            android.bluetooth.BluetoothGattService service = gatt.getService(
                    UUID.fromString(SERVICE_UUID)
            );
            if (service == null) {
                Log.e(TAG, "DoomStop service not found");
                if (callback != null) {
                    callback.onError("Service not found");
                }
                return;
            }

            accelCharacteristic = service.getCharacteristic(UUID.fromString(ACCEL_CHAR_UUID));
            shockCharacteristic = service.getCharacteristic(UUID.fromString(SHOCK_CMD_CHAR_UUID));

            if (accelCharacteristic == null || shockCharacteristic == null) {
                Log.e(TAG, "Characteristics not found");
                if (callback != null) {
                    callback.onError("Characteristics not found");
                }
                return;
            }

            if (!gatt.setCharacteristicNotification(accelCharacteristic, true)) {
                Log.e(TAG, "Failed to enable notifications");
                return;
            }

            BluetoothGattDescriptor cccd = accelCharacteristic.getDescriptor(
                    UUID.fromString(CCCD_UUID)
            );
            if (cccd != null) {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(cccd);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Connection complete");
                isConnected = true;
                if (callback != null) {
                    callback.onConnected();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().toString().equals(ACCEL_CHAR_UUID)) {
                byte[] value = characteristic.getValue();
                if (value == null || value.length < 10) {
                    return;
                }

                short x = byteArrayToShort(value, 0);
                short y = byteArrayToShort(value, 2);
                short z = byteArrayToShort(value, 4);
                long timestamp = byteArrayToInt(value, 6) & 0xFFFFFFFFL;

                if (callback != null) {
                    callback.onAccelDataReceived(x, y, z, timestamp);
                }

                Log.d(TAG, "Accel: x=" + x + " y=" + y + " z=" + z);
            }
        }
    };

    private short byteArrayToShort(byte[] data, int offset) {
        return (short) (
                (data[offset] & 0xFF) |
                        ((data[offset + 1] & 0xFF) << 8)
        );
    }

    private int byteArrayToInt(byte[] data, int offset) {
        return (
                (data[offset] & 0xFF) |
                        ((data[offset + 1] & 0xFF) << 8) |
                        ((data[offset + 2] & 0xFF) << 16) |
                        ((data[offset + 3] & 0xFF) << 24)
        );
    }
}