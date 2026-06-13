# SafeExit — Rapport de projet

**Projet PGL — ING1-GI — CY Tech — 2025-2026**
**Dépôt :** <https://github.com/jonahlz/PGL-Groupe-E>

> Documents de conception associés (exigés par le cahier des charges) :
> - [Diagramme de classes (UML)](CONCEPTION_UML.md)
> - [Cas d'utilisation](CAS_UTILISATION.md)
> - JavaDoc générée : dossier [`/docs`](../docs/index.html) (ouvrir `docs/index.html`)

---

## 1. Équipe et sujet

**Équipe :** ZAAID Nassim, EL DANA Aida, HOPPER Kelyan, LENZ Jonah, KETTE Sidney.

**SafeExit** est une application **Java 21 / JavaFX** de **simulation et de supervision d'une évacuation d'urgence** dans une salle de concert, modélisée comme un **graphe 2D** (nœuds = sièges, allées, couloirs, sorties ; arêtes = chemins avec une capacité limitée). Le routage d'évacuation utilise un algorithme de **Voronoï / Dijkstra multi-sources** recalculé en temps réel.

Notre travail en **Éthique** porte sur la **sécurité des personnes lors d'événements de grande affluence** : comment évacuer une foule rapidement et équitablement, sans créer de mouvement de panique ni laisser de zone livrée à elle-même. Le périmètre **Design** vise un **poste de supervision clair et non anxiogène** destiné à un opérateur de salle : visualisation de l'occupation en temps réel, alertes lisibles par secteur et actions simples et sûres (bloquer une sortie, déclencher une évacuation). SafeExit est la traduction logicielle de ce poste de supervision.

## 2. Contexte et périmètre (pivot v2)

Après retour de la tutrice, l'équipe a **abandonné** le guidage individuel par smartphone au profit d'un **système centralisé et physique**, plus réaliste et mieux maîtrisable :

> capteurs de siège → capteurs de rangée → secteurs → **panneaux d'affichage physiques** → **tableau de bord superviseur**.

Conformément au cahier des charges (« il faut adapter ce cahier des charges à votre périmètre Éthique/Design » et « préférable d'avoir une application stable avec moins de fonctionnalités »), nous avons privilégié un **cœur réduit mais robuste**. Les choix de périmètre sont détaillés en section 5.

## 3. Architecture logicielle (MVC)

L'application respecte une séparation **Modèle-Vue-Contrôleur stricte** :

```
src/main/java/fr/cytech/safeexit/
├── model/        (aucune dépendance JavaFX)
│   ├── graph/        Node, NodeType, Edge, Graph, GraphException
│   ├── agent/        Agent, AgentState, BehaviorStrategy (+ 4 stratégies), StrategyFactory
│   ├── observer/     Observable, Observer, AbstractObservable, SimulationEvent
│   ├── routing/      VoronoiEvacuationRouter (Dijkstra multi-sources)
│   ├── sensor/       SeatSensor, SeatStatus, RowSensor, SensorNetwork
│   ├── sector/       PanelMode, PanelMessage, DisplayPanel, Sector, SectorManager
│   ├── simulation/   SimulationEngine, SimulationState, SimulationClock, EventPhase, SimulationSerializer
│   ├── tracking/     PositionRecord, AgentTracker
│   └── venue/        ConcertHallBuilder
├── view/         (JavaFX uniquement, aucune logique métier)
│   ├── graph/        GraphCanvas
│   └── dashboard/    AlertPanel, SectorPanelBoard
└── controller/   GraphController, SimulationController, VenueController
```

**Règle d'or respectée :** aucun `import javafx.*` dans `model/`, aucune logique métier dans `view/`. Le modèle communique avec la vue **uniquement par événements** (patron Observer), ce qui garantit la testabilité du modèle en ligne de commande.

## 4. Patrons de conception

| Patron | Où | Rôle |
|---|---|---|
| **Observer** | `Observable`/`Observer`/`AbstractObservable`, `SimulationEvent` | Tout changement d'état du modèle émet un `SimulationEvent` ; la vue et le routeur réagissent sans couplage fort. |
| **Strategy** | `BehaviorStrategy` + `CalmEvacuationStrategy`, `PanickedEvacuationStrategy`, `FrozenStrategy`, `LeaderFollowStrategy` | Le comportement de déplacement d'un agent dépend de son état ; changer l'état change la stratégie. |
| **Factory** | `StrategyFactory`, `ConcertHallBuilder`, `SectorManager.build(...)` | Construction centralisée des stratégies, de la salle et de la couche secteurs/panneaux. |

Voir le [diagramme de classes](CONCEPTION_UML.md) pour le détail des relations.

## 5. Fonctionnalités vis-à-vis du cahier des charges

### Implémentées
| Exigence | Réalisation |
|---|---|
| Version **ligne de commande** + version **JavaFX** | `Main --cli`, `Main --sensors`, et le dashboard JavaFX |
| Simulation temporelle : **vitesse réglable, pas-à-pas, pause/play** | Barre de contrôle, `SimulationController` |
| Politique de **capacité d'arête** (« vérifier la place avant d'entrer ») | `Edge.hasRoom()`, gel de l'agent (`FROZEN`) sinon |
| **Plus court chemin / temps** | `VoronoiEvacuationRouter` (Dijkstra multi-sources depuis les sorties) |
| Propriétés des nœuds / arêtes / agents | capacité, blocage, attractivité ; longueur, sens, largeur, modificateur de vitesse ; vitesse, comportement, état, tolérance à la densité |
| **Visualisation de la densité** (gradient de couleurs) | `GraphCanvas` mode `DENSITY` |
| **Sauvegarde / restauration binaire** | `SimulationSerializer` + boutons *Sauvegarder / Charger* |
| Gestion des erreurs, **aucune exception non gérée** | validations, `try/catch`, dialogues d'erreur |
| Code **anglais + JavaDoc**, packages modulaires | dossier `docs/` généré |

### Fonctionnalités propres à notre périmètre (centralisé)
- Capteurs de siège/rangée et **secteurs avec panneaux d'affichage** (modes : NORMAL, SURVEILLANCE, ALERTE, GUIDAGE, BLOQUÉ).
- **Déplacements spontanés animés** en phase normale (un spectateur quitte sa place, marche, revient) et **sortie temporaire par une issue** (toilettes/bar) puis retour.
- **Scénarios manuels** : bloquer une sortie, **paniquer / calmer un secteur**.
- **Suivi des déplacements** (`AgentTracker`) : trajectoires, heatmap, nœud le plus fréquenté.
- À l'évacuation, les panneaux passent en **GUIDAGE** vers la sortie assignée par le Voronoï.

### Écartées volontairement (et justification)
Conformément à la clause d'adaptation au périmètre et à la priorité « stabilité > exhaustivité », nous avons **écarté l'édition générique du graphe** (ajout/déplacement/suppression libres de nœuds, d'arêtes et d'agents, ajout de masse aléatoire). Dans notre contexte — une **salle de concert réelle au plan fixe** — ces actions n'ont pas de sens métier ; nous proposons à la place des **interactions ciblées** (bloquer une sortie, paniquer/calmer un secteur) cohérentes avec un poste de supervision. Ce recentrage (pivot v2) a été décidé après retour de notre tutrice, qui nous a orientés vers un système centralisé et physique plutôt qu'un guidage individuel par smartphone.

## 6. Problèmes rencontrés et solutions

> Section technique réelle ; **complétez-la avec vos propres anecdotes d'équipe.**

- **Sérialisation sûre.** Sérialiser l'état entraîne tout le graphe d'objets. Les observateurs appartiennent à la vue et ne doivent pas être sauvegardés : ils sont déclarés `transient` dans `AbstractObservable`, qui **recrée paresseusement** la liste au rechargement (`getObservers()`), évitant tout `NullPointerException`. Validé par un test de round-trip en ligne de commande.
- **Fausses alertes de congestion.** Une salle pleine mais **assise** déclenchait l'alerte « zone dense ». Nous avons remplacé le critère « taux de sièges occupés » par un **taux de mouvement** (spectateurs hors de leur siège) : au repos l'alerte est nulle, elle ne monte que lors d'un vrai mouvement de foule ou d'une panique.
- **Panneaux incohérents pendant l'évacuation.** Les panneaux revenaient au message « Bienvenue » à mesure que la salle se vidait. Solution : à l'évacuation, on **coupe la surveillance de congestion** et on diffuse un message de **GUIDAGE** vers la sortie assignée.
- **Affichage trop zoomé.** Le canvas dessinait à l'échelle 1:1 ancrée à l'origine. Ajout d'un **zoom-to-fit** (`GraphCanvas.fitToView`) et d'une fenêtre **maximisée** + barre de contrôle **adaptative** (`FlowPane`).
- **Conflits Git sur les fichiers centraux.** Le moteur (`SimulationEngine`) étant modifié par plusieurs personnes en parallèle, nous avons connu des conflits de fusion récurrents. Nous les avons limités en **répartissant les responsabilités par package** et en intégrant plus souvent (commits courts et fréquents, points d'intégration réguliers sur `main`).

## 7. Résultats et validation

Le modèle a été validé **indépendamment de l'interface** via des démos en ligne de commande. Exemple (`--cli`, salle 6×10, sortie S4 bloquée) : **évacuation complète en 31 cycles pour 60 agents**, sans compteur d'occupation négatif, avec **1146 positions enregistrées** par le suivi de déplacements. Le round-trip de sérialisation restitue à l'identique le nombre d'agents, l'occupation, le cycle et les secteurs (aucune exception). L'application graphique s'exécute via `mvn javafx:run` et permet de dérouler un scénario complet : normal → promenade/sortie par une issue → panique d'un secteur → évacuation (panneaux en guidage) → sauvegarde/chargement.

*(Des captures d'écran du tableau de bord peuvent être ajoutées ici pour la version imprimée.)*

## 8. Organisation de l'équipe et flux de travail

- Dépôt Git centralisé sur GitHub, **≥ 1 commit par jour**, intégration continue sur `main`.
- Communication d'équipe sur Discord et points d'avancement réguliers avec la tutrice.
- Répartition des responsabilités principales par module :

| Membre | Responsabilités principales |
|---|---|
| ZAAID Nassim | Modèle du graphe (`graph/`) et construction de la salle (`venue/`) |
| EL DANA Aida | Couche capteurs et secteurs (`sensor/`, `sector/`) |
| HOPPER Kelyan | Routage Voronoï (`routing/`) et moteur de simulation (`simulation/`) |
| LENZ Jonah | Interface JavaFX (`view/`, `SafeExitApp`) et contrôleurs (`controller/`) |
| KETTE Sidney | Agents et stratégies (`agent/`), suivi des déplacements (`tracking/`), sérialisation |

> *Le travail étant collaboratif, ces responsabilités indiquent les contributions principales ; chaque membre est intervenu sur plusieurs modules.*

## 9. Compiler et lancer le projet

```bash
git clone https://github.com/jonahlz/PGL-Groupe-E.git
cd PGL-Groupe-E
mvn javafx:run            # application graphique (évaluée)
mvn exec:java -Dexec.args=--cli      # démo évacuation en ligne de commande
mvn exec:java -Dexec.args=--sensors  # démo capteurs / phase normale
mvn javadoc:javadoc       # régénère la JavaDoc dans docs/
```

*(Prérequis : JDK 21 et Maven. Maven télécharge automatiquement JavaFX 21.)*

---

*Rapport — GROUPE E — 2025-2026.*
