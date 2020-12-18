package cps.wsan.network;

import android.os.ParcelUuid;

public interface NetworkListener {

    void onMessage(ParcelUuid uuid, byte[] data);

}
