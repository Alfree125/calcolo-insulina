package com.calcoloinsulina.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

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
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Parametri personali e impostazioni. Al primo avvio fa da schermata di benvenuto,
 * poi si apre dal menu a tendina della schermata principale.
 */
public class ImpostazioniActivity extends AppCompatActivity {

    static final String EXTRA_PRIMO_AVVIO = "primo_avvio";
    static final String EXTRA_SEZIONE = "sezione";
    static final String SEZIONE_PARAMETRI = "parametri";
    static final String SEZIONE_IMPOSTAZIONI = "impostazioni";
    static final String SEZIONE_BETA = "beta";

    private static final float[] PASSI_ARROTONDAMENTO = {0.5f, 1f, 0f};

    private SharedPreferences prefs;
    private boolean primoAvvio;
    private View root;
    private TextInputLayout campoRapporto, campoFattore, campoTarget, campoIntervallo;
    private EditText inRapporto, inFattore, inTarget;
    private MaterialSwitch switchPromemoria, switchColori, switchFoto;
    private MaterialAutoCompleteTextView sceltaIntervallo, sceltaArrotondamento, sceltaModello;
    private TextInputLayout campoChiave;
    private EditText inChiave;
    private View gruppoFoto;
    private int[] minutiIntervalli;
    private String[] modelli;
    private int indiceIntervallo, indiceArrotondamento, indiceModello;

