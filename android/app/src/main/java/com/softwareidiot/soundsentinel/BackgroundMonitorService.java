package com.softwareidiot.soundsentinel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started microphone monitor. Audio capture stays native while the WebView is backgrounded. */
public class BackgroundMonitorService extends Service {
    public static final String ACTION_START = "com.softwareidiot.soundsentinel.START";
    public static final String ACTION_STOP = "com.softwareidiot.soundsentinel.STOP";
    public static final String ACTION_UPDATE = "com.softwareidiot.soundsentinel.UPDATE";
    private static final String CHANNEL_ID = "sound_sentinel_monitor";
    private static final int NOTIFICATION_ID = 7142;
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_CLIP_SECONDS = 120;
    private static final long PRE_ROLL_BYTES = SAMPLE_RATE * 2L;
    private static final long MAX_PENDING_BYTES = 250L * 1024L * 1024L;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int threshold = 45;
    private volatile int postRollSeconds = 10;
    private volatile int currentLevel = 0;
    private volatile double currentDb = -80;
    private volatile boolean recording = false;
    private PowerManager.WakeLock wakeLock;
    private Thread audioThread;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        File dir = recordingsDir();
        if (!dir.exists()) dir.mkdirs();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopMonitor();
            return START_NOT_STICKY;
        }
        if (intent != null) {
            threshold = intent.getIntExtra("threshold", threshold);
            postRollSeconds = Math.max(1, intent.getIntExtra("postRollSeconds", postRollSeconds));
            persistConfig();
        } else {
            loadConfig();
        }
        if (ACTION_UPDATE.equals(action)) {
            return START_STICKY;
        }
        startAsForeground();
        if (running.compareAndSet(false, true)) {
            setState(true);
            acquireWakeLock();
            audioThread = new Thread(this::captureLoop, "SoundSentinel-AudioRecord");
            audioThread.start();
        }
        return START_STICKY;
    }

    private void startAsForeground() {
        Notification notification = buildNotification(false);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void captureLoop() {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) minBuffer = SAMPLE_RATE;
        int bufferBytes = Math.max(minBuffer, 4096);
        AudioRecord audioRecord = null;
        ByteArrayOutputStream clip = null;
        ArrayDeque<byte[]> preRoll = new ArrayDeque<>();
        long preRollBytes = 0;
        ArrayList<Integer> waveform = new ArrayList<>();
        long clipStart = 0, totalScore = 0, samples = 0, quietDurationMs = 0;
        int peak = 0, triggers = 0;
        int aboveGateFrames = 0;
        double noiseFloorDb = -60.0;
        double smoothedDb = -80.0;
        long lastStatusWrite = 0;
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Android could not initialize microphone capture");
            audioRecord.startRecording();
            if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Android opened the audio recorder but did not start microphone capture");
            }
            getSharedPreferences("native_monitor", MODE_PRIVATE).edit().putBoolean("capturing", true).apply();
            short[] pcm = new short[bufferBytes / 2];
            while (running.get()) {
                int count = audioRecord.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                if (count < 0) throw new IllegalStateException("Microphone capture stopped (AudioRecord " + count + ")");
                if (count == 0) continue;
                double sum = 0;
                double highPassSum = 0;
                int peakSample = 0;
                int previousSample = pcm[0];
                for (int i = 0; i < count; i++) {
                    int rawSample = pcm[i];
                    double sample = rawSample / 32768.0;
                    sum += sample * sample;
                    peakSample = Math.max(peakSample, Math.abs(rawSample));
                    if (i > 0) {
                        double difference = (rawSample - previousSample) / 32768.0;
                        highPassSum += difference * difference;
                    }
                    previousSample = rawSample;
                }
                double rms = Math.sqrt(sum / count);
                double db = rms <= 0.0001 ? -80 : Math.max(-80, Math.min(0, 20 * Math.log10(rms)));
                double peakDb = peakSample == 0 ? -80 : Math.max(-80, 20 * Math.log10(peakSample / 32768.0));
                double crestDb = peakDb - db;
                double highFrequencyRatio = rms == 0 || count < 2 ? 0
                        : Math.sqrt(highPassSum / Math.max(1, count - 1)) / (rms * Math.sqrt(2));
                long now = System.currentTimeMillis();
                smoothedDb += (db - smoothedDb) * (db > smoothedDb ? 0.45 : 0.12);
                double absoluteGateDb = -70.0 + threshold * 0.65;
                double adaptiveGateDb = Math.max(absoluteGateDb, noiseFloorDb + 6.0);
                double releaseGateDb = Math.max(absoluteGateDb - 4.0, noiseFloorDb + 3.0);
                boolean impulsiveEvent = peakDb >= Math.max(absoluteGateDb + 9.0, noiseFloorDb + 15.0)
                        && crestDb >= 7.0 && highFrequencyRatio >= 0.40;
                boolean aboveGate = smoothedDb >= (clip == null ? adaptiveGateDb : releaseGateDb);
                if (clip == null && aboveGate) aboveGateFrames++;
                else if (!aboveGate) aboveGateFrames = 0;
                boolean detected = impulsiveEvent || (clip == null ? aboveGateFrames >= 2 : aboveGate);
                if (clip == null && !detected) {
                    noiseFloorDb = Math.max(-72.0, Math.min(-35.0, noiseFloorDb + (db - noiseFloorDb) * 0.015));
                }
                int level = smoothedDb <= -70 ? 0 : smoothedDb >= -5 ? 100 : (int)Math.round(((smoothedDb + 70) / 65) * 100);
                int transientLevel = peakDb <= -70 ? 0 : peakDb >= -5 ? 100 : (int)Math.round(((peakDb + 70) / 65) * 100);
                int eventLevel = impulsiveEvent ? Math.max(level, transientLevel) : level;
                byte[] pcmBytes = encodePcm(pcm, count);
                preRoll.addLast(pcmBytes);
                preRollBytes += pcmBytes.length;
                while (preRollBytes > PRE_ROLL_BYTES && preRoll.size() > 1) {
                    preRollBytes -= preRoll.removeFirst().length;
                }
                long frameDurationMs = Math.max(1, count * 1000L / SAMPLE_RATE);
                currentLevel = eventLevel;
                currentDb = smoothedDb;
                boolean newClip = false;
                if (detected) {
                    if (clip == null) {
                        clip = new ByteArrayOutputStream();
                        long preRollDurationMs = preRollBytes / 2L * 1000L / SAMPLE_RATE;
                        clipStart = now - preRollDurationMs;
                        for (byte[] previousFrame : preRoll) clip.write(previousFrame, 0, previousFrame.length);
                        peak = 0; totalScore = 0; samples = 0; triggers = 0;
                        waveform.clear();
                        quietDurationMs = 0;
                        newClip = true;
                    } else if (quietDurationMs >= 500) {
                        triggers++;
                    }
                    quietDurationMs = 0;
                    peak = Math.max(peak, eventLevel);
                    if (waveform.size() < 40 && (waveform.isEmpty() || now - lastStatusWrite >= 400)) waveform.add(eventLevel);
                    if (newClip) updateNotification(true);
                }
                if (clip != null) {
                    if (!newClip) clip.write(pcmBytes, 0, pcmBytes.length);
                    totalScore += eventLevel;
                    samples++;
                    recording = true;
                    if (!detected) quietDurationMs += frameDurationMs;
                    long clipDurationMs = clip.size() * 1000L / (SAMPLE_RATE * 2L);
                    if (clipDurationMs >= MAX_CLIP_SECONDS * 1000L || (!detected && quietDurationMs >= postRollSeconds * 1000L)) {
                        saveClip(clip.toByteArray(), clipStart, peak, samples == 0 ? peak : (int)(totalScore / samples), triggers + 1, waveform);
                        clip = null; recording = false;
                        quietDurationMs = 0;
                        updateNotification(false);
                    }
                }
                if (now - lastStatusWrite >= 250) {
                    android.content.SharedPreferences p = getSharedPreferences("native_monitor", MODE_PRIVATE);
                    float durationSeconds = clip == null ? 0 : (float)clip.size() / (SAMPLE_RATE * 2.0f);
                    float silenceRemaining = clip == null ? postRollSeconds
                            : Math.max(0, postRollSeconds * 1000L - quietDurationMs) / 1000.0f;
                    p.edit().putInt("level", currentLevel).putFloat("db", (float)currentDb)
                            .putBoolean("recording", recording).putFloat("recordingDuration", durationSeconds)
                            .putFloat("silenceRemaining", silenceRemaining).apply();
                    lastStatusWrite = now;
                }
            }
        } catch (Throwable error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            getSharedPreferences("native_monitor", MODE_PRIVATE).edit().putString("error", "Microphone capture failed: " + message).putBoolean("capturing", false).apply();
            android.util.Log.e("SoundSentinel", "Microphone capture failed", error);
        } finally {
            if (clip != null && clip.size() > 0) {
                saveClip(clip.toByteArray(), clipStart, peak,
                        samples == 0 ? peak : (int)(totalScore / samples), Math.max(1, triggers + 1), waveform);
            }
            recording = false;
            if (audioRecord != null) {
                try { audioRecord.stop(); } catch (Exception ignored) {}
                audioRecord.release();
            }
            if (running.getAndSet(false)) {
                setState(false);
                releaseWakeLock();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    }

    private void saveClip(byte[] pcm, long start, int peak, int average, int triggers, ArrayList<Integer> waveform) {
        try {
            prunePendingClips(pcm.length + 44L);
            String id = "native-" + start;
            File audio = new File(recordingsDir(), id + ".wav");
            try (FileOutputStream out = new FileOutputStream(audio)) {
                writeWavHeader(out, pcm.length);
                out.write(pcm);
            }
            JSONArray wave = new JSONArray();
            for (Integer point : waveform) wave.put(point);
            JSONObject meta = new JSONObject();
            meta.put("id", id);
            meta.put("timestamp", start);
            meta.put("duration", Math.max(0.1, pcm.length / (double)(SAMPLE_RATE * 2)));
            meta.put("sizeBytes", audio.length());
            meta.put("peakDecibels", peak);
            meta.put("avgDecibels", average);
            meta.put("triggersCount", Math.max(1, triggers));
            meta.put("waveformData", wave);
            try (FileOutputStream out = new FileOutputStream(new File(recordingsDir(), id + ".json"))) {
                out.write(meta.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            getSharedPreferences("native_monitor", MODE_PRIVATE).edit().putString("error", "Could not save recording: " + message).apply();
            android.util.Log.e("SoundSentinel", "Could not save native recording", error);
        }
    }

    private void prunePendingClips(long incomingBytes) {
        File[] files = recordingsDir().listFiles((dir, name) -> name.endsWith(".wav"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long total = incomingBytes;
        for (File file : files) total += file.length();
        for (File file : files) {
            if (total <= MAX_PENDING_BYTES) break;
            String id = file.getName().substring(0, file.getName().length() - 4);
            long bytes = file.length();
            file.delete();
            new File(recordingsDir(), id + ".json").delete();
            total -= bytes;
        }
    }

    private static byte[] encodePcm(short[] pcm, int count) {
        byte[] bytes = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            bytes[i * 2] = (byte)(pcm[i] & 0xff);
            bytes[i * 2 + 1] = (byte)((pcm[i] >> 8) & 0xff);
        }
        return bytes;
    }

    private static void writeWavHeader(FileOutputStream out, int dataLength) throws Exception {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{'R','I','F','F'}).putInt(36 + dataLength).put(new byte[]{'W','A','V','E'});
        h.put(new byte[]{'f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1);
        h.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short)2).putShort((short)16);
        h.put(new byte[]{'d','a','t','a'}).putInt(dataLength);
        out.write(h.array());
    }

    private File recordingsDir() { return new File(getFilesDir(), "background-recordings"); }
    private void persistConfig() { getSharedPreferences("native_monitor", MODE_PRIVATE).edit().putInt("threshold", threshold).putInt("postRoll", postRollSeconds).apply(); }
    private void loadConfig() {
        android.content.SharedPreferences p = getSharedPreferences("native_monitor", MODE_PRIVATE);
        threshold = p.getInt("threshold", 45); postRollSeconds = p.getInt("postRoll", 10);
    }
    private void setState(boolean active) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences("native_monitor", MODE_PRIVATE).edit()
                .putBoolean("running", active).putBoolean("capturing", false)
                .putInt("level", 0).putFloat("db", -80).putBoolean("recording", false)
                .putFloat("recordingDuration", 0).putFloat("silenceRemaining", postRollSeconds);
        if (active) editor.remove("error");
        editor.apply();
    }
    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundSentinel::AudioMonitor");
            wakeLock.acquire();
        } catch (Exception ignored) { }
    }
    private void releaseWakeLock() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); wakeLock = null; }
    private void stopMonitor() {
        running.set(false);
        setState(false);
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SoundSentinel microphone monitoring", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible notification while SoundSentinel monitors the microphone");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    private Notification buildNotification(boolean isRecording) {
        Intent stop = new Intent(this, BackgroundMonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle(isRecording ? "SoundSentinel — recording event" : "SoundSentinel — monitoring")
                .setContentText("Microphone monitoring is active. Tap Stop to disarm.")
                .setOngoing(true).setContentIntent(openPending)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending).build();
    }
    private void updateNotification(boolean isRecording) {
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification(isRecording));
    }
    @Override public void onDestroy() {
        running.set(false); releaseWakeLock(); setState(false); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
