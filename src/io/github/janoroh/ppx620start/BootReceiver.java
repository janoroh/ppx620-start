package io.github.janoroh.ppx620start;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        // nach einem App-Update laeuft der Hintergrunddienst sonst erst beim naechsten Start wieder
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        Logbook.log(c, (Intent.ACTION_BOOT_COMPLETED.equals(a) ? "BOOT_COMPLETED" : "App update") + " received");
        c.startForegroundService(new Intent(c, StartService.class));
    }
}
