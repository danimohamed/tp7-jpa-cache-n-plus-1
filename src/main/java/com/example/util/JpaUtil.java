package com.example.util;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * Point d'acces unique a l'{@link EntityManagerFactory}.
 *
 * <p>L'EMF est cree paresseusement et peut etre detruit puis recree : les tests ont besoin de
 * repartir d'un cache de second niveau vide entre deux scenarios.</p>
 */
public final class JpaUtil {

    public static final String PERSISTENCE_UNIT = "hibernate-cache-pu";

    private static EntityManagerFactory emf;

    private JpaUtil() {
    }

    public static synchronized EntityManagerFactory getEntityManagerFactory() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT);
        }
        return emf;
    }

    public static EntityManager createEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    public static synchronized void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
        emf = null;
    }

    /**
     * Statistiques Hibernate de l'EMF courante : c'est la source de verite pour compter les
     * requetes SQL, les entites chargees et les hits/misses du cache de second niveau.
     */
    public static Statistics getStatistics() {
        return getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    /**
     * Remet les compteurs a zero pour isoler la mesure qui suit.
     */
    public static void resetStatistics() {
        getStatistics().clear();
    }

    /**
     * Vide entierement le cache de second niveau.
     *
     * <p>Indispensable pour que la premiere passe d'une mesure "avec cache" parte d'un cache
     * reellement froid : sans cela les entites inserees par le jeu de donnees seraient deja
     * presentes et le nombre de hits ne voudrait rien dire.</p>
     */
    public static void evictSecondLevelCache() {
        getEntityManagerFactory().unwrap(SessionFactory.class).getCache().evictAllRegions();
    }
}
