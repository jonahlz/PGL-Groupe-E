# SafeExit — Document de conception

> Projet PGL ING1-GI 2025-2026 · CY Tech
> Équipe : ZAAID Nassim, EL DANA Aida, HOPPER Kelyan, LENZ Jonah, KETTE Sidney
> Thématique #3 (agents dans un graphe, limitation par arête) + routage Voronoï (Thématique #1)

---

## 1. Vue d'ensemble

SafeExit simule l'évacuation d'urgence d'une salle de concert modélisée comme un **graphe 2D**.
Des agents (spectateurs) se déplacent vers les sorties de secours. Le routage utilise une
**partition de Voronoï sur le graphe** (sortie la plus proche en distance de graphe), recalculée
en temps réel quand une sortie ou une arête se bloque.

### Décisions structurantes (validées)

| Sujet | Choix | Justification |
|---|---|---|
| Build | **Maven** + `javafx-maven-plugin` | `mvn javafx:run`, `mvn javadoc:javadoc` → dossier `/docs` exigé |
| Gestion des arêtes | **Option 1 — vérification avant entrée** | Pas de collision à gérer ; agent bloqué → état `FROZEN` (2 cycles) |
| Routage / Voronoï | **Dijkstra multi-sources** depuis les sorties | Recalcul temps réel facile, démontrable au jury |
| Architecture | **MVC + Observer + Strategy** | Sépare strictement modèle/vue → version CLI quasi gratuite |
| Threading | **mono-thread logique** | Évite tout crash de concurrence (très pénalisé) |

---

## 2. Architecture en couches

```
        ┌──────────────┐   appelle    ┌──────────────┐
        │  CONTROLLER  │ ───────────▶ │    MODEL     │
        └──────────────┘              └──────────────┘
               ▲                             │
   la view appelle le controller     notifie (Observer)
               │                             ▼
        ┌──────────────┐  s'abonne    ┌──────────────┐
        │     VIEW     │ ◀─────────── │ SimulationEvent│
        └──────────────┘              └──────────────┘
```

**Règle d'or :** le `model` ne référence **jamais** JavaFX. Il émet des `SimulationEvent`.
La `view` s'abonne. La version **ligne de commande** branche un `Observer` qui écrit dans la
console à la place du rendu graphique → prouve la séparation des couches.

### Arborescence des packages

```
safeexit/
├── Main.java                  ← parse args : --cli ou GUI
├── SafeExitApp.java           ← point d'entrée JavaFX
├── cli/
│   └── SafeExitCLI.java       ← REPL ligne de commande (obligatoire)
├── model/
│   ├── graph/      Node, Edge, Graph, NodeType
│   ├── agent/      Agent, AgentState, BehaviorStrategy + 4 stratégies, StrategyFactory
│   ├── venue/      ConcertHallBuilder
│   ├── routing/    VoronoiEvacuationRouter (Dijkstra multi-sources)
│   ├── simulation/ SimulationEngine, SimulationClock, SimulationState
│   ├── io/         SimulationSerializer (binaire)
│   └── observer/   Observable, Observer, SimulationEvent
├── controller/     GraphController, AgentController, SimulationController, VenueController
└── view/
    ├── graph/      GraphCanvas
    ├── dashboard/  SupervisorDashboard, AlertPanel, StatsPanel
    ├── spectator/  SpectatorView
    └── controls/   SimulationControlBar
```

---

## 3. Modèle métier — décisions clés

### 3.1 Position d'un agent
Un agent est **soit dans un nœud** (`currentEdge == null`), **soit sur une arête**
(`progressOnEdge ∈ [0,1]`). Le déplacement *dans* un nœud est instantané (cf. cahier) ;
les goulots d'étranglement sont **sur les arêtes**.

### 3.2 Gestion des arêtes (Option 1)
Avant d'entrer sur une arête, l'agent vérifie :
`edge.currentAgentCount(sens confondu) < edge.maxConcurrentAgents` **et** l'arête/le nœud cible
ne sont pas bloqués. Sinon → l'agent passe `FROZEN` pour 2 cycles puis réessaie (éventuellement
en recalculant sa route).

