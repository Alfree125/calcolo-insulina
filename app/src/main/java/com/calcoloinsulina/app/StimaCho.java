package com.calcoloinsulina.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stima dei CHO di un pasto da una foto con l'API Messages di Anthropic (Claude).
 * Chiamata HTTP diretta: l'SDK Java richiede Java 8+ e non supporta Android 6.
 * Va eseguita fuori dal thread principale.
 */
final class StimaCho {

    private static final String SISTEMA =
            "Stimi i carboidrati di un pasto a partire da una foto, per una persona con diabete "
            + "che conta i carboidrati (CHO) per calcolare la dose di insulina. Un errore di stima "
            + "diventa un errore di dose: sii realistico sulle porzioni.\n\n"
            + "Per ogni alimento visibile indica il nome in italiano, il peso stimato della porzione "
            + "come servita (cotto se cotto) e i grammi di carboidrati disponibili, con i valori "
            + "tipici delle tabelle nutrizionali italiane. Le bevande zuccherate contano; acqua, "
            + "caffè senza zucchero e condimenti grassi hanno CHO trascurabili. Se l'utente dà "
            + "indicazioni (pesi, ingredienti, porzioni), hanno la precedenza su ciò che vedi.\n\n"
            + "Usa i riferimenti nella foto (piatto, posate, mani, confezioni) per stimare le "
            + "dimensioni. Se la foto non mostra cibo o non è valutabile, restituisci un elenco "
            + "vuoto, cho_totali_g pari a 0, affidabilità bassa e spiega il motivo nelle note. "
            + "Indica affidabilità bassa anche quando la porzione è difficile da valutare "
            + "(alimenti coperti, piatti fondi) e suggerisci nelle note cosa conviene pesare. "
            + "Le note sono in italiano, al massimo due frasi.";

    private static final int TENTATIVI = 3;

    static final class Alimento {
        final String nome;
        final double grammi;
        final double cho;

        Alimento(String nome, double grammi, double cho) {
            this.nome = nome;
            this.grammi = grammi;
            this.cho = cho;
        }
    }

    static final class Risultato {
        final List<Alimento> alimenti;
        final double totale;
        /** "alta", "media" o "bassa". */
        final String affidabilita;
        final String note;

        Risultato(List<Alimento> alimenti, double totale, String affidabilita, String note) {
            this.alimenti = alimenti;
            this.totale = totale;
            this.affidabilita = affidabilita;
            this.note = note;
        }
    }

    /** Errore da mostrare cosi' com'e' all'utente. */
    static final class Errore extends Exception {
        Errore(String messaggio) {
            super(messaggio);
        }
    }

    private StimaCho() {
    }

    static Risultato stima(String chiave, String modello, String jpegBase64, String indicazioni)
            throws Errore {
        byte[] corpo;
        try {
            corpo = corpo(modello, jpegBase64, indicazioni).toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new Errore("Errore interno nella preparazione della richiesta.");
        }

        Errore ultimo = null;
        for (int tentativo = 1; tentativo <= TENTATIVI; tentativo++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(BuildConfig.API_BASE + "/v1/messages").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(180_000);
                conn.setFixedLengthStreamingMode(corpo.length);
                conn.setRequestProperty("content-type", "application/json");
                conn.setRequestProperty("x-api-key", chiave);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                if (usaFallback(modello)) {
                    conn.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01");
                }
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(corpo);
                }

                int codice = conn.getResponseCode();
                String testo = leggi(codice >= 400 ? conn.getErrorStream() : conn.getInputStream());
                if (codice == 200) return interpreta(testo);

                ultimo = new Errore(messaggioErrore(codice, testo));
                if (!riprovabile(codice) || tentativo == TENTATIVI) throw ultimo;
                attendi(tentativo, conn.getHeaderField("retry-after"));
            } catch (SocketTimeoutException e) {
                throw new Errore("Il servizio non ha risposto in tempo. Riprova.");
            } catch (UnknownHostException e) {
                throw new Errore("Nessuna connessione a Internet.");
            } catch (IOException e) {
                ultimo = new Errore("Connessione non riuscita. Controlla Internet e riprova.");
                if (tentativo == TENTATIVI) throw ultimo;
                attendi(tentativo, null);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw ultimo;
    }

    /** I modelli con fallback lato server per i rifiuti (non esiste per Claude Haiku 5.5). */
    private static boolean usaFallback(String modello) {
        return modello.startsWith("claude-opus") || modello.startsWith("claude-sonnet");
    }

