# TP 7 — Cache de second niveau, problème N+1, `JOIN FETCH` et entity graph

Ce TP mesure, chiffres à l'appui, les quatre leviers d'optimisation vus en cours :

1. **Diagnostiquer** le problème N+1 avec les statistiques Hibernate ;
2. **Supprimer** le N+1 côté requête avec `JOIN FETCH` puis avec un **entity graph nommé** ;
3. **Mettre en cache** les requêtes avec `hibernate.cache.use_query_cache` et le hint `cacheable` ;
4. **Activer le cache de second niveau** (EhCache via JCache) et mesurer ce qu'il apporte — et
   surtout ce qu'il n'apporte pas.

Chaque mesure est prise avec `Statistics` (`requetes SQL`, `entites chargees`, `hits/misses/puts`
du cache L2, `hits` du cache de requêtes), dans un `EntityManager` neuf, après remise à zéro des
compteurs. Les durées affichées sont indicatives : **ce sont les compteurs qui font foi**, jamais
les millisecondes.

## Prérequis

- **JDK 11 ou plus récent** (développé et vérifié avec JDK 21.0.7)
- **Maven 3.8+** (vérifié avec Maven 3.9.9)
- Aucun serveur : la base est H2 **en mémoire**, créée et détruite à chaque exécution.

## Démarrage rapide

```bash
# 1. Compiler
mvn -B compile

# 2. Lancer les 27 tests de la couche de vérification
mvn -B test

# 3. Exécuter la démonstration (les 7 scénarios du TP)
mvn -B exec:java
```

Le `pom.xml` configure `exec-maven-plugin` avec `mainClass=com.example.App` : `mvn -B exec:java`
suffit donc à lancer la démonstration, sans avoir à reconstruire le classpath à la main.

## Structure du projet

```
src/main/java/com/example/
├── App.java                              # démonstration : 7 scénarios + conclusion
├── model/
│   ├── Auteur.java                       # @Cache sur l'entité ET sur la collection livres
│   └── Livre.java                        # @Cache sur l'entité
├── service/
│   ├── DataInitService.java              # jeu de données : 10 auteurs × 5 livres
│   ├── Mesure.java                       # DTO immuable : les compteurs d'une mesure
│   └── PerformanceTestService.java       # le cœur du TP : les 8 scénarios mesurés
└── util/
    └── JpaUtil.java                      # cycle de vie de l'EMF + accès aux statistiques

src/main/resources/
├── META-INF/persistence.xml              # unité de persistance hibernate-cache-pu
└── ehcache.xml                           # configuration EhCache des régions L2

src/test/java/com/example/
├── AbstractJpaTest.java                  # socle commun : EMF, services, helpers SQL
├── Capture.java                          # capture de stdout/stderr pour AppTest
├── AppTest.java                          # 3 tests  — la démonstration produit ses preuves
├── ModeleTest.java                       # 4 tests  — mapping et cycle de vie des entités
├── DataInitServiceTest.java              # 6 tests  — jeu de données et remise à zéro
├── ProblemeN1Test.java                   # 5 tests  — diagnostic, JOIN FETCH, entity graph
├── CacheSecondNiveauTest.java            # 5 tests  — cache L2 et suppression du N+1
└── CacheRequetesTest.java                # 4 tests  — cache de requêtes et hint cacheable
```

## Dépendances

| Dépendance | Version | Rôle |
|---|---|---|
| `javax.persistence:javax.persistence-api` | 2.2 | API JPA (imposée par le TP) |
| `org.hibernate:hibernate-core` | 5.6.15.Final | implémentation JPA |
| `org.hibernate:hibernate-jcache` | 5.6.15.Final | `JCacheRegionFactory` pour le cache L2 |
| `javax.cache:cache-api` | 1.1.1 | API JCache (JSR-107) |
| `org.ehcache:ehcache` | 3.10.8 | implémentation JCache |
| `org.glassfish.jaxb:jaxb-runtime` | 2.3.9 | lecture du fichier `ehcache.xml` |
| `org.slf4j:slf4j-nop` | 1.7.36 | silencieux : les logs Hibernate passent par JUL |
| `com.h2database:h2` | 2.1.214 | base en mémoire |
| `net.bytebuddy:byte-buddy` | 1.14.18 | proxy des entités (compatible JDK 21) |
| `junit:junit` | 4.13.2 | tests |

