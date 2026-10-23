package com.example.service;

import com.example.model.Auteur;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mesure le cout des differentes strategies de chargement :
 * requete simple (probleme N+1), JOIN FETCH, Entity Graph, cache de second niveau
 * et cache de requetes.
 */
public class PerformanceTestService {

    private final EntityManagerFactory emf;

    public PerformanceTestService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    /**
     * Quelques lectures preliminaires : evite de mesurer le demarrage de la JVM
     * (compilation JIT, ouverture du pool de connexions).
     */
    public void echauffer() {
        for (int i = 0; i < 3; i++) {
            mesurer("échauffement", this::lireAvecLivres);
        }
        viderCacheSecondNiveau();
    }

    /**
     * Vide toutes les regions du cache de second niveau (entites, collections et requetes).
     * JPA Cache#evictAll ne vide que les regions d'entites.
     */
    public void viderCacheSecondNiveau() {
        emf.unwrap(SessionFactory.class).getCache().evictAllRegions();
    }

    /**
     * Requete simple : les livres sont charges en LAZY, ce qui declenche
     * une requete supplementaire par auteur (1 + N requetes).
     */
    public Resultat testPerformanceSansCache() {
        return mesurer("Sans optimisation (N+1)", em -> {
            TypedQuery<Auteur> query = em.createQuery("select a from Auteur a", Auteur.class);
            return chargerEtCompter(query);
        });
    }

    /**
     * JOIN FETCH : les auteurs et leurs livres sont ramenes en une seule requete SQL.
     */
    public Resultat testPerformanceAvecJoinFetch() {
        return mesurer("Avec JOIN FETCH", em -> {
            TypedQuery<Auteur> query = em.createQuery(
                    "select distinct a from Auteur a join fetch a.livres", Auteur.class);
            return chargerEtCompter(query);
        });
    }

    /**
     * Entity Graph : les associations a charger sont declarees sur l'entite
     * (Auteur.livres) puis appliquees a la requete par un hint.
     */
    public Resultat testPerformanceAvecEntityGraph() {
        return mesurer("Avec Entity Graph", em -> {
            @SuppressWarnings("unchecked")
            EntityGraph<Auteur> graph = (EntityGraph<Auteur>) em.createEntityGraph("Auteur.livres");
            TypedQuery<Auteur> query = em.createQuery("select a from Auteur a", Auteur.class);
            query.setHint("javax.persistence.fetchgraph", graph);
            return chargerEtCompter(query);
        });
    }

    /**
     * Deux lectures identiques, chacune dans une nouvelle session :
     * la seconde lecture est servie par le cache de second niveau.
     */
    public Resultat[] testPerformanceAvecCache() {
        // on repart d'un cache vide pour isoler la mesure
        viderCacheSecondNiveau();

        Resultat lecteurFroid = mesurer("Cache 2e niveau - lecture 1", this::lireAvecLivres);
        Resultat lectureChaude = mesurer("Cache 2e niveau - lecture 2", this::lireAvecLivres);
        return new Resultat[]{lecteurFroid, lectureChaude};
    }

    /**
     * Meme requete marquee comme cachable : la seconde execution ne genere
     * aucune requete SQL grace au cache de requetes.
     */
    public Resultat[] testPerformanceAvecCacheDeRequetes() {
        Resultat premierAppel = mesurer("Cache de requêtes - appel 1", this::lireAvecLivresCacheable);
        Resultat secondAppel = mesurer("Cache de requêtes - appel 2", this::lireAvecLivresCacheable);
        return new Resultat[]{premierAppel, secondAppel};
    }

    private int[] chargerEtCompter(TypedQuery<Auteur> query) {
        List<Auteur> auteurs = query.getResultList();
        int nbLivres = 0;
        for (Auteur auteur : auteurs) {
            nbLivres += auteur.getLivres().size();
        }
        return new int[]{auteurs.size(), nbLivres};
    }

    private int[] lireAvecLivres(EntityManager em) {
        TypedQuery<Auteur> query = em.createQuery("select a from Auteur a", Auteur.class);
        return chargerEtCompter(query);
    }

    private int[] lireAvecLivresCacheable(EntityManager em) {
        TypedQuery<Auteur> query = em.createQuery("select a from Auteur a", Auteur.class);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);
        return chargerEtCompter(query);
    }

    private Resultat mesurer(String libelle, Mesure mesure) {
        Statistics stats = statistics();
        long requetesAvant = stats.getPrepareStatementCount();
        long hitsAvant = stats.getSecondLevelCacheHitCount();

        EntityManager em = emf.createEntityManager();
        int[] compteurs;
        long debut = System.nanoTime();
        try {
            compteurs = dansTransaction(em, () -> mesure.executer(em));
        } finally {
            em.close();
        }
        long duree = (System.nanoTime() - debut) / 1_000_000;

        long nbRequetes = stats.getPrepareStatementCount() - requetesAvant;
        long nbHits = stats.getSecondLevelCacheHitCount() - hitsAvant;

        return new Resultat(libelle, compteurs[0], compteurs[1], nbRequetes, duree, nbHits);
    }

    private <T> T dansTransaction(EntityManager em, Supplier<T> action) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            T resultat = action.get();
            tx.commit();
            return resultat;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    private Statistics statistics() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    private interface Mesure {
        int[] executer(EntityManager em);
    }
}
