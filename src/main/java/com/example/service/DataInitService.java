package com.example.service;

import com.example.model.Auteur;
import com.example.model.Livre;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

public class DataInitService {

    private static final int NB_AUTEURS = 10;
    private static final int NB_LIVRES_PAR_AUTEUR = 5;

    private final EntityManager em;

    public DataInitService(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            for (int i = 1; i <= NB_AUTEURS; i++) {
                Auteur auteur = new Auteur("Auteur " + i);

                for (int j = 1; j <= NB_LIVRES_PAR_AUTEUR; j++) {
                    Livre livre = new Livre("Livre " + j + " de l'auteur " + i);
                    auteur.ajouterLivre(livre);
                }

                em.persist(auteur);
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    public int getNbAuteurs() {
        return NB_AUTEURS;
    }

    public int getNbLivres() {
        return NB_AUTEURS * NB_LIVRES_PAR_AUTEUR;
    }
}
