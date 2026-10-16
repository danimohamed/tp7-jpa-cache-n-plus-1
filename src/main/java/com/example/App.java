package com.example;

import com.example.service.DataInitService;
import com.example.service.Mesure;
import com.example.service.PerformanceTestService;
import com.example.util.JpaUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Demonstration du TP 7.
 *
 * <p>Le programme mesure plusieurs facons d'acceder aux memes donnees et affiche, pour chacune, le
 * nombre de requetes SQL reellement emises ainsi que les compteurs du cache de second niveau.
 * Les traces SQL produites par {@code hibernate.show_sql} sont volontairement visibles : elles
 * constituent la preuve du probleme N+1 puis de sa disparition.</p>
 */
public class App {

    public static void main(String[] args) {
        // Hibernate 5.6 journalise via java.util.logging : on coupe le bruit de demarrage.
        // Attention, cela ne masque pas les traces SQL : hibernate.show_sql ecrit sur System.out.
        Logger.getLogger("org.hibernate").setLevel(Level.WARNING);
        Logger.getLogger("org.hibernate.orm.deprecation").setLevel(Level.SEVERE);

        try {
            DataInitService dataInitService = new DataInitService();
            PerformanceTestService performanceTestService = new PerformanceTestService();

            titre("TP 7 : cache de second niveau, probleme N+1, JOIN FETCH et entity graph");

            dataInitService.reset();
            System.out.println();
            System.out.println("--- Jeu de donnees ---");
            System.out.println("Auteurs en base : " + dataInitService.compterAuteurs());
            System.out.println("Livres en base  : " + dataInitService.compterLivres());

            System.out.println();
            System.out.println("--- 1. Sans optimisation : probleme N+1 ---");
            Mesure sansCache = performanceTestService.testPerformanceSansCache();
            System.out.println(sansCache);

            System.out.println();
            System.out.println("--- 2. Avec JOIN FETCH ---");
            Mesure joinFetch = performanceTestService.testPerformanceAvecJoinFetch();
            System.out.println(joinFetch);

            System.out.println();
            System.out.println("--- 3. Avec entity graph nomme ---");
            Mesure entityGraph = performanceTestService.testPerformanceAvecEntityGraph();
            System.out.println(entityGraph);

            System.out.println();
            System.out.println("--- 4. Cache de requetes : deux passes de la requete cacheable ---");
            Mesure[] deuxPasses = performanceTestService.testPerformanceAvecCacheDeuxPasses();
            System.out.println(deuxPasses[0]);
            System.out.println();
            System.out.println(deuxPasses[1]);

            System.out.println();
            System.out.println("--- 4 bis. Contre-exemple : meme requete SANS le hint cacheable ---");
            Mesure[] sansQueryCache = performanceTestService.testPerformanceSansQueryCacheDeuxPasses();
            System.out.println(sansQueryCache[0]);
            System.out.println();
            System.out.println(sansQueryCache[1]);

            System.out.println();
            System.out.println("--- 5. find() par identifiant : cache froid puis cache chaud ---");
            Long auteurId = performanceTestService.premierAuteurId();
            System.out.println("Identifiant teste : " + auteurId);
            Mesure[] parId = performanceTestService.mesurerLectureParIdAvecCache(auteurId);
            System.out.println(parId[0]);
            System.out.println();
            System.out.println(parId[1]);

            System.out.println();
            System.out.println("--- 6. Le N+1 rejoue avec un cache de second niveau deja chaud ---");
            Mesure[] nPlusUnDeuxPasses = performanceTestService.testPerformanceSansCacheDeuxPasses();
            System.out.println(nPlusUnDeuxPasses[0]);
            System.out.println();
            System.out.println(nPlusUnDeuxPasses[1]);
            System.out.println();
            System.out.println("La requete sur les auteurs est toujours emise, mais les collections ne sont");
            System.out.println("plus chargees en base : le cache de second niveau supprime a lui seul les");
            System.out.println(nPlusUnDeuxPasses[0].getAuteursParcourus()
                    + " requetes supplementaires du N+1, sans modifier le JPQL.");

            System.out.println();
            System.out.println("--- 7. Limite du cache de second niveau : requete optimisee a cache chaud ---");
            Mesure optimiseeChaude = performanceTestService.testPerformanceAvecCache();
            System.out.println(optimiseeChaude);
            System.out.println();
            System.out.println("Le cache est chaud, pourtant le SQL est rejoue et aucun hit n'est enregistre :");
            System.out.println("une requete JPQL ne lit jamais l'etat des entites dans le cache de second");
            System.out.println("niveau. Pour supprimer ce SQL il faut le cache de requetes (section 4).");

            System.out.println();
            System.out.println("--- Conclusion ---");
            System.out.printf("Requetes SQL sans optimisation ...... %d%n", sansCache.getRequetesSql());
            System.out.printf("Requetes SQL avec JOIN FETCH ........ %d%n", joinFetch.getRequetesSql());
            System.out.printf("Requetes SQL avec entity graph ...... %d%n", entityGraph.getRequetesSql());
            System.out.printf("Hits cache de requetes, 1re passe ... %d%n", deuxPasses[0].getHitsQueryCache());
            System.out.printf("Hits cache de requetes, 2e passe .... %d%n", deuxPasses[1].getHitsQueryCache());
            System.out.printf("Requetes SQL du 2e find() par id .... %d%n", parId[1].getRequetesSql());
            System.out.printf("Requetes SQL du N+1 a cache chaud ... %d%n",
                    nPlusUnDeuxPasses[1].getRequetesSql());
            System.out.printf("Hits cache L2 du N+1 a cache chaud .. %d%n",
                    nPlusUnDeuxPasses[1].getHitsSecondLevelCache());
            System.out.printf("SQL de la requete optimisee a chaud . %d%n", optimiseeChaude.getRequetesSql());
            System.out.println();
            System.out.println("Le N+1 est supprime (" + joinFetch.getRequetesSql() + " requete au lieu de "
                    + sansCache.getRequetesSql() + ") et la deuxieme lecture de la requete cacheable "
                    + "ne touche plus la base.");
            System.out.println("Le cache de second niveau supprime aussi les requetes du N+1 des lors qu'il");
            System.out.println("est chaud (" + nPlusUnDeuxPasses[1].getRequetesSql() + " requete), mais il ne "
                    + "dispense pas du SQL :");
            System.out.println("seul le cache de requetes fait tomber la deuxieme lecture a "
                    + deuxPasses[1].getRequetesSql() + " requete.");
        } finally {
            JpaUtil.close();
        }
    }

    private static void titre(String texte) {
        System.out.println("================================================================");
        System.out.println(" " + texte);
        System.out.println("================================================================");
    }
}
