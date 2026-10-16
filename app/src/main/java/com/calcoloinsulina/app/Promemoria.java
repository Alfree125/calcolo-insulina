package com.calcoloinsulina.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Promemoria "controlla la glicemia" a un intervallo di tempo dal calcolo. */
final class Promemoria {

    static final String AZIONE = "com.calcoloinsulina.app.PROMEMORIA";
    static final String EXTRA_MINUTI = "minuti";
    private static final String CANALE = "promemoria_glicemia";
    private static final int ID_NOTIFICA = 1;

    private Promemoria() {
    }

    static void programma(Context c, int minuti) {
        long alle = System.currentTimeMillis() + minuti * 60_000L;
        imposta(c, alle, minuti);
        Prefs.get(c).edit()
                .putLong(Prefs.PROMEMORIA_ALLE, alle)
                .putInt(Prefs.PROMEMORIA_DURATA, minuti)
                .apply();
    }

    static void imposta(Context c, long alle, int minuti) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = intent(c, minuti);
        // Da Android 12 gli allarmi esatti richiedono USE_EXACT_ALARM (concesso
        // all'installazione); senza, setAndAllowWhileIdle tarderebbe fino a un'ora.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alle, pi);
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, alle, 10 * 60_000L, pi);
        }
    }

    static void annulla(Context c) {
        c.getSystemService(AlarmManager.class).cancel(intent(c, 0));
        Prefs.get(c).edit().remove(Prefs.PROMEMORIA_ALLE).apply();
    }

    /** Orario del promemoria in attesa, 0 se nessuno. */
    static long programmatoAlle(Context c) {
        long alle = Prefs.get(c).getLong(Prefs.PROMEMORIA_ALLE, 0);
        return alle > System.currentTimeMillis() ? alle : 0;
    }

    static boolean notifichePermesse(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    static void mostraNotifica(Context c, int minuti) {
        Prefs.get(c).edit().remove(Prefs.PROMEMORIA_ALLE).apply();
        if (!notifichePermesse(c)) return;

        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        nm.createNotificationChannel(new NotificationChannelCompat.Builder(
                CANALE, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(c.getString(R.string.canale_nome))
                .build());

        PendingIntent apri = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String testo = c.getString(R.string.notifica_testo, durata(minuti));
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CANALE)
                .setSmallIcon(R.drawable.ic_notifica)
                .setColor(ContextCompat.getColor(c, R.color.md_primary))
                .setContentTitle(c.getString(R.string.notifica_titolo))
                .setContentText(testo)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(testo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(apri);
        try {
            nm.notify(ID_NOTIFICA, b.build());
        } catch (SecurityException ignored) {
            // permesso revocato nel frattempo
        }
    }

    /** "30 minuti", "1 ora", "2 ore e 30 minuti"... */
    static String durata(int minuti) {
        int ore = minuti / 60, min = minuti % 60;
        String h = ore == 1 ? "1 ora" : ore + " ore";
        if (ore == 0) return min + " minuti";
        if (min == 0) return h;
        return h + " e " + min + " minuti";
    }

    private static PendingIntent intent(Context c, int minuti) {
        Intent i = new Intent(c, PromemoriaReceiver.class)
                .setAction(AZIONE)
                .putExtra(EXTRA_MINUTI, minuti);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
