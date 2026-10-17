package com.calcoloinsulina.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Base64;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Caricamento della foto del pasto, ridotta e raddrizzata, e conversione in JPEG base64. */
final class Foto {

    /**
     * Lato lungo inviato all'API: il costo cresce con l'area dell'immagine (circa 1 token
     * ogni 28x28 pixel), 1280 px resta intorno ai 1.200 token e basta per riconoscere le porzioni.
     */
    static final int LATO_MAX = 1280;

    private Foto() {
    }

    static Bitmap carica(Context c, Uri uri) throws IOException {
        BitmapFactory.Options dim = new BitmapFactory.Options();
        dim.inJustDecodeBounds = true;
        try (InputStream in = apri(c, uri)) {
            BitmapFactory.decodeStream(in, null, dim);
        }
        if (dim.outWidth <= 0 || dim.outHeight <= 0) throw new IOException("Immagine non leggibile");

        BitmapFactory.Options opz = new BitmapFactory.Options();
        opz.inSampleSize = 1;
        while (Math.max(dim.outWidth, dim.outHeight) / (opz.inSampleSize * 2) >= LATO_MAX) {
            opz.inSampleSize *= 2;
        }
        Bitmap b;
        try (InputStream in = apri(c, uri)) {
            b = BitmapFactory.decodeStream(in, null, opz);
        }
        if (b == null) throw new IOException("Immagine non leggibile");

        float scala = Math.min(1f, (float) LATO_MAX / Math.max(b.getWidth(), b.getHeight()));
        Matrix m = new Matrix();
        m.postScale(scala, scala);
        try (InputStream in = apri(c, uri)) {
            applicaOrientamento(m, new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
        } catch (IOException ignored) {
            // senza EXIF l'immagine resta com'e'
        }
        if (m.isIdentity()) return b;
        Bitmap ruotata = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (ruotata != b) b.recycle();
        return ruotata;
    }

    static String jpegBase64(Bitmap b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 85, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private static InputStream apri(Context c, Uri uri) throws IOException {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Immagine non disponibile");
        return in;
    }

    private static void applicaOrientamento(Matrix m, int orientamento) {
        switch (orientamento) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.postRotate(90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.postRotate(270);
                m.postScale(-1, 1);
                break;
            default:
                break;
        }
    }
}
