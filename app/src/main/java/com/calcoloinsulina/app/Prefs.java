package com.calcoloinsulina.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.material.color.DynamicColors;

import java.security.KeyStore;

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

    /** Modalita' sviluppatore: sottrae l'insulina attiva delle dosi registrate. */
    static final String SVILUPPATORE = "sviluppatore";
    /** Durata d'azione dell'insulina in minuti. */
    static final String DURATA_INSULINA = "durata_insulina";
    static final int DURATA_INSULINA_DEFAULT = 240;

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

    /**
     * La 2.1.0-beta.1 stimava i CHO da foto via Internet con una chiave API dell'utente:
     * alla prima apertura di una versione successiva chiave e impostazioni vengono cancellate.
     */
    static void rimuoviDatiStimaFoto(Context c) {
        SharedPreferences p = get(c);
        if (!p.contains("chiave_api") && !p.contains("foto_attiva")) return;
        p.edit().remove("chiave_api").remove("foto_attiva").remove("modello_ai").apply();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry("chiave_api_anthropic");
        } catch (Exception ignored) {
            // nessuna chiave da eliminare
        }
    }

    /** Applica i colori dinamici di Android 12+ se attivi; restituisce la scelta applicata. */
    static boolean applicaColori(Activity a) {
        boolean attivi = get(a).getBoolean(COLORI_DINAMICI, true);
        if (attivi) DynamicColors.applyToActivityIfAvailable(a);
        return attivi;
    }
}
