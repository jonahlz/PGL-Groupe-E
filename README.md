# SafeExit

Application Java de **simulation et de guidage d'évacuation d'urgence** dans une salle de
concert, modélisée comme un graphe 2D. Les agents (spectateurs) se déplacent vers les sorties
de secours selon un routage de **Voronoï** (Dijkstra multi-sources) recalculé en temps réel.

> Projet PGL ING1-GI 2025-2026 · CY Tech
> Thématique #3 (agents dans un graphe, limitation par arête) + routage Voronoï (Thématique #1)
> Équipe GROUPE E : ZAAID Nassim, EL DANA Aida, HOPPER Kelyan, LENZ Jonah, KETTE Sidney

## Prérequis

- **JDK 21** (Eclipse Temurin recommandé)
- **Maven 3.9+**

## Compiler et lancer

```bash
# Compiler
mvn compile

# Lancer l'interface graphique JavaFX (mode évalué)
mvn javafx:run

# Lancer la version ligne de commande (test du modèle sans JavaFX)
mvn compile exec:java -Dexec.mainClass=fr.cytech.safeexit.Main -Dexec.args=--cli

# Générer la JavaDoc dans le dossier /docs
mvn javadoc:javadoc

# Lancer les tests unitaires
mvn test
```

## Architecture

MVC strict + patterns **Observer** et **Strategy**. Voir [`CONCEPTION.md`](CONCEPTION.md)
pour le document de conception complet et le diagramme de classes UML.

```
safeexit/
├── model/       données & logique métier (aucune dépendance JavaFX)
│   ├── graph/       Node, Edge, Graph, NodeType
│   ├── agent/       Agent, AgentState, BehaviorStrategy + stratégies
│   ├── venue/       ConcertHallBuilder
│   ├── routing/     VoronoiEvacuationRouter
│   ├── simulation/  SimulationEngine, SimulationClock, SimulationState
│   ├── io/          SimulationSerializer (sauvegarde binaire)
│   └── observer/    Observable, Observer, SimulationEvent
├── controller/  pont entre Model et View
└── view/        interfaces JavaFX (aucun accès direct aux données)
```