> **Note sur les versions.** Le TP prescrit `hibernate-core`/`hibernate-ehcache` **5.4.32.Final**,
> `org.ehcache:ehcache` **3.8.1** et `h2` **1.4.200**. Ces versions embarquent un Byte Buddy qui ne
> sait pas lire les fichiers de classe du JDK 21 (`Unsupported class file major version 65`), et
> `hibernate-ehcache` n'existe pas au-delà de la branche 5.4 (remplacé par `hibernate-jcache`). Le
> projet utilise donc **Hibernate 5.6.15.Final** + `hibernate-jcache`, `ehcache` 3.10.8 et `h2`
> 2.1.214, avec compilation en `release 11`. Le code applicatif reste strictement celui du TP.

## Configuration JPA

`src/main/resources/META-INF/persistence.xml` — unité `hibernate-cache-pu`, `RESOURCE_LOCAL` :

```xml
<property name="hibernate.connection.url"             value="jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"/>
<property name="hibernate.dialect"                    value="org.hibernate.dialect.H2Dialect"/>
<property name="hibernate.hbm2ddl.auto"               value="create-drop"/>
<property name="hibernate.show_sql"                   value="true"/>
<property name="hibernate.format_sql"                 value="true"/>
<property name="hibernate.generate_statistics"        value="true"/>

<property name="hibernate.cache.use_second_level_cache"
          value="true"/>
<property name="hibernate.cache.region.factory_class"
          value="org.hibernate.cache.jcache.JCacheRegionFactory"/>
<property name="hibernate.javax.cache.provider"
          value="org.ehcache.jsr107.EhcacheCachingProvider"/>
<property name="hibernate.javax.cache.uri"            value="ehcache.xml"/>
<property name="hibernate.cache.use_query_cache"      value="true"/>
```

Deux points méritent l'attention :

- **`hibernate.generate_statistics=true`** est indispensable : c'est ce qui alimente
  `Statistics.getPrepareStatementCount()` et les compteurs L2 sur lesquels reposent tous les tests.
- **`hibernate.javax.cache.uri`** n'est pas dans l'énoncé, mais **sans lui le fichier `ehcache.xml`
  est purement et simplement ignoré**. Le fournisseur JCache d'EhCache n'a *aucune* découverte
  automatique sur le classpath : `DefaultConfigurationResolver` lit la propriété
  `ehcache.jsr107.config.default` (une URI) et retombe sinon sur
  `urn:X-ehcache:jsr107-default-config`. La valeur doit être une **URI relative** (`ehcache.xml`) :
  Hibernate appelle `new URI(valeur)` et refuse le schéma `classpath:`.

## Points clés du TP

### 1. Le problème N+1 : 11 requêtes pour 10 auteurs

```java
List<Auteur> auteurs = em.createQuery("SELECT a FROM Auteur a", Auteur.class).getResultList();
for (Auteur a : auteurs) { a.getLivres().size(); }   // déclenche un SELECT par auteur
```

Mesuré : **11 requêtes SQL**, **60 entités** chargées pour 10 auteurs et 50 livres.

### 2. `JOIN FETCH` : une requête, sans changer le mapping

```java
SELECT DISTINCT a FROM Auteur a LEFT JOIN FETCH a.getLivres()   // 1 requête
```

Le `DISTINCT` est nécessaire : la jointure duplique la ligne de l'auteur pour chacun de ses livres.
Le `LEFT` garantit qu'un auteur sans livre n'est pas perdu. Mesuré : **1 requête**.

### 3. L'entity graph nommé : le même gain, déclaré sur l'entité

```java
@NamedEntityGraph(name = Auteur.GRAPH_LIVRES, attributeNodes = @NamedAttributeNode("livres"))
public class Auteur { ... }

// utilisation :
em.createQuery("SELECT a FROM Auteur a", Auteur.class)
  .setHint("javax.persistence.fetchgraph", em.getEntityGraph(Auteur.GRAPH_LIVRES))
  .getResultList();
```

