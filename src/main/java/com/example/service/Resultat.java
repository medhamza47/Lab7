package com.example.service;

/**
 * Resultat d'une mesure : nombre d'entites lues, nombre de requetes SQL emises,
 * duree d'execution et hits sur le cache de second niveau.
 */
public class Resultat {

    private final String libelle;
    private final int nbAuteurs;
    private final int nbLivres;
    private final long nbRequetesSql;
    private final long dureeMs;
    private final long hitsCache;

    public Resultat(String libelle, int nbAuteurs, int nbLivres, long nbRequetesSql, long dureeMs, long hitsCache) {
        this.libelle = libelle;
        this.nbAuteurs = nbAuteurs;
        this.nbLivres = nbLivres;
        this.nbRequetesSql = nbRequetesSql;
        this.dureeMs = dureeMs;
        this.hitsCache = hitsCache;
    }

    public String getLibelle() {
        return libelle;
    }

    public int getNbAuteurs() {
        return nbAuteurs;
    }

    public int getNbLivres() {
        return nbLivres;
    }

    public long getNbRequetesSql() {
        return nbRequetesSql;
    }

    public long getDureeMs() {
        return dureeMs;
    }

    public long getHitsCache() {
        return hitsCache;
    }

    @Override
    public String toString() {
        return String.format("%-34s | auteurs %2d | livres %2d | requetes SQL %2d | hits cache %2d | %4d ms",
                libelle, nbAuteurs, nbLivres, nbRequetesSql, hitsCache, dureeMs);
    }
}
