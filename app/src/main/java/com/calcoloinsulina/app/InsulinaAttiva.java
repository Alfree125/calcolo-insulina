package com.calcoloinsulina.app;

import android.content.SharedPreferences;

/**
 * Insulina ancora attiva (IOB) delle dosi registrate in modalita' sviluppatore,
 * con la curva esponenziale di OpenAPS (oref0, lib/iob/calculate.js).
 * Le dosi stanno nelle preferenze come "msEpoca:UI,msEpoca:UI".
 */
final class InsulinaAttiva {

    /** Picco d'azione degli analoghi rapidi (lispro, aspart, glulisina), come in oref0. */
    static final double PICCO_MIN = 75;
    private static final String DOSI = "dosi";
    private static final long GIORNO_MS = 24 * 60 * 60_000L;

    private InsulinaAttiva() {
    }

    /** Frazione di una dose ancora attiva dopo {@code minuti}; {@code durata} in minuti, oltre 150. */
    static double frazione(double minuti, double durata) {
        if (minuti <= 0) return 1;
        if (minuti >= durata) return 0;
        double tau = PICCO_MIN * (1 - PICCO_MIN / durata) / (1 - 2 * PICCO_MIN / durata);
        double a = 2 * tau / durata;
        double s = 1 / (1 - a + (1 + a) * Math.exp(-durata / tau));
        return 1 - s * (1 - a) * ((minuti * minuti / (tau * durata * (1 - a)) - minuti / tau - 1)
                * Math.exp(-minuti / tau) + 1);
    }

    static double calcola(SharedPreferences p, long adesso) {
        int durata = p.getInt(Prefs.DURATA_INSULINA, Prefs.DURATA_INSULINA_DEFAULT);
        double totale = 0;
        for (String voce : p.getString(DOSI, "").split(",")) {
            if (voce.isEmpty()) continue;
            String[] x = voce.split(":");
            totale += Double.parseDouble(x[1])
                    * frazione((adesso - Long.parseLong(x[0])) / 60_000.0, durata);
        }
        return totale;
    }

    /** Aggiunge una dose; quelle piu' vecchie di 24 ore non servono piu' e vengono tolte. */
    static void registra(SharedPreferences p, double dose, long adesso) {
        StringBuilder dosi = new StringBuilder();
        for (String voce : p.getString(DOSI, "").split(",")) {
            if (!voce.isEmpty() && adesso - Long.parseLong(voce.split(":")[0]) < GIORNO_MS) {
                dosi.append(voce).append(',');
            }
        }
        p.edit().putString(DOSI, dosi.append(adesso).append(':').append(dose).toString()).apply();
    }

    static void annullaUltima(SharedPreferences p) {
        String dosi = p.getString(DOSI, "");
        int virgola = dosi.lastIndexOf(',');
        p.edit().putString(DOSI, virgola < 0 ? "" : dosi.substring(0, virgola)).apply();
    }
}
