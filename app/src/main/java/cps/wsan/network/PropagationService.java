package cps.wsan.network;

import android.os.ParcelUuid;
import android.util.Log;

public class PropagationService implements NetworkListener {
    private static final String TAG = PropagationService.class.getSimpleName();

    public static final ParcelUuid UUID = ParcelUuid.fromString("911ff9ac-315d-4475-b848-536d802640d7");

    private AdhocNetwork network;

    private byte lastId;

    public PropagationService(AdhocNetwork network) {
        this.network = network;
        network.addListener(this);

        lastId = -1;
    }

    @Override
    public void onMessage(ParcelUuid uuid, byte[] data) {
        if (!uuid.equals(UUID)) return;

        // Test message id
        if (data.length == 0) {
            Log.w(TAG, "Received a message of length 0");
            return;
        }

        byte id = data[0];
        if (id <= lastId) {
            if (id < -64 && lastId > 64) {
                Log.i(TAG, "ID window wrapped");
            } else return;
        }

        Log.v(TAG, "Propagating message with ID " + id);

        network.advertise(UUID, data);
        lastId = id;
    }

    public byte getNewId() {
        return ++lastId;
    }

}
