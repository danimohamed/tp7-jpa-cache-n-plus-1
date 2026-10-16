package com.example.service;

import com.example.model.Auteur;
import com.example.model.Livre;
import com.example.util.JpaUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

/**
 * Cree le jeu de donnees de demonstration : {@value #NB_AUTEURS} auteurs de
 * {@value #NB_LIVRES_PAR_AUTEUR} livres chacun.
 *
 * <p>Contrairement au code du sujet, ce service ne recoit aucun {@code EntityManager} par
 * injection : dans une application autonome RESOURCE_LOCAL il n'y a pas de conteneur pour
 * injecter quoi que ce soit. Chaque methode ouvre et ferme son propre EntityManager et possede
 * donc exactement une transaction.</p>
 */
public class DataInitService {

    public static final int NB_AUTEURS = 10;
    public static final int NB_LIVRES_PAR_AUTEUR = 5;

    /**
     * Insere le jeu de donnees dans une seule transaction.
     */
    public void initData() {
        EntityManager em = JpaUtil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            for (int i = 1; i <= NB_AUTEURS; i++) {
                Auteur auteur = new Auteur("Auteur " + i);
                for (int j = 1; j <= NB_LIVRES_PAR_AUTEUR; j++) {
                    auteur.ajouterLivre(new Livre("Livre " + i + "-" + j));
                }
                em.persist(auteur);
            }
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    /**
     * Supprime toutes les donnees puis reinitialise les compteurs d'identite H2.
     */
    public void clearData() {
        EntityManager em = JpaUtil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM Livre").executeUpdate();
            em.createQuery("DELETE FROM Auteur").executeUpdate();
            em.createNativeQuery("ALTER TABLE livres ALTER COLUMN id RESTART WITH 1").executeUpdate();
            em.createNativeQuery("ALTER TABLE auteurs ALTER COLUMN id RESTART WITH 1").executeUpdate();
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    /**
     * Reinitialise completement le contexte : purge du cache de second niveau, remise a zero des
     * statistiques, donnees recreees.
     */
    public void reset() {
        clearData();
        JpaUtil.evictSecondLevelCache();
        JpaUtil.resetStatistics();
        initData();
        JpaUtil.evictSecondLevelCache();
        JpaUtil.resetStatistics();
    }

    public int compterAuteurs() {
        EntityManager em = JpaUtil.createEntityManager();
        try {
            return ((Number) em.createQuery("SELECT COUNT(a) FROM Auteur a").getSingleResult()).intValue();
        } finally {
            em.close();
        }
    }

    public int compterLivres() {
        EntityManager em = JpaUtil.createEntityManager();
        try {
            return ((Number) em.createQuery("SELECT COUNT(l) FROM Livre l").getSingleResult()).intValue();
        } finally {
            em.close();
        }
    }
}
