package com.calcoloinsulina.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class RicercaOnlineTest {

    @Test
    public void tieneSoloProdottiConCarboidrati() throws Exception {
        // Forma reale della risposta di search.openfoodfacts.org, ridotta
        String json = "{\"hits\":["
                + "{\"product_name\":\"Greek yogurt\",\"product_name_it\":\"Yogurt greco magro\","
                + "\"brands\":[\"Carrefour\"],\"nutriments\":{\"carbohydrates_100g\":4.8},\"serving_quantity\":\"150\"},"
                + "{\"product_name\":\"Pane Carasau\",\"brands\":null,\"nutriments\":{\"carbohydrates_100g\":73}},"
                + "{\"product_name\":\"Senza dati\",\"nutriments\":{}},"
                + "{\"product_name\":\"\",\"nutriments\":{\"carbohydrates_100g\":10}}"
                + "],\"count\":4}";

        List<RicercaOnline.Prodotto> p = RicercaOnline.interpreta(json);

        assertEquals(2, p.size());
        assertEquals("Yogurt greco magro (Carrefour)", p.get(0).nome);
        assertEquals(4.8, p.get(0).cho100, 1e-9);
        assertEquals(150, p.get(0).porzione, 1e-9);
        assertEquals("Pane Carasau", p.get(1).nome);
        assertEquals(0, p.get(1).porzione, 1e-9);
    }
}
