package com.example;

import com.example.service.DataInitService;
import com.example.service.Mesure;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Vérifie le cache de requêtes.
 *
 * <p>Point clé : activer {@code hibernate.cache.use_query_cache} ne suffit pas, il faut aussi le
 * hint {@code org.hibernate.cacheable} sur la requête. Sans lui, le SQL est rejoué à chaque appel
 * alors que le cache de second niveau est chaud.</p>
 */
public class CacheRequetesTest extends AbstractJpaTest {

    private static final int NB_LIVRES_ATTENDUS =
            DataInitService.NB_AUTEURS * DataInitService.NB_LIVRES_PAR_AUTEUR;

    /**
     * Lectures servies par le cache lors de la seconde passe de la requête cacheable : les
     * identifiants sortent du cache de requêtes, donc les auteurs aussi sont résolus dans le cache
     * de second niveau, en plus des collections et des livres.
     */
    private static final int HITS_CACHE_DE_REQUETES =
            2 * DataInitService.NB_AUTEURS + NB_LIVRES_ATTENDUS;

    @Test
    public void laRequeteCacheableNeRejouePasLeSql() {
        Mesure[] passes = performance.testPerformanceAvecCacheDeuxPasses();
        Mesure froid = passes[0];
        Mesure chaud = passes[1];

        assertEquals(1, froid.getRequetesSql());
        assertEquals(0, froid.getHitsQueryCache());
        assertEquals(HITS_CACHE_DE_REQUETES, froid.getPutsSecondLevelCache());

        assertEquals(0, chaud.getRequetesSql());
        assertEquals(1, chaud.getHitsQueryCache());
        assertEquals(HITS_CACHE_DE_REQUETES, chaud.getHitsSecondLevelCache());
        assertEquals(0, chaud.getEntitesChargees());
        assertEquals(DataInitService.NB_AUTEURS, chaud.getAuteursParcourus());
        assertEquals(NB_LIVRES_ATTENDUS, chaud.getLivresParcourus());
    }

    @Test
    public void sansLeHintCacheableLeSqlEstRejoueMalgreUnCacheChaud() {
        Mesure[] passes = performance.testPerformanceSansQueryCacheDeuxPasses();
        Mesure froid = passes[0];
        Mesure chaud = passes[1];

        assertEquals(1, froid.getRequetesSql());
        assertEquals(1, chaud.getRequetesSql());
        assertEquals(0, chaud.getHitsQueryCache());
        assertEquals(DataInitService.NB_AUTEURS + NB_LIVRES_ATTENDUS, chaud.getEntitesChargees());
    }

    @Test
    public void uneRequeteOptimiseeNeLitPasLEtatDesEntitesDansLeCache() {
        Mesure mesure = performance.testPerformanceAvecCache();

        assertEquals(1, mesure.getRequetesSql());
        assertEquals(0, mesure.getHitsSecondLevelCache());
        assertEquals(DataInitService.NB_AUTEURS + NB_LIVRES_ATTENDUS, mesure.getEntitesChargees());
    }

    @Test
    public void leCacheDeRequetesEstVideParUneEvictionComplete() {
        Mesure[] premierPassage = performance.testPerformanceAvecCacheDeuxPasses();
        assertEquals(1, premierPassage[1].getHitsQueryCache());

        Mesure[] secondPassage = performance.testPerformanceAvecCacheDeuxPasses();

        assertEquals(1, secondPassage[0].getRequetesSql());
        assertEquals(0, secondPassage[0].getHitsQueryCache());
        assertEquals(1, secondPassage[1].getHitsQueryCache());
    }
}
