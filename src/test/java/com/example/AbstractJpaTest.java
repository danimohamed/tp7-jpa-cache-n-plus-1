package com.example;

import com.example.service.DataInitService;
import com.example.service.PerformanceTestService;
import com.example.util.JpaUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

/**
 * Socle commun aux tests de TP 7.
 *
 * <p>Chaque test repart du même jeu de données ({@value DataInitService#NB_AUTEURS} auteurs de
 * {@value DataInitService#NB_LIVRES_PAR_AUTEUR} livres) et d'un cache de second niveau vide :
 * sans cette remise à zéro, le résultat d'un test dépendrait de celui qui l'a précédé.</p>
 *
 * <p>La base H2 est en mémoire avec {@code DB_CLOSE_DELAY=-1} : elle survit à la fermeture de
 * l'EntityManagerFactory, mais le schéma est recréé ({@code create-drop}) à chaque nouvelle
 * EMF. Les compteurs d'identité sont donc réinitialisés par {@link DataInitService#clearData()}
 * de façon explicite.</p>
 */
public abstract class AbstractJpaTest {

    protected static EntityManagerFactory emf;

    protected DataInitService dataInitService;
    protected PerformanceTestService performance;

    @BeforeClass
    public static void ouvrirEntityManagerFactory() {
        emf = JpaUtil.getEntityManagerFactory();
    }

    @AfterClass
    public static void fermerEntityManagerFactory() {
        JpaUtil.close();
    }

    @Before
    public void preparer() {
        emf = JpaUtil.getEntityManagerFactory();
        dataInitService = new DataInitService();
        performance = new PerformanceTestService();
        dataInitService.reset();
    }

    @After
    public void nettoyer() {
        JpaUtil.evictSecondLevelCache();
    }

    protected int compterAuteurs() {
        return dataInitService.compterAuteurs();
    }

    protected int compterLivres() {
        return dataInitService.compterLivres();
    }

    /** Nombre d'auteurs ayant exactement le nombre de livres attendu. */
    protected long compterAuteursAvecLeBonNombreDeLivres() {
        EntityManager em = emf.createEntityManager();
        try {
            return ((Number) em.createQuery(
                    "SELECT COUNT(a) FROM Auteur a WHERE SIZE(a.livres) = :n")
                    .setParameter("n", DataInitService.NB_LIVRES_PAR_AUTEUR)
                    .getSingleResult()).longValue();
        } finally {
            em.close();
        }
    }

    /** Identifiant du premier auteur, tel qu'attendu par les scénarios de lecture par id. */
    protected Long premierAuteurId() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT MIN(a.id) FROM Auteur a", Long.class).getSingleResult();
        } finally {
            em.close();
        }
    }

    protected void executerSql(String requete) {
        executer(requete, true);
    }

    private void executer(String requete, boolean natif) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (natif) {
                em.createNativeQuery(requete).executeUpdate();
            } else {
                em.createQuery(requete).executeUpdate();
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
