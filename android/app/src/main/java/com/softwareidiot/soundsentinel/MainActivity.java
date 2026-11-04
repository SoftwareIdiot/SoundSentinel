package com.softwareidiot.soundsentinel;

import com.getcapacitor.BridgeActivity;
import android.os.Bundle;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(BackgroundMonitorPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
