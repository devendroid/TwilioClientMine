package com.devs.twilioclientmine;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;
import android.util.Log;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.twilio.client.Device;
import com.twilio.client.Twilio;

/**
 * Created by ${Deven} on ${4/1/16}.
 */
public class IncomingService extends Service {

    private static final String TOKEN_SERVICE_URL = "https://young-ocean-29864.herokuapp.com/token";
    private static final String TAG = "IncomingService";
    private static final String DEFAULT_USER = "deven1";

    public static Device clientDevice = null;
    public static ClientProfile clientProfile = null;

    private ResultReceiver mResultReceiver = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "==onStartCommand");

        if(intent!=null) {
         if(intent.getExtras() != null)
            mResultReceiver = (ResultReceiver) intent.getExtras().get("receiver");
        }

        initializeTwilioClientSDK();

        return super.onStartCommand(intent, flags, startId);
    }

    /*
     * Initialize the Twilio Client SDK
     */
    private void initializeTwilioClientSDK() {

        if (!Twilio.isInitialized()) {
            Twilio.initialize(getApplicationContext(), new Twilio.InitListener() {

                /*
                 * Now that the SDK is initialized we can register using a Capability Token.
                 * A Capability Token is a JSON Web Token (JWT) that specifies how an associated Device
                 * can interact with Twilio services.
                 */
                @Override
                public void onInitialized() {
                    Twilio.setLogLevel(Log.INFO);
                    /*
                     * Retrieve the Capability Token from your own web server
                     */
                    clientProfile  = new ClientProfile(DEFAULT_USER, true, true);
                     retrieveCapabilityToken(clientProfile);
                }

                @Override
                public void onError(Exception e) {
                    Log.i(TAG, e.toString());
                   // Toast.makeText(ClientActivity.this, "Failed to initialize the Twilio Client SDK", Toast.LENGTH_LONG).show();
                }
            });
        }
        else {
            Log.i(TAG, "==Twilio already initialized");
            clientProfile  = new ClientProfile(DEFAULT_USER, true, true);
            retrieveCapabilityToken(clientProfile);
        }
    }

    /*
     * Request a Capability Token from your public accessible server
     */
    private void retrieveCapabilityToken(final ClientProfile newClientProfile) {

        // Correlate desired properties of the Device (from ClientProfile) to properties of the Capability Token
        Uri.Builder b = Uri.parse(TOKEN_SERVICE_URL).buildUpon();
        if (newClientProfile.isAllowOutgoing()) {
            b.appendQueryParameter("allowOutgoing", newClientProfile.isAllowOutgoing() ? "true" : "false");
        }
        if (newClientProfile.isAllowIncoming() && newClientProfile.getName() != null) {
            b.appendQueryParameter("client", newClientProfile.getName());
        }

        Ion.with(getApplicationContext())
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
                            Log.i(TAG, "Error retrieving token: " + e.toString());
                           // Toast.makeText(ClientActivity.this, "Error retrieving token", Toast.LENGTH_SHORT).show();
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
                clientDevice = Twilio.createDevice(capabilityToken, MainActivity.mDeviceListener);

                /*
                 * Providing a PendingIntent to the newly create Device, allowing you to receive incoming calls
                 *
                 *  What you do when you receive the intent depends on the component you set in the Intent.
                 *
                 *  If you're using an Activity, you'll want to override Activity.onNewIntent()
                 *  If you're using a Service, you'll want to override Service.onStartCommand().
                 *  If you're using a BroadcastReceiver, override BroadcastReceiver.onReceive().
                 */

                Intent intent = new Intent(getApplicationContext(), DialogCallInOut.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                        0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                //Intent intent = new Intent(getApplicationContext(), IncomingService.class);
                //PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(),
                //       0,intent,PendingIntent.FLAG_UPDATE_CURRENT);
                clientDevice.setIncomingIntent(pendingIntent);

            } else {
                Log.i(TAG, "==Device already initialized");
                clientDevice.updateCapabilityToken(capabilityToken);
            }

            if(mResultReceiver != null)
           mResultReceiver.send(Activity.RESULT_OK, null);

        } catch (Exception e) {
            Log.i(TAG, "An error has occured updating or creating a Device: \n" + e.toString());
            //Toast.makeText(ClientActivity.this, "Device error", Toast.LENGTH_SHORT).show();
        }
    }
}
