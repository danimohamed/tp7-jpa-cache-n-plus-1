package com.example.service;

import org.hibernate.stat.Statistics;

/**
 * Resultat d'une mesure de performance.
 *
 * <p>La duree en millisecondes est purement indicative : elle depend de la machine et n'est jamais
 * utilisee comme critere de succes. Les compteurs issus des {@link Statistics} d'Hibernate, eux,
 * sont deterministes et ce sont eux qui prouvent le probleme N+1 et l'effet du cache.</p>
 */
public class Mesure {

    private final String libelle;
    private final long dureeMs;
    private final long requetesSql;
    private final long entitesChargees;
    private final long hitsSecondLevelCache;
    private final long missesSecondLevelCache;
    private final long putsSecondLevelCache;
    private final long hitsQueryCache;
    private final int auteursParcourus;
    private final int livresParcourus;

    public Mesure(String libelle,
                  long dureeMs,
                  long requetesSql,
                  long entitesChargees,
                  long hitsSecondLevelCache,
                  long missesSecondLevelCache,
                  long putsSecondLevelCache,
                  long hitsQueryCache,
                  int auteursParcourus,
                  int livresParcourus) {
        this.libelle = libelle;
        this.dureeMs = dureeMs;
        this.requetesSql = requetesSql;
        this.entitesChargees = entitesChargees;
        this.hitsSecondLevelCache = hitsSecondLevelCache;
        this.missesSecondLevelCache = missesSecondLevelCache;
        this.putsSecondLevelCache = putsSecondLevelCache;
        this.hitsQueryCache = hitsQueryCache;
        this.auteursParcourus = auteursParcourus;
        this.livresParcourus = livresParcourus;
    }

    public String getLibelle() {
        return libelle;
    }

    public long getDureeMs() {
        return dureeMs;
    }

    /** Nombre de PreparedStatement JDBC executes : c'est la mesure directe du N+1. */
    public long getRequetesSql() {
        return requetesSql;
    }

    public long getEntitesChargees() {
        return entitesChargees;
    }

    public long getHitsSecondLevelCache() {
        return hitsSecondLevelCache;
    }

    public long getMissesSecondLevelCache() {
        return missesSecondLevelCache;
    }

    public long getPutsSecondLevelCache() {
        return putsSecondLevelCache;
    }

    /**
     * Nombre de hits du cache de requetes ({@code default-query-results-region}). Un hit signifie
     * que la requete n'a meme pas ete rejouee en base.
     */
    public long getHitsQueryCache() {
        return hitsQueryCache;
    }

    public int getAuteursParcourus() {
        return auteursParcourus;
    }

    public int getLivresParcourus() {
        return livresParcourus;
    }

    @Override
    public String toString() {
        return String.format(
                "%s%n"
                        + "  duree ................. %d ms%n"
                        + "  requetes SQL (JDBC) ... %d%n"
                        + "  entites chargees ...... %d%n"
                        + "  auteurs parcourus ..... %d%n"
                        + "  livres parcourus ...... %d%n"
                        + "  cache L2 (hit/miss/put) %d / %d / %d%n"
                        + "  cache de requetes ..... %d hit(s)",
                libelle,
                dureeMs,
                requetesSql,
                entitesChargees,
                auteursParcourus,
                livresParcourus,
                hitsSecondLevelCache,
                missesSecondLevelCache,
                putsSecondLevelCache,
                hitsQueryCache);
    }
}
