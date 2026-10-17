package com.calcoloinsulina.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String STATO_CHIAVE = "chiave_calcolo";
    /** Attesa dopo l'ultima modifica prima di considerare concluso il calcolo. */
    private static final long ATTESA_PROGRAMMAZIONE_MS = 3000;

    private EditText inCho, inGlicemia;
    private TextInputLayout campoGlicemia, campoCho;
    private TextView outTotale, outArrotondato, outPasto, outExtra, testoPromemoria;
    private View root, resultChips, cardPromemoria;
    private SharedPreferences prefs;
    private boolean coloriDinamici;
    private boolean compatto;
    private String statoGlicemia;

    /** Valori ("cho|glicemia") dell'ultimo calcolo per cui e' stato programmato il promemoria. */
    private String chiaveCalcolo;
    private String chiaveInAttesa;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable programma = this::programmaPromemoria;
    /** Nasconde la riga del promemoria quando la notifica scatta con l'app aperta. */
    private final SharedPreferences.OnSharedPreferenceChangeListener ascoltaPromemoria =
            (p, key) -> {
                if (Prefs.PROMEMORIA_ALLE.equals(key)) mostraPromemoria();
            };

    private final DecimalFormat fmt =
            new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ITALY));

    /** CHO stimati dalla foto e confermati dall'utente nella schermata di stima. */
    private final ActivityResultLauncher<Intent> stimaFoto =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                double cho = r.getData().getDoubleExtra(StimaActivity.EXTRA_CHO, -1);
                if (cho < 0) return;
                inCho.setText(fmt.format(cho));
                Snackbar.make(root, R.string.cho_inseriti, Snackbar.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = Prefs.get(this);
        coloriDinamici = Prefs.applicaColori(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Solo al primo avvio in assoluto: configurazione di parametri e impostazioni
        if (!prefs.getBoolean(Prefs.CONFIGURATO, false)) {
            startActivity(new Intent(this, ImpostazioniActivity.class)
                    .putExtra(ImpostazioniActivity.EXTRA_PRIMO_AVVIO, true));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        inCho = findViewById(R.id.in_cho);
        inGlicemia = findViewById(R.id.in_glicemia);
        campoGlicemia = findViewById(R.id.campo_glicemia);
        campoCho = findViewById(R.id.campo_cho);
        outTotale = findViewById(R.id.out_totale);
        outArrotondato = findViewById(R.id.out_arrotondato);
        outPasto = findViewById(R.id.out_pasto);
        outExtra = findViewById(R.id.out_extra);
        resultChips = findViewById(R.id.result_chips);
        cardPromemoria = findViewById(R.id.card_promemoria);
        testoPromemoria = findViewById(R.id.testo_promemoria);

        if (savedInstanceState != null) {
            chiaveCalcolo = savedInstanceState.getString(STATO_CHIAVE);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            String sezione = item.getItemId() == R.id.menu_impostazioni
                    ? ImpostazioniActivity.SEZIONE_IMPOSTAZIONI
                    : ImpostazioniActivity.SEZIONE_PARAMETRI;
            startActivity(new Intent(this, ImpostazioniActivity.class)
                    .putExtra(ImpostazioniActivity.EXTRA_SEZIONE, sezione));
            return true;
        });

        // Edge-to-edge (obbligatorio da Android 15): spazio per barre di sistema e tastiera
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barre.left, barre.top, barre.right, Math.max(barre.bottom, ime.bottom));
            impostaCompatto(insets.isVisible(WindowInsetsCompat.Type.ime()));
            return WindowInsetsCompat.CONSUMED;
        });

        TextWatcher ricalcola = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                aggiorna();
            }
        };
        inCho.addTextChangedListener(ricalcola);
        inGlicemia.addTextChangedListener(ricalcola);

        campoGlicemia.setEndIconOnClickListener(v -> {
            if (statoGlicemia != null) Snackbar.make(root, statoGlicemia, Snackbar.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_azzera).setOnClickListener(v -> azzera());
        findViewById(R.id.btn_annulla_promemoria).setOnClickListener(v -> {
            Promemoria.annulla(this);
            mostraPromemoria();
            Snackbar.make(root, R.string.promemoria_annullato, Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Parametri o tema possono essere cambiati nelle impostazioni
        if (coloriDinamici != prefs.getBoolean(Prefs.COLORI_DINAMICI, true)) {
            recreate();
            return;
        }
        aggiorna();
        mostraPromemoria();
        aggiornaIconaFoto();
        prefs.registerOnSharedPreferenceChangeListener(ascoltaPromemoria);
    }

    /** Icona fotocamera nel campo CHO, solo con la funzione beta attiva. */
    private void aggiornaIconaFoto() {
        if (!prefs.getBoolean(Prefs.FOTO_ATTIVA, false)) {
            campoCho.setEndIconMode(TextInputLayout.END_ICON_NONE);
            return;
        }
        campoCho.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        campoCho.setEndIconDrawable(R.drawable.ic_fotocamera);
        campoCho.setEndIconContentDescription(R.string.stima_da_foto);
        campoCho.setEndIconOnClickListener(v -> scegliFoto());
    }

    private void scegliFoto() {
        if (Segreti.chiaveApi(this) == null) {
            Snackbar.make(root, R.string.chiave_mancante, Snackbar.LENGTH_LONG)
                    .setAction(R.string.impostazioni, v -> startActivity(
                            new Intent(this, ImpostazioniActivity.class)
                                    .putExtra(ImpostazioniActivity.EXTRA_SEZIONE,
                                            ImpostazioniActivity.SEZIONE_BETA)))
                    .show();
            return;
        }
        String[] origini = {StimaActivity.ORIGINE_FOTOCAMERA, StimaActivity.ORIGINE_GALLERIA};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.stima_titolo_beta)
                .setItems(new CharSequence[]{getString(R.string.scatta_foto), getString(R.string.scegli_galleria)},
                        (d, i) -> stimaFoto.launch(new Intent(this, StimaActivity.class)
                                .putExtra(StimaActivity.EXTRA_ORIGINE, origini[i])))
                .setNegativeButton(R.string.annulla, null)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.unregisterOnSharedPreferenceChangeListener(ascoltaPromemoria);
        // Se si esce subito dopo il calcolo il promemoria parte comunque
        if (chiaveInAttesa != null) programmaPromemoria();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATO_CHIAVE, chiaveCalcolo);
    }

    private void aggiorna() {
        Double cho = parse(inCho);
        Double glicemia = parse(inGlicemia);

        InsulinCalculator.Result r = InsulinCalculator.calcola(cho, glicemia,
                Prefs.numero(prefs, Prefs.RAPPORTO),
                Prefs.numero(prefs, Prefs.FATTORE),
                Prefs.numero(prefs, Prefs.TARGET));

        outPasto.setText(r.uiPasto == null ? getString(R.string.placeholder) : fmt.format(r.uiPasto));
        outExtra.setText(r.uiExtra == null ? getString(R.string.placeholder) : conSegno(r.uiExtra));

        float passo = prefs.getFloat(Prefs.ARROTONDAMENTO, Prefs.ARROTONDAMENTO_DEFAULT);
        String nota;
        if (r.totale == null) {
            outTotale.setText(R.string.placeholder);
            nota = getString(R.string.inserisci_valori);
        } else if (r.totale < 0) {
            outTotale.setText(conUnita("0"));
            nota = getString(R.string.nessun_bolo, fmt.format(r.totale));
        } else {
            outTotale.setText(conUnita(fmt.format(r.totale)));
            nota = passo > 0
                    ? getString(R.string.arrotondato, fmt.format(passo),
                    fmt.format(InsulinCalculator.arrotonda(r.totale, passo)))
                    : null;
        }
        if (glicemia != null && r.uiExtra == null) nota = getString(R.string.glicemia_non_valida);
        outArrotondato.setText(nota);
        outArrotondato.setVisibility(nota == null ? View.GONE : View.VISIBLE);

        aggiornaStatoGlicemia(glicemia);
        valutaPromemoria(r);
    }

    /** Pallino colorato nel campo glicemia; toccandolo compare lo stato. */
    private void aggiornaStatoGlicemia(Double g) {
        if (g == null) {
            statoGlicemia = null;
            campoGlicemia.setEndIconVisible(false);
            return;
        }
        int colore;
        if (g < InsulinCalculator.GLICEMIA_MIN_VALIDA || g > InsulinCalculator.GLICEMIA_MAX_VALIDA) {
            colore = R.color.glicemia_bassa;
            statoGlicemia = getString(R.string.glicemia_non_valida);
        } else if (g < 70) {
            colore = R.color.glicemia_bassa;
            statoGlicemia = getString(R.string.stato_bassa);
        } else if (g <= 180) {
            colore = R.color.glicemia_ok;
            statoGlicemia = getString(R.string.stato_in_range);
        } else if (g <= 250) {
            colore = R.color.glicemia_alta;
            statoGlicemia = getString(R.string.stato_alta);
        } else {
            colore = R.color.glicemia_bassa;
            statoGlicemia = getString(R.string.stato_molto_alta);
        }
        campoGlicemia.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, colore)));
        campoGlicemia.setEndIconContentDescription(statoGlicemia);
        campoGlicemia.setEndIconVisible(true);
    }

    private void valutaPromemoria(InsulinCalculator.Result r) {
        handler.removeCallbacks(programma);
        chiaveInAttesa = null;
        if (r.totale == null
                || !prefs.getBoolean(Prefs.PROMEMORIA_ATTIVO, false)
                || !Promemoria.notifichePermesse(this)) {
            return;
        }
        String chiave = inCho.getText() + "|" + inGlicemia.getText();
        if (chiave.equals(chiaveCalcolo)) return;
        chiaveInAttesa = chiave;
        handler.postDelayed(programma, ATTESA_PROGRAMMAZIONE_MS);
    }

    private void programmaPromemoria() {
        handler.removeCallbacks(programma);
        if (chiaveInAttesa == null) return;
        chiaveCalcolo = chiaveInAttesa;
        chiaveInAttesa = null;
        Promemoria.programma(this, prefs.getInt(Prefs.PROMEMORIA_MINUTI, Prefs.MINUTI_DEFAULT));
        mostraPromemoria();
    }

    private void mostraPromemoria() {
        long alle = Promemoria.programmatoAlle(this);
        if (alle == 0) {
            cardPromemoria.setVisibility(View.GONE);
        } else {
            testoPromemoria.setText(getString(R.string.promemoria_alle,
                    DateFormat.getTimeFormat(this).format(new Date(alle))));
            cardPromemoria.setVisibility(View.VISIBLE);
        }
    }

    private void azzera() {
        handler.removeCallbacks(programma);
        chiaveInAttesa = null;
        chiaveCalcolo = null;
        inCho.setText("");
        inGlicemia.setText("");
        View focus = getCurrentFocus();
        if (focus != null) {
            focus.clearFocus();
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        ((NestedScrollView) findViewById(R.id.scroll)).smoothScrollTo(0, 0);
    }

    /** Con la tastiera aperta la card dei risultati si riduce per lasciare spazio ai campi. */
    private void impostaCompatto(boolean tastiera) {
        if (tastiera == compatto) return;
        compatto = tastiera;
        resultChips.setVisibility(tastiera ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams lp = outTotale.getLayoutParams();
        lp.height = getResources().getDimensionPixelSize(
                tastiera ? R.dimen.altezza_totale_compatta : R.dimen.altezza_totale);
        outTotale.setLayoutParams(lp);
    }

    private CharSequence conUnita(String numero) {
        SpannableStringBuilder s = new SpannableStringBuilder(numero);
        int inizio = s.length();
        s.append(" UI");
        s.setSpan(new RelativeSizeSpan(0.4f), inizio, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private String conSegno(double v) {
        if (v > 0) return "+" + fmt.format(v);
        if (v < 0) return "−" + fmt.format(-v);
        return fmt.format(v);
    }

    static Double parse(EditText e) {
        String s = e.getText().toString().trim().replace(',', '.');
        if (s.isEmpty() || s.equals(".")) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
