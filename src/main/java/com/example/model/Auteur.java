package com.example.model;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Auteur d'un ou plusieurs livres.
 *
 * <p>La collection {@code livres} est en {@link FetchType#LAZY} : c'est cette configuration qui
 * provoque le probleme N+1 lorsque l'on parcourt les auteurs sans optimiser la requete.</p>
 *
 * <p>L'entite est declaree cachable en lecture/ecriture : elle est donc stockee dans le cache de
 * second niveau, partage entre tous les EntityManager d'une meme EntityManagerFactory.</p>
 *
 * <p>L'entity graph nomme {@code Auteur.livres} permet de charger la collection de facon
 * declarative, sans modifier la requete JPQL.</p>
 */
@Entity
@Table(name = "auteurs")
@NamedEntityGraph(
        name = Auteur.GRAPH_LIVRES,
        attributeNodes = @NamedAttributeNode("livres")
)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Auteur {

    public static final String GRAPH_LIVRES = "Auteur.livres";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 120)
    private String nom;

    @OneToMany(mappedBy = "auteur", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Livre> livres = new ArrayList<>();

    public Auteur() {
    }

    public Auteur(String nom) {
        this.nom = nom;
    }

    public void ajouterLivre(Livre livre) {
        livres.add(livre);
        livre.setAuteur(this);
    }

    public void retirerLivre(Livre livre) {
        livres.remove(livre);
        livre.setAuteur(null);
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public List<Livre> getLivres() {
        return livres;
    }

    /**
     * Ne touche pas a la collection : {@code toString()} doit rester utilisable sur une instance
     * detachee sans declencher de {@code LazyInitializationException}.
     */
    @Override
    public String toString() {
        return "Auteur{id=" + id + ", nom='" + nom + "'}";
    }
}
