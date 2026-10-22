package app.rwun;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import java.util.*;

/** Standard BLE Heart Rate service. ANT-only original Instinct is not compatible. */
final class HeartRateClient {
  static final UUID SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
  static final UUID MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
  static final UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

  interface Listener {
    void status(String status);

    void heartRate(int bpm);
  }

  private final Context context;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Map<String, BluetoothDevice> devices = new LinkedHashMap<>();
  private BluetoothLeScanner scanner;
  private BluetoothGatt gatt;
  private boolean scanning;

  HeartRateClient(Context c, Listener l) {
    context = c;
    listener = l;
  }

  List<BluetoothDevice> devices() {
    return new ArrayList<>(devices.values());
  }

  private final ScanCallback scanCallback =
      new ScanCallback() {
        @Override
        public void onScanResult(int type, ScanResult result) {
          main.post(
              () -> {
                BluetoothDevice d = result.getDevice();
                devices.put(d.getAddress(), d);
                listener.status(
                    "Found " + devices.size() + " heart-rate sensor(s). Select one to connect.");
              });
        }

        @Override
        public void onScanFailed(int code) {
          main.post(
              () -> {
                scanning = false;
                listener.status("Bluetooth scan failed: " + code);
              });
        }
      };

  void scan() {
    stopScan();
    devices.clear();
    BluetoothManager manager = context.getSystemService(BluetoothManager.class);
    if (manager == null || manager.getAdapter() == null || !manager.getAdapter().isEnabled())
      throw new IllegalStateException("Enable Bluetooth first.");
    scanner = manager.getAdapter().getBluetoothLeScanner();
    if (scanner == null) throw new IllegalStateException("Bluetooth scanning is unavailable.");
    try {
      scanner.startScan(
          List.of(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE)).build()),
          new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
          scanCallback);
      scanning = true;
    } catch (SecurityException denied) {
      throw new IllegalStateException("Nearby Devices permission is required.", denied);
    }
    listener.status("Scanning for 12 seconds. Enable Broadcast Heart Rate on a compatible watch.");
    main.postDelayed(
        () -> {
          stopScan();
          if (devices.isEmpty())
            listener.status(
                "No Bluetooth HR sensor found. Original Instinct uses ANT+; use activity-file"
                    + " import instead.");
        },
        12000);
  }

  void stopScan() {
    if (scanning && scanner != null) {
      try {
        scanner.stopScan(scanCallback);
      } catch (SecurityException ignored) {
      }
      scanning = false;
    }
  }

  void connect(BluetoothDevice d) {
    stopScan();
    disconnect();
    listener.status("Connecting to heart-rate sensor…");
    try {
      gatt = d.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
    } catch (SecurityException denied) {
      listener.status("Nearby Devices permission was revoked.");
    }
  }

  private final BluetoothGattCallback callback =
      new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
          main.post(
              () -> {
                try {
                  if (status == BluetoothGatt.GATT_SUCCESS
                      && state == BluetoothProfile.STATE_CONNECTED) {
                    listener.status("Discovering heart-rate service…");
                    g.discoverServices();
                  } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    listener.heartRate(0);
                    listener.status("Heart-rate sensor disconnected. Reconnect from Wearable.");
                    if (gatt == g) gatt = null;
                    g.close();
                  }
                } catch (SecurityException denied) {
                  listener.heartRate(0);
                  listener.status("Nearby Devices permission was revoked.");
                }
              });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
          main.post(
              () -> {
                try {
                  BluetoothGattService s = g.getService(SERVICE);
                  BluetoothGattCharacteristic c =
                      s == null ? null : s.getCharacteristic(MEASUREMENT);
                  if (status != BluetoothGatt.GATT_SUCCESS || c == null) {
                    listener.status("Sensor has no standard heart-rate service.");
                    disconnect();
                    return;
                  }
                  BluetoothGattDescriptor descriptor = c.getDescriptor(CCC);
                  if (descriptor == null || !g.setCharacteristicNotification(c, true)) {
                    listener.status("Heart-rate notifications are unavailable.");
                    disconnect();
                    return;
                  }
                  boolean queued;
                  if (Build.VERSION.SDK_INT >= 33)
                    queued =
                        g.writeDescriptor(
                                descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            == BluetoothStatusCodes.SUCCESS;
                  else {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    queued = g.writeDescriptor(descriptor);
                  }
                  if (!queued) {
                    listener.status("Could not subscribe to heart rate.");
                    disconnect();
                  }
                } catch (SecurityException denied) {
                  listener.status("Nearby Devices permission was revoked.");
                  disconnect();
                }
              });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
          main.post(
              () ->
                  listener.status(
                      status == BluetoothGatt.GATT_SUCCESS
                          ? "Heart-rate sensor connected"
                          : "Heart-rate subscription failed: " + status));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
          receive(c.getValue());
        }

        @Override
        public void onCharacteristicChanged(
            BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
          receive(value);
        }
      };

  private void receive(byte[] v) {
    if (v == null || v.length < 2) return;
    int bpm =
        (v[0] & 1) == 0 ? (v[1] & 255) : (v.length >= 3 ? ((v[1] & 255) | ((v[2] & 255) << 8)) : 0);
    if (bpm > 0 && bpm < 250) main.post(() -> listener.heartRate(bpm));
  }

  void disconnect() {
    if (gatt != null) {
      try {
        gatt.disconnect();
        gatt.close();
      } catch (SecurityException ignored) {
      }
      gatt = null;
    }
  }

  void close() {
    main.removeCallbacksAndMessages(null);
    stopScan();
    disconnect();
  }
}
