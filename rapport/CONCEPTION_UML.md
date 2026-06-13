# SafeExit — Conception (diagrammes de classes UML)

Document de conception exigé par le cahier des charges. Les diagrammes sont écrits en **Mermaid** et s'affichent directement sur GitHub. Ils sont découpés par préoccupation pour rester lisibles.

> Convention : `<|--` héritage, `<|..` implémentation d'interface, `o--` agrégation, `-->` association/dépendance.

---

## 1. Patron Observer (cœur de la communication modèle → vue)

Chaque changement d'état du modèle émet un `SimulationEvent`. La vue, le routeur et les capteurs s'abonnent. Les observateurs sont `transient` (non sérialisés).

```mermaid
classDiagram
    class Observable {
        <<interface>>
        +addObserver(Observer)
        +removeObserver(Observer)
        +notifyObservers(SimulationEvent)
    }
    class Observer {
        <<interface>>
        +update(SimulationEvent)
    }
    class AbstractObservable {
        <<abstract>>
        -transient List~Observer~ observers
        +notifyObservers(SimulationEvent)
    }
    class SimulationEvent {
        +Type getType()
        +Object getSource()
    }
    Observable <|.. AbstractObservable
    AbstractObservable ..> SimulationEvent : émet
    Observer ..> SimulationEvent : reçoit
```

## 2. Patron Strategy (comportement des agents)

L'état d'un agent détermine sa stratégie de déplacement ; `StrategyFactory` fait la correspondance.

```mermaid
classDiagram
    class BehaviorStrategy {
        <<interface>>
        +chooseNextNode(Graph, SimulationState, Agent) Node
        +getMovementDelay() int
    }
    class AbstractBehaviorStrategy {
        <<abstract>>
    }
    class CalmEvacuationStrategy
    class PanickedEvacuationStrategy
    class FrozenStrategy
    class LeaderFollowStrategy
    class StrategyFactory {
        +fromState(AgentState)$ BehaviorStrategy
    }
    class AgentState {
        <<enumeration>>
        CALM
        PANICKED
        FROZEN
        EXITED
    }
    BehaviorStrategy <|.. AbstractBehaviorStrategy
    AbstractBehaviorStrategy <|-- CalmEvacuationStrategy
    AbstractBehaviorStrategy <|-- PanickedEvacuationStrategy
    AbstractBehaviorStrategy <|-- FrozenStrategy
    AbstractBehaviorStrategy <|-- LeaderFollowStrategy
    StrategyFactory ..> BehaviorStrategy : crée
    StrategyFactory ..> AgentState
```

## 3. Modèle du graphe et agents

```mermaid
classDiagram
    class Graph {
        +addNode(Node)
        +addEdge(Edge)
        +removeNode(Node) List~Edge~
        +getNeighbors(Node) List~Node~
    }
    class Node {
        +String id
        +double x
        +double y
        +int maxCapacity
        +boolean blocked
        +setPosition(double, double)
    }
    class Edge {
        +int maxConcurrentAgents
        +double speedModifier
        +boolean hasRoom()
        +int getAgentPassCount()
    }
    class NodeType {
        <<enumeration>>
        SEAT
        AISLE
        CORRIDOR
        CROSS_SECTION
        EXIT
        STAGE
        BLOCKED_ZONE
    }
    class Agent {
        +String id
        +double maxSpeed
        +double densityTolerance
        +setState(AgentState)
    }
    AbstractObservable <|-- Graph
    AbstractObservable <|-- Node
    AbstractObservable <|-- Edge
    AbstractObservable <|-- Agent
    Observer <|.. Agent
    Graph "1" o-- "*" Node
    Graph "1" o-- "*" Edge
    Edge --> "2" Node : source / target
    Node --> NodeType
    Agent --> Node : currentNode
    Agent --> AgentState
    Agent --> BehaviorStrategy
```

## 4. Simulation, routage et persistance

