package no.nordicsemi.android.nrfthingy;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import cps.wsan.network.AdhocNetwork;
import cps.wsan.network.MessageService;
import cps.wsan.network.NetworkListener;
import cps.wsan.network.RoutingService;
import no.nordicsemi.android.nrfthingy.common.Utils;

public class SinkFragment extends Fragment {

    TextView cheadView;
    TextView eventView;
    Map<Byte, Byte> clusterheads;
    Queue<String> eventQueue = new LinkedList<>();
    private static final int EVENT_SIZE = 10;
    private int eventCounter = 0;
    AdhocNetwork network;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_sink, container, false);
        clusterheads = new HashMap<>();
        network = new AdhocNetwork((byte) 0);
        network.getRoutingService().addListener(new RoutingService.RoutingListener() {
            @Override
            public void onPathAdded(byte dest, byte cost, byte nextHop) {
                clusterheads.put(dest, nextHop);
                update();
            }

            @Override
            public void onPathUpdated(byte dest, byte cost, byte nextHop) {
                clusterheads.put(dest, nextHop);
                update();
            }

            @Override
            public void onPathDeleted(byte dest, byte cost, byte nextHop) {
                clusterheads.remove(dest);
                update();
            }
        });
        network.getMessageService().addListener(new MessageService.MessageListener() {
            @Override
            public void onMessageReceived(byte source, byte[] data) {
                Log.i("aa", "received msg");
                event(String.format("Event from <b>%s<b>: %s", source, new String(data)));
            }

            @Override
            public void onMessageForward(byte source, byte nextHop) {
                // should never happen, as we are the final destination!
            }
        });
        network.scan();
        Log.i("ss", "starting scna");
        cheadView = rootView.findViewById(R.id.cheadContent);
        eventView = rootView.findViewById(R.id.eventContent);
        Button testButton = rootView.findViewById(R.id.eventTest);
        testButton.setOnClickListener(v -> testEvent());
        update();

        return rootView;
    }

    public static SinkFragment newInstance() {
        return new SinkFragment();
    }

    private void update() {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (Byte dest : clusterheads.keySet()) {
            ssb.append(Html.fromHtml(String.format("ID: <b>%s</b> (next hop: <i>%s</i>)",
                    dest, clusterheads.get(dest)))).append("\n");
        }
        cheadView.setText(ssb);
        ssb = new SpannableStringBuilder();
        for (String event : eventQueue) {
            ssb.append(Html.fromHtml(event)).append("\n");
        }
        eventView.setText(ssb);
    }

    public void event(String text) {
        eventQueue.add(text);
        if (eventQueue.size() > EVENT_SIZE) {
            eventQueue.poll();
        }
        update();
    }

    public void testEvent() {
         event(String.format("Testevent <b>%d</b>", eventCounter++));
    }


}
