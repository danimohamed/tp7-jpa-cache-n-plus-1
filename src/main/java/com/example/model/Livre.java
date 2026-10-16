package com.example.model;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Livre rattache a un auteur.
 *
 * <p>Le {@link FetchType#LAZY} sur la relation {@code ManyToOne} est volontaire : il rend visible
 * le probleme N+1 avant optimisation.</p>
 */
@Entity
@Table(name = "livres")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Livre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id")
    private Auteur auteur;

    public Livre() {
    }

    public Livre(String titre) {
        this.titre = titre;
    }

    public Long getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public Auteur getAuteur() {
        return auteur;
    }

    public void setAuteur(Auteur auteur) {
        this.auteur = auteur;
    }

    /**
     * Ne touche pas a l'auteur : {@code toString()} doit rester utilisable sur une instance
     * detachee sans declencher de {@code LazyInitializationException}.
     */
    @Override
    public String toString() {
        return "Livre{id=" + id + ", titre='" + titre + "'}";
    }
}
