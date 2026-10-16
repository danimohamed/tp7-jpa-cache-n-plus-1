package com.example.service;

import com.example.model.Auteur;
import com.example.util.JpaUtil;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;
import java.util.List;

/**
 * Mesures de performance comparant trois facons de parcourir les auteurs et leurs livres :
 * <ol>
 *   <li>requete simple + acces paresseux a la collection : c'est le probleme N+1 ;</li>
 *   <li>{@code JOIN FETCH} : une seule requete ;</li>
 *   <li>entity graph nomme : une seule requete, sans modifier le JPQL.</li>
 * </ol>
 *
 * <p>Chaque mesure s'execute dans son <strong>propre</strong> EntityManager. C'est essentiel :
 * avec un EntityManager partage, le cache de premier niveau (le contexte de persistance)
 * repondrait tout seul et la deuxieme lecture ne prouverait rien sur le cache de second niveau.</p>
 *
 * <p>Le code du sujet utilise {@code @PersistenceContext} et {@code @Transactional} puis appelle
 * {@code em.getTransaction().begin()} : ces deux approches sont incompatibles. Ici il n'y a ni
 * injection ni annotation transactionnelle, et chaque scenario possede exactement une
 * transaction implicite en lecture.</p>
 */
public class PerformanceTestService {

    @FunctionalInterface
    private interface Scenario {
        /** Retourne {nombre d'auteurs parcourus, nombre de livres parcourus}. */
        int[] executer(EntityManager em);
    }

    private static final String JPQL_AUTEURS = "SELECT a FROM Auteur a";
    private static final String JPQL_AUTEURS_JOIN_FETCH =
            "SELECT DISTINCT a FROM Auteur a JOIN FETCH a.livres";

    // ------------------------------------------------------------------ N+1

    /**
     * Requete simple puis parcours paresseux des collections : une requete pour les auteurs, puis
     * une requete par auteur pour sa collection de livres.
     */
    public Mesure testPerformanceSansCache() {
        return mesurer("Sans optimisation (probleme N+1)", scenarioSansOptimisation());
    }

    /**
     * Rejoue le parcours N+1 avec un cache de second niveau deja chaud.
     *
     * <p>C'est ici que le cache de second niveau se voit le mieux : la requete sur les auteurs est
     * toujours emise (une requete JPQL ne lit jamais l'etat des entites dans le cache L2), mais les
     * {@value DataInitService#NB_AUTEURS} collections {@code livres} sont desormais servies par le
     * cache. Les {@value DataInitService#NB_AUTEURS} requetes supplementaires disparaissent donc :
     * le N+1 est ramene a une seule requete, sans avoir touche au JPQL.</p>
     *
     * @return {mesure a cache froid, mesure a cache chaud}
     */
    public Mesure[] testPerformanceSansCacheDeuxPasses() {
        JpaUtil.evictSecondLevelCache();
        Mesure froid = mesurer("Parcours N+1, 1re passe (cache L2 froid)", scenarioSansOptimisation());
        Mesure chaud = mesurer("Parcours N+1, 2e passe (cache L2 chaud)", scenarioSansOptimisation());
        return new Mesure[]{froid, chaud};
    }

    // ------------------------------------------------------------- JOIN FETCH

    /**
     * {@code JOIN FETCH} force le chargement de la collection dans la meme requete.
     * {@code DISTINCT} evite de recevoir une ligne par livre.
     */
    public Mesure testPerformanceAvecJoinFetch() {
        return mesurer("Avec JOIN FETCH", scenarioJoinFetch());
    }

    // ----------------------------------------------------------- Entity Graph

    /**
     * Meme effet que le JOIN FETCH, mais la requete JPQL reste inchangee : c'est le hint
     * {@code javax.persistence.fetchgraph} qui decide de ce qui est charge.
     */
    public Mesure testPerformanceAvecEntityGraph() {
        return mesurer("Avec entity graph nomme", em -> {
            List<Auteur> auteurs = em.createQuery(JPQL_AUTEURS, Auteur.class)
                    .setHint("javax.persistence.fetchgraph", em.getEntityGraph(Auteur.GRAPH_LIVRES))
                    .getResultList();
            int livres = 0;
            for (Auteur auteur : auteurs) {
                livres += auteur.getLivres().size();
            }
            return new int[]{auteurs.size(), livres};
        });
    }

    // --------------------------------------------------- Cache de 2e niveau

    /**
     * Rejoue la requete optimisee dans un EntityManager neuf, alors que le cache de second niveau
     * est deja chaud.
     *
     * <p>Le resultat est instructif et contraire a l'intuition : le SQL est <strong>toujours</strong>
     * execute et le cache L2 n'enregistre aucun hit. Une requete JPQL ne consulte jamais le cache de
     * second niveau pour l'etat des entites qu'elle ramene : elle lit le jeu de resultats JDBC.
     * Le cache L2 n'intervient que sur le chargement d'une entite par identifiant, sur le chargement
     * d'une collection, et sur la resolution des identifiants rendus par le cache de requetes.
     * Pour eviter de rejouer le SQL il faut donc le cache de requetes (voir
     * {@link #testPerformanceAvecCacheDeuxPasses()}).</p>
     *
     * <p>La mesure commence par vider le cache puis par une passe de chauffe : sans cela la mesure
     * dependrait du scenario execute avant elle.</p>
     */
    public Mesure testPerformanceAvecCache() {
        JpaUtil.evictSecondLevelCache();
        mesurer("Passe de chauffe du cache L2", scenarioJoinFetch());
        return mesurer("Requete optimisee avec un cache L2 chaud", scenarioJoinFetch());
    }