L'avantage sur `JOIN FETCH` est la **réutilisation** : la stratégie de chargement quitte le JPQL
pour vivre sur l'entité, et le JPQL redevient trivial. Mesuré : **1 requête**.

### 4. Le cache de requêtes : le seul moyen de ramener le SQL à zéro

```java
em.createQuery("SELECT a FROM Auteur a", Auteur.class)
  .setHint("org.hibernate.cacheable", Boolean.TRUE)     // <- le hint, pas seulement la propriété
  .getResultList();
```

`hibernate.cache.use_query_cache=true` **ne suffit pas** : sans le hint, la requête n'est jamais
mise en cache (section 4 bis du programme, qui sert de contre-exemple). Avec le hint :

| | 1<sup>re</sup> passe (L2 froid) | 2<sup>e</sup> passe (L2 chaud) |
|---|---|---|
| requêtes SQL | 1 | **0** |
| entités chargées | 60 | 0 |
| cache L2 (hit/miss/put) | 0 / 0 / 70 | 70 / 0 / 0 |
| cache de requêtes | 0 hit | **1 hit** |

Le cache de requêtes ne stocke que les **identifiants** ; les entités sont ensuite résolues dans le
cache L2 — d'où les 70 hits (10 auteurs + 10 collections + 50 livres).

### 5. Le cache de second niveau supprime le N+1 sans toucher au JPQL

C'est le résultat le plus intéressant du TP. En rejouant exactement le même parcours non optimisé,
une fois le cache L2 chaud :

| | 1<sup>re</sup> passe | 2<sup>e</sup> passe |
|---|---|---|
| requêtes SQL | 11 | **1** |
| entités chargées | 60 | **10** |
| cache L2 (hit/miss/put) | 0 / 10 / 70 | **60 / 0 / 0** |

Les 10 requêtes de collection disparaissent : elles sont servies par le cache. La requête JPQL,
elle, est toujours émise — le cache L2 **ne sert jamais** l'état des entités à une requête, il ne
sert que les chargements par identifiant et les collections.

### 6. La limite : une requête optimisée reste une requête

