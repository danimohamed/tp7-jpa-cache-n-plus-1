package com.example;

import com.example.model.Auteur;
import com.example.service.DataInitService;
import com.example.service.Mesure;
import com.example.util.JpaUtil;

import org.junit.Test;

import javax.persistence.EntityManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie le cache de second niveau (EhCache via JCache).
 *
 * <p>Trois propriétés sont démontrées : le cache ne sert qu'aux chargements par identifiant et aux
 * collections, il est partagé entre deux {@code EntityManager}, et il suffit à supprimer les
 * requêtes du N+1 sans toucher au JPQL.</p>
 */
public class CacheSecondNiveauTest extends AbstractJpaTest {

    private static final int NB_LIVRES_ATTENDUS =
            DataInitService.NB_AUTEURS * DataInitService.NB_LIVRES_PAR_AUTEUR;

    /**
     * Entrées écrites dans le cache lors du premier parcours : les auteurs, leurs collections de
     * livres et les livres eux-mêmes.
     */
    private static final int PUTS_PREMIER_PASSAGE =
            2 * DataInitService.NB_AUTEURS + NB_LIVRES_ATTENDUS;

    /**
     * Lectures servies par le cache lors d'un parcours à cache chaud : les collections et les
     * livres. Les auteurs, eux, sont réhydratés depuis le résultat de la requête JPQL, qui est
     * rejouée à chaque appel.
     */
    private static final int HITS_PARCOURS_A_CACHE_CHAUD =
            DataInitService.NB_AUTEURS + NB_LIVRES_ATTENDUS;

    @Test
    public void leParcoursNonOptimiseRemplitLeCacheSansJamaisLeLire() {
        Mesure mesure = performance.testPerformanceSansCache();

        assertEquals(0, mesure.getHitsSecondLevelCache());
        assertEquals(DataInitService.NB_AUTEURS, mesure.getMissesSecondLevelCache());
        assertEquals(PUTS_PREMIER_PASSAGE, mesure.getPutsSecondLevelCache());
    }

    @Test
    public void leCacheSupprimeLesRequetesDuNPremierDesLaDeuxiemePasse() {
        Mesure[] passes = performance.testPerformanceSansCacheDeuxPasses();
        Mesure froid = passes[0];
        Mesure chaud = passes[1];

        assertEquals(1 + DataInitService.NB_AUTEURS, froid.getRequetesSql());
        assertEquals(0, froid.getHitsSecondLevelCache());
        assertEquals(DataInitService.NB_AUTEURS, froid.getMissesSecondLevelCache());

        assertEquals(1, chaud.getRequetesSql());
        assertEquals(HITS_PARCOURS_A_CACHE_CHAUD, chaud.getHitsSecondLevelCache());
        assertEquals(0, chaud.getMissesSecondLevelCache());
        assertEquals(0, chaud.getPutsSecondLevelCache());
        assertEquals(DataInitService.NB_AUTEURS, chaud.getEntitesChargees());
        assertEquals(NB_LIVRES_ATTENDUS, chaud.getLivresParcourus());
    }

    @Test
    public void laDeuxiemeLectureParIdentifiantEstServieParLeCache() {
        Mesure[] passes = performance.mesurerLectureParIdAvecCache(performance.premierAuteurId());
        Mesure froid = passes[0];
        Mesure chaud = passes[1];

        assertEquals(1, froid.getRequetesSql());
        assertEquals(0, froid.getHitsSecondLevelCache());
        assertEquals(1, froid.getMissesSecondLevelCache());
        assertEquals(1, froid.getPutsSecondLevelCache());
        assertEquals(1, froid.getEntitesChargees());

        assertEquals(0, chaud.getRequetesSql());
        assertEquals(1, chaud.getHitsSecondLevelCache());
        assertEquals(0, chaud.getMissesSecondLevelCache());
        assertEquals(0, chaud.getEntitesChargees());
    }

    @Test
    public void lEntiteEstPresenteDansLeCacheApresUnChargement() {
        Long id = performance.premierAuteurId();

        assertFalse(emf.getCache().contains(Auteur.class, id));

        chargerDansUnEntityManagerNeuf(id);

        assertTrue(emf.getCache().contains(Auteur.class, id));
    }

    @Test
    public void evictAllRegionsVideLeCacheDeSecondNiveau() {
        Long id = performance.premierAuteurId();
        chargerDansUnEntityManagerNeuf(id);
        assertTrue(emf.getCache().contains(Auteur.class, id));

        JpaUtil.evictSecondLevelCache();

        assertFalse(emf.getCache().contains(Auteur.class, id));

        Mesure[] passes = performance.mesurerLectureParIdAvecCache(id);
        assertEquals(0, passes[0].getHitsSecondLevelCache());
        assertEquals(1, passes[0].getMissesSecondLevelCache());
        assertEquals(1, passes[0].getRequetesSql());
    }

    private void chargerDansUnEntityManagerNeuf(Long id) {
        EntityManager em = JpaUtil.createEntityManager();
        try {
            assertNotNull(em.find(Auteur.class, id));
        } finally {
            em.close();
        }
    }
}
