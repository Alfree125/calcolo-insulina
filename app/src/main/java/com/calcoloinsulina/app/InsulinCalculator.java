package com.calcoloinsulina.app;

/**
 * Formule del foglio "calcolo insulina 2.xlsx" (scheda CALCOLO CHO):
 *   UI        = CHO totali / rapporto insulina-CHO          (C8 = C2/C5)
 *   UI extra  = (glicemia pre pasto - 150) / fattore sens.  (D8 = (D2-150)/D5)
 *   UI TOTALI = UI + UI extra                               (E8 = C8+D8)
 * Il 150 del foglio e' qui la glicemia obiettivo, modificabile.
 */
public final class InsulinCalculator {

    public static final double DEFAULT_RAPPORTO = 10;
    public static final double DEFAULT_FATTORE = 50;
    public static final double DEFAULT_TARGET = 150;

    public static final double GLICEMIA_MIN_VALIDA = 20;
    public static final double GLICEMIA_MAX_VALIDA = 600;

    public static final class Result {
        /** UI per coprire i carboidrati, null se non calcolabile. */
        public final Double uiPasto;
        /** UI di correzione (anche negativa, come nel foglio), null se non calcolabile. */
        public final Double uiExtra;
        /** Somma come nel foglio, null se non calcolabile. */
        public final Double totale;

        Result(Double uiPasto, Double uiExtra, Double totale) {
            this.uiPasto = uiPasto;
            this.uiExtra = uiExtra;
            this.totale = totale;
        }
    }

    private InsulinCalculator() {
    }

    /** Parametri null = campo vuoto. */
    public static Result calcola(Double cho, Double glicemia, Double rapporto,
                                 Double fattore, Double target) {
        Double uiPasto = null;
        if (cho != null && rapporto != null && rapporto > 0) {
            uiPasto = cho / rapporto;
        }

        Double uiExtra = null;
        if (glicemia != null && fattore != null && fattore > 0 && target != null
                && glicemia >= GLICEMIA_MIN_VALIDA && glicemia <= GLICEMIA_MAX_VALIDA) {
            uiExtra = (glicemia - target) / fattore;
        }

        Double totale;
        if (uiPasto == null && uiExtra == null) {
            totale = null;
        } else {
            totale = (uiPasto != null ? uiPasto : 0) + (uiExtra != null ? uiExtra : 0);
        }
        return new Result(uiPasto, uiExtra, totale);
    }

    /** Arrotonda al passo piu' vicino (es. 0.5 UI), mai sotto zero. */
    public static double arrotonda(double ui, double passo) {
        if (ui <= 0) return 0;
        return Math.round(ui / passo) * passo;
    }
}
