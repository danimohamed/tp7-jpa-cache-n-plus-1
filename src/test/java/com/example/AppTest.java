package com.example;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exécute la démonstration de bout en bout et vérifie qu'elle produit bien les preuves attendues.
 *
 * <p>{@code hibernate.show_sql} écrit les requêtes SQL sur la sortie standard : c'est donc dans
 * {@link Capture#sortie()} qu'il faut les chercher, pas dans le flux d'erreur.</p>
 */
public class AppTest extends AbstractJpaTest {

    /**
     * {@link App#main(String[])} ferme l'EntityManagerFactory en sortie : inutile de vider le cache
     * d'une EMF déjà détruite (cela la recréerait pour rien).
     */
    @Override
    @After
    public void nettoyer() {
    }

    @Test
    public void laDemonstrationAfficheToutesLesMesuresAttendues() {
        String sortie = executerLaDemonstration();

        assertTrue(sortie.contains("--- 1. Sans optimisation : probleme N+1 ---"));
        assertTrue(sortie.contains("--- 2. Avec JOIN FETCH ---"));
        assertTrue(sortie.contains("--- 3. Avec entity graph nomme ---"));
        assertTrue(sortie.contains("--- 4. Cache de requetes : deux passes de la requete cacheable ---"));
        assertTrue(sortie.contains("--- 4 bis. Contre-exemple : meme requete SANS le hint cacheable ---"));
        assertTrue(sortie.contains("--- 5. find() par identifiant : cache froid puis cache chaud ---"));
        assertTrue(sortie.contains("--- 6. Le N+1 rejoue avec un cache de second niveau deja chaud ---"));
        assertTrue(sortie.contains("--- 7. Limite du cache de second niveau : requete optimisee a cache chaud ---"));
        assertTrue(sortie.contains("--- Conclusion ---"));
    }

    @Test
    public void laConclusionRendCompteDesMesuresReelles() {
        String sortie = executerLaDemonstration();

        assertTrue(sortie.contains("Requetes SQL sans optimisation ...... 11"));
        assertTrue(sortie.contains("Requetes SQL avec JOIN FETCH ........ 1"));
        assertTrue(sortie.contains("Requetes SQL avec entity graph ...... 1"));
        assertTrue(sortie.contains("Hits cache de requetes, 1re passe ... 0"));
        assertTrue(sortie.contains("Hits cache de requetes, 2e passe .... 1"));
        assertTrue(sortie.contains("Requetes SQL du 2e find() par id .... 0"));
        assertTrue(sortie.contains("Requetes SQL du N+1 a cache chaud ... 1"));
        assertTrue(sortie.contains("Hits cache L2 du N+1 a cache chaud .. 60"));
        assertTrue(sortie.contains("SQL de la requete optimisee a chaud . 1"));
    }

    @Test
    public void laDemonstrationTraceLeSqlEtNeSignaleAucuneErreur() {
        String sortie;
        String erreur;
        try (Capture capture = new Capture()) {
            App.main(new String[0]);
            sortie = capture.sortie();
            erreur = capture.erreur();
        }

        assertTrue(sortie.contains("select"));
        assertTrue(sortie.contains("Requetes SQL sans optimisation ...... 11"));
        assertFalse(erreur.contains("Exception"));
    }

    private String executerLaDemonstration() {
        try (Capture capture = new Capture()) {
            App.main(new String[0]);
            return capture.sortie();
        }
    }
}
