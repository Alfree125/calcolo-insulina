package com.calcoloinsulina.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Chiave API cifrata con una chiave AES dell'Android Keystore (mai esportabile).
 * Nelle preferenze resta solo il testo cifrato: un backup ripristinato altrove non e' decifrabile.
 */
final class Segreti {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "chiave_api_anthropic";
    private static final String CIFRARIO = "AES/GCM/NoPadding";
    private static final int IV_BYTE = 12;

    private Segreti() {
    }

    static void salvaChiaveApi(Context c, String chiave) {
        if (chiave == null || chiave.isEmpty()) {
            Prefs.get(c).edit().remove(Prefs.CHIAVE_API).apply();
            return;
        }
        try {
            Cipher cifrario = Cipher.getInstance(CIFRARIO);
            cifrario.init(Cipher.ENCRYPT_MODE, chiaveKeystore());
            byte[] cifrato = cifrario.doFinal(chiave.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cifrario.getIV();
            byte[] tutto = new byte[iv.length + cifrato.length];
            System.arraycopy(iv, 0, tutto, 0, iv.length);
            System.arraycopy(cifrato, 0, tutto, iv.length, cifrato.length);
            Prefs.get(c).edit()
                    .putString(Prefs.CHIAVE_API, Base64.encodeToString(tutto, Base64.NO_WRAP))
                    .apply();
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile cifrare la chiave API", e);
        }
    }

    /** Chiave API in chiaro, o null se assente o non piu' decifrabile. */
    static String chiaveApi(Context c) {
        String salvata = Prefs.get(c).getString(Prefs.CHIAVE_API, null);
        if (salvata == null) return null;
        try {
            byte[] tutto = Base64.decode(salvata, Base64.NO_WRAP);
            Cipher cifrario = Cipher.getInstance(CIFRARIO);
            cifrario.init(Cipher.DECRYPT_MODE, chiaveKeystore(),
                    new GCMParameterSpec(128, tutto, 0, IV_BYTE));
            byte[] chiaro = cifrario.doFinal(tutto, IV_BYTE, tutto.length - IV_BYTE);
            return new String(chiaro, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static SecretKey chiaveKeystore() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }
}