    private static JSONObject corpo(String modello, String jpegBase64, String indicazioni)
            throws JSONException {
        JSONObject alimento = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("nome", new JSONObject().put("type", "string"))
                        .put("quantita_g", new JSONObject().put("type", "number"))
                        .put("cho_g", new JSONObject().put("type", "number")))
                .put("required", new JSONArray().put("nome").put("quantita_g").put("cho_g"))
                .put("additionalProperties", false);
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("alimenti", new JSONObject().put("type", "array").put("items", alimento))
                        .put("cho_totali_g", new JSONObject().put("type", "number"))
                        .put("affidabilita", new JSONObject().put("type", "string")
                                .put("enum", new JSONArray().put("alta").put("media").put("bassa")))
                        .put("note", new JSONObject().put("type", "string")))
                .put("required", new JSONArray()
                        .put("alimenti").put("cho_totali_g").put("affidabilita").put("note"))
                .put("additionalProperties", false);

        String richiesta = "Stima i carboidrati di questo pasto.";
        if (indicazioni != null && !indicazioni.trim().isEmpty()) {
            richiesta += "\nIndicazioni dell'utente: " + indicazioni.trim();
        }
        JSONArray contenuto = new JSONArray()
                .put(new JSONObject()
                        .put("type", "image")
                        .put("source", new JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", jpegBase64)))
                .put(new JSONObject().put("type", "text").put("text", richiesta));

        JSONObject corpo = new JSONObject()
                .put("model", modello)
                .put("max_tokens", 16000)
                .put("system", SISTEMA)
                .put("output_config", new JSONObject()
                        .put("effort", "medium")
                        .put("format", new JSONObject()
                                .put("type", "json_schema")
                                .put("schema", schema)))
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", contenuto)));
        if (usaFallback(modello)) corpo.put("fallbacks", "default");
        return corpo;
    }

    private static Risultato interpreta(String testo) throws Errore {
        try {
            JSONObject risposta = new JSONObject(testo);
            String stop = risposta.optString("stop_reason");
            if ("refusal".equals(stop)) {
                throw new Errore("Il servizio ha rifiutato l'analisi di questa foto. Prova con un'altra immagine.");
            }
            if ("max_tokens".equals(stop)) {
                throw new Errore("Risposta incompleta. Riprova.");
            }

            // Con l'output strutturato il JSON arriva nei blocchi di testo (i blocchi thinking si ignorano)
            StringBuilder json = new StringBuilder();
            JSONArray blocchi = risposta.getJSONArray("content");
            for (int i = 0; i < blocchi.length(); i++) {
                JSONObject b = blocchi.getJSONObject(i);
                if ("text".equals(b.optString("type"))) json.append(b.optString("text"));
            }
            JSONObject dati = new JSONObject(json.toString());

            List<Alimento> alimenti = new ArrayList<>();
            double somma = 0;
            JSONArray lista = dati.getJSONArray("alimenti");
            for (int i = 0; i < lista.length(); i++) {
                JSONObject a = lista.getJSONObject(i);
                double cho = Math.max(0, a.getDouble("cho_g"));
                alimenti.add(new Alimento(a.getString("nome"), Math.max(0, a.getDouble("quantita_g")), cho));
                somma += cho;
            }
            // Il totale mostrato e' la somma delle righe, cosi' resta coerente con l'elenco
            double totale = alimenti.isEmpty() ? Math.max(0, dati.getDouble("cho_totali_g")) : somma;
            return new Risultato(Collections.unmodifiableList(alimenti), totale,
                    dati.getString("affidabilita"), dati.optString("note"));
        } catch (JSONException e) {
            throw new Errore("Risposta del servizio non valida. Riprova.");
        }
    }

    private static String messaggioErrore(int codice, String testo) {
        String dettaglio = "";
        try {
            dettaglio = new JSONObject(testo).getJSONObject("error").optString("message");
        } catch (JSONException ignored) {
            // corpo non JSON
        }
        switch (codice) {
            case 401:
                return "Chiave API non valida. Controllala nelle impostazioni.";
            case 403:
                return "La chiave API non ha i permessi per questa richiesta.";
            case 429:
                return "Troppe richieste in poco tempo. Riprova tra qualche secondo.";
            case 413:
                return "Foto troppo grande.";
            case 529:
                return "Il servizio è sovraccarico. Riprova tra poco.";
            default:
                if (codice >= 500) return "Il servizio non è disponibile al momento. Riprova tra poco.";
                return dettaglio.isEmpty()
                        ? "Richiesta non accettata (errore " + codice + ")."
                        : "Richiesta non accettata: " + dettaglio;
        }
    }

    private static boolean riprovabile(int codice) {
        return codice == 408 || codice == 429 || codice >= 500;
    }

    private static void attendi(int tentativo, String retryAfter) {
        long ms = tentativo * 2000L;
        try {
            if (retryAfter != null) ms = Math.min(10_000, Long.parseLong(retryAfter.trim()) * 1000);
        } catch (NumberFormatException ignored) {
            // formato data: si usa l'attesa predefinita
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String leggi(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
