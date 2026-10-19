package io.github.janoroh.ppx620start;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Einstellungen per adb, z. B.
 * am broadcast -n io.github.janoroh.ppx620start/.ConfigReceiver --es target hdmi --ez guard true --ei delay_ms 13000
 * Speicherplatz sichern: --ei save_slot 1; setzen: --es slot1 "650,110,330,14;330,111,149,1";
 * Akkuprofil wieder ableiten: --ez reset_battery true
 */
public class ConfigReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        SharedPreferences.Editor e = Settings.prefs(c).edit();
        if (i.hasExtra("target")) e.putString("target", i.getStringExtra("target"));
        for (String k : new String[]{"guard", "color"}) if (i.hasExtra(k)) e.putBoolean(k, i.getBooleanExtra(k, true));
        for (String k : new String[]{"delay_ms", "red", "green", "blue", "look", "bat_red", "bat_green", "bat_blue", "bat_look"}) if (i.hasExtra(k)) e.putInt(k, i.getIntExtra(k, 0));
        for (int n = 1; n <= ProfileSlots.COUNT; n++) {
            if (i.hasExtra("slot" + n)) e.putString("slot" + n, i.getStringExtra("slot" + n));
        }
        if (i.getIntExtra("save_slot", 0) > 0) {
            e.commit();
            ProfileSlots.save(c, i.getIntExtra("save_slot", 0));
        }
        if (i.getBooleanExtra("reset_battery", false)) for (String k : Settings.BATTERY_KEYS) e.remove(k);
        e.commit();
        Logbook.log(c, "Settings changed: " + Settings.load(c));
    }
}
