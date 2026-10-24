package com.calcoloinsulina.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InsulinaAttivaTest {

    @Test
    public void curvaDaTuttaANullaSempreDecrescente() {
        for (int durata : new int[]{180, 240, 360}) {
            assertEquals(1, InsulinaAttiva.frazione(0, durata), 1e-9);
            assertEquals(0, InsulinaAttiva.frazione(durata - 1, durata), 0.01);
            assertEquals(0, InsulinaAttiva.frazione(durata + 30, durata), 0);
            double prima = 1;
            for (int m = 5; m < durata; m += 5) {
                double f = InsulinaAttiva.frazione(m, durata);
                assertTrue("non decrescente a " + m + " min", f <= prima);
                prima = f;
            }
        }
        // a meta' delle 4 ore resta circa un terzo della dose (valore di oref0: ~0,35)
        assertEquals(0.35, InsulinaAttiva.frazione(120, 240), 0.05);
    }
}
