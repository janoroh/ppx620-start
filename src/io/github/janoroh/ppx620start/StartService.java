package io.github.janoroh.ppx620start;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Vordergrunddienst fuer den Ablauf nach dem Booten; beendet sich danach selbst. */
public class StartService extends Service {
    private static volatile boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("start", "PPX620 Start", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "start")
                .setContentTitle("PPX620 Start")
                .setContentText(getString(R.string.notification_running))
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();
        startForeground(1, n);
        if (!running) {
            running = true;
            new Thread(() -> {
                try {
                    new Worker(this).run();
                } catch (Throwable t) {
                    Logbook.log(this, "Error in start sequence: " + t);
                } finally {
                    running = false;
                    stopForeground(true);
                    stopSelf();
                }
            }, "ppx620start").start();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