```mermaid
classDiagram
    class SimulationEngine {
        +tick()
        +triggerEvacuation()
        +triggerPanicInSector(String) int
        +calmSector(String) int
        +recomputeRoutes()
    }
    class SimulationState {
        +List~Agent~ getAgents()
        +Graph getGraph()
        +long currentCycle
    }
    class SimulationClock
    class EventPhase {
        <<enumeration>>
        NORMAL
        EVACUATION
    }
    class VoronoiEvacuationRouter {
        +computeRoutes()
    }
    class SimulationSerializer {
        +save(SimulationState, Path)$
        +load(Path)$ SimulationState
    }
    AbstractObservable <|-- SimulationEngine
    Observer <|.. SimulationEngine
    SimulationEngine --> SimulationState
    SimulationEngine --> VoronoiEvacuationRouter
    SimulationEngine --> SimulationClock
    SimulationEngine --> EventPhase
    SimulationState --> Graph
    SimulationState "1" o-- "*" Agent
    SimulationState --> AgentTracker
    VoronoiEvacuationRouter --> Graph
    SimulationSerializer ..> SimulationState : (dé)sérialise
```

## 5. Capteurs, secteurs et panneaux (système centralisé)

```mermaid
classDiagram
    class SeatSensor {
        +SeatStatus getStatus()
        +markAway()
        +markOccupied(Agent)
    }
    class SeatStatus {
        <<enumeration>>
        OCCUPIED
        AWAY
        FREE
    }
    class RowSensor {
        +int occupiedCount()
        +int awayCount()
    }
    class SensorNetwork {
        +build(Graph, List~Agent~)$ SensorNetwork
        +int totalOccupied()
    }
    class Sector {
        +double computeMovementRatio()
        +refresh()
    }
    class SectorManager {
        +build(SensorNetwork, Graph)$ SectorManager
        +setMonitoring(boolean)
    }
    class DisplayPanel {
        +broadcast(PanelMessage)
        +PanelMode getMode()
    }
    class PanelMessage {
        +standby()$
        +alert(String)$
        +evacuation(String, ArrowDirection)$
    }
    class PanelMode {
        <<enumeration>>
        STANDBY
        MONITORING
        ALERT
        DIRECTIONAL_GUIDANCE
        ROUTE_BLOCKED
    }
    AbstractObservable <|-- SeatSensor
    AbstractObservable <|-- RowSensor
    AbstractObservable <|-- Sector
    AbstractObservable <|-- DisplayPanel
    Observer <|.. RowSensor
    Observer <|.. Sector
    RowSensor o-- SeatSensor
    SensorNetwork o-- RowSensor
    Sector o-- RowSensor
    Sector --> DisplayPanel
    SectorManager o-- Sector
    DisplayPanel --> PanelMessage
    DisplayPanel --> PanelMode
    SeatSensor --> SeatStatus
```

## 6. Couches MVC (vue d'ensemble)

```mermaid
classDiagram
    class Main {
        +main(String[])$
    }
    class SafeExitApp {
        <<View / wiring>>
    }
    class GraphCanvas
    class AlertPanel
    class SectorPanelBoard
    class GraphController
    class SimulationController
    class VenueController
    Main --> SafeExitApp
    Main ..> SimulationEngine : mode --cli
    SafeExitApp --> GraphController
    SafeExitApp --> SimulationController
    SafeExitApp --> VenueController
    SafeExitApp --> GraphCanvas
    SafeExitApp --> SectorPanelBoard
    SafeExitApp --> AlertPanel
    SimulationController --> SimulationEngine
    GraphController --> SimulationEngine
    VenueController ..> SimulationState : crée la salle
    Observer <|.. AlertPanel
```

---

### Notes de conception

- **`AbstractObservable`** factorise tout le mécanisme Observer ; les classes du modèle (graphe, agents, capteurs, secteurs, moteur) en héritent. Les observateurs sont `transient` → la sérialisation ne sauvegarde que le modèle.
- **`SimulationState`** est un simple porteur de données (sérialisable) ; toute la logique vit dans **`SimulationEngine`**.
- Le **routeur Voronoï** part des sorties comme sources d'un Dijkstra multi-sources : chaque nœud connaît sa sortie la plus proche (cellule de Voronoï) et son « prochain saut ».
- La couche **capteurs → secteurs → panneaux** est entièrement pilotée par événements (`SEAT_STATE_CHANGED` → `ROW_OCCUPANCY_CHANGED` → mise à jour du secteur et de son panneau).