### 3.3 Congestion d'un nœud
Si `currentAgentCount > maxCapacity` (déplacement forcé après suppression d'un nœud voisin) le nœud
passe en **« forte congestion »** : les agents mettent **2 cycles** à pouvoir entrer dans une arête
tant que le compte n'est pas redescendu.

### 3.4 Routage Voronoï (Dijkstra multi-sources)
```
computeOptimalRoutes(graph, exits):
    PQ ← toutes les sorties non bloquées, distance 0
    tant que PQ non vide:
        u ← extraire min
        pour chaque voisin v via arête non bloquée:
            relax(dist[v], dist[u] + poids(arête))   # poids = longueur × (1/speedModifier)
    retourne pour chaque nœud : la sortie « racine » de son plus court chemin
```
- La **partition de Voronoï** émerge naturellement : chaque nœud est rattaché à la sortie dont il
  est le plus proche en distance de graphe.
- **Recalcul incrémental** : sur `EDGE_BLOCKED` / `NODE_STATE_CHANGED`, on relance le calcul
  (au début global, optimisation possible plus tard sur les zones affectées).
- Le **poids** peut intégrer la congestion → plus court *chemin en temps* (option du cahier).

### 3.5 États & Strategy
Le changement d'état remplace automatiquement la stratégie via `StrategyFactory.fromState(state)`.

| État | Strategy | Comportement |
|---|---|---|
| `CALM` | `CalmEvacuationStrategy` | suit la route Voronoï optimale, cède le passage |
| `PANICKED` | `PanickedEvacuationStrategy` | fonce vers la sortie la plus proche (distance brute), ignore la congestion |
| `FROZEN` | `FrozenStrategy` | bloqué, attend 2 cycles |
| `EXITED` | — | a atteint une sortie, retiré de la simulation |

---

## 4. Boucle de simulation — `SimulationEngine.tick()`

```
1. pour chaque agent actif (CALM | PANICKED) :
     a. node ← agent.strategy.chooseNextNode(agent, graph, state)
     b. si l'arête vers node a de la place (Option 1) :
            engager l'agent sur l'arête, émettre AGENT_MOVED
        sinon :
            agent.setState(FROZEN)  → émet AGENT_STATE_CHANGED
     c. avancer progressOnEdge selon currentSpeed × speedModifier
     d. si arrivé à un nœud EXIT : agent.setState(EXITED), émettre AGENT_REACHED_EXIT
2. mettre à jour densités des nœuds
3. vérifier seuils → émettre DENSITY_ALERT si dépassement
4. si un nœud/arête a été bloqué ce tick → router.recompute() → émettre ROUTE_RECALCULATED
```

Pilotage : `Timeline` JavaFX (GUI) ou boucle simple (CLI), **intervalle réglable**, + Pause / Play / Step.

---

## 5. Diagramme de classes (PlantUML — pour le rapport)

```plantuml
@startuml SafeExit
skinparam classAttributeIconSize 0

' ---------- OBSERVER ----------
interface Observable {
  +addObserver(o: Observer)
  +removeObserver(o: Observer)
  +notifyObservers(e: SimulationEvent)
}
interface Observer {
  +update(e: SimulationEvent)
}
class SimulationEvent {
  -type: Type
  -source: Object
  -timestamp: long
}
enum SimulationEvent.Type {
  NODE_STATE_CHANGED
  EDGE_BLOCKED
  AGENT_MOVED
  AGENT_STATE_CHANGED
  AGENT_REACHED_EXIT
  DENSITY_ALERT
  EVACUATION_TRIGGERED
  ROUTE_RECALCULATED
}

' ---------- GRAPH ----------
class Graph {
  -nodes: List<Node>
  -edges: List<Edge>
  +addNode(n) / removeNode(n)
  +addEdge(e) / removeEdge(e)
  +neighbors(n): List<Node>
}
class Node {
  -id: String
  -type: NodeType
  -x, y: double
  -maxCapacity: int
  -blocked: boolean
  -currentAgentCount: int
  +hasCapacity(): boolean
}
class Edge {
  -source, target: Node
  -directed: boolean
  -length: double
  -maxConcurrentAgents: int
  -speedModifier: double
  -blocked: boolean
  +hasRoom(): boolean
}
enum NodeType { SEAT AISLE CORRIDOR CROSS_SECTION EXIT STAGE BLOCKED_ZONE }

' ---------- AGENT + STRATEGY ----------
class Agent {
  -id: String
  -currentNode: Node
  -currentEdge: Edge
  -progressOnEdge: double
  -maxSpeed: double
  -state: AgentState
  -strategy: BehaviorStrategy
  -densityTolerance: double
  -targetExit: Node
  +setState(s: AgentState)
}
enum AgentState { CALM PANICKED FROZEN EXITED }
interface BehaviorStrategy {
  +chooseNextNode(a, g, s): Node
  +getMovementDelay(): int
  +shouldAvoidDensity(): boolean
}
class CalmEvacuationStrategy
class PanickedEvacuationStrategy
class FrozenStrategy
class LeaderFollowStrategy
class StrategyFactory {
  +{static} fromState(s: AgentState): BehaviorStrategy
}

' ---------- SIMULATION ----------
class SimulationEngine {
  -graph: Graph
  -agents: List<Agent>
  -router: VoronoiEvacuationRouter
  -paused, stepMode: boolean
  +tick()
}
class VoronoiEvacuationRouter {
  +computeOptimalRoutes(g, exits): Map<Node,Node>
  +onObstacleDetected(e: SimulationEvent)
}
class ConcertHallBuilder {
  +buildHall(rows, seatsPerRow, exitCount): Graph
  +loadFromFile(path): Graph
}
class SimulationSerializer {
  +save(state, path)
  +load(path): SimulationState
}

' ---------- RELATIONS ----------
Observable <|.. Agent
Observable <|.. SimulationEngine
Observer <|.. GraphCanvas
Observer <|.. Agent
Graph "1" o-- "*" Node
Graph "1" o-- "*" Edge
Edge "*" --> "2" Node
Node --> NodeType
Agent --> AgentState
Agent --> BehaviorStrategy
BehaviorStrategy <|.. CalmEvacuationStrategy
BehaviorStrategy <|.. PanickedEvacuationStrategy
BehaviorStrategy <|.. FrozenStrategy
BehaviorStrategy <|.. LeaderFollowStrategy
StrategyFactory ..> BehaviorStrategy
SimulationEngine o-- Graph
SimulationEngine o-- "*" Agent
SimulationEngine o-- VoronoiEvacuationRouter
SimulationEngine ..> SimulationEvent
@enduml
```

---

## 6. Conformité au cahier des charges (checklist)

- [x] Code **100% anglais** (variables, classes, commentaires)
- [x] **JavaDoc** sur classes/méthodes publiques → `mvn javadoc:javadoc` → `/docs`
- [x] **Aucune exception non gérée** : try/catch sur opérations critiques, alertes en vue
- [x] **Version ligne de commande** indépendante de JavaFX
- [x] **Import/Export binaire** (`Serializable` + `SimulationSerializer`)
- [x] Ajout/suppression/déplacement nœuds, arêtes, agents **en cours de simulation**
- [x] Ajout **en masse** (X aléatoires) avec plages réglables
- [x] Déplacement des agents lors d'une suppression de nœud/arête
- [x] Forte congestion (2 cycles)
- [x] Stats par nœud/arête + **heatmap** de densité
- [x] Sélection d'un agent → visualisation du trajet restant
- [x] Vitesse réglable, **Pause / Play / Step**
- [x] **≥ 1 commit/jour** sur Git public

---

## 7. Plan d'implémentation suggéré (≈ 8 jours restants)

1. **J1** — Bootstrap Maven + Git + interfaces Observer/Strategy + `Node/Edge/Graph`.
2. **J2** — `Agent`, `AgentState`, les 4 stratégies, `StrategyFactory`.
3. **J3** — `SimulationEngine.tick()` + `VoronoiEvacuationRouter` (Dijkstra) + **CLI testable**.
4. **J4** — `ConcertHallBuilder` (salle réaliste) + `SimulationSerializer` (binaire).
5. **J5-6** — `GraphCanvas` JavaFX (rendu, zoom/pan, sélection) + barre de contrôle.
6. **J7** — Vue superviseur (dashboard, contrôle sorties, heatmap) + vue spectateur.
7. **J8** — Stabilisation, gestion d'erreurs, JavaDoc `/docs`, rapport + UML, relecture sans IA.
```
