package com.devs.twilioclientmine;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.twilio.client.Device;
import com.twilio.client.DeviceListener;
import com.twilio.client.PresenceEvent;
import com.twilio.client.Twilio;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private static final String TAG = "MainActivity";
    private static TextView tvStatus;
    private Button btnCall;
    private EditText edtContact;
    private static StringBuilder sb ;

    public static DeviceListener mDeviceListener = new DeviceListener(){

        @Override
        public void onStartListening(Device device) {
            Log.i(TAG,"==onStartListening: "+device.getState().toString());
        }

        @Override
        public void onStopListening(Device device) {
            Log.i(TAG,"==onStopListening: "+device.getState().toString());
        }

        @Override
        public void onStopListening(Device device, int i, String s) {
            Log.i(TAG,"==onStopListening: "+device.getState().toString()+"msg: "+s);
            // Log.e(TAG, String.format("Device has encountered an error and has stopped" +
            //        " listening for incoming connections: %s", error));
        }

        @Override
        public boolean receivePresenceEvents(Device device) {
            Log.i(TAG,"==receivePresenceEvents: "+device.getState().toString());
            return false;
        }

        @Override
        public void onPresenceChanged(Device device, PresenceEvent presenceEvent) {
            Log.i(TAG,"==onPresenceChanged: "+device.getState().toString());
        }
    };


    private ResultReceiver mResultReceiver = new ResultReceiver(new Handler()){
        // Get result within a specific time interval
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            if(resultCode == RESULT_OK){
                showDetail();
            }
        }
    };

    private static void showDetail() {
        if(tvStatus != null) {
            sb = new StringBuilder("");
            sb.append("\nClient Device: " + IncomingService.clientDevice.getState().toString());
            sb.append("\nClient Name: " + IncomingService.clientProfile.getName());
            sb.append("\nIncoming Capability: " + Boolean.toString(IncomingService.clientProfile.isAllowIncoming()));
            sb.append("\nOutgoing Capability: " + Boolean.toString(IncomingService.clientProfile.isAllowOutgoing()));
            sb.append("\nLibrary Version: " + Twilio.getVersion());

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    tvStatus.setText(sb.toString());
                }
            });
        }
    }

    private TwilioConnector mTwilioConnector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // init
        // CAll to Client name
        tvStatus = (TextView) findViewById(R.id.tv_status);
        btnCall = (Button) findViewById(R.id.btn_call);
        btnCall.setOnClickListener(this);
        edtContact = (EditText)findViewById(R.id.edt_contact);

//        Intent intent = new Intent(this, IncomingService.class);
//        intent.putExtra("receiver",mResultReceiver);
//        startService(intent);

        // Call to phone number
        Button btnCallNumber = (Button) findViewById(R.id.btn_call_no);
        btnCallNumber.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_call:

                String contactStr = edtContact.getText().toString();
                if(!contactStr.equals("")){
                    Intent mIntent = new Intent(this, DialogCallInOut.class);
                    mIntent.putExtra("to_id",contactStr);
                    startActivity(mIntent);
                }
                else  Toast.makeText(this,"Oye..?",Toast.LENGTH_SHORT).show();

                break;

            case R.id.btn_call_no:
                String contactNo = edtContact.getText().toString();
                if(!contactNo.equals("")){
                if(isNumeric(contactNo)) {
                    mTwilioConnector.showCallDialog(contactNo, "Username");
                }
                else {
                    Toast.makeText(this,"Enter Phone number",Toast.LENGTH_SHORT).show();
                }
                }
                else  Toast.makeText(this,"Oye..?",Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private boolean isNumeric(String str) {
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

}
