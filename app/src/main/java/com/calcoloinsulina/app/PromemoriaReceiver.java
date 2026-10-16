package com.calcoloinsulina.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Mostra il promemoria e lo riprogramma dopo un riavvio o un aggiornamento dell'app. */
public class PromemoriaReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        String azione = intent.getAction();
        SharedPreferences p = Prefs.get(c);
        int minuti = p.getInt(Prefs.PROMEMORIA_DURATA, Prefs.MINUTI_DEFAULT);

        if (Promemoria.AZIONE.equals(azione)) {
            Promemoria.mostraNotifica(c, intent.getIntExtra(Promemoria.EXTRA_MINUTI, minuti));
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(azione)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(azione)) {
            // Gli allarmi non sopravvivono al riavvio
            long alle = p.getLong(Prefs.PROMEMORIA_ALLE, 0);
            if (alle == 0) return;
            if (alle > System.currentTimeMillis()) {
                Promemoria.imposta(c, alle, minuti);
            } else {
                Promemoria.mostraNotifica(c, minuti);
            }
        }
    }
}