    /**
     * Enchaine une lecture a cache froid puis une lecture a cache chaud, chacune dans son propre
     * EntityManager. Le tableau retourne contient les deux mesures dans cet ordre.
     *
     * <p>La requete porte le hint {@code org.hibernate.cacheable} : ses <em>resultats</em> sont donc
     * stockes dans le cache de requetes, en plus des entites. La deuxieme passe ne rejoue alors
     * plus aucun SQL, ce que la mesure des PreparedStatement demontre directement.</p>
     */
    public Mesure[] testPerformanceAvecCacheDeuxPasses() {
        JpaUtil.evictSecondLevelCache();
        Mesure froid = mesurer("1re lecture (cache L2 froid)", scenarioJointFetchCacheable());
        Mesure chaud = mesurer("2e lecture (cache L2 chaud)", scenarioJointFetchCacheable());
        return new Mesure[]{froid, chaud};
    }

    /**
     * Meme scenario sans le hint {@code cacheable} : le SQL est rejoue a chaque passe, seules les
     * entites sont servies par le cache de second niveau. Sert de contre-exemple dans la
     * demonstration et dans les tests.
     */
    public Mesure[] testPerformanceSansQueryCacheDeuxPasses() {
        JpaUtil.evictSecondLevelCache();
        Mesure froid = mesurer("1re lecture (cache L2 froid)", scenarioJoinFetch());
        Mesure chaud = mesurer("2e lecture (cache L2 chaud)", scenarioJoinFetch());
        return new Mesure[]{froid, chaud};
    }

    /**
     * Demonstration la plus nette du cache de second niveau : deux {@code find} successifs par
     * identifiant, dans deux EntityManager differents. La deuxieme lecture n'emet plus aucune
     * requete SQL et se solde par un hit.
     *
     * @return {mesure a froid, mesure a chaud}
     */
    public Mesure[] mesurerLectureParIdAvecCache(Long auteurId) {
        JpaUtil.evictSecondLevelCache();
        Mesure froid = mesurer("find() a froid", em -> {
            Auteur auteur = em.find(Auteur.class, auteurId);
            return new int[]{auteur == null ? 0 : 1, 0};
        });
        Mesure chaud = mesurer("find() a chaud", em -> {
            Auteur auteur = em.find(Auteur.class, auteurId);
            return new int[]{auteur == null ? 0 : 1, 0};
        });
        return new Mesure[]{froid, chaud};
    }

    /** Renvoie l'identifiant du premier auteur, ou {@code null} si la base est vide. */
    public Long premierAuteurId() {
        EntityManager em = JpaUtil.createEntityManager();
        try {
            List<Long> ids = em.createQuery("SELECT a.id FROM Auteur a ORDER BY a.id", Long.class)
                    .setMaxResults(1)
                    .getResultList();
            return ids.isEmpty() ? null : ids.get(0);
        } finally {
            em.close();
        }
    }

    // ------------------------------------------------------------------ outillage

    private Scenario scenarioSansOptimisation() {
        return em -> {
            List<Auteur> auteurs = em.createQuery(JPQL_AUTEURS, Auteur.class).getResultList();
            int livres = 0;
            for (Auteur auteur : auteurs) {
                livres += auteur.getLivres().size();
            }
            return new int[]{auteurs.size(), livres};
        };
    }

    private Scenario scenarioJoinFetch() {
        return em -> {
            List<Auteur> auteurs = em.createQuery(JPQL_AUTEURS_JOIN_FETCH, Auteur.class)
                    .getResultList();
            int livres = 0;
            for (Auteur auteur : auteurs) {
                livres += auteur.getLivres().size();
            }
            return new int[]{auteurs.size(), livres};
        };
    }

    /** Variante dont les resultats sont mis en cache de requetes. */
    private Scenario scenarioJointFetchCacheable() {
        return em -> {
            List<Auteur> auteurs = em.createQuery(JPQL_AUTEURS_JOIN_FETCH, Auteur.class)
                    .setHint("org.hibernate.cacheable", Boolean.TRUE)
                    .getResultList();
            int livres = 0;
            for (Auteur auteur : auteurs) {
                livres += auteur.getLivres().size();
            }
            return new int[]{auteurs.size(), livres};
        };
    }

    private Mesure mesurer(String libelle, Scenario scenario) {
        JpaUtil.resetStatistics();

        EntityManager em = JpaUtil.createEntityManager();
        int[] compteurs;
        long debut = System.currentTimeMillis();
        try {
            compteurs = scenario.executer(em);
        } finally {
            em.close();
        }
        long duree = System.currentTimeMillis() - debut;

        Statistics stats = JpaUtil.getStatistics();
        return new Mesure(
                libelle,
                duree,
                stats.getPrepareStatementCount(),
                stats.getEntityLoadCount(),
                stats.getSecondLevelCacheHitCount(),
                stats.getSecondLevelCacheMissCount(),
                stats.getSecondLevelCachePutCount(),
                stats.getQueryCacheHitCount(),
                compteurs[0],
                compteurs[1]);
    }
}
