package com.devs.twilioclientmine;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.twilio.client.Connection;
import com.twilio.client.ConnectionListener;
import com.twilio.client.Device;

import java.util.HashMap;
import java.util.Map;


public class DialogCallInOut extends Activity implements View.OnClickListener{

    private static final String TAG = "DialogCallInOut";
    private TextView tvCalling, tvUserName;
    private ImageView ivCalling;
    private Chronometer chronometer;
    private Button btnEndCall, btnAccept, btnReject;
    private LinearLayout layoutBottom;

    private static Connection connection, pendingConnection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setContentView(R.layout.dialog_call_in_out);
            this.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            this.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            init();

            if(IncomingService.clientDevice == null){
                Toast.makeText(this,"User not registered",Toast.LENGTH_SHORT).show();
            }
            else {
                if(getIntent().getStringExtra("to_id") != null) {
                    String toID = getIntent().getStringExtra("to_id");
                    connect(toID, true);
                    showOutgoingUI(toID);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void init() {
        ivCalling = (ImageView)findViewById(R.id.iv_calling);
        tvCalling = (TextView) findViewById(R.id.tv_calling);
        tvUserName = (TextView) findViewById(R.id.tv_user_name);
        btnEndCall = (Button) findViewById(R.id.btn_end_call);
        btnEndCall.setOnClickListener(this);
        btnAccept = (Button) findViewById(R.id.btn_accept);
        btnAccept.setOnClickListener(this);
        btnReject = (Button) findViewById(R.id.btn_reject);
        btnReject.setOnClickListener(this);
        layoutBottom = (LinearLayout)findViewById(R.id.layout_bottom);

        chronometer = (Chronometer) findViewById(R.id.chronometer);
    }

    private void showIncomingUI(String fromID){
        tvUserName.setText(fromID);
        layoutBottom.setVisibility(View.VISIBLE);
        btnEndCall.setVisibility(View.GONE);
        tvCalling.setVisibility(View.VISIBLE);
        chronometer.setVisibility(View.GONE);
    }

    private void showOutgoingUI(String toID){
        tvUserName.setText(toID);
        layoutBottom.setVisibility(View.GONE);
        btnEndCall.setVisibility(View.VISIBLE);
        tvCalling.setVisibility(View.GONE);
        chronometer.setVisibility(View.VISIBLE);
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_end_call:
                chronometer.stop();
                disconnect();
                finish();
                break;

            case R.id.btn_accept:
                tvCalling.setVisibility(View.GONE);
                chronometer.setVisibility(View.VISIBLE);
                layoutBottom.setVisibility(View.GONE);
                btnEndCall.setVisibility(View.VISIBLE);
                chronometer.setBase(SystemClock.elapsedRealtime());
                chronometer.start();
                answer();
                break;

            case R.id.btn_reject:
                if (pendingConnection != null) {
                    pendingConnection.reject();
                }
                finish();
                break;
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
   * Create an outgoing connection
   */
    private void connect(String contact, boolean isPhoneNumber) {
        // Determine if you're calling another client or a phone number
        if (!isPhoneNumber) {
            contact = "client:" + contact.trim();
        }

        Map<String, String> params = new HashMap<String, String>();
        params.put("To", contact);

        if (IncomingService.clientDevice != null) {

            if(isCapabilityTokenValid()) {
                // Create an outgoing connection
                IncomingService.clientDevice.disconnectAll();
                connection = IncomingService.clientDevice.connect(params, mConnectionListener);
                //((InternalConnection) connection).setOutgoingCallSid(SID);
            }
            else {
                Toast.makeText(this, "Token Expire", Toast.LENGTH_SHORT).show();
                //Twilio.shutdown();
                startService(new Intent(this, IncomingService.class));
                finish();
            }

        } else {
            Toast.makeText(this, "No existing device", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isCapabilityTokenValid() {
        if (IncomingService.clientDevice == null )
            return false;
        else if(IncomingService.clientDevice.getCapabilities() == null)
            return false;

        long expTime = 0;
        if(IncomingService.clientDevice.getCapabilities().get(
                Device.Capability.EXPIRATION) != null ) {
             expTime = (Long) IncomingService.clientDevice.getCapabilities().get(
                    Device.Capability.EXPIRATION);
        }

        Log.i(TAG, "===expTime "+(expTime - System.currentTimeMillis() / 1000) );

        return expTime - System.currentTimeMillis() / 1000 > 0;
    }

    /*
     * Disconnect an active connection
     */
    private void disconnect() {
        try {
            if (connection != null) {
                connection.disconnect();
                connection = null;
                pendingConnection = null;
            }
            else   Toast.makeText(this, "Connection null", Toast.LENGTH_SHORT).show();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    private ConnectionListener mConnectionListener = new ConnectionListener(){

        @Override
        public void onConnecting(Connection connection) {
            Log.i(TAG, "==Connecting");
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
                    connection = null;
                    pendingConnection = null;
                }
            }
            finish();
        }

        @Override
        public void onDisconnected(Connection inConnection, int errorCode, String error) {
            Log.i(TAG, String.format("==Connection error: %s", error));
            // A connection other than active connection could have errored out.
            if (connection != null && inConnection != null) {
                if (connection == inConnection) {
                    connection = null;
                    pendingConnection = null;
                }
            }
            finish();
        }
    };


    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "==onResume()");
        Intent intent = getIntent();

        if (intent != null) {
            /*
             * Determine if the receiving Intent has an extra for the incoming connection. If so,
             * remove it from the Intent to prevent handling it again next time the Activity is resumed
             */
            Device device = intent.getParcelableExtra(Device.EXTRA_DEVICE);
            Connection incomingConnection = intent.getParcelableExtra(Device.EXTRA_CONNECTION);

            if (incomingConnection == null && device == null) {
                return;
            }
            intent.removeExtra(Device.EXTRA_DEVICE);
            intent.removeExtra(Device.EXTRA_CONNECTION);

            pendingConnection = incomingConnection;

            showIncomingUI("Unkown User");
        }
    }

    /*
     * Receive intent for incoming call from Twilio Client Service
     * Android will only call Activity.onNewIntent() if `android:launchMode` is set to `singleTop`.
     */
    @Override
    public void onNewIntent(Intent intent) {
        Log.i(TAG, "==onNewIntent()");
        super.onNewIntent(intent);
        setIntent(intent);
    }
}
