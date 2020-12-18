/*
 * Copyright (c) 2010 - 2017, Nordic Semiconductor ASA
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form, except as embedded into a Nordic
 *    Semiconductor ASA integrated circuit in a product or a software update for
 *    such product, must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. Neither the name of Nordic Semiconductor ASA nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * 4. This software, with or without modification, must only be used with a
 *    Nordic Semiconductor ASA integrated circuit.
 *
 * 5. Any software provided in binary form under this license must not be reverse
 *    engineered, decompiled, modified and/or disassembled.
 *
 * THIS SOFTWARE IS PROVIDED BY NORDIC SEMICONDUCTOR ASA "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY, NONINFRINGEMENT, AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NORDIC SEMICONDUCTOR ASA OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.nrfthingy;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.SpannableString;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.tabs.TabLayout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import cps.wsan.audio.AmplitudeLoud;
import cps.wsan.network.AdhocNetwork;
import cps.wsan.network.MessageService;
import cps.wsan.network.RoutingService;
import no.nordicsemi.android.nrfthingy.common.MessageDialogFragment;
import no.nordicsemi.android.nrfthingy.common.PermissionRationaleDialogFragment;
import no.nordicsemi.android.nrfthingy.common.Utils;
import no.nordicsemi.android.nrfthingy.database.DatabaseContract;
import no.nordicsemi.android.nrfthingy.database.DatabaseHelper;
import no.nordicsemi.android.nrfthingy.sound.FrequencyModeFragment;
import no.nordicsemi.android.nrfthingy.sound.ThingyMicrophoneService;
import no.nordicsemi.android.nrfthingy.thingy.ThingyService;
import no.nordicsemi.android.nrfthingy.widgets.VoiceVisualizer;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;
import no.nordicsemi.android.thingylib.ThingyListenerHelper;
import no.nordicsemi.android.thingylib.ThingySdkManager;
import no.nordicsemi.android.thingylib.utils.ThingyUtils;

import static no.nordicsemi.android.thingylib.utils.ThingyUtils.LED_GREEN;

public class SoundFragment extends Fragment implements PermissionRationaleDialogFragment.PermissionDialogListener {
    private static final String AUDIO_PLAYING_STATE = "AUDIO_PLAYING_STATE";
    private static final String AUDIO_RECORDING_STATE = "AUDIO_RECORDING_STATE";
    private static final float ALPHA_MAX = 0.60f;
    private static final float ALPHA_MIN = 0.0f;
    private static final int DURATION = 800;
    private static final int MAX_THINGIES = 4;
    private final static long SCAN_DURATION = 8000;
    private final Handler mHandler = new Handler();
    private final String TAG = "ThingyConnectionKevin";
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private ImageView mMicrophone;
    private ImageView mMicrophoneOverlay;
    private ImageView mThingyOverlay;
    private ImageView mThingy;
    private VoiceVisualizer mVoiceVisualizer;
    private ArrayList<BluetoothDevice> mDevices;
    private Map<Integer, BluetoothDevice> mEventOrchestrator;
    private FragmentAdapter mFragmentAdapter;
    private ThingySdkManager mThingySdkManager;
    private boolean mStartRecordingAudio = false;
    private boolean mStartPlayingAudio = false;
    private AdhocNetwork network;
    private Button mAdvertiseButton;
    private Button mTestnet;
    private EditText mClhIDInput;
    private TextView mClhLog;
    private HashMap<ScanResult, Integer> deviceStrength = new HashMap<>();

    private final BroadcastReceiver mAudioRecordBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.startsWith(Utils.EXTRA_DATA_AUDIO_RECORD)) {
                final byte[] tempPcmData = intent.getExtras().getByteArray(ThingyUtils.EXTRA_DATA_PCM);
                final int length = intent.getExtras().getInt(ThingyUtils.EXTRA_DATA);
                if (tempPcmData != null) {
                    if (length != 0) {
                        mVoiceVisualizer.draw(tempPcmData);
                    }
                }
            } else if (action.equals(Utils.ERROR_AUDIO_RECORD)) {
                final String error = intent.getExtras().getString(Utils.EXTRA_DATA);
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            }
        }
    };
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, @NonNull final ScanResult result) {
            // do nothing
        }

        @Override
        public void onBatchScanResults(final List<ScanResult> results) {
            for (ScanResult device : results) {
                Log.d(TAG, "onBatchScanResults: " + device.getRssi() + ";" + device.getDevice().getAddress());

                boolean alreadyFound = false;
                for (Map.Entry<ScanResult, Integer> thingy : deviceStrength.entrySet()) {
                    if (thingy.getKey().getDevice().getAddress().equals(device.getDevice().getAddress())) {
                        alreadyFound = true;
                    }
                }
                if (!alreadyFound) {
                    deviceStrength.put(device, device.getRssi());
                }
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            // should never be called
        }
    };
    private DatabaseHelper mDatabaseHelper;
    private ByteBuffer buf = ByteBuffer.allocate(2000000);

    public static SoundFragment newInstance(final BluetoothDevice device) {
        SoundFragment fragment = new SoundFragment();
        final Bundle args = new Bundle();
        args.putParcelable(Utils.CURRENT_DEVICE, device);
        fragment.setArguments(args);
        return fragment;
    }

    private static IntentFilter createAudioRecordIntentFilter(final String address) {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(Utils.EXTRA_DATA_AUDIO_RECORD + address);
        intentFilter.addAction(Utils.ERROR_AUDIO_RECORD);
        return intentFilter;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDevices = new ArrayList<>();
        mEventOrchestrator = new HashMap<>();
        mThingySdkManager = ThingySdkManager.getInstance();

        final int interval = 5000;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mEventOrchestrator.isEmpty()) {
                    int maxAmplitude = 0;
                    BluetoothDevice maxBluetooth = null;
                    for (Map.Entry<Integer, BluetoothDevice> entry : mEventOrchestrator.entrySet()) {
                        if (entry.getKey() > maxAmplitude) {
                            maxAmplitude = entry.getKey();
                            maxBluetooth = entry.getValue();
                        }
                    }
                    if (maxBluetooth != null) {
                        mThingySdkManager.setConstantLedMode(maxBluetooth, 255, 0, 0);
                        if (maxBluetooth.getName() != null && network != null) {
                            network.send((byte) (0 & 0xFF), maxBluetooth.getName().replace("Zone", "").getBytes());
                        }
                        mClhLog.append("    Loudest was " + maxBluetooth.getAddress() + "\r\n");
                    }
                    mEventOrchestrator.clear();
                }
                mHandler.postDelayed(this, interval);
            }
        }, interval);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_sound, container, false);

        final Toolbar speakerToolbar = rootView.findViewById(R.id.speaker_toolbar);
        speakerToolbar.inflateMenu(R.menu.audio_warning);
        speakerToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final int id = item.getItemId();
                switch (id) {
                    case R.id.action_audio_warning:
                        MessageDialogFragment fragment = MessageDialogFragment.newInstance(getString(R.string.info), getString(R.string.mtu_warning));
                        fragment.show(getChildFragmentManager(), null);
                        break;
                }
                return false;
            }
        });

        final Toolbar microphoneToolbar = rootView.findViewById(R.id.microphone_toolbar);
        microphoneToolbar.inflateMenu(R.menu.audio_warning);
        microphoneToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final int id = item.getItemId();
                switch (id) {
                    case R.id.action_audio_warning:
                        MessageDialogFragment fragment = MessageDialogFragment.newInstance(getString(R.string.info), getString(R.string.mtu_warning));
                        fragment.show(getChildFragmentManager(), null);
                        break;
                }
                return false;
            }
        });

        mMicrophone = rootView.findViewById(R.id.microphone);
        mMicrophoneOverlay = rootView.findViewById(R.id.microphoneOverlay);
        mThingy = rootView.findViewById(R.id.thingy);
        mThingyOverlay = rootView.findViewById(R.id.thingyOverlay);
        mVoiceVisualizer = rootView.findViewById(R.id.voice_visualizer);

        // Prepare the sliding tab layout and the view pager
        final TabLayout mTabLayout = rootView.findViewById(R.id.sliding_tabs);
        final ViewPager pager = rootView.findViewById(R.id.view_pager);
        mFragmentAdapter = new FragmentAdapter(getChildFragmentManager());
        pager.setAdapter(mFragmentAdapter);
        mTabLayout.setupWithViewPager(pager);

        mThingy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mThingySdkManager.disconnectFromAllThingies();
                mDatabaseHelper = new DatabaseHelper(getContext());
                startScan();
                deviceStrength = new HashMap<>();
            }
//            }
        });

        if (savedInstanceState != null) {
            mStartPlayingAudio = savedInstanceState.getBoolean(AUDIO_PLAYING_STATE);
            mStartRecordingAudio = savedInstanceState.getBoolean(AUDIO_RECORDING_STATE);

            if (mStartPlayingAudio) {
                startThingyOverlayAnimation();
            }

            if (mStartRecordingAudio) {
                for (BluetoothDevice thingy : mDevices) {
                    if (mThingySdkManager.isConnected(thingy)) {
                        startMicrophoneOverlayAnimation();
                        sendAudiRecordingBroadcast();
                    }
                }
            }
        }

        mAdvertiseButton = rootView.findViewById(R.id.startClh_btn);
        mClhIDInput = rootView.findViewById(R.id.clhIDInput_text);
        mClhLog = rootView.findViewById(R.id.logClh_text);
        mTestnet = rootView.findViewById(R.id.testnet);


        mTestnet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (network != null) {
                    mClhLog.append("sending hi to sink\r\n");
                    network.send((byte) (0 & 0xFF), "Hi".getBytes());
                } else {
                    mClhLog.append("net not init\r\n");
                }
            }
        });

        mAdvertiseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                network = new AdhocNetwork((byte) Integer.parseInt(mClhIDInput.getText().toString()));

                mClhLog.append("networking as node " + (byte) Integer.parseInt(mClhIDInput.getText().toString()) + " \r\n");
                network.getRoutingService().addListener(new RoutingService.RoutingListener() {
                    @Override
                    public void onPathAdded(byte dest, byte cost, byte nextHop) {
                        mClhLog.append("path addedd to " + dest + "; cost:" + cost + "; nexthop:" + nextHop + " \r\n");
                    }

                    @Override
                    public void onPathUpdated(byte dest, byte cost, byte nextHop) {
                        mClhLog.append("path updated to " + dest + "; cost:" + cost + "; nexthop:" + nextHop + " \r\n");
                    }

                    @Override
                    public void onPathDeleted(byte dest, byte cost, byte nextHop) {
                        mClhLog.append("path deleted to " + dest + "; cost:" + cost + "; nexthop:" + nextHop + " \r\n");
                    }
                });
                network.getMessageService().addListener(new MessageService.MessageListener() {
                    @Override
                    public void onMessageReceived(byte source, byte[] data) {
                        mClhLog.append("Received an event from " + source + " : " + new String(data) + "\r\n");
                    }

                    @Override
                    public void onMessageForward(byte source, byte nextHop) {
                        mClhLog.append("Received an event from " + source + " for " + nextHop + "\r\n");
                    }
                });
                mClhLog.append("adhoc scan\r\n");
                network.scan();
            }
        });
        mClhIDInput.setText("2");


        loadFeatureDiscoverySequence();
        return rootView;
    }

    private void sendAudiRecordingBroadcast() {
        Intent startAudioRecording = new Intent(getActivity(), ThingyMicrophoneService.class);
        startAudioRecording.setAction(Utils.START_RECORDING);
        for (BluetoothDevice device : mDevices) {
            startAudioRecording.putExtra(Utils.EXTRA_DEVICE, device);
        }
        getActivity().startService(startAudioRecording);
    }

    private void stop() {
        final Intent s = new Intent(Utils.STOP_RECORDING);
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(s);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUDIO_PLAYING_STATE, mStartPlayingAudio);
        outState.putBoolean(AUDIO_RECORDING_STATE, mStartRecordingAudio);
    }

    @Override
    public void onResume() {
        super.onResume();
        for (BluetoothDevice thingy : mDevices) {
//            ThingyListenerHelper.registerThingyListener(getContext(), mThingyListener, thingy);
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mAudioRecordBroadcastReceiver, createAudioRecordIntentFilter(thingy.getAddress()));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
//        ThingyListenerHelper.unregisterThingyListener(getContext(), mThingyListener);
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mAudioRecordBroadcastReceiver);
        mVoiceVisualizer.stopDrawing();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopRecording();
        stopThingyOverlayAnimation();
    }

    @Override
    public void onRequestPermission(final String permission, final int requestCode) {
        // Since the nested child fragment (activity > fragment > fragment) wasn't getting called
        // the exact fragment index has to be used to get the fragment.
        // Also super.onRequestPermissionResult had to be used in both the main activity, fragment
        // in order to propagate the request permission callback to the nested fragment
        requestPermissions(new String[]{permission}, requestCode);
    }

    @Override
    public void onCancellingPermissionRationale() {
        Utils.showToast(getActivity(), getString(R.string.requested_permission_not_granted_rationale));
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Utils.REQ_PERMISSION_RECORD_AUDIO:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Utils.showToast(getActivity(), getString(R.string.rationale_permission_denied));
                } else {
                    startRecording();
                }
        }
    }

    private void checkMicrophonePermissions() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            final PermissionRationaleDialogFragment dialog = PermissionRationaleDialogFragment.getInstance(Manifest.permission.RECORD_AUDIO,
                    Utils.REQ_PERMISSION_RECORD_AUDIO, getString(R.string.microphone_permission_text));
            dialog.show(getChildFragmentManager(), null);
        }
    }

    private void startRecording() {
        startMicrophoneOverlayAnimation();
        sendAudiRecordingBroadcast();
        mStartRecordingAudio = true;
    }

    private void stopRecording() {
        stopMicrophoneOverlayAnimation();
        stop();
        mStartRecordingAudio = false;
    }

    private void startMicrophoneOverlayAnimation() {
        mThingy.setEnabled(false);
        mMicrophone.setImageResource(R.drawable.ic_mic_white_off);
        mMicrophone.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_device_bg_red));
        mMicrophoneOverlay.animate().alpha(ALPHA_MAX).setDuration(DURATION).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (mMicrophoneOverlay.getAlpha() == ALPHA_MAX) {
                    mMicrophoneOverlay.animate().alpha(ALPHA_MIN).setDuration(DURATION).withEndAction(this).start();
                } else {
                    mMicrophoneOverlay.animate().alpha(ALPHA_MAX).setDuration(DURATION).withEndAction(this).start();
                }
            }
        }).start();
    }

    private void stopMicrophoneOverlayAnimation() {
        mThingy.setEnabled(true);
        mStartRecordingAudio = false;
        mMicrophoneOverlay.animate().cancel();
        mMicrophoneOverlay.setAlpha(ALPHA_MIN);
        mMicrophone.setImageResource(R.drawable.ic_mic_white);
        mMicrophone.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_device_bg_blue));
    }

    private void startThingyOverlayAnimation() {
        mMicrophone.setEnabled(false);
        mThingy.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_device_bg_red));
        mThingyOverlay.animate().alpha(ALPHA_MAX).setDuration(DURATION).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (mThingyOverlay.getAlpha() == ALPHA_MAX) {
                    mThingyOverlay.animate().alpha(ALPHA_MIN).setDuration(DURATION).withEndAction(this).start();
                } else {
                    mThingyOverlay.animate().alpha(ALPHA_MAX).setDuration(DURATION).withEndAction(this).start();
                }
            }
        }).start();
    }

    private void stopThingyOverlayAnimation() {
        mMicrophone.setEnabled(true);
        mThingyOverlay.animate().cancel();
        mThingyOverlay.setAlpha(ALPHA_MIN);
        mThingy.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_device_bg_blue));
        mStartPlayingAudio = false;
    }

    private void loadFeatureDiscoverySequence() {
        if (!Utils.checkIfSequenceIsCompleted(requireContext(), Utils.INITIAL_SOUND_TUTORIAL)) {

            final SpannableString advertisebutton = new SpannableString("Tap here to start the AD-Hoc network");
            final SpannableString thingy = new SpannableString("Tap here to scan for thingies, after which the listeners will be activated");

            final TapTargetSequence sequence = new TapTargetSequence(requireActivity());
            sequence.continueOnCancel(true);
            sequence.targets(
                    TapTarget.forView(mAdvertiseButton, advertisebutton).
                            transparentTarget(true).
                            dimColor(R.color.grey).
                            outerCircleColor(R.color.accent).id(0),
                    TapTarget.forView(mThingy, thingy).
                            transparentTarget(true).
                            dimColor(R.color.grey).
                            outerCircleColor(R.color.accent).id(1)
            ).listener(new TapTargetSequence.Listener() {
                @Override
                public void onSequenceFinish() {
                    Utils.saveSequenceCompletion(requireContext(), Utils.INITIAL_SOUND_TUTORIAL);
                }

                @Override
                public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {

                }

                @Override
                public void onSequenceCanceled(TapTarget lastTarget) {

                }
            }).start();
        }
    }

    /**
     * Scan for 5 seconds and then stop scanning when a BluetoothLE device is found then mLEScanCallback is activated This will perform regular scan for custom BLE Service UUID and then filter out
     * using class ScannerServiceParser
     */
    private void startScan() {
        if (scanning.compareAndSet(false, true)) { // dont want multiple scans at the same time!
            mClhLog.append("Scanning\r\n");
            // Since Android 6.0 we need to obtain either Manifest.permission.ACCESS_FINE_LOCATION or Manifest.permission.ACCESS_FINE_LOCATION to be able to scan for
            // Bluetooth LE devices. This is related to beacons as proximity devices.
            // On API older than Marshmallow the following code does nothing.
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // When user pressed Deny and still wants to use this functionality, show the rationale
                if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                    return;
                }
                int REQUEST_PERMISSION_REQ_CODE = 76;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_REQ_CODE);
                return;
            }

            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            final ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(750).setUseHardwareBatchingIfSupported(false)
                    .setUseHardwareFilteringIfSupported(false)
                    .build();
            final List<ScanFilter> filters = new ArrayList<>();
            filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(ThingyUtils.THINGY_BASE_UUID)).build());
            scanner.stopScan(scanCallback);
            scanner.startScan(filters, settings, scanCallback);

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    connect();
                    scanning.set(false);
                }
            }, SCAN_DURATION);
        }
    }

    private void connect() {
        ArrayList<Integer> rssiValues = new ArrayList<>(deviceStrength.values());
        Collections.sort(rssiValues, Collections.reverseOrder());
        mClhLog.append("Connecting to best " + MAX_THINGIES + " out of " + rssiValues.size() + "\r\n");

        for (Map.Entry<ScanResult, Integer> entry : deviceStrength.entrySet()) {
            final BluetoothDevice thingy = entry.getKey().getDevice();
            mClhLog.append("    FOUND:" + thingy.getName() + "; " + thingy.getAddress() + "\r\n");
            if (mClhIDInput.getText() == null || thingy == null || thingy.getName() == null) {
                continue;
            }
            if (thingy.getName().startsWith("Zone" + mClhIDInput.getText().toString())) {
                if (!mThingySdkManager.isConnected(thingy)) {
                    mThingySdkManager.connectToThingy(getContext(), thingy, ThingyService.class);
                    enableSoundNotifications(thingy, true);
                    enableUiNotifications(thingy);

                    mDevices.add(thingy);
                    ThingyListenerHelper.registerThingyListener(getContext(), new SoundThingyListener(mEventOrchestrator), thingy);
                }
            }
        }

        // TODO Connecting with RSSI

//        for (int i = 0; i < MAX_THINGIES; i++) {
//            if (rssiValues.size() >= i + 1) {
//                // Get the thingy belonging to the RSSI value
//                final BluetoothDevice thingy = getScanResultFor(rssiValues.get(i), deviceStrength).getDevice();
//                if(!mThingySdkManager.isConnected(thingy)){ // If we are not yet connected
//                    // Make connection to the thingy
//                    mThingySdkManager.connectToThingy(getContext(), thingy, ThingyService.class);
//
//                    // Necessary to change LED's and record Audio from thingies:
//                    enableSoundNotifications(thingy, true);
//                    enableUiNotifications(thingy);
//                    mDevices.add(thingy);
//                    ThingyListenerHelper.registerThingyListener(getContext(), new SoundThingyListener(mEventOrchestrator), thingy);
//                }
//            }
//        }
    }

    private ScanResult getScanResultFor(int rssi, HashMap<ScanResult, Integer> results) {
        for (Map.Entry<ScanResult, Integer> result : results.entrySet()) {
            if (result.getValue() == rssi) {
                return result.getKey();
            }
        }
        return null;
    }

    public void enableSoundNotifications(final BluetoothDevice device, final boolean flag) {
        if (mThingySdkManager != null) {
            mThingySdkManager.requestMtu(device);
            mThingySdkManager.enableSpeakerStatusNotifications(device, flag);
        }
    }

    private void enableUiNotifications(final BluetoothDevice device) {
        final String address = device.getAddress();
        mThingySdkManager.enableButtonStateNotification(device, mDatabaseHelper.getNotificationsState(address, DatabaseContract.ThingyDbColumns.COLUMN_NOTIFICATION_BUTTON));
    }

    private void setConnectedLED(BluetoothDevice thingy) {
        if (thingy != null) {
            if (mThingySdkManager.isConnected(thingy)) {
                mThingySdkManager.setBreatheLedMode(thingy, LED_GREEN, ThingyUtils.DEFAULT_LED_INTENSITY, 1000);
            }
        }
    }

    private class FragmentAdapter extends FragmentPagerAdapter {
        FragmentAdapter(FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return FrequencyModeFragment.newInstance(mDevices);
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "Piano";
        }
    }

    class SoundThingyListener extends EmptyThingyListener {
        private final Map<Integer, BluetoothDevice> orchestrator;
        private final Handler mHandler = new Handler();
        boolean running = true;
        int ticksFingerprint = 0;
        public SoundThingyListener(Map<Integer, BluetoothDevice> orchestrator) {
            this.orchestrator = orchestrator;
        }
        @Override
        public void onDeviceConnected(final BluetoothDevice device, int connectionState) {
            Log.i(TAG, "onDeviceConnected: " + device.getAddress());
            mClhLog.append(device.getAddress() + ": Connected \r\n");
            enableUiNotifications(device);

            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    setConnectedLED(device);
                    //Do something after 100ms
                }
            }, 3000);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mClhLog.append(device.getAddress() + ": Listening\r\n");
                    mStartPlayingAudio = true;
                    startThingyOverlayAnimation();
                    mThingySdkManager.enableThingyMicrophone(device, true);
                }
            }, 8000);

            final int delay = 1000; // 1000 milliseconds == 1 second
            final int delayTicksFingerprint = 16;
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (running) {

                        ticksFingerprint += 1;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("CLAP-" + device.getName(), "Doing a clap check");
                                AmplitudeLoud amplitudeLoud = new AmplitudeLoud();
                                amplitudeLoud.init(buf.array(), false);
                                final int loud = AmplitudeLoud.isLoud(amplitudeLoud.wave);
                                if (loud > 0) {
                                    mThingySdkManager.setConstantLedMode(device, 255, 255, 0);
                                    final Handler handler = new Handler(Looper.getMainLooper());
                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            // normal connected LED:
                                            mThingySdkManager.setBreatheLedMode(device, LED_GREEN, ThingyUtils.DEFAULT_LED_INTENSITY, 1000);
                                        }
                                    }, 5000); // execute after 5 seconds
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mClhLog.append(device.getAddress() + ": Loud " + loud + "\r\n");
                                        }
                                    });

                                    orchestrator.put(loud, device);
                                    buf = ByteBuffer.allocate(2000000); //goodluck garbage collector of android. 
                                }
                                if (ticksFingerprint >= delayTicksFingerprint) {
                                    Log.i("CLAP-" + device.getName(), "Reinitializing the buffer");
                                    buf = ByteBuffer.allocate(2000000); //goodluck garbage collector of android.
                                    ticksFingerprint = 0;
                                }
                            }
                        }).start();

                        handler.postDelayed(this, delay); // repeat
                    }
                }
            }, delay);

        }

        @Override
        public void onDeviceDisconnected(BluetoothDevice device, int connectionState) {
            mClhLog.append(device.getAddress() + ": Disconnected.\r\n");
            if (device.equals(mDevices)) {
                stopRecording();
                stopMicrophoneOverlayAnimation();
                stopThingyOverlayAnimation();
                mStartPlayingAudio = false;
            }
            running = false;
            startScan();
        }

        @Override
        public void onMicrophoneValueChangedEvent(final BluetoothDevice bluetoothDevice, final byte[] data) {
            if (data != null) {
                if (data.length != 0) {
                    if ((data != null) && data.length > 0 && buf.remaining() > data.length) {
                        buf.put(data);
                    }
                }
            }
        }
    }
}

