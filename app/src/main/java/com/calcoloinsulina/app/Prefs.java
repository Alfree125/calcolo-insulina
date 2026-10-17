package com.calcoloinsulina.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.material.color.DynamicColors;

/** Chiavi e valori predefiniti delle preferenze salvate. */
final class Prefs {

    static final String FILE = "parametri";

    static final String RAPPORTO = "rapporto";
    static final String FATTORE = "fattore";
    static final String TARGET = "target";

    /** true dopo la configurazione del primo avvio. */
    static final String CONFIGURATO = "configurato";

    static final String PROMEMORIA_ATTIVO = "promemoria_attivo";
    static final String PROMEMORIA_MINUTI = "promemoria_minuti";
    /** Orario (ms) del promemoria programmato, 0 se nessuno. */
    static final String PROMEMORIA_ALLE = "promemoria_alle";
    static final String PROMEMORIA_DURATA = "promemoria_durata";

    /** Passo di arrotondamento della dose in UI, 0 = nessuno. */
    static final String ARROTONDAMENTO = "arrotondamento";
    static final String COLORI_DINAMICI = "colori_dinamici";

    /** Stima CHO da foto (beta). */
    static final String FOTO_ATTIVA = "foto_attiva";
    /** Chiave API Anthropic cifrata: leggerla con Segreti.chiaveApi(). */
    static final String CHIAVE_API = "chiave_api";
    static final String MODELLO_AI = "modello_ai";
    static final String MODELLO_DEFAULT = "claude-opus-5-5";

    static final int MINUTI_DEFAULT = 120;
    static final float ARROTONDAMENTO_DEFAULT = 0.5f;

    private Prefs() {
    }

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Valore salvato o null se assente. */
    static Double numero(SharedPreferences p, String key) {
        return p.contains(key) ? (double) p.getFloat(key, 0) : null;
    }

    /** Applica i colori dinamici di Android 12+ se attivi; restituisce la scelta applicata. */
    static boolean applicaColori(Activity a) {
        boolean attivi = get(a).getBoolean(COLORI_DINAMICI, true);
        if (attivi) DynamicColors.applyToActivityIfAvailable(a);
        return attivi;
    }
}
