package com.softwareidiot.soundsentinel;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.PermissionState;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Comparator;

@CapacitorPlugin(name = "BackgroundMonitor", permissions = {
        @Permission(alias = "microphone", strings = { Manifest.permission.RECORD_AUDIO }),
        @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
})
public class BackgroundMonitorPlugin extends Plugin {
    @PluginMethod public void requestMicrophonePermission(PluginCall call) {
        if (Build.VERSION.SDK_INT < 23 || getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            JSObject result = new JSObject();
            result.put("granted", true);
            call.resolve(result);
            return;
        }
        requestPermissionForAlias("microphone", call, "microphonePermissionResult");
    }

    @PermissionCallback private void microphonePermissionResult(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", getPermissionState("microphone") == PermissionState.GRANTED);
        call.resolve(result);
    }

    @PluginMethod public void requestNotificationPermission(PluginCall call) {
        JSObject result = new JSObject();
        if (Build.VERSION.SDK_INT < 33 || getContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            result.put("granted", true);
            call.resolve(result);
            return;
        }
        requestPermissionForAlias("notifications", call, "notificationPermissionResult");
    }

    @PermissionCallback private void notificationPermissionResult(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", getPermissionState("notifications") == PermissionState.GRANTED);
        call.resolve(result);
    }

    @PluginMethod public void startMonitoring(PluginCall call) {
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            call.reject("Microphone permission is required. Allow it in Android settings, then arm monitoring again.");
            return;
        }
        Intent intent = new Intent(getContext(), BackgroundMonitorService.class)
                .setAction(BackgroundMonitorService.ACTION_START)
                .putExtra("threshold", call.getInt("threshold", 45))
                .putExtra("postRollSeconds", call.getInt("postRollSeconds", 10));
        try {
            if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(intent);
            else getContext().startService(intent);
            call.resolve();
        } catch (Exception error) {
            call.reject("Android could not start the microphone service: " + error.getMessage());
        }
    }

    @PluginMethod public void stopMonitoring(PluginCall call) {
        Intent intent = new Intent(getContext(), BackgroundMonitorService.class).setAction(BackgroundMonitorService.ACTION_STOP);
        getContext().startService(intent);
        call.resolve();
    }

    @PluginMethod public void updateMonitorSettings(PluginCall call) {
        Intent intent = new Intent(getContext(), BackgroundMonitorService.class)
                .setAction(BackgroundMonitorService.ACTION_UPDATE)
                .putExtra("threshold", call.getInt("threshold", 45))
                .putExtra("postRollSeconds", call.getInt("postRollSeconds", 10));
        getContext().startService(intent);
        call.resolve();
    }

    @PluginMethod public void getStatus(PluginCall call) {
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("native_monitor", 0);
        JSObject result = new JSObject();
        result.put("running", prefs.getBoolean("running", false));
        result.put("capturing", prefs.getBoolean("capturing", false));
        result.put("soundLevel", prefs.getInt("level", 0));
        result.put("decibels", prefs.getFloat("db", -80));
        result.put("recording", prefs.getBoolean("recording", false));
        result.put("recordingDuration", prefs.getFloat("recordingDuration", 0));
        result.put("silenceRemaining", prefs.getFloat("silenceRemaining", prefs.getInt("postRoll", 10)));
        File[] pending = recordingsDir().listFiles((folder, name) -> name.endsWith(".json"));
        result.put("pendingRecordings", pending == null ? 0 : pending.length);
        String error = prefs.getString("error", null);
        if (error != null) result.put("error", error);
        call.resolve(result);
    }

    @PluginMethod public void listPendingRecordings(PluginCall call) {
        File dir = recordingsDir();
        File[] files = dir.listFiles((folder, name) -> name.endsWith(".json"));
        JSArray recordings = new JSArray();
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (File file : files) {
                try {
                    JSONObject item = new JSONObject(new String(readFile(file), StandardCharsets.UTF_8));
                    recordings.put(JSObject.fromJSONObject(item));
                } catch (Exception ignored) { }
            }
        }
        JSObject result = new JSObject();
        result.put("recordings", recordings);
        call.resolve(result);
    }

    @PluginMethod public void readRecording(PluginCall call) {
        String id = call.getString("id", "");
        File file = audioFile(id);
        if (!file.isFile()) { call.reject("Background recording is no longer available"); return; }
        try {
            byte[] bytes = readFile(file);
            JSObject result = new JSObject();
            result.put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP));
            call.resolve(result);
        } catch (Exception error) { call.reject("Could not read audio clip: " + error.getMessage()); }
    }

    @PluginMethod public void acknowledgeRecording(PluginCall call) {
        deleteFiles(call.getString("id", ""));
        call.resolve();
    }

    @PluginMethod public void deleteRecording(PluginCall call) {
        deleteFiles(call.getString("id", ""));
        call.resolve();
    }

    private File recordingsDir() { return new File(getContext().getFilesDir(), "background-recordings"); }
    private byte[] readFile(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
    private File audioFile(String id) {
        if (!id.matches("native-[0-9]+")) return new File(recordingsDir(), "invalid");
        return new File(recordingsDir(), id + ".wav");
    }
    private void deleteFiles(String id) {
        if (!id.matches("native-[0-9]+")) return;
        audioFile(id).delete();
        new File(recordingsDir(), id + ".json").delete();
    }
}
