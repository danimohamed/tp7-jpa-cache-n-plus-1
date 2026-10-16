package com.example;

import com.example.model.Auteur;
import com.example.service.DataInitService;
import com.example.service.Mesure;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;

import static org.junit.Assert.assertEquals;

/**
 * Mesure le problème N+1 et sa correction par {@code JOIN FETCH} puis par entity graph.
 *
 * <p>Toutes les mesures s'appuient sur {@code Statistics} de Hibernate, jamais sur un
 * chronomètre : le nombre de requêtes SQL préparées est déterministe, ce qui autorise des
 * assertions exactes.</p>
 */
public class ProblemeN1Test extends AbstractJpaTest {

    private static final int NB_LIVRES_ATTENDUS =
            DataInitService.NB_AUTEURS * DataInitService.NB_LIVRES_PAR_AUTEUR;

    private static final String JPQL_JOIN_FETCH =
            "SELECT DISTINCT a FROM Auteur a LEFT JOIN FETCH a.livres";

    private static final String JPQL_JOIN_FETCH_SANS_DISTINCT =
            "SELECT a FROM Auteur a LEFT JOIN FETCH a.livres";

    @Test
    public void sansOptimisationLeParcoursEmmetUneRequeteParAuteur() {
        Mesure mesure = performance.testPerformanceSansCache();

        assertEquals(1 + DataInitService.NB_AUTEURS, mesure.getRequetesSql());
        assertEquals(DataInitService.NB_AUTEURS + NB_LIVRES_ATTENDUS, mesure.getEntitesChargees());
        assertEquals(DataInitService.NB_AUTEURS, mesure.getAuteursParcourus());
        assertEquals(NB_LIVRES_ATTENDUS, mesure.getLivresParcourus());
    }

    @Test
    public void leJoinFetchRemplaceLeNPremierParUneSeuleRequete() {
        Mesure mesure = performance.testPerformanceAvecJoinFetch();

        assertEquals(1, mesure.getRequetesSql());
        assertEquals(DataInitService.NB_AUTEURS + NB_LIVRES_ATTENDUS, mesure.getEntitesChargees());
        assertEquals(DataInitService.NB_AUTEURS, mesure.getAuteursParcourus());
        assertEquals(NB_LIVRES_ATTENDUS, mesure.getLivresParcourus());
    }

    @Test
    public void lEntityGraphRemplaceLeNPremierParUneSeuleRequete() {
        Mesure mesure = performance.testPerformanceAvecEntityGraph();

        assertEquals(1, mesure.getRequetesSql());
        assertEquals(DataInitService.NB_AUTEURS + NB_LIVRES_ATTENDUS, mesure.getEntitesChargees());
        assertEquals(DataInitService.NB_AUTEURS, mesure.getAuteursParcourus());
        assertEquals(NB_LIVRES_ATTENDUS, mesure.getLivresParcourus());
    }

    @Test
    public void leJoinFetchDoitEtreCombineADistinct() {
        List<Auteur> avecDistinct = executerRequete(JPQL_JOIN_FETCH);

        Set<Long> identifiants = new HashSet<>();
        int livres = 0;
        for (Auteur auteur : avecDistinct) {
            identifiants.add(auteur.getId());
            livres += auteur.getLivres().size();
        }

        assertEquals(DataInitService.NB_AUTEURS, identifiants.size());
        assertEquals(DataInitService.NB_AUTEURS, avecDistinct.size());
        assertEquals(NB_LIVRES_ATTENDUS, livres);
    }

    @Test
    public void sansDistinctLeJoinFetchDupliqueLesAuteurs() {
        List<Auteur> sansDistinct = executerRequete(JPQL_JOIN_FETCH_SANS_DISTINCT);

        assertEquals(DataInitService.NB_AUTEURS * DataInitService.NB_LIVRES_PAR_AUTEUR, sansDistinct.size());
    }

    private List<Auteur> executerRequete(String jpql) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(jpql, Auteur.class).getResultList();
        } finally {
            em.close();
        }
    }
}
