package cps.wsan.network;

import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RoutingService implements NetworkListener {
    private static final String TAG = RoutingService.class.getSimpleName();

    private static final ParcelUuid UUID = ParcelUuid.fromString("e41cc060-9bbe-45cb-a7e1-8e8557652687");

    private static final long BROADCAST_FREQUENCY = 3000; // 5.0 s
    private static final long TIMEOUT = 7 * 1000; // 12.0 s
    private static final byte INFINITY = 127;

    private final Set<RoutingListener> listeners;

    private final AdhocNetwork network;

    // <ip, last update>
    private final Map<Byte, Long> neighbours;

    // <dest, path>
    private final Map<Byte, Path> routes;

    private final byte ip;
    private long lastUpdate;
    private boolean running;

    public RoutingService(AdhocNetwork network, byte ip) {
        this.network = network;
        network.addListener(this);

        this.ip = ip;

        listeners = new HashSet<>();
        neighbours = new HashMap<>();
        routes = new HashMap<>();

        Path self = new Path();
        self.dest = ip;
        self.cost = 0;
        self.lastUpdate = Long.MAX_VALUE - 2 * TIMEOUT;
        self.nextHop = ip;
        routes.put(ip, self);

        lastUpdate = 0;
        running = false;
    }

    private void update() {
        long now = System.currentTimeMillis();

        // Handle routing timeouts
        if (now > lastUpdate + TIMEOUT) {
            lastUpdate = now;

            // Remove all invalid neighbours
            Set<Byte> toRemove = new HashSet<>();
            for (byte neighbour : neighbours.keySet()) {
                if (neighbours.get(neighbour) + TIMEOUT > now) continue;

                // Remove all neighbours from which we haven't heard for a while
                toRemove.add(neighbour);
                Log.w(TAG, String.format("Disconnected from %s", neighbour));
            }

            toRemove.forEach((ip) -> neighbours.remove(ip));
            toRemove.clear();

            for (byte dest : routes.keySet()) {
                Path path = routes.get(dest);
                if (path.lastUpdate + TIMEOUT > now || path.cost == INFINITY) continue;

                // Remove all routes that haven't been updated for a while
                path.cost = INFINITY;
                path.lastUpdate = now + 3 * BROADCAST_FREQUENCY;
                path.nextHop = -1;

                Log.w(TAG, String.format("Lost the path to %s", dest));
                listeners.forEach((l) -> l.onPathDeleted(dest, path.cost, path.nextHop));
            }
        }

        // Then broadcast our updated routing table
        byte[] data = new byte[routes.size() * 3];
        data[0] = ip;
        data[1] = routes.get(ip).cost;
        data[2] = ip;

        int index = 3;
        for (Path path : routes.values()) {
            if (path.dest == ip) continue;

            // Don't broadcast routes that have expired already
            if (path.lastUpdate < now && path.cost == INFINITY) continue;

            data[index] = path.dest;
            data[index + 1] = path.cost;
            data[index + 2] = path.nextHop;

            index += 3;
        }

        // Compress (remove ill entries)
        byte[] packet = new byte[index];
        System.arraycopy(data, 0, packet, 0, packet.length);

        // Split up into multiple packets (if needed)
        List<byte[]> packets = new LinkedList<>();
        if (packet.length <= 12) packets.add(packet);
        else {
            int amount = packet.length / 3 - 1;
            for (int i = 0; i < amount; i += 3) {
                data = new byte[i + 3 < amount ? 12 : (amount - i + 1) * 3];

                // Copy routing header
                System.arraycopy(packet, 0, data, 0, 3);

                // Copy payload
                System.arraycopy(packet, (i + 1) * 3, data, 3, data.length - 3);
            }
        }

        // Send
        packets.forEach((p) -> network.advertise(UUID, p));

        // And requeue
        if (running) new Handler().postDelayed(this::update, BROADCAST_FREQUENCY);
    }

    @Override
    public void onMessage(ParcelUuid uuid, byte[] data) {
        if (!uuid.equals(UUID)) return;

        long now = System.currentTimeMillis();
        byte neighbour = data[0];
        neighbours.put(neighbour, now);

        // Loop through all entries in the received packet
        for (int i = 0; i < data.length; i += 3) {
            byte dest = data[i];
            byte cost = (byte) (data[i + 1] + 1);
            byte nextHop = data[i + 2];

            // Don't acknowledge any paths that route through me
            if (nextHop == ip) continue;

            // Cap the cost at INFINITY
            if (cost > INFINITY || cost < -16) cost = INFINITY;
            byte finalCost = cost;

            Path path = routes.get(dest);
            if (path == null) {
                // Create a new path if one did not exist already
                path = new Path();
                path.dest = dest;
                path.cost = cost;
                path.nextHop = neighbour;
                path.lastUpdate = now;

                routes.put(dest, path);

                if (cost != INFINITY) {
                    Log.i(TAG, String.format(
                            "Found new destination: %s via %s at cost %s",
                            dest, neighbour, cost));
                    listeners.forEach((l) -> l.onPathAdded(dest, finalCost, neighbour));
                }
            } else if (now >= path.lastUpdate) {
                if (path.nextHop == neighbour && cost >= INFINITY) {
                    // Remove a path and make sure it won't reset for a while
                    path.cost = INFINITY;
                    path.lastUpdate = now + 3 * BROADCAST_FREQUENCY;
                    path.nextHop = -1;

                    Log.w(TAG, String.format("Lost the path to %s", dest));
                    listeners.forEach((l) -> l.onPathDeleted(dest, finalCost, neighbour));
                } else if (path.nextHop == neighbour) {
                    // Always accept the path from the neighbour that has the current shortest path
                    path.cost = cost;
                    path.lastUpdate = now;
                } else if (cost < path.cost) {
                    // Accept a new path if it is shorter
                    byte oldCost = path.cost;
                    path.cost = cost;
                    path.nextHop = neighbour;
                    path.lastUpdate = now;

                    if (oldCost == INFINITY) {
                        Log.i(TAG, String.format(
                                "Reconnected to %s via %s at a cost of %s",
                                dest, neighbour, cost));
                        listeners.forEach((l) -> l.onPathAdded(dest, finalCost, neighbour));
                    } else {
                        Log.v(TAG, String.format(
                                "Found a shorter path to %s via %s at a cost of %s",
                                dest, neighbour, cost));
                        listeners.forEach((l) -> l.onPathUpdated(dest, finalCost, neighbour));
                    }

                }
            }
        }
    }

    public synchronized void start() {
        running = true;
        new Handler().postDelayed(this::update, BROADCAST_FREQUENCY);
    }

    public synchronized void stop() {
        running = false;
    }

    public byte getNextHop(byte dest) {
        Path path = routes.get(dest);

        if (path == null) return -1;
        return path.nextHop;
    }

    public Map<Byte, Path> getRoutes() {
        return new HashMap<>(routes);
    }

    public void addListener(RoutingListener l) {
        listeners.add(l);
    }

    public void removeListener(RoutingListener l) {
        listeners.remove(l);
    }

    public interface RoutingListener {

        void onPathAdded(byte dest, byte cost, byte nextHop);
        void onPathUpdated(byte dest, byte cost, byte nextHop);
        void onPathDeleted(byte dest, byte cost, byte nextHop);

    }

    private static class Path {

        public byte dest;
        public byte cost;
        public byte nextHop;
        public long lastUpdate;

    }
}
