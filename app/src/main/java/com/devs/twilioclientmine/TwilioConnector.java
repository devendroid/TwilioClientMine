package com.devs.twilioclientmine;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.twilio.client.Connection;
import com.twilio.client.ConnectionListener;
import com.twilio.client.Device;
import com.twilio.client.DeviceListener;
import com.twilio.client.PresenceEvent;
import com.twilio.client.Twilio;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ${Deven} on ${4/1/16}.
 */
public class TwilioConnector {

    private static final String TOKEN_SERVICE_URL = "https://young-ocean-29864.herokuapp.com/token";
    private static final String TAG = "TwilioConnector";
    private static final boolean ALLOW_OUTGOING = true;
    private static final boolean ALLOW_INCOMING = false;
    private static final boolean IS_PHONE_NUMBER = true;

    private boolean muteMicrophone;
    private boolean speakerPhone;
    private AudioManager audioManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;
    private  AlertDialog mAlertDialog;

    /*
     * A Device is the primary entry point to Twilio Services
     */
    private Device clientDevice;

    /*
     * A Connection represents a connection between a Device and Twilio Services.
     * Connections are either outgoing or incoming, and not created directly.
     * An outgoing connection is created by Device.connect()
     * An incoming connection are created internally by a Device and hanged to the registered PendingIntent
     */
    private Connection connection;
    private Connection pendingConnection;
    private Context mContext;
    private ClientProfile clientProfile;

    public TwilioConnector(Context mContext, String user){
        this.mContext = mContext;
        init(user);
    }

    private void init(String user) {
        /*
         * Create a default profile (name=jenny, allowOutgoing=true, allowIncoming=true)
         */
        clientProfile = new ClientProfile(user, ALLOW_OUTGOING, ALLOW_INCOMING);
        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        initializeTwilioClientSDK();
    }

    private ConnectionListener mConnectionListener = new ConnectionListener(){

        @Override
        public void onConnecting(Connection connection) {
            Log.i(TAG, "==Attempting to connect");
        }

        @Override
        public void onConnected(Connection connection) {
            Log.i(TAG, "==Connected");
        }

        @Override
        public void onDisconnected(Connection inConnection) {
            Log.i(TAG, "==Disconnect");
            if (connection != null && inConnection != null) {
                if (connection == inConnection) {
                    disconnect();
                    if(mAlertDialog != null)
                    mAlertDialog.dismiss();
                }
            }
        }

        @Override
        public void onDisconnected(Connection inConnection, int errorCode, String error) {
            Log.i(TAG, String.format("==Connection error: %s", error));
            //Toast.makeText(mContext," Connection error",Toast.LENGTH_SHORT).show();
            // A connection other than active connection could have errored out.
            if (connection != null && inConnection != null) {
                if (connection == inConnection) {
                    disconnect();
                    if(mAlertDialog != null)
                        mAlertDialog.dismiss();
                }
            }
        }
    };

    private DeviceListener mDeviceListener = new DeviceListener(){

        @Override
        public void onStartListening(Device device) {
            Log.i(TAG,"==onStartListening: "+device.getState().toString());
           // Toast.makeText(mContext," onStartListening",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStopListening(Device device) {
            Log.i(TAG,"==onStopListening: "+device.getState().toString());
           // Toast.makeText(mContext," onStopListening",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStopListening(Device device, int i, String s) {
            Log.i(TAG,"==onStopListening: "+device.getState().toString());
            //Toast.makeText(mContext," onStopListening",Toast.LENGTH_SHORT).show();
            // Log.e(TAG, String.format("Device has encountered an error and has stopped" +
            //        " listening for incoming connections: %s", error));
        }

        @Override
        public boolean receivePresenceEvents(Device device) {
            Log.i(TAG,"==receivePresenceEvents: "+device.getState().toString());
            //Toast.makeText(mContext," receivePresenceEvents",Toast.LENGTH_SHORT).show();
            return false;
        }

        @Override
        public void onPresenceChanged(Device device, PresenceEvent presenceEvent) {
            Log.i(TAG,"==onPresenceChanged: "+device.getState().toString());
           // Toast.makeText(mContext," onPresenceChanged",Toast.LENGTH_SHORT).show();
        }
    };