    private final DecimalFormat fmtParam =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ITALY));

    private final ActivityResultLauncher<String> richiestaPermesso =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concesso -> {
                if (!concesso) {
                    switchPromemoria.setChecked(false);
                    Snackbar.make(root, R.string.permesso_negato, Snackbar.LENGTH_LONG)
                            .setAction(R.string.apri_impostazioni, v -> apriImpostazioniNotifiche())
                            .show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = Prefs.get(this);
        Prefs.applicaColori(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_impostazioni);

        primoAvvio = getIntent().getBooleanExtra(EXTRA_PRIMO_AVVIO, false);
        root = findViewById(R.id.root);
        campoRapporto = findViewById(R.id.campo_rapporto);
        campoFattore = findViewById(R.id.campo_fattore);
        campoTarget = findViewById(R.id.campo_target);
        campoIntervallo = findViewById(R.id.campo_intervallo);
        inRapporto = findViewById(R.id.in_rapporto);
        inFattore = findViewById(R.id.in_fattore);
        inTarget = findViewById(R.id.in_target);
        switchPromemoria = findViewById(R.id.switch_promemoria);
        switchColori = findViewById(R.id.switch_colori);
        sceltaIntervallo = findViewById(R.id.scelta_intervallo);
        sceltaArrotondamento = findViewById(R.id.scelta_arrotondamento);
        minutiIntervalli = getResources().getIntArray(R.array.intervalli_minuti);
        switchFoto = findViewById(R.id.switch_foto);
        sceltaModello = findViewById(R.id.scelta_modello);
        campoChiave = findViewById(R.id.campo_chiave);
        inChiave = findViewById(R.id.in_chiave);
        gruppoFoto = findViewById(R.id.gruppo_foto);
        modelli = getResources().getStringArray(R.array.modelli_id);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (primoAvvio) {
            toolbar.setTitle(R.string.benvenuto);
            findViewById(R.id.testo_benvenuto).setVisibility(View.VISIBLE);
            ((android.widget.Button) findViewById(R.id.btn_salva)).setText(R.string.inizia);
        } else {
            toolbar.setTitle(R.string.impostazioni);
            toolbar.setNavigationIcon(R.drawable.ic_indietro);
            toolbar.setNavigationContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description);
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barre.left, barre.top, barre.right, Math.max(barre.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        if (savedInstanceState == null) caricaValori();
        else {
            indiceIntervallo = savedInstanceState.getInt("intervallo");
            indiceArrotondamento = savedInstanceState.getInt("arrotondamento");
            indiceModello = savedInstanceState.getInt("modello");
        }

        rimuoviErroreAllaModifica(inRapporto, campoRapporto);
        rimuoviErroreAllaModifica(inFattore, campoFattore);
        rimuoviErroreAllaModifica(inTarget, campoTarget);
        rimuoviErroreAllaModifica(inChiave, campoChiave);

        sceltaIntervallo.setOnItemClickListener((p, v, pos, id) -> indiceIntervallo = pos);
        sceltaArrotondamento.setOnItemClickListener((p, v, pos, id) -> indiceArrotondamento = pos);
        sceltaModello.setOnItemClickListener((p, v, pos, id) -> indiceModello = pos);

        findViewById(R.id.riga_foto).setOnClickListener(v -> switchFoto.toggle());
        switchFoto.setOnCheckedChangeListener((b, attivo) ->
                gruppoFoto.setVisibility(attivo ? View.VISIBLE : View.GONE));
        gruppoFoto.setVisibility(switchFoto.isChecked() ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_crea_chiave).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://console.anthropic.com/settings/keys")));
            } catch (android.content.ActivityNotFoundException ignored) {
                // nessun browser installato
            }
        });

        findViewById(R.id.riga_promemoria).setOnClickListener(v -> switchPromemoria.toggle());
        switchPromemoria.setOnCheckedChangeListener((b, attivo) -> {
            campoIntervallo.setEnabled(attivo);
            if (attivo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                richiestaPermesso.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
        campoIntervallo.setEnabled(switchPromemoria.isChecked());

        if (DynamicColors.isDynamicColorAvailable()) {
            View rigaColori = findViewById(R.id.riga_colori);
            rigaColori.setVisibility(View.VISIBLE);
            rigaColori.setOnClickListener(v -> switchColori.toggle());
        }

        findViewById(R.id.btn_salva).setOnClickListener(v -> salva());

        String sezione = getIntent().getStringExtra(EXTRA_SEZIONE);
        if (savedInstanceState == null
                && (SEZIONE_IMPOSTAZIONI.equals(sezione) || SEZIONE_BETA.equals(sezione))) {
            NestedScrollView scroll = findViewById(R.id.scroll);
            View titolo = findViewById(SEZIONE_BETA.equals(sezione)
                    ? R.id.sezione_beta : R.id.sezione_impostazioni);
            scroll.post(() -> scroll.scrollTo(0, titolo.getTop()));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("intervallo", indiceIntervallo);
        outState.putInt("arrotondamento", indiceArrotondamento);
        outState.putInt("modello", indiceModello);
    }

    private void caricaValori() {
        // Al primo avvio rapporto e fattore restano vuoti: vanno personalizzati
        Double rapporto = Prefs.numero(prefs, Prefs.RAPPORTO);
        Double fattore = Prefs.numero(prefs, Prefs.FATTORE);
        Double target = Prefs.numero(prefs, Prefs.TARGET);
        if (rapporto != null) inRapporto.setText(fmtParam.format(rapporto));
        if (fattore != null) inFattore.setText(fmtParam.format(fattore));
        inTarget.setText(fmtParam.format(target != null ? target : InsulinCalculator.DEFAULT_TARGET));

        switchPromemoria.setChecked(prefs.getBoolean(Prefs.PROMEMORIA_ATTIVO, false)
                && Promemoria.notifichePermesse(this));
        switchColori.setChecked(prefs.getBoolean(Prefs.COLORI_DINAMICI, true));

        int minuti = prefs.getInt(Prefs.PROMEMORIA_MINUTI, Prefs.MINUTI_DEFAULT);
        indiceIntervallo = 3; // 2 ore
        for (int i = 0; i < minutiIntervalli.length; i++) {
            if (minutiIntervalli[i] == minuti) indiceIntervallo = i;
        }
        float passo = prefs.getFloat(Prefs.ARROTONDAMENTO, Prefs.ARROTONDAMENTO_DEFAULT);
        indiceArrotondamento = 0;
        for (int i = 0; i < PASSI_ARROTONDAMENTO.length; i++) {
            if (PASSI_ARROTONDAMENTO[i] == passo) indiceArrotondamento = i;
        }
        String[] intervalli = getResources().getStringArray(R.array.intervalli_etichette);
        String[] arrotondamenti = getResources().getStringArray(R.array.arrotondamento_etichette);
        sceltaIntervallo.setText(intervalli[indiceIntervallo], false);
        sceltaArrotondamento.setText(arrotondamenti[indiceArrotondamento], false);

        switchFoto.setChecked(prefs.getBoolean(Prefs.FOTO_ATTIVA, false));
        String chiave = Segreti.chiaveApi(this);
        if (chiave != null) inChiave.setText(chiave);
        String modello = prefs.getString(Prefs.MODELLO_AI, Prefs.MODELLO_DEFAULT);
        indiceModello = 0;
        for (int i = 0; i < modelli.length; i++) {
            if (modelli[i].equals(modello)) indiceModello = i;
        }
        sceltaModello.setText(
                getResources().getStringArray(R.array.modelli_etichette)[indiceModello], false);
    }

    private void salva() {
        Double rapporto = MainActivity.parse(inRapporto);
        Double fattore = MainActivity.parse(inFattore);
        Double target = MainActivity.parse(inTarget);

        boolean ok = true;
        if (rapporto == null || rapporto <= 0) {
            campoRapporto.setError(getString(R.string.errore_positivo));
            ok = false;
        }
        if (fattore == null || fattore <= 0) {
            campoFattore.setError(getString(R.string.errore_positivo));
            ok = false;
        }
        if (target == null || target < 70 || target > 250) {
            campoTarget.setError(getString(R.string.errore_target));
            ok = false;
        }
        if (!ok) {
            NestedScrollView scroll = findViewById(R.id.scroll);
            scroll.smoothScrollTo(0, findViewById(R.id.sezione_parametri).getTop());
            return;
        }
        String chiave = inChiave.getText().toString().trim();
        if (switchFoto.isChecked() && chiave.isEmpty()) {
            campoChiave.setError(getString(R.string.errore_chiave));
            NestedScrollView scroll = findViewById(R.id.scroll);
            scroll.smoothScrollTo(0, findViewById(R.id.sezione_beta).getTop());
            return;
        }
        if (!chiave.equals(Segreti.chiaveApi(this))) {
            try {
                Segreti.salvaChiaveApi(this, chiave);
            } catch (IllegalStateException e) {
                campoChiave.setError(getString(R.string.errore_salvataggio_chiave));
                return;
            }
        }

        boolean promemoria = switchPromemoria.isChecked();
        prefs.edit()
                .putFloat(Prefs.RAPPORTO, rapporto.floatValue())
                .putFloat(Prefs.FATTORE, fattore.floatValue())
                .putFloat(Prefs.TARGET, target.floatValue())
                .putBoolean(Prefs.PROMEMORIA_ATTIVO, promemoria)
                .putInt(Prefs.PROMEMORIA_MINUTI, minutiIntervalli[indiceIntervallo])
                .putFloat(Prefs.ARROTONDAMENTO, PASSI_ARROTONDAMENTO[indiceArrotondamento])
                .putBoolean(Prefs.COLORI_DINAMICI, switchColori.isChecked())
                .putBoolean(Prefs.FOTO_ATTIVA, switchFoto.isChecked())
                .putString(Prefs.MODELLO_AI, modelli[indiceModello])
                .putBoolean(Prefs.CONFIGURATO, true)
                .apply();
        if (!promemoria) Promemoria.annulla(this);

        if (primoAvvio) startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void apriImpostazioniNotifiche() {
        Intent i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(i);
    }

    private static void rimuoviErroreAllaModifica(EditText e, TextInputLayout campo) {
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                campo.setError(null);
            }
        });
    }
}
