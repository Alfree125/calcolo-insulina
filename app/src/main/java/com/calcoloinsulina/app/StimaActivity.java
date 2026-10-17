package com.calcoloinsulina.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.os.BundleCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Stima dei CHO da una foto del pasto (beta): scatto o galleria, analisi con Claude, conferma. */
public class StimaActivity extends AppCompatActivity {

    static final String EXTRA_ORIGINE = "origine";
    static final String ORIGINE_FOTOCAMERA = "fotocamera";
    static final String ORIGINE_GALLERIA = "galleria";
    /** Risultato: grammi di CHO confermati dall'utente (double). */
    static final String EXTRA_CHO = "cho";

    private static final String STATO_URI = "uri";

    private final ExecutorService esecutore = Executors.newSingleThreadExecutor();
    private final Handler principale = new Handler(Looper.getMainLooper());
    private final DecimalFormat fmt =
            new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ITALY));

    private Uri fotoUri;
    private Bitmap foto;
    private double choStimati;

    private ImageView anteprima;
    private View caricamento, gruppoAttesa, cardErrore, cardRisultato;
    private Button btnAnalizza, btnUsa;
    private EditText inIndicazioni;
    private TextView testoErrore, outCho, outAffidabilita, outNote;
    private LinearLayout listaAlimenti;
    private NestedScrollView scroll;

    private final ActivityResultLauncher<Uri> scatta =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok) caricaFoto();
                else finish();
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> scegli =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) {
                    finish();
                } else {
                    fotoUri = uri;
                    caricaFoto();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Prefs.applicaColori(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_stima);

        anteprima = findViewById(R.id.anteprima);
        caricamento = findViewById(R.id.caricamento);
        gruppoAttesa = findViewById(R.id.gruppo_attesa);
        cardErrore = findViewById(R.id.card_errore);
        cardRisultato = findViewById(R.id.card_risultato);
        btnAnalizza = findViewById(R.id.btn_analizza);
        btnUsa = findViewById(R.id.btn_usa);
        inIndicazioni = findViewById(R.id.in_indicazioni);
        testoErrore = findViewById(R.id.testo_errore);
        outCho = findViewById(R.id.out_cho);
        outAffidabilita = findViewById(R.id.out_affidabilita);
        outNote = findViewById(R.id.out_note);
        listaAlimenti = findViewById(R.id.lista_alimenti);
        scroll = findViewById(R.id.scroll);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barre.left, barre.top, barre.right, Math.max(barre.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        btnAnalizza.setOnClickListener(v -> analizza());
        btnUsa.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_CHO, choStimati));
            finish();
        });

        if (savedInstanceState != null) {
            fotoUri = BundleCompat.getParcelable(savedInstanceState, STATO_URI, Uri.class);
            if (fotoUri != null) caricaFoto();
            else finish();
        } else {
            avviaScelta();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATO_URI, fotoUri);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        esecutore.shutdownNow();
        if (isFinishing()) {
            File[] scatti = new File(getCacheDir(), "foto").listFiles();
            if (scatti != null) for (File f : scatti) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    private void avviaScelta() {
        try {
            if (ORIGINE_FOTOCAMERA.equals(getIntent().getStringExtra(EXTRA_ORIGINE))) {
                File cartella = new File(getCacheDir(), "foto");
                //noinspection ResultOfMethodCallIgnored
                cartella.mkdirs();
                fotoUri = FileProvider.getUriForFile(this, getPackageName() + ".foto",
                        new File(cartella, "pasto.jpg"));
                scatta.launch(fotoUri);
            } else {
                scegli.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.nessuna_fotocamera, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void caricaFoto() {
        caricamento.setVisibility(View.VISIBLE);
        esecutore.execute(() -> {
            try {
                Bitmap b = Foto.carica(this, fotoUri);
                principale.post(() -> {
                    if (isDestroyed()) return;
                    foto = b;
                    anteprima.setImageBitmap(b);
                    caricamento.setVisibility(View.GONE);
                    btnAnalizza.setEnabled(true);
                });
            } catch (IOException | SecurityException e) {
                principale.post(() -> {
                    if (isDestroyed()) return;
                    Toast.makeText(this, R.string.foto_non_leggibile, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void analizza() {
        String chiave = Segreti.chiaveApi(this);
        if (chiave == null) {
            mostraErrore(getString(R.string.chiave_mancante));
            return;
        }
        String modello = Prefs.get(this).getString(Prefs.MODELLO_AI, Prefs.MODELLO_DEFAULT);
        String indicazioni = inIndicazioni.getText().toString();
        Bitmap daInviare = foto;

        btnAnalizza.setEnabled(false);
        gruppoAttesa.setVisibility(View.VISIBLE);
        cardErrore.setVisibility(View.GONE);
        cardRisultato.setVisibility(View.GONE);
        btnUsa.setVisibility(View.GONE);

        esecutore.execute(() -> {
            try {
                StimaCho.Risultato r = StimaCho.stima(chiave, modello,
                        Foto.jpegBase64(daInviare), indicazioni);
                principale.post(() -> {
                    if (!isDestroyed()) mostraRisultato(r);
                });
            } catch (StimaCho.Errore e) {
                principale.post(() -> {
                    if (!isDestroyed()) mostraErrore(e.getMessage());
                });
            }
        });
    }

    private void mostraErrore(String messaggio) {
        gruppoAttesa.setVisibility(View.GONE);
        btnAnalizza.setEnabled(foto != null);
        testoErrore.setText(messaggio);
        cardErrore.setVisibility(View.VISIBLE);
    }

    private void mostraRisultato(StimaCho.Risultato r) {
        gruppoAttesa.setVisibility(View.GONE);
        btnAnalizza.setEnabled(true);
        btnAnalizza.setText(R.string.analizza_di_nuovo);

        choStimati = Math.round(r.totale);
        outCho.setText(getString(R.string.grammi, fmt.format(choStimati)));

        int colore, etichetta;
        switch (r.affidabilita) {
            case "alta":
                colore = R.color.glicemia_ok;
                etichetta = R.string.affidabilita_alta;
                break;
            case "media":
                colore = R.color.glicemia_alta;
                etichetta = R.string.affidabilita_media;
                break;
            default:
                colore = R.color.glicemia_bassa;
                etichetta = R.string.affidabilita_bassa;
                break;
        }
        outAffidabilita.setText(etichetta);
        outAffidabilita.setTextColor(ContextCompat.getColor(this, colore));
        outAffidabilita.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.md_surface_container_lowest)));

        listaAlimenti.removeAllViews();
        for (StimaCho.Alimento a : r.alimenti) listaAlimenti.addView(riga(a));

        outNote.setText(r.note);
        outNote.setVisibility(r.note.isEmpty() ? View.GONE : View.VISIBLE);
        cardRisultato.setVisibility(View.VISIBLE);

        if (choStimati > 0) {
            btnUsa.setText(getString(R.string.usa_cho, fmt.format(choStimati)));
            btnUsa.setVisibility(View.VISIBLE);
        }
        // Dopo l'animazione di comparsa, porta in vista risultato e pulsante "Usa"
        scroll.postDelayed(() -> scroll.smoothScrollTo(0, cardRisultato.getTop()), 350);
    }

    private View riga(StimaCho.Alimento a) {
        LinearLayout riga = new LinearLayout(this);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        riga.setGravity(Gravity.CENTER_VERTICAL);
        int spazio = Math.round(6 * getResources().getDisplayMetrics().density);
        riga.setPadding(0, spazio, 0, spazio);
        int colore = coloreTema(com.google.android.material.R.attr.colorOnPrimaryContainer);

        TextView nome = new TextView(this);
        nome.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        nome.setTextColor(colore);
        nome.setText(a.nome);
        riga.addView(nome, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView peso = new TextView(this);
        peso.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        peso.setTextColor(colore);
        peso.setAlpha(0.75f);
        peso.setText(getString(R.string.grammi, fmt.format(Math.round(a.grammi))));
        peso.setPadding(spazio * 2, 0, spazio * 2, 0);
        riga.addView(peso);

        TextView cho = new TextView(this);
        cho.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        cho.setTextColor(colore);
        // Un decimale: righe arrotondate all'intero non tornerebbero con il totale
        cho.setText(getString(R.string.grammi_cho, fmt.format(a.cho)));
        riga.addView(cho);
        return riga;
    }

    private int coloreTema(int attr) {
        android.util.TypedValue v = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, v, true);
        return v.data;
    }
}
