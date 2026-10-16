package com.example;

import com.example.service.DataInitService;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Vérifie le jeu de données de démonstration et, surtout, que chaque opération possède bien
 * <strong>une seule</strong> transaction : c'est le point que le code du sujet ratait en ouvrant
 * une transaction dans {@code App.main} en plus de celle du service.
 */
public class DataInitServiceTest extends AbstractJpaTest {

    private static final int NB_LIVRES_ATTENDUS =
            DataInitService.NB_AUTEURS * DataInitService.NB_LIVRES_PAR_AUTEUR;

    @Test
    public void resetCreeDixAuteursEtCinquanteLivres() {
        assertEquals(DataInitService.NB_AUTEURS, compterAuteurs());
        assertEquals(NB_LIVRES_ATTENDUS, compterLivres());
    }

    @Test
    public void chaqueAuteurPossedeCinqLivres() {
        assertEquals(DataInitService.NB_AUTEURS, compterAuteursAvecLeBonNombreDeLivres());
    }

    @Test
    public void clearDataVideLesDeuxTables() {
        dataInitService.clearData();

        assertEquals(0, compterAuteurs());
        assertEquals(0, compterLivres());
    }

    @Test
    public void resetEstIdempotent() {
        dataInitService.reset();
        dataInitService.reset();

        assertEquals(DataInitService.NB_AUTEURS, compterAuteurs());
        assertEquals(NB_LIVRES_ATTENDUS, compterLivres());
    }

    @Test
    public void clearDataReinitialiseLesCompteursDIdentite() {
        dataInitService.clearData();
        dataInitService.initData();

        assertEquals(Long.valueOf(1L), performance.premierAuteurId());
    }

    @Test
    public void initDataEmpileLesDonneesSiLaTableNEstPasPurgee() {
        dataInitService.initData();

        assertEquals(2 * DataInitService.NB_AUTEURS, compterAuteurs());
        assertEquals(2 * NB_LIVRES_ATTENDUS, compterLivres());
    }
}
