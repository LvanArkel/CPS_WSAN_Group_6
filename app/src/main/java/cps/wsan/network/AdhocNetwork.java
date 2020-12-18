package cps.wsan.network;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

public class AdhocNetwork {
    private final static String TAG = AdhocNetwork.class.getSimpleName();

    public static final long ADVERTISE_PERIOD = 1000;
    private static final long SCAN_PERIOD = 10000;

    private final BluetoothLeAdvertiser advertiser;
    private final BluetoothAdapter bt;
    private final BluetoothLeScanner scanner;

    private AdvertiseCallback advertiseCallback;
    private ScanCallback scanCallback;

    private final MessageService messageService;
    private final RoutingService routingService;

    private final List<NetworkListener> listeners;
    private final LinkedBlockingQueue<Packet> packetQueue;

    private boolean advertising;
    private byte ip;
    private boolean scanning;

    public AdhocNetwork(byte ip) {
        this.ip = ip;

        // Initialize the bluetooth adapter
        bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) throw new IllegalStateException("This device does not support BT");
        if (!bt.isEnabled()) throw new IllegalStateException("Bluetooth is not enabled");
        if (!bt.isMultipleAdvertisementSupported()) throw new IllegalStateException("Multiple advertisment is not supported");

        // Initialize the advertiser
        advertiser = bt.getBluetoothLeAdvertiser();
        if (advertiser == null) throw new IllegalStateException("BLE not supported");

        // Initialize the scanner
        scanner = bt.getBluetoothLeScanner();
        if (scanner == null) throw new IllegalStateException("BLE not supported");

        listeners = new LinkedList<>();
        packetQueue = new LinkedBlockingQueue<>();

        // Initialize services
        routingService = new RoutingService(this, ip);
        messageService = new MessageService(this, routingService, ip);

        routingService.start();
    }

    public void send(byte dest, byte[] payload) {
        byte[] packet = new byte[payload.length + 4];

        // Set headers
        packet[0] = routingService.getNextHop(dest);
        packet[1] = ip;
        packet[2] = dest;
        packet[3] = (byte) new Random().nextInt(127);

        // Copy payload
        System.arraycopy(payload, 0, packet, 4, payload.length);
        advertise(MessageService.UUID, packet);
    }

    /**
     * Advertises this device to other BLE devices.
     */
    public void advertise(ParcelUuid uuid, byte[] bytes) {
        Packet packet = new Packet();
        packet.uuid = uuid;
        packet.packet = bytes;
        packetQueue.add(packet);

        if (!advertising) this.restartAdvertise();
    }

    private synchronized void startAdvertise(ParcelUuid uuid, byte[] bytes) {
        if (advertising) return;
        advertising = true;

        // Set advertise settings
        AdvertiseSettings.Builder settings = new AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY); // or LOW_LATENCY / LOW_POWER

        // Set advertised data
        AdvertiseData.Builder data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceData(uuid, bytes);

        Log.v(TAG, String.format(
                "Advertising packet (%s bytes @ '%s')",
                bytes.length, uuid.toString()));

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
                Log.e(TAG, String.format("Advertising could not start (error: %s)", errorCode));
            }
        };

        advertiser.startAdvertising(settings.build(), data.build(), advertiseCallback);
    }

    private synchronized void restartAdvertise() {
        this.stopAdvertise();
        if (this.packetQueue.size() == 0) return;

        try {
            Packet p = packetQueue.take();
            this.startAdvertise(p.uuid, p.packet);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while advertising", e);
        }

        new Handler().postDelayed(this::restartAdvertise, ADVERTISE_PERIOD);
    }

    private synchronized void stopAdvertise() {
        if (!advertising) return;
        advertising = false;

        advertiser.stopAdvertising(advertiseCallback);
    }

    /**
     * Scans for nearby BLE devices, devices will be stored and listeners will be called whenever a
     * new device has been found.
     */
    public void scan() {
        startScan();
        restartScan();
    }

    private void restartScan() {
        // Either stop scanning
        if (!scanning) return;

        // Or extend the scanning period
        new Handler().postDelayed(this::restartScan, SCAN_PERIOD);
    }

    private synchronized void startScan() {
        if (scanning) return;
        scanning = true;

        ScanSettings.Builder settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                // Send data to listeners
                Map<ParcelUuid, byte[]> data = result.getScanRecord().getServiceData();
                data.forEach((uuid, bytes) -> listeners.forEach((l) -> l.onMessage(uuid, bytes)));
            }
        };

        scanner.startScan(new LinkedList<>(), settings.build(), scanCallback);
    }

    public synchronized void stopScan() {
        if (!scanning) return;
        scanning = false;

        scanner.stopScan(scanCallback);
    }

    public void addListener(NetworkListener l) {
        listeners.add(l);
    }

    public void removeListener(NetworkListener l) {
        listeners.remove(l);
    }

    public void clearListeners() {
        listeners.clear();
    }

    public RoutingService getRoutingService() {
        return routingService;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    private static class Packet {
        public ParcelUuid uuid;
        public byte[] packet;
    }
}
