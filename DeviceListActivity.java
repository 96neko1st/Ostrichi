package com.example.owner.myapplication;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;

public class DeviceListActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        Bundle b = new Bundle();
        b.putString(BluetoothDevice.EXTRA_DEVICE, "CF:58:86:95:75:8F");
        Intent result = new Intent();
        result.putExtras(b);
        setResult(Activity.RESULT_OK, result);
        finish();
    }
}