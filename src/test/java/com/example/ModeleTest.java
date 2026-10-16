package com.example;

import com.example.model.Auteur;
import com.example.model.Livre;
import com.example.service.DataInitService;
import com.example.util.JpaUtil;

import org.junit.Test;

import javax.persistence.EntityManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie le mapping et le contrat des entités.
 */
public class ModeleTest extends AbstractJpaTest {

    @Test
    public void ajouterLivreMaintientLaRelationDesDeuxCotes() {
        Auteur auteur = new Auteur("Victor Hugo");
        Livre livre = new Livre("Les Miserables");

        auteur.ajouterLivre(livre);

        assertEquals(1, auteur.getLivres().size());
        assertSame(auteur, livre.getAuteur());
    }

    @Test
    public void retirerLivreMaintientLaRelationDesDeuxCotes() {
        Auteur auteur = new Auteur("Victor Hugo");
        Livre livre = new Livre("Les Miserables");
        auteur.ajouterLivre(livre);

        auteur.retirerLivre(livre);

        assertTrue(auteur.getLivres().isEmpty());
        assertNull(livre.getAuteur());
    }

    @Test
    public void toStringNeDeclenchePasLeChargementDeLaCollection() {
        Long id = performance.premierAuteurId();

        Auteur detache;
        EntityManager em = JpaUtil.createEntityManager();
        try {
            detache = em.find(Auteur.class, id);
        } finally {
            em.close();
        }

        String texte = detache.toString();

        assertTrue(texte.contains("Auteur{"));
        assertTrue(texte.contains(detache.getNom()));
        assertFalse(texte.contains("livres"));
    }

    @Test
    public void unAuteurPeutEtreReluParSonIdentifiant() {
        Long id = performance.premierAuteurId();

        EntityManager em = JpaUtil.createEntityManager();
        try {
            Auteur auteur = em.find(Auteur.class, id);
            assertEquals(Long.valueOf(1L), auteur.getId());
            assertEquals(DataInitService.NB_LIVRES_PAR_AUTEUR, auteur.getLivres().size());
        } finally {
            em.close();
        }
    }
}
