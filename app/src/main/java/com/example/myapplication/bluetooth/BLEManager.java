package com.example.myapplication.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.myapplication.LogManager;

import java.util.UUID;

public class BLEManager {

    private static final String TAG = "INTRAHACK";

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

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                String name = device.getName();
                if (name != null && name.equals(DEVICE_NAME)) {
                    LogManager.log(context, "BLE: Found Ring " + device.getAddress());
                    stopScan();
                    connectToDevice(device);
                }
            } catch (SecurityException e) {
                LogManager.log(context, "BLE Security Error during scan: " + e.getMessage());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            LogManager.log(context, "BLE: Scan failed with code " + errorCode);
            isScanning = false;
            if (callback != null) {
                callback.onError("Scan failed: " + errorCode);
            }
        }
    };

    public interface BLECallback {
        void onConnected();
        void onDisconnected();
        void onAccelDataReceived(short x, short y, short z, long timestamp);
        void onError(String message);
    }

    public BLEManager(Context context) {
        this.context = context;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
    }

    public void setCallback(BLECallback callback) {
        this.callback = callback;
    }

    public void startScan() {
        if (isScanning) return;

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            if (callback != null) callback.onError("Bluetooth is disabled or unavailable");
            return;
        }

        try {
            android.bluetooth.le.BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner == null) {
                if (callback != null) callback.onError("Scanner not available");
                return;
            }

            isScanning = true;
            LogManager.log(context, "BLE: Starting scan for " + DEVICE_NAME);
            scanner.startScan(scanCallback);

            handler.postDelayed(() -> {
                if (isScanning) {
                    LogManager.log(context, "BLE: Scan timeout");
                    stopScan();
                    if (callback != null) callback.onError("Ring not found");
                }
            }, 10000);
        } catch (SecurityException e) {
            LogManager.log(context, "BLE: Missing permissions for scanning");
            if (callback != null) callback.onError("Permission denied");
        }
    }

    public void stopScan() {
        if (!isScanning) return;
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.getBluetoothLeScanner() != null) {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            }
        } catch (SecurityException e) {
            LogManager.log(context, "BLE: Error stopping scan: " + e.getMessage());
        }
        isScanning = false;
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            LogManager.log(context, "BLE: Connecting to " + device.getAddress());
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            LogManager.log(context, "BLE: Connection permission error");
        }
    }

    public void disconnect() {
        try {
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        } catch (SecurityException e) {
            LogManager.log(context, "BLE: Disconnect error");
        }
        isConnected = false;
    }

    public void sendShockCommand(int durationMs, int intensity) {
        if (!isConnected || shockCharacteristic == null) return;

        try {
            short duration = (short) (durationMs & 0xFFFF);
            byte intensity_byte = (byte) (intensity & 0xFF);
            byte[] value = new byte[]{(byte) (duration & 0xFF), (byte) ((duration >> 8) & 0xFF), intensity_byte};
            
            shockCharacteristic.setValue(value);
            bluetoothGatt.writeCharacteristic(shockCharacteristic);
            LogManager.log(context, "BLE: Shock sent " + durationMs + "ms");
        } catch (SecurityException e) {
            LogManager.log(context, "BLE: Write permission error");
        }
    }

    public boolean isDeviceConnected() {
        return isConnected;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                LogManager.log(context, "BLE: Connected, discovering...");
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    LogManager.log(context, "BLE: Discover services permission error");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                LogManager.log(context, "BLE: Disconnected");
                isConnected = false;
                if (callback != null) callback.onDisconnected();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;

            android.bluetooth.BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
            if (service == null) return;

            accelCharacteristic = service.getCharacteristic(UUID.fromString(ACCEL_CHAR_UUID));
            shockCharacteristic = service.getCharacteristic(UUID.fromString(SHOCK_CMD_CHAR_UUID));

            if (accelCharacteristic != null) {
                try {
                    gatt.setCharacteristicNotification(accelCharacteristic, true);
                    BluetoothGattDescriptor cccd = accelCharacteristic.getDescriptor(UUID.fromString(CCCD_UUID));
                    if (cccd != null) {
                        cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(cccd);
                    }
                } catch (SecurityException e) {
                    LogManager.log(context, "BLE: Notification setup permission error");
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isConnected = true;
                if (callback != null) callback.onConnected();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().toString().equals(ACCEL_CHAR_UUID)) {
                byte[] val = characteristic.getValue();
                if (val != null && val.length >= 6) {
                    short x = (short) ((val[0] & 0xFF) | ((val[1] & 0xFF) << 8));
                    short y = (short) ((val[2] & 0xFF) | ((val[3] & 0xFF) << 8));
                    short z = (short) ((val[4] & 0xFF) | ((val[5] & 0xFF) << 8));
                    if (callback != null) callback.onAccelDataReceived(x, y, z, System.currentTimeMillis());
                }
            }
        }
    };
}
