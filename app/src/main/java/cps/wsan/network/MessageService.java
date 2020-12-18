package cps.wsan.network;

import android.os.ParcelUuid;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MessageService implements NetworkListener {
    private static final String TAG = MessageService.class.getSimpleName();

    public static final ParcelUuid UUID = ParcelUuid.fromString("95112dfd-bd82-4f2d-8d3d-acc995b56b45");

    private Map<Byte, Long> recentMessages;
    private Set<MessageListener> listeners;

    private AdhocNetwork network;
    private RoutingService routing;

    private byte ip;

    public MessageService(AdhocNetwork network, RoutingService routing, byte ip) {
        this.network = network;
        network.addListener(this);

        this.routing = routing;
        this.ip = ip;

        listeners = new HashSet<>();
        recentMessages = new HashMap<>();
    }

    @Override
    public void onMessage(ParcelUuid uuid, byte[] data) {
        if (!uuid.equals(UUID)) return;

        byte nextHop = data[0];
        byte source = data[1];
        byte dest = data[2];
        byte id = data[3];

        // Don't do anything if we are not supposed to do anything
        if (nextHop != ip) return;

        // Don't do anything if we have processed this message already
        long now = System.currentTimeMillis();
        long prev = recentMessages.containsKey(id) ? recentMessages.get(id) : 0;
        if (now < prev + 2 * AdhocNetwork.ADVERTISE_PERIOD) return;
        recentMessages.put(id, now);

        // Forward the message if we are supposed to do so
        if (dest != ip) {
            nextHop = routing.getNextHop(dest);
            data[0] = nextHop;
            network.advertise(UUID, data);
            byte finalNextHop = nextHop;
            listeners.forEach((l) -> l.onMessageForward(source, finalNextHop));
            return;
        }

        // Otherwise we must be the destination, so handle the message
        byte[] payload = new byte[data.length - 4];
        System.arraycopy(data, 4, payload, 0, payload.length);

        Log.v(TAG, String.format("Message received of length: %s (including 3 header bytes)", data.length));
        listeners.forEach((l) -> l.onMessageReceived(source, payload));
    }

    public void addListener(MessageListener l) {
        listeners.add(l);
    }

    public void removeListener(MessageListener l) {
        listeners.remove(l);
    }

    public interface MessageListener {
        void onMessageReceived(byte source, byte[] data);
        void onMessageForward(byte source, byte nextHop);
    }
}
