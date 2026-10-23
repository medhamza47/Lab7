package com.example;

import com.example.service.DataInitService;
import com.example.service.PerformanceTestService;
import com.example.service.Resultat;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {

    private static final String LIGNE = "================================================================";

    public static void main(String[] args) {
        // La console Windows n'est pas en UTF-8 par defaut : sans cela les accents
        // sortent en caracteres bizarres des que l'on redirige la sortie dans un fichier
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        // Seules les requetes SQL et les resultats nous interessent : on coupe les logs INFO d'Hibernate
        Logger.getLogger("org.hibernate").setLevel(Level.WARNING);

        boolean masquerSql = false;
        for (String arg : args) {
            if ("--silent-sql".equals(arg)) {
                masquerSql = true;
            }
        }

        Map<String, Object> overrides = new HashMap<>();
        if (masquerSql) {
            overrides.put("hibernate.show_sql", "false");
            overrides.put("hibernate.format_sql", "false");
        }

        // Hibernate ne transmet pas d'URI au provider JCache : on lui indique explicitement
        // le fichier ehcache.xml place dans src/main/resources
        URL cacheConfig = App.class.getResource("/ehcache.xml");
        if (cacheConfig != null) {
            overrides.put("hibernate.javax.cache.uri", cacheConfig.toString());
        }

        EntityManagerFactory emf = Persistence.createEntityManagerFactory("hibernate-cache-pu", overrides);
        try {
            System.out.println(LIGNE);
            System.out.println(" TP 7 - Hibernate : cache de second niveau, N+1 et Entity Graphs");
            System.out.println(LIGNE);

            initialiserDonnees(emf);

            PerformanceTestService perf = new PerformanceTestService(emf);
            perf.echauffer();

            List<Resultat> resultats = new ArrayList<>();
            resultats.addAll(lancer("Lecture sans optimisation (problème N+1)", seul(perf::testPerformanceSansCache)));
            resultats.addAll(lancer("Lecture avec JOIN FETCH", seul(perf::testPerformanceAvecJoinFetch)));
            resultats.addAll(lancer("Lecture avec Entity Graph", seul(perf::testPerformanceAvecEntityGraph)));
            resultats.addAll(lancer("Cache de second niveau", () -> Arrays.asList(perf.testPerformanceAvecCache())));
            resultats.addAll(lancer("Cache de requêtes", () -> Arrays.asList(perf.testPerformanceAvecCacheDeRequetes())));

            afficherRecapitulatif(resultats);
            afficherStatistiques(emf);
        } finally {
            emf.close();
        }
    }

    private static void initialiserDonnees(EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager();
        try {
            DataInitService dataInitService = new DataInitService(em);
            dataInitService.initData();
            System.out.println();
            System.out.println("[1/6] Données initialisées : " + dataInitService.getNbAuteurs()
                    + " auteurs et " + dataInitService.getNbLivres() + " livres");
        } finally {
            em.close();
        }
    }

    /**
     * Affiche le titre de la section avant de lancer la mesure, pour que le SQL
     * genere par le test apparaisse bien sous son titre dans la console.
     */
    private static List<Resultat> lancer(String titre, Supplier<List<Resultat>> test) {
        System.out.println();
        System.out.println(titre);

        List<Resultat> mesures = test.get();
        for (Resultat mesure : mesures) {
            afficher(mesure);
        }
        return mesures;
    }

    private static Supplier<List<Resultat>> seul(Supplier<Resultat> test) {
        return () -> Collections.singletonList(test.get());
    }

    private static void afficher(Resultat resultat) {
        System.out.println("  " + resultat.getLibelle());
        System.out.println("    auteurs lus  : " + resultat.getNbAuteurs());
        System.out.println("    livres lus   : " + resultat.getNbLivres());
        System.out.println("    requêtes SQL : " + resultat.getNbRequetesSql());
        System.out.println("    hits cache   : " + resultat.getHitsCache());
        System.out.println("    durée        : " + resultat.getDureeMs() + " ms");
    }

    private static void afficherRecapitulatif(List<Resultat> resultats) {
        System.out.println();
        System.out.println(LIGNE);
        System.out.println(" Récapitulatif");
        System.out.println(LIGNE);
        System.out.println();
        System.out.printf(" %-34s %-13s %-11s %s%n", "Stratégie", "Requêtes SQL", "Hits cache", "Durée");
        System.out.println(" " + "-".repeat(72));
        for (Resultat resultat : resultats) {
            System.out.printf(" %-34s %-13d %-11d %d ms%n", resultat.getLibelle(),
                    resultat.getNbRequetesSql(), resultat.getHitsCache(), resultat.getDureeMs());
        }
    }

    private static void afficherStatistiques(EntityManagerFactory emf) {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        System.out.println();
        System.out.println(" Statistiques Hibernate");
        System.out.println(" " + "-".repeat(72));
        System.out.println(" sessions ouvertes       : " + stats.getSessionOpenCount());
        System.out.println(" requêtes JPQL exécutées : " + stats.getQueryExecutionCount());
        System.out.println(" 2e niveau - hits        : " + stats.getSecondLevelCacheHitCount());
        System.out.println(" 2e niveau - miss        : " + stats.getSecondLevelCacheMissCount());
        System.out.println(" 2e niveau - puts        : " + stats.getSecondLevelCachePutCount());
        System.out.println(" régions en cache        : " + String.join(", ", stats.getSecondLevelCacheRegionNames()));
    }
}
