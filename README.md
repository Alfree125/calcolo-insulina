# Calcolo Insulina

App Android per calcolare il bolo di insulina del pasto e la correzione in base alla glicemia, con interfaccia Material 3.

<p align="center">
  <img src="docs/screenshots/principale.png" width="260" alt="Schermata principale">
  <img src="docs/screenshots/configurazione.png" width="260" alt="Configurazione al primo avvio">
  <img src="docs/screenshots/scuro.png" width="260" alt="Tema scuro">
</p>

## Download

Scarica l'ultimo `CalcoloInsulina.apk` dalla pagina [Releases](https://github.com/Alfree125/calcolo-insulina/releases/latest) e aprilo sul telefono. Android ti chiederà di consentire l'installazione da fonti sconosciute.

Supporta da **Android 6.0** (API 23) ad **Android 17** (API 37).

> **Se hai installato la versione 2.0**, disinstallala prima di installare la 2.0.1 o successive: dalla 2.0.1 l'app è firmata con una chiave diversa e Android non permette l'aggiornamento diretto. Dovrai reinserire i parametri personali.

Al primo avvio l'app chiede i parametri personali. Se attivi il promemoria, su Android 13 e successivi ti chiede anche il permesso per le notifiche.

## Come calcola

Le formule sono quelle del foglio di calcolo da cui è nata l'app:

| Valore | Formula |
| --- | --- |
| UI per i CHO | CHO totali ÷ rapporto insulina/CHO |
| UI extra | (glicemia pre pasto − glicemia obiettivo) ÷ fattore di sensibilità |
| **UI totali** | UI per i CHO + UI extra |

Esempio: 250 g di CHO, glicemia 180 mg/dL, rapporto 10 g/UI, fattore 50 mg/dL/UI, obiettivo 150 mg/dL → 25 + 0,6 = **25,6 UI**.

La correzione può essere negativa quando la glicemia è sotto l'obiettivo: in quel caso riduce la dose del pasto. Se il totale scende sotto zero l'app mostra 0.

## Funzioni

- Calcolo in tempo reale mentre scrivi, con risultato sempre visibile in alto.
- Promemoria di attendere 10 minuti tra l'insulina e il pasto.
- Configurazione dei parametri personali al primo avvio; dopo si modificano dal menu ⋮.
- Promemoria opzionale con notifica per controllare la glicemia dopo il calcolo (2 ore di default, da 30 minuti a 4 ore).
- Arrotondamento della dose a 0,5 UI, 1 UI o nessuno.
- Indicatore colorato dello stato della glicemia (tocca il pallino per i dettagli).
- Tema chiaro e scuro, colori dinamici su Android 12+.
- Layout a due colonne su tablet, pieghevoli e in orizzontale.

## Beta: stima dei CHO da foto

Nelle versioni beta (`2.1.0-beta.x`, pagina [Releases](https://github.com/Alfree125/calcolo-insulina/releases)) puoi fotografare il pasto e ottenere una stima dei carboidrati fatta da Claude, il modello di intelligenza artificiale di Anthropic.

- La funzione è **disattivata** di default. Si attiva da menu ⋮ → Impostazioni → Funzioni beta.
- Serve una **tua chiave API Anthropic**, da creare su [console.anthropic.com](https://console.anthropic.com/settings/keys). Ogni analisi ha un piccolo costo sul tuo account; il modello si sceglie nelle impostazioni (Opus 5.5 più preciso, Haiku 5.5 più economico).
- La chiave è salvata cifrata con l'Android Keystore e resta solo sul telefono.
- La foto viene inviata ad Anthropic solo quando premi **Analizza**.
- Il risultato mostra gli alimenti riconosciuti, il peso stimato, i CHO e un livello di affidabilità. Nel campo CHO finisce solo se premi **Usa**.

> La stima da foto può sbagliare, soprattutto su porzioni nascoste o piatti fondi. Usala come aiuto e, quando puoi, pesa gli alimenti. Puoi aggiungere dettagli (es. "pasta 80 g a crudo") per una stima più precisa.

## Compilare dai sorgenti

Servono JDK 17 e l'Android SDK con la piattaforma `android-37.0`.

```bash
./gradlew assembleRelease
```

L'APK viene generato in `app/build/outputs/apk/release/`.

Per provare la stima da foto senza una chiave reale puoi puntare l'app a un server di prova con `-PapiBase=http://10.0.2.2:8787` (dall'emulatore, 10.0.2.2 è il PC). Le build di debug accettano HTTP solo verso quell'indirizzo.

La chiave di firma non è nel repository. La build la legge da `~/android-keys/calcolo-insulina.properties` (con `storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Puoi indicare un altro file con `-PfirmaRelease=percorso` o con la variabile d'ambiente `CALCOLO_INSULINA_FIRMA`. Se il file non c'è, l'APK viene firmato con la chiave debug locale e non si potrà installare sopra le release ufficiali.

## Avvertenza

Questa app è uno strumento di supporto al calcolo e non sostituisce il parere medico. Controlla sempre la dose e segui le indicazioni del tuo diabetologo. I parametri personali (rapporto insulina/CHO, fattore di sensibilità, glicemia obiettivo) devono essere stabiliti con il tuo medico.

## Licenza

Distribuita con licenza [GNU GPL v3.0](LICENSE).
