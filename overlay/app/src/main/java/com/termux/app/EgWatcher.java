package com.termux.app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

/**
 * Watches {@code $HOME/.eg_open} for requests written by the {@code eg <file>} command and
 * opens {@link EditorActivity} for them.
 *
 * <p>We deliberately do not shell out to {@code am}: {@code /system/bin/am} cannot be used from
 * an app uid on Android 8+, and termux-app 0.118.0 has no am socket server. Polling a small file
 * in the app's own home directory works on every Android version.</p>
 */
public final class EgWatcher {

    private static final String LOG_TAG = "EgWatcher";
    private static final String QUEUE_NAME = ".eg_open";
    private static final long POLL_MS = 300L;

    private static boolean sStarted = false;

    private EgWatcher() {
    }

    public static synchronized void start(final Context context) {
        if (sStarted) return;
        sStarted = true;

        final Context appContext = context.getApplicationContext();
        final File queue = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, QUEUE_NAME);

        Thread thread = new Thread(() -> {
            // Ignore any stale request left over from a previous run.
            String last = read(queue);
            while (true) {
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    return;
                }

                String current = read(queue);
                if (current == null) continue;
                current = current.trim();
                if (current.isEmpty() || current.equals(last)) continue;
                last = current;

                String path = current;
                int separator = current.lastIndexOf('|');
                if (separator > 0) path = current.substring(0, separator);
                path = path.trim();
                if (path.isEmpty()) continue;

                try {
                    Intent intent = new Intent(appContext, EditorActivity.class);
                    intent.putExtra("file", path);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    appContext.startActivity(intent);
                    Log.i(LOG_TAG, "opening editor for " + path);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "could not open editor: " + e);
                }
            }
        }, "eg-watcher");

        thread.setDaemon(true);
        thread.start();
    }

    private static String read(File file) {
        if (!file.exists() || !file.isFile()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }
}
