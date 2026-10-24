package com.calcoloinsulina.app;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Ricerca di prodotti su Open Food Facts (database libero di etichette nutrizionali),
 * solo in modalita' sviluppatore. Va eseguita fuori dal thread principale.
 */
final class RicercaOnline {

    private static final String URL_RICERCA = "https://search.openfoodfacts.org/search"
            + "?page_size=20&langs=it&fields=product_name,product_name_it,brands,nutriments,serving_quantity&q=";

    static final class Prodotto {
        final String nome;
        final double cho100;
        /** Porzione indicata in etichetta in grammi, 0 se assente. */
        final double porzione;

        Prodotto(String nome, double cho100, double porzione) {
            this.nome = nome;
            this.cho100 = cho100;
            this.porzione = porzione;
        }
    }

    private RicercaOnline() {
    }

    static List<Prodotto> cerca(Context c, String testo) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(
                URL_RICERCA + URLEncoder.encode(testo.trim(), "UTF-8")).openConnection();
        try {
            // Android < 7.1.1 non conosce la radice di Let's Encrypt usata da Open Food Facts
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) conn.setSSLSocketFactory(conIsrg(c));
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            // Open Food Facts chiede un User-Agent che identifichi l'app
            conn.setRequestProperty("User-Agent", "CalcoloInsulina (github.com/Alfree125/calcolo-insulina)");
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            return interpreta(leggi(conn.getInputStream()));
        } finally {
            conn.disconnect();
        }
    }

    /** Prodotti con i carboidrati per 100 g; quelli senza il dato vengono scartati. */
    static List<Prodotto> interpreta(String json) throws IOException {
        List<Prodotto> prodotti = new ArrayList<>();
        try {
            JSONArray risultati = new JSONObject(json).getJSONArray("hits");
            for (int i = 0; i < risultati.length(); i++) {
                JSONObject p = risultati.getJSONObject(i);
                JSONObject nutrienti = p.optJSONObject("nutriments");
                double cho = nutrienti == null ? Double.NaN : nutrienti.optDouble("carbohydrates_100g");
                String nome = p.optString("product_name_it", "").trim();
                if (nome.isEmpty()) nome = p.optString("product_name", "").trim();
                if (Double.isNaN(cho) || cho < 0 || cho > 100 || nome.isEmpty()) continue;
                JSONArray marche = p.optJSONArray("brands");
                if (marche != null && marche.length() > 0) nome += " (" + marche.optString(0) + ")";
                double porzione = p.optDouble("serving_quantity");
                prodotti.add(new Prodotto(nome, cho, Double.isNaN(porzione) || porzione <= 0 ? 0 : porzione));
            }
        } catch (JSONException e) {
            throw new IOException("Risposta non valida", e);
        }
        return prodotti;
    }

    private static SSLSocketFactory conIsrg(Context c) throws IOException {
        try (InputStream in = c.getResources().openRawResource(R.raw.isrg_root_x1)) {
            KeyStore radici = KeyStore.getInstance(KeyStore.getDefaultType());
            radici.load(null);
            radici.setCertificateEntry("isrg_root_x1",
                    CertificateFactory.getInstance("X.509").generateCertificate(in));
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(radici);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, tmf.getTrustManagers(), null);
            return ssl.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static String leggi(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