    /*
     * Initialize the Twilio Client SDK
     */
    private void initializeTwilioClientSDK() {

        if (!Twilio.isInitialized()) {
            Twilio.initialize(mContext, new Twilio.InitListener() {

                /*
                 * Now that the SDK is initialized we can register using a Capability Token.
                 * A Capability Token is a JSON Web Token (JWT) that specifies how an associated Device
                 * can interact with Twilio services.
                 */
                @Override
                public void onInitialized() {
                    Twilio.setLogLevel(Log.DEBUG);
                    /*
                     * Retrieve the Capability Token from your own web server
                     */
                     retrieveCapabilityToken(clientProfile);
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, e.toString());
                    Toast.makeText(mContext, "Failed to initialize the Twilio Client SDK", Toast.LENGTH_LONG).show();
                }
            });
        }
        else {
            Log.i(TAG, "==Twilio already initialized");
            //clientProfile  = new com.devs.twilioclientmine.ClientProfile(DEFAULT_USER, true, true);
            //retrieveCapabilityToken(clientProfile);
        }
    }

    /*
    * Request a Capability Token from your public accessible server
    */
    private void retrieveCapabilityToken(final ClientProfile newClientProfile) {

        // Correlate desired properties of the Device (from ClientProfile) to properties of the Capability Token
        Uri.Builder b = Uri.parse(TOKEN_SERVICE_URL).buildUpon();
        if (newClientProfile.isAllowOutgoing()) {
            b.appendQueryParameter("allowOutgoing", newClientProfile.allowOutgoing ? "true" : "false");
        }
        if (newClientProfile.isAllowIncoming() && newClientProfile.getName() != null) {
            b.appendQueryParameter("client", newClientProfile.getName());
        }
        Ion.with(mContext)
                .load(b.toString())
                .asString()
                .setCallback(new FutureCallback<String>() {
                    @Override
                    public void onCompleted(Exception e, String capabilityToken) {
                        if (e == null) {
                            Log.i(TAG, "capabilityToken=== "+capabilityToken);

                            // Update the current Client Profile to represent current properties
                            clientProfile = newClientProfile;

                            // Create a Device with the Capability Token
                            createDevice(capabilityToken);
                        } else {
                            Log.e(TAG, "Error retrieving token: " + e.toString());
                        }
                    }
                });
    }

    /*
     * Create a Device or update the capabilities of the current Device
     */
    private void createDevice(String capabilityToken) {
        try {
            if (clientDevice == null) {
                clientDevice = Twilio.createDevice(capabilityToken, mDeviceListener);

                /*
                 * Providing a PendingIntent to the newly create Device, allowing you to receive incoming calls
                 *
                 *  What you do when you receive the intent depends on the component you set in the Intent.
                 *
                 *  If you're using an Activity, you'll want to override Activity.onNewIntent()
                 *  If you're using a Service, you'll want to override Service.onStartCommand().
                 *  If you're using a BroadcastReceiver, override BroadcastReceiver.onReceive().
                 */

//                Intent intent = new Intent(mContext, ClientActivity.class);
//                PendingIntent pendingIntent = PendingIntent.getActivity(mContext,
//                        0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//                clientDevice.setIncomingIntent(pendingIntent);

            } else {
                clientDevice.updateCapabilityToken(capabilityToken);
            }

//            TextView clientDeviceTextView = (TextView) capabilityPropertiesView.findViewById(R.id.client_device_status);
//            clientDeviceTextView.setText("Client Device: " + clientDevice.getState().toString());
//
//            TextView clientNameTextView = (TextView) capabilityPropertiesView.findViewById(R.id.client_name_registered_text);
//            clientNameTextView.setText("Client Name: " + ClientActivity.this.clientProfile.getName());
//
//            TextView outgoingCapabilityTextView = (TextView) capabilityPropertiesView.findViewById(R.id.outgoing_capability_registered_text);
//            outgoingCapabilityTextView.setText("Outgoing Capability: " +Boolean.toString(ClientActivity.this.clientProfile.isAllowOutgoing()));
//
//            TextView incomingCapabilityTextView = (TextView) capabilityPropertiesView.findViewById(R.id.incoming_capability_registered_text);
//            incomingCapabilityTextView.setText("Incoming Capability: " +Boolean.toString(ClientActivity.this.clientProfile.isAllowIncoming()));
//
//            TextView libraryVersionTextView = (TextView) capabilityPropertiesView.findViewById(R.id.library_version_text);
//            libraryVersionTextView.setText("Library Version: " + Twilio.getVersion());

        } catch (Exception e) {
            Log.e(TAG, "An error has occured updating or creating a Device: \n" + e.toString());
            Toast.makeText(mContext, "Device error", Toast.LENGTH_SHORT).show();
        }
    }

    /*
     * Create an outgoing connection
     */
    private void connect(String contact, boolean isPhoneNumber) {
        // Determine if you're calling another client or a phone number
        if (!isPhoneNumber) {
            contact = "client:" + contact.trim();
        }

        Map<String, String> params = new HashMap<String, String>();
        params.put("To", contact);

        if (clientDevice != null) {
            // Create an outgoing connection
            connection = clientDevice.connect(params, mConnectionListener);
            //((InternalConnection) connection).setOutgoingCallSid(SID);
        } else {
            Toast.makeText(mContext, "No existing device", Toast.LENGTH_SHORT).show();
        }
    }

    /*
     * Disconnect an active connection
     */
    private void disconnect() {
        try {
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Accept an incoming connection
     */
    private void answer() {
        pendingConnection.accept();
        pendingConnection.setConnectionListener(mConnectionListener);
        connection = pendingConnection;
        pendingConnection = null;
    }

    /*
     * Create an outgoing call UI dialog
     */
    public void showCallDialog(String toNumberWidSTDCode, String displayName) {
        // Create an outgoing connection
        connect(toNumberWidSTDCode, IS_PHONE_NUMBER);
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        //alertDialogBuilder.setIcon(R.drawable.ic_call_black_24dp);
        //alertDialogBuilder.setTitle("Call");
        //alertDialogBuilder.setPositiveButton("Call", callClickListener);
        //alertDialogBuilder.setNegativeButton("Cancel", cancelClickListener);
        alertDialogBuilder.setCancelable(false);
        LayoutInflater li = LayoutInflater.from(mContext);
        View dialogView = li.inflate(R.layout.dialog_calling, null);

        ImageView ivCalling = (ImageView)dialogView.findViewById(R.id.iv_calling);
        final Chronometer chronometer = (Chronometer) dialogView.findViewById(R.id.chronometer);
        chronometer.start();
        TextView tvUserName = (TextView) dialogView.findViewById(R.id.tv_user_name);
        tvUserName.setText(displayName);
        FloatingActionButton fabEndCAll  = (FloatingActionButton)dialogView.findViewById(R.id.fab_end_call);
        fabEndCAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
                mAlertDialog.dismiss();
                chronometer.stop();
            }
        });

        ImageView ivSpeaker = (ImageView)dialogView.findViewById(R.id.iv_speaker);
        ivSpeaker.setOnClickListener(toggleSpeakerPhoneFabClickListener());
        ImageView ivMic = (ImageView)dialogView.findViewById(R.id.iv_mic);
        ivMic.setOnClickListener(muteMicrophoneFabClickListener());
        muteMicrophone = false;
        speakerPhone = false;
        setAudioFocus(false);
        audioManager.setSpeakerphoneOn(speakerPhone);

        alertDialogBuilder.setView(dialogView);
        mAlertDialog = alertDialogBuilder.create();
        mAlertDialog.show();
    }

    private View.OnClickListener muteMicrophoneFabClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 *  Mute/unmute microphone
                 */
                muteMicrophone = !muteMicrophone;
                if (connection != null) {
                    connection.setMuted(muteMicrophone);
                }
                if (muteMicrophone) {
                    ((ImageView)v).setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_mic_off_red_24px));
                } else {
                    ((ImageView)v).setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_mic_green_24px));
                }
            }
        };
    }

    private View.OnClickListener toggleSpeakerPhoneFabClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Audio routing to speakerphone or headset
                 */
                speakerPhone = !speakerPhone;

                setAudioFocus(true);
                audioManager.setSpeakerphoneOn(speakerPhone);

                if (speakerPhone) {
                    ((ImageView)v).setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_speaker_on_black_24dp));
                } else {
                    ((ImageView)v).setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_speaker_off_black_24dp));
                }
            }
        };
    }

    private void setAudioFocus(boolean setFocus) {
        if (audioManager != null) {
            if (setFocus) {
                savedAudioMode = audioManager.getMode();
                // Request audio focus before making any device switch.
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                /*
                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
                 * required to be in this mode when playout and/or recording starts for
                 * best possible VoIP performance. Some devices have difficulties with speaker mode
                 * if this is not set.
                 */
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(savedAudioMode);
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    /*
     * A representation of the current properties of a client token
     */
    protected class ClientProfile {
        private String name;
        private boolean allowOutgoing = true;
        private boolean allowIncoming = true;


        public ClientProfile(String name, boolean allowOutgoing, boolean allowIncoming) {
            this.name = name;
            this.allowOutgoing = allowOutgoing;
            this.allowIncoming = allowIncoming;
        }

        public String getName() {
            return name;
        }

        public boolean isAllowOutgoing() {
            return allowOutgoing;
        }

        public boolean isAllowIncoming() {
            return allowIncoming;
        }
    }

    private boolean isCapabilityTokenValid() {
        if (clientDevice == null )
            return false;
        else if(clientDevice.getCapabilities() == null)
            return false;

        long expTime = 0;
        if(clientDevice.getCapabilities().get(
                Device.Capability.EXPIRATION) != null ) {
            expTime = (Long) clientDevice.getCapabilities().get(
                    Device.Capability.EXPIRATION);
        }

        Log.i(TAG, "===expTime "+(expTime - System.currentTimeMillis() / 1000) );

        return expTime - System.currentTimeMillis() / 1000 > 0;
    }

}
