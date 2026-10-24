package com.calcoloinsulina.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Database locale (SQLite) degli alimenti con i CHO per 100 g: gli alimenti di base
 * arrivano da res/raw/alimenti.csv, quelli personali li aggiunge l'utente dalle etichette.
 * Nessun accesso a Internet. Il database e' piccolo: le query girano sul thread principale.
 */
final class DatabaseAlimenti extends SQLiteOpenHelper {

    private static final String NOME = "alimenti.db";
    /** Da aumentare quando cambia alimenti.csv: gli alimenti di base vengono ricaricati. */
    private static final int VERSIONE = 2;

    private static final String TABELLA = "alimenti";
    private static final String COLONNE =
            "_id, nome, categoria, cho_100g, porzione_g, porzione_nome, personale";

    private static DatabaseAlimenti istanza;
    private final Context app;

    static final class Alimento {
        final long id;
        final String nome;
        final String categoria;
        final double cho100;
        /** Porzione tipica in grammi, 0 se non indicata. */
        final double porzione;
        final String nomePorzione;
        final boolean personale;

        Alimento(long id, String nome, String categoria, double cho100, double porzione,
                 String nomePorzione, boolean personale) {
            this.id = id;
            this.nome = nome;
            this.categoria = categoria;
            this.cho100 = cho100;
            this.porzione = porzione;
            this.nomePorzione = nomePorzione;
            this.personale = personale;
        }

        double cho(double grammi) {
            return cho100 * grammi / 100;
        }
    }

    private DatabaseAlimenti(Context c) {
        super(c, NOME, null, VERSIONE);
        app = c;
    }

    static synchronized DatabaseAlimenti get(Context c) {
        if (istanza == null) istanza = new DatabaseAlimenti(c.getApplicationContext());
        return istanza;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABELLA + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "nome TEXT NOT NULL, "
                + "categoria TEXT NOT NULL, "
                + "cho_100g REAL NOT NULL, "
                + "porzione_g REAL, "
                + "porzione_nome TEXT, "
                + "nome_norm TEXT NOT NULL, "
                + "ricerca TEXT NOT NULL, "
                + "personale INTEGER NOT NULL DEFAULT 0, "
                + "usi INTEGER NOT NULL DEFAULT 0)");
        caricaBase(db, new HashMap<>());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int vecchia, int nuova) {
        // Si ricaricano gli alimenti di base mantenendo quanto spesso sono stati usati
        Map<String, Integer> usi = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT nome, usi FROM " + TABELLA + " WHERE personale = 0", null)) {
            while (c.moveToNext()) usi.put(c.getString(0), c.getInt(1));
        }
        db.delete(TABELLA, "personale = 0", null);
        caricaBase(db, usi);
    }

    private void caricaBase(SQLiteDatabase db, Map<String, Integer> usi) {
        db.beginTransaction();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                app.getResources().openRawResource(R.raw.alimenti), StandardCharsets.UTF_8))) {
            String riga;
            while ((riga = r.readLine()) != null) {
                if (riga.trim().isEmpty() || riga.startsWith("#")) continue;
                String[] c = riga.split(";", -1);
                ContentValues v = valori(c[0], c[1], Double.parseDouble(c[2]),
                        c[3].isEmpty() ? 0 : Double.parseDouble(c[3]), c[4], c[5], false);
                Integer n = usi.get(c[0]);
                if (n != null) v.put("usi", n);
                db.insert(TABELLA, null, v);
            }
            db.setTransactionSuccessful();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere gli alimenti di base", e);
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Alimenti che contengono tutte le parole cercate (nel nome, nella categoria o nei sinonimi).
     * Prima i nomi che iniziano con la prima parola, poi quelli che la contengono, poi il resto;
     * a parita', i piu' usati.
     */
    List<Alimento> cerca(String testo) {
        StringBuilder sql = new StringBuilder("SELECT " + COLONNE + " FROM " + TABELLA);
        List<String> argomenti = new ArrayList<>();
        String prima = null;
        for (String parola : normalizza(testo).split("\\s+")) {
            if (parola.isEmpty()) continue;
            sql.append(prima == null ? " WHERE " : " AND ").append("ricerca LIKE ?");
            argomenti.add("%" + parola + "%");
            if (prima == null) prima = parola;
        }
        sql.append(" ORDER BY ");
        if (prima != null) {
            sql.append("CASE WHEN nome_norm LIKE ? THEN 3 WHEN nome_norm LIKE ? THEN 2 ")
                    .append("WHEN nome_norm LIKE ? THEN 1 ELSE 0 END DESC, ");
            argomenti.add(prima + "%");
            argomenti.add("% " + prima + "%");
            argomenti.add("%" + prima + "%");
        }
        sql.append("usi DESC, personale DESC, nome COLLATE NOCASE");

        List<Alimento> risultati = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql.toString(), argomenti.toArray(new String[0]))) {
            while (c.moveToNext()) risultati.add(daCursore(c));
        }
        return risultati;
    }

    Alimento perId(long id) {
        try (Cursor c = getReadableDatabase().query(TABELLA, COLONNE.split(", "),
                "_id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? daCursore(c) : null;
        }
    }

    long aggiungiPersonale(String nome, double cho100, double porzione, String nomePorzione) {
        return getWritableDatabase().insert(TABELLA, null,
                valori(nome, app.getString(R.string.categoria_personali), cho100, porzione,
                        nomePorzione, "", true));
    }

    void modificaPersonale(long id, String nome, double cho100, double porzione, String nomePorzione) {
        ContentValues v = valori(nome, app.getString(R.string.categoria_personali), cho100,
                porzione, nomePorzione, "", true);
        getWritableDatabase().update(TABELLA, v, "_id = ? AND personale = 1",
                new String[]{String.valueOf(id)});
    }

    void eliminaPersonale(long id) {
        getWritableDatabase().delete(TABELLA, "_id = ? AND personale = 1",
                new String[]{String.valueOf(id)});
    }

    void segnaUso(long id) {
        getWritableDatabase().execSQL("UPDATE " + TABELLA + " SET usi = usi + 1 WHERE _id = ?",
                new Object[]{id});
    }

    private static ContentValues valori(String nome, String categoria, double cho100,
                                        double porzione, String nomePorzione, String sinonimi,
                                        boolean personale) {
        ContentValues v = new ContentValues();
        v.put("nome", nome);
        v.put("categoria", categoria);
        v.put("cho_100g", cho100);
        if (porzione > 0) v.put("porzione_g", porzione);
        else v.putNull("porzione_g");
        v.put("porzione_nome", nomePorzione);
        v.put("nome_norm", normalizza(nome));
        v.put("ricerca", normalizza(nome + " " + categoria + " " + sinonimi));
        v.put("personale", personale ? 1 : 0);
        return v;
    }

    private static Alimento daCursore(Cursor c) {
        return new Alimento(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3),
                c.isNull(4) ? 0 : c.getDouble(4), c.isNull(5) ? "" : c.getString(5),
                c.getInt(6) == 1);
    }

    /** Minuscole, senza accenti e apostrofi: "Purè" e "pure" si trovano allo stesso modo. */
    static String normalizza(String s) {
        String senzaAccenti = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return senzaAccenti.toLowerCase(Locale.ITALIAN).replaceAll("['’/(),.]", " ").trim();
    }
}