`JOIN FETCH` (ou l'entity graph) avec un cache L2 déjà chaud donne toujours **1 requête SQL et
0 hit L2**. Le cache ne s'interpose pas entre le JPQL et la base : le `JOIN FETCH` ramène ses lignes
du SQL, et Hibernate se contente de rafraîchir les entités déjà présentes. Pour éliminer cette
dernière requête, il faut le cache de requêtes — pas le cache d'entités.

### 7. Bonnes pratiques retenues

- **Ne jamais se fier au chronomètre** : sur un jeu de 10 lignes, un écart de quelques millisecondes
  n'est pas une preuve. Les compteurs `Statistics` sont déterministes et reproductibles.
- **Un `EntityManager` par mesure** : dans un même `EntityManager`, le cache de premier niveau
  répondrait à la place du cache de second niveau, et la mesure ne prouverait rien.
- **`evictSecondLevelCache()` avant chaque mesure à froid** : sans cela, les tests passent ou
  échouent selon l'ordre d'exécution.
- **`@Cache` sur la collection aussi** : sans `@Cache` sur le champ `livres`, la région
  `com.example.model.Auteur.livres` n'existe pas et les 10 requêtes du N+1 reviennent.
- **Le cache de requêtes est à double tranchant** : toute écriture sur une table concernée invalide
  la région, ce qui peut coûter plus cher que la requête économisée.

## Tests

```bash
mvn -B test
```

```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.AppTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.CacheRequetesTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.CacheSecondNiveauTest
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.DataInitServiceTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.ModeleTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.example.ProblemeN1Test
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Ce que chaque classe vérifie :

- **`ModeleTest`** — la relation `Auteur`/`Livre` est câblée des deux côtés (`ajouterLivre` remplit
  la collection *et* la clé étrangère), `toString()` ne déclenche pas de chargement paresseux sur une
  entité détachée, `find()` ramène bien les 5 livres d'un auteur.
- **`DataInitServiceTest`** — 10 auteurs, 50 livres, `reset()` idempotent, comptages stables.
- **`ProblemeN1Test`** — le N+1 fait exactement 11 requêtes ; `JOIN FETCH` et l'entity graph en font
  exactement 1 ; les deux ramènent les 60 entités ; aucun doublon malgré la jointure.
- **`CacheSecondNiveauTest`** — le parcours non optimisé **remplit** le cache (70 puts) sans jamais
  le lire ; une fois chaud il fait tomber le SQL de 11 à 1 et sert 60 lectures depuis le cache ;
  le cache est partagé entre deux `EntityManager` ; `evictAllRegions()` le vide réellement.
- **`CacheRequetesTest`** — la requête cacheable ne rejoue pas le SQL à la seconde passe (0 requête,
  1 hit de cache de requêtes, 70 hits L2) ; la même requête **sans** le hint rejoue le SQL ; une
  requête optimisée à cache chaud rejoue aussi le SQL ; une éviction complète remet le cache de
  requêtes à zéro.
- **`AppTest`** — la démonstration s'exécute sans exception, affiche ses 7 sections et ses
  conclusions chiffrées (`11`, `1`, `1`, `0`, `1`, `0`, `1`, `60`), et la sortie standard contient
  bien les `select` (c'est `show_sql` qui les écrit, sur **stdout** et non sur stderr).

## Résultat de la démonstration

`mvn -B exec:java` (extrait ; les durées varient d'une machine à l'autre, les compteurs non) :

```
================================================================
 TP 7 : cache de second niveau, probleme N+1, JOIN FETCH et entity graph
================================================================
--- Jeu de donnees ---
Auteurs en base : 10
Livres en base  : 50

--- 1. Sans optimisation : probleme N+1 ---
  requetes SQL (JDBC) ... 11
  entites chargees ...... 60
  cache L2 (hit/miss/put) 0 / 10 / 70

--- 2. Avec JOIN FETCH ---
  requetes SQL (JDBC) ... 1
  entites chargees ...... 60

--- 3. Avec entity graph nomme ---
  requetes SQL (JDBC) ... 1
  entites chargees ...... 60

--- 4. Cache de requetes : deux passes de la requete cacheable ---
1re lecture (cache L2 froid)
  requetes SQL (JDBC) ... 1
  entites chargees ...... 60
  cache L2 (hit/miss/put) 0 / 0 / 70
  cache de requetes ..... 0 hit(s)
2e lecture (cache L2 chaud)
  requetes SQL (JDBC) ... 0
  entites chargees ...... 0
  cache L2 (hit/miss/put) 70 / 0 / 0
  cache de requetes ..... 1 hit(s)

--- 4 bis. Contre-exemple : meme requete SANS le hint cacheable ---
2e lecture (cache L2 chaud)
  requetes SQL (JDBC) ... 1
  entites chargees ...... 60
  cache de requetes ..... 0 hit(s)

--- 5. find() par identifiant : cache froid puis cache chaud ---
  cache L2 (hit/miss/put) 0 / 1 / 1     -> requetes SQL : 1
  cache L2 (hit/miss/put) 1 / 0 / 0     -> requetes SQL : 0

--- 6. Le N+1 rejoue avec un cache de second niveau deja chaud ---
  requetes SQL (JDBC) ... 11           # 1re passe
  cache L2 (hit/miss/put) 0 / 10 / 70
  requetes SQL (JDBC) ... 1            # 2e passe
  entites chargees ...... 10
  cache L2 (hit/miss/put) 60 / 0 / 0

--- 7. Limite du cache de second niveau : requete optimisee a cache chaud ---
  requetes SQL (JDBC) ... 1
  cache L2 (hit/miss/put) 0 / 0 / 0

--- Conclusion ---
Requetes SQL sans optimisation ...... 11
Requetes SQL avec JOIN FETCH ........ 1
Requetes SQL avec entity graph ...... 1
Hits cache de requetes, 1re passe ... 0
Hits cache de requetes, 2e passe .... 1
Requetes SQL du 2e find() par id .... 0
Requetes SQL du N+1 a cache chaud ... 1
Hits cache L2 du N+1 a cache chaud .. 60
```

### Lecture des résultats

| Question | Réponse mesurée |
|---|---|
| Le N+1, c'est grave ? | 11 requêtes au lieu d'1 pour 10 auteurs. |
| `JOIN FETCH` suffit ? | Oui pour le N+1 : 1 requête. Mais le SQL est rejoué à chaque appel. |
| L'entity graph ? | Même gain, mais la stratégie vit sur l'entité et se réutilise. |
| Le cache de requêtes ? | Ramène le SQL à **0** — à condition de passer le hint `cacheable`. |
| Le cache L2 suffit-il à supprimer le N+1 ? | Oui : 11 → 1 requête, sans toucher au JPQL. |
| Le cache L2 remplace-t-il le cache de requêtes ? | **Non** : une requête JPQL ne lit jamais l'état des entités dans le L2. |
| Faut-il chronométrer ? | Non. Sur 10 lignes, seuls les compteurs `Statistics` sont probants. |

## Écarts assumés

Le TP comporte quatre défauts qui empêchent le code de l'énoncé de fonctionner tel quel. Ils sont
corrigés ici, et documentés :

| # | Ce que dit l'énoncé | Pourquoi ça ne marche pas | Ce qui est fait ici |
|---|---|---|---|
| 1 | Injection d'`EntityManager` par `@PersistenceContext` | En application *standalone* `RESOURCE_LOCAL`, personne n'injecte ce champ : il reste `null` (l'énoncé le contourne en l'assignant à la main dans `App.main`). | Les services ne reçoivent aucun `EntityManager` : chaque méthode en ouvre et en ferme un. |
| 2 | `DataInitService.initData()` ouvre sa transaction, et `App.main` l'enveloppe dans une autre | Deux propriétaires de la transaction ⇒ `IllegalStateException` / `TransactionRequiredException`. | Un seul propriétaire : le service. `App` ne gère plus aucune transaction. |
| 3 | `testPerformanceAvecCache()` relit dans le **même** `EntityManager` | Le cache de premier niveau répond le premier : la « seconde lecture » ne mesure plus le cache L2. | Chaque mesure tourne dans un `EntityManager` neuf, précédée d'un `evictSecondLevelCache()` ; le scénario a en outre été rejoué en deux passes explicites (froid puis chaud) pour être indépendant de l'ordre d'exécution. |
| 4 | `ehcache.xml` avec `<heap>` avant `<expiry>`, `<heap>` hors de `<resources>`, et aucune déclaration de `hibernate.javax.cache.uri` | Le document est rejeté par le XSD d'EhCache 3 (`cvc-complex-type.2.4.a`), et même valide il serait ignoré faute de découverte automatique. | Fichier réordonné (`<expiry>` puis `<resources><heap unit="entries">N</heap></resources>`) **et** URI déclarée dans `persistence.xml`. Vérifié : plus aucun `HHH90001006` en sortie. |

Autres adaptations, liées à l'environnement :

| Sujet | Énoncé | Projet | Raison |
|---|---|---|---|
| Hibernate | 5.4.32.Final | 5.6.15.Final | Byte Buddy de la 5.4 ne lit pas les classes JDK 21 |
| Cache L2 | `hibernate-ehcache` | `hibernate-jcache` | `hibernate-ehcache` n'existe plus au-delà de la branche 5.4 |
| EhCache | 3.8.1 | 3.10.8 | version maintenue, compatible JCache 1.1 |
| JAXB | (implicite) | exclusion de `jaxb-runtime` sur `ehcache` + `jaxb-runtime:2.3.9` | la plage de versions `[2.2,3)` d'EhCache résolvait vers des artefacts préliminaires hébergés sur des dépôts inaccessibles |
| SLF4J | — | `slf4j-nop:1.7.36` | Hibernate 5.6 journalise via JUL ; sans binding, seul un avertissement est émis |
| H2 | 1.4.200 | 2.1.214 | l'ancienne version ne démarre pas sur JDK 21 |
| Noms de régions L2 | — | `default-update-timestamps-region`, `default-query-results-region` | ce sont les noms réels en Hibernate 5.6 (et non les noms de classes) |

## Auteur

**Mohamed Dani** — [@danimohamed](https://github.com/danimohamed)

TP 7 du cours « JPA & Hibernate » (MLIAEdu, cours 28).
