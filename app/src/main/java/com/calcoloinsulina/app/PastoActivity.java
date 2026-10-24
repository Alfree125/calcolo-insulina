package com.calcoloinsulina.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Calcolo dei CHO di un pasto componendolo dagli alimenti del database locale. */
public class PastoActivity extends AppCompatActivity {

    /** Risultato: grammi di CHO del pasto (double). */
    static final String EXTRA_CHO = "cho";

    private static final String STATO_ID = "id";
    private static final String STATO_GRAMMI = "grammi";

    /** Un alimento del pasto con il suo peso. */
    private static final class Voce {
        final DatabaseAlimenti.Alimento alimento;
        double grammi;

        Voce(DatabaseAlimenti.Alimento alimento, double grammi) {
            this.alimento = alimento;
            this.grammi = grammi;
        }

        double cho() {
            return alimento.cho(grammi);
        }
    }

    private final List<Voce> pasto = new ArrayList<>();
    private final DecimalFormat fmt =
            new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ITALY));

    private DatabaseAlimenti db;
    private Adattatore adattatore;
    private EditText inCerca;
    private TextView outTotale, testoVuoto, testoNessunRisultato;
    private ChipGroup chipPasto;
    private NestedScrollView scrollChip;
    private View gruppoNessunRisultato;
    private RecyclerView lista;
    private Button btnUsa;
    private final ExecutorService rete = Executors.newSingleThreadExecutor();
    private boolean tastiera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Prefs.applicaColori(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pasto);

        db = DatabaseAlimenti.get(this);
        inCerca = findViewById(R.id.in_cerca);
        outTotale = findViewById(R.id.out_totale);
        testoVuoto = findViewById(R.id.testo_vuoto);
        testoNessunRisultato = findViewById(R.id.testo_nessun_risultato);
        chipPasto = findViewById(R.id.chip_pasto);
        scrollChip = findViewById(R.id.scroll_chip);
        gruppoNessunRisultato = findViewById(R.id.gruppo_nessun_risultato);
        lista = findViewById(R.id.lista);
        btnUsa = findViewById(R.id.btn_usa);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        // Ricerca online solo in modalita' sviluppatore
        boolean sviluppatore = Prefs.get(this).getBoolean(Prefs.SVILUPPATORE, false);
        toolbar.getMenu().findItem(R.id.menu_cerca_online).setVisible(sviluppatore);
        findViewById(R.id.btn_cerca_online).setVisibility(sviluppatore ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_cerca_online).setOnClickListener(v -> cercaOnline());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_cerca_online) cercaOnline();
            else mostraDialogoAlimento(null, "", null);
            return true;
        });

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barre.left, barre.top, barre.right, Math.max(barre.bottom, ime.bottom));
            // Con la tastiera aperta restano il totale e l'elenco: gli alimenti scelti si nascondono
            boolean visibile = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (visibile != tastiera) {
                tastiera = visibile;
                aggiornaCompatto();
            }
            return WindowInsetsCompat.CONSUMED;
        });

        // Al massimo tre righe di alimenti scelti: dalla quarta si scorre dentro la card
        int massimo = Math.round(170 * getResources().getDisplayMetrics().density);
        chipPasto.addOnLayoutChangeListener((v, l, t, r, b, vl, vt, vr, vb) -> {
            int altezza = b - t > massimo ? massimo : ViewGroup.LayoutParams.WRAP_CONTENT;
            if (scrollChip.getLayoutParams().height != altezza) {
                scrollChip.post(() -> {
                    ViewGroup.LayoutParams lp = scrollChip.getLayoutParams();
                    lp.height = altezza;
                    scrollChip.setLayoutParams(lp);
                });
            }
        });

        adattatore = new Adattatore();
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adattatore);

        inCerca.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                cerca();
            }
        });
        findViewById(R.id.btn_nuovo_da_ricerca).setOnClickListener(
                v -> mostraDialogoAlimento(null, inCerca.getText().toString().trim(), null));

        btnUsa.setOnClickListener(v -> {
            for (Voce voce : pasto) db.segnaUso(voce.alimento.id);
            double totale = Math.round(totale() * 10) / 10.0;
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_CHO, totale));
            finish();
        });

        if (savedInstanceState != null) {
            long[] id = savedInstanceState.getLongArray(STATO_ID);
            double[] grammi = savedInstanceState.getDoubleArray(STATO_GRAMMI);
            if (id != null && grammi != null) {
                for (int i = 0; i < id.length; i++) {
                    DatabaseAlimenti.Alimento a = db.perId(id[i]);
                    if (a != null) pasto.add(new Voce(a, grammi[i]));
                }
            }
        }
        cerca();
        aggiornaPasto();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        long[] id = new long[pasto.size()];
        double[] grammi = new double[pasto.size()];
        for (int i = 0; i < pasto.size(); i++) {
            id[i] = pasto.get(i).alimento.id;
            grammi[i] = pasto.get(i).grammi;
        }
        outState.putLongArray(STATO_ID, id);
        outState.putDoubleArray(STATO_GRAMMI, grammi);
    }

    private void cerca() {
        String testo = inCerca.getText().toString();
        List<DatabaseAlimenti.Alimento> risultati = db.cerca(testo);
        adattatore.imposta(risultati);
        boolean vuoto = risultati.isEmpty();
        gruppoNessunRisultato.setVisibility(vuoto ? View.VISIBLE : View.GONE);
        if (vuoto) {
            testoNessunRisultato.setText(getString(R.string.nessun_risultato, testo.trim()));
        }
        lista.scrollToPosition(0);
    }

    private double totale() {
        double t = 0;
        for (Voce v : pasto) t += v.cho();
        return t;
    }

    private void aggiornaCompatto() {
        testoVuoto.setVisibility(!tastiera && pasto.isEmpty() ? View.VISIBLE : View.GONE);
        scrollChip.setVisibility(tastiera ? View.GONE : View.VISIBLE);
    }

    private void aggiornaPasto() {
        double totale = totale();
        outTotale.setText(getString(R.string.grammi, fmt.format(totale)));
        aggiornaCompatto();

        chipPasto.removeAllViews();
        for (Voce voce : pasto) {
            Chip chip = new Chip(this);
            chip.setText(getString(R.string.voce_pasto, voce.alimento.nome,
                    fmt.format(voce.grammi), fmt.format(voce.cho())));
            chip.setCloseIconVisible(true);
            chip.setCloseIconContentDescription(getString(R.string.rimuovi));
            chip.setOnCloseIconClickListener(v -> {
                pasto.remove(voce);
                aggiornaPasto();
            });
            chip.setOnClickListener(v -> mostraDialogoGrammi(voce.alimento, voce));
            chipPasto.addView(chip);
        }
        btnUsa.setEnabled(totale > 0);
        btnUsa.setText(getString(R.string.usa_cho, fmt.format(Math.round(totale * 10) / 10.0)));
    }

    /** Aggiunta di un alimento al pasto (voce null) o modifica del peso di una voce esistente. */
    private void mostraDialogoGrammi(DatabaseAlimenti.Alimento a, Voce voce) {
        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_grammi, null);
        TextView info = vista.findViewById(R.id.info);
        EditText inGrammi = vista.findViewById(R.id.in_grammi);
        Chip chipPorzione = vista.findViewById(R.id.chip_porzione);
        TextView anteprima = vista.findViewById(R.id.anteprima);

        info.setText(getString(R.string.cho_ogni_100, fmt.format(a.cho100)));
        if (a.porzione > 0) {
            String nomePorzione = a.nomePorzione.isEmpty()
                    ? getString(R.string.una_porzione) : a.nomePorzione;
            chipPorzione.setText(getString(R.string.porzione_chip, nomePorzione, fmt.format(a.porzione)));
            chipPorzione.setOnClickListener(v -> inGrammi.setText(fmt.format(a.porzione)));
        } else {
            chipPorzione.setVisibility(View.GONE);
        }

        AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle(a.nome)
                .setView(vista)
                .setPositiveButton(voce == null ? R.string.aggiungi : R.string.salva, null)
                .setNegativeButton(R.string.annulla, null)
                .create();
        if (voce != null) {
            dialogo.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.rimuovi), (d, w) -> {
                pasto.remove(voce);
                aggiornaPasto();
            });
        }

        TextWatcher anteprimaCho = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Double g = MainActivity.parse(inGrammi);
                anteprima.setText(g == null ? "" : getString(R.string.uguale_cho, fmt.format(a.cho(g))));
                Button ok = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);
                if (ok != null) ok.setEnabled(g != null && g > 0);
            }
        };
        inGrammi.addTextChangedListener(anteprimaCho);

        dialogo.setOnShowListener(d -> {
            if (voce != null) inGrammi.setText(fmt.format(voce.grammi));
            else if (a.porzione > 0) inGrammi.setText(fmt.format(a.porzione));
            else anteprimaCho.afterTextChanged(inGrammi.getText());
            inGrammi.requestFocus();
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Double g = MainActivity.parse(inGrammi);
                if (g == null || g <= 0) return;
                if (voce != null) {
                    voce.grammi = g;
                } else {
                    Voce esistente = null;
                    for (Voce x : pasto) if (x.alimento.id == a.id) esistente = x;
                    if (esistente != null) esistente.grammi += g;
                    else pasto.add(new Voce(a, g));
                }
                aggiornaPasto();
                dialogo.dismiss();
            });
        });
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialogo.show();
    }

    /** Cerca il testo su Open Food Facts; dai risultati si crea un alimento personale. */
    private void cercaOnline() {
        String testo = inCerca.getText().toString().trim();
        if (testo.isEmpty()) {
            Toast.makeText(this, R.string.scrivi_prima, Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog attesa = new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.ricerca_in_corso)
                .setNegativeButton(R.string.annulla, null)
                .show();
        rete.execute(() -> {
            List<RicercaOnline.Prodotto> trovati;
            try {
                trovati = RicercaOnline.cerca(this, testo);
            } catch (IOException e) {
                trovati = null;
            }
            List<RicercaOnline.Prodotto> risultato = trovati;
            runOnUiThread(() -> {
                if (isDestroyed() || !attesa.isShowing()) return;
                attesa.dismiss();
                mostraRisultatiOnline(testo, risultato);
            });
        });
    }

    /** prodotti null = ricerca fallita. */
    private void mostraRisultatiOnline(String testo, List<RicercaOnline.Prodotto> prodotti) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.risultati_online)
                .setNeutralButton(R.string.cerca_su_google, (d, w) -> cercaSuGoogle(testo))
                .setNegativeButton(R.string.annulla, null);
        if (prodotti == null) {
            b.setMessage(R.string.ricerca_errore);
        } else if (prodotti.isEmpty()) {
            b.setMessage(getString(R.string.ricerca_vuota, testo));
        } else {
            CharSequence[] righe = new CharSequence[prodotti.size()];
            for (int i = 0; i < righe.length; i++) {
                righe[i] = getString(R.string.prodotto_online, prodotti.get(i).nome,
                        fmt.format(prodotti.get(i).cho100));
            }
            b.setItems(righe, (d, i) -> mostraDialogoAlimento(null, "", prodotti.get(i)));
        }
        b.show();
    }

    private void cercaSuGoogle(String testo) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q="
                    + URLEncoder.encode(getString(R.string.query_google, testo), "UTF-8"))));
        } catch (ActivityNotFoundException | java.io.UnsupportedEncodingException e) {
            Toast.makeText(this, R.string.nessun_browser, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        rete.shutdownNow();
    }

    /**
     * Creazione (alimento null) o modifica/eliminazione di un alimento personale;
     * {@code proposta} precompila un nuovo alimento con i dati trovati online.
     */
    private void mostraDialogoAlimento(DatabaseAlimenti.Alimento a, String nomeIniziale,
                                       RicercaOnline.Prodotto proposta) {
        View vista = LayoutInflater.from(this).inflate(R.layout.dialog_alimento, null);
        TextInputLayout campoNome = vista.findViewById(R.id.campo_nome);
        TextInputLayout campoCho = vista.findViewById(R.id.campo_cho100);
        EditText inNome = vista.findViewById(R.id.in_nome);
        EditText inCho = vista.findViewById(R.id.in_cho100);
        EditText inPorzione = vista.findViewById(R.id.in_porzione);
        EditText inNomePorzione = vista.findViewById(R.id.in_nome_porzione);

        if (a != null) {
            inNome.setText(a.nome);
            inCho.setText(fmt.format(a.cho100));
            if (a.porzione > 0) inPorzione.setText(fmt.format(a.porzione));
            inNomePorzione.setText(a.nomePorzione);
        } else if (proposta != null) {
            inNome.setText(proposta.nome);
            inCho.setText(fmt.format(proposta.cho100));
            if (proposta.porzione > 0) inPorzione.setText(fmt.format(proposta.porzione));
        } else {
            inNome.setText(nomeIniziale);
        }

        AlertDialog dialogo = new MaterialAlertDialogBuilder(this)
                .setTitle(a == null ? R.string.nuovo_alimento : R.string.modifica_alimento)
                .setView(vista)
                .setPositiveButton(R.string.salva, null)
                .setNegativeButton(R.string.annulla, null)
                .create();
        if (a != null) {
            dialogo.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.elimina), (d, w) -> {
                db.eliminaPersonale(a.id);
                for (int i = pasto.size() - 1; i >= 0; i--) {
                    if (pasto.get(i).alimento.id == a.id) pasto.remove(i);
                }
                aggiornaPasto();
                cerca();
            });
        }
        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String nome = inNome.getText().toString().trim();
                    Double cho = MainActivity.parse(inCho);
                    Double porzione = MainActivity.parse(inPorzione);
                    boolean ok = true;
                    if (nome.isEmpty()) {
                        campoNome.setError(getString(R.string.errore_nome));
                        ok = false;
                    } else {
                        campoNome.setError(null);
                    }
                    if (cho == null || cho < 0 || cho > 100) {
                        campoCho.setError(getString(R.string.errore_cho100));
                        ok = false;
                    } else {
                        campoCho.setError(null);
                    }
                    if (!ok) return;
                    double p = porzione == null ? 0 : porzione;
                    String nomePorzione = inNomePorzione.getText().toString().trim();
                    if (a == null) {
                        long id = db.aggiungiPersonale(nome, cho, p, nomePorzione);
                        inCerca.setText(nome);
                        dialogo.dismiss();
                        DatabaseAlimenti.Alimento nuovo = db.perId(id);
                        if (nuovo != null) mostraDialogoGrammi(nuovo, null);
                    } else {
                        db.modificaPersonale(a.id, nome, cho, p, nomePorzione);
                        DatabaseAlimenti.Alimento aggiornato = db.perId(a.id);
                        for (Voce x : pasto) {
                            if (x.alimento.id == a.id && aggiornato != null) {
                                pasto.set(pasto.indexOf(x), new Voce(aggiornato, x.grammi));
                                break;
                            }
                        }
                        aggiornaPasto();
                        cerca();
                        dialogo.dismiss();
                    }
                }));
        dialogo.show();
    }

    private String dettaglio(DatabaseAlimenti.Alimento a) {
        String base = getString(R.string.cho_100_breve, fmt.format(a.cho100));
        if (a.porzione > 0) {
            String nomePorzione = a.nomePorzione.isEmpty()
                    ? getString(R.string.una_porzione) : a.nomePorzione;
            base += " · " + getString(R.string.porzione_dettaglio, nomePorzione,
                    fmt.format(a.porzione), fmt.format(a.cho(a.porzione)));
        }
        return a.personale ? getString(R.string.personale) + " · " + base : base;
    }

    private final class Adattatore extends RecyclerView.Adapter<Adattatore.Riga> {
        private List<DatabaseAlimenti.Alimento> alimenti = new ArrayList<>();

        void imposta(List<DatabaseAlimenti.Alimento> nuovi) {
            alimenti = nuovi;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Riga onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Riga(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.riga_alimento, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Riga riga, int posizione) {
            DatabaseAlimenti.Alimento a = alimenti.get(posizione);
            riga.nome.setText(a.nome);
            riga.dettaglio.setText(dettaglio(a));
            riga.itemView.setOnClickListener(v -> mostraDialogoGrammi(a, null));
            riga.itemView.setOnLongClickListener(v -> {
                if (!a.personale) return false;
                mostraDialogoAlimento(a, "", null);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return alimenti.size();
        }

        final class Riga extends RecyclerView.ViewHolder {
            final TextView nome, dettaglio;

            Riga(View v) {
                super(v);
                nome = v.findViewById(R.id.nome);
                dettaglio = v.findViewById(R.id.dettaglio);
            }
        }
    }
}
