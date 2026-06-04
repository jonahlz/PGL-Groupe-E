package fr.cytech.safeexit.model.agent;

import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.Observer;
import fr.cytech.safeexit.model.observer.SimulationEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A spectator moving through the concert-hall graph toward an exit.
 * <p>
 * An agent is either standing on a {@link Node} ({@code currentEdge == null}) or
 * travelling along an {@link Edge} (with {@code progressOnEdge} in
 * {@code [0, 1]}). Its {@link AgentState} drives its {@link BehaviorStrategy}:
 * changing the state through {@link #setState(AgentState)} swaps the strategy
 * automatically. A state-independent strategy (such as
 * {@link LeaderFollowStrategy}) can also be assigned with
 * {@link #setStrategy(BehaviorStrategy)}.
 * <p>
 * The agent is {@link fr.cytech.safeexit.model.observer.Observable} (it emits
 * events when it changes state) and an {@link Observer} (it can react to events,
 * keeping a personal history).
 *
 * @author GROUPE E
 * @version 1.0
 */
public class Agent extends AbstractObservable implements Observer {

    private static final long serialVersionUID = 1L;

    // Identity
    private final String id;
    private String ticketSector;
    private int seatRow;
    private int seatNumber;

    // Position
    private Node currentNode;
    private Edge currentEdge;
    private double progressOnEdge;
    private Node edgeDestination;

    // Kinematics
    private double maxSpeed;
    private double currentSpeed;

    // State and behaviour
    private AgentState state;
    private BehaviorStrategy strategy;

    // Density tolerance: 0.0 (flees crowds) to 1.0 (insensitive)
    private double densityTolerance;

    // Navigation
    private final List<Node> plannedPath;
    private Node targetExit;

    // Cooldown (in cycles) before the agent may move again (used by FROZEN)
    private int remainingDelay;

    // Statistics
    private long evacuationStartTime;
    private final List<SimulationEvent> history;

    /**
     * Creates an agent standing on the given start node, in the {@link
     * AgentState#CALM} state.
     *
     * @param id        unique identifier (e.g. "AGT_042"); never null/blank
     * @param startNode the node the agent starts on; never {@code null}
     * @throws IllegalArgumentException if {@code id} is blank or {@code startNode} is null
     */
    public Agent(String id, Node startNode) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Agent id must not be null or blank");
        }
        if (startNode == null) {
            throw new IllegalArgumentException("Agent start node must not be null");
        }
        this.id = id;
        this.currentNode = startNode;
        this.maxSpeed = 1.0;
        this.currentSpeed = 1.0;
        this.densityTolerance = 0.5;
        this.state = AgentState.CALM;
        this.strategy = StrategyFactory.fromState(AgentState.CALM);
        this.plannedPath = new ArrayList<>();
        this.history = new ArrayList<>();
        this.evacuationStartTime = System.currentTimeMillis();
        // The agent observes itself so its own state changes are logged.
        addObserver(this);
    }

    /**
     * Changes the agent state and swaps the behaviour strategy accordingly,
     * then notifies observers. Setting the same state again is a no-op.
     *
     * @param newState the new state; never {@code null}
     */
    public void setState(AgentState newState) {
        if (newState == null || newState == this.state) {
            return;
        }
        this.state = newState;
        BehaviorStrategy newStrategy = StrategyFactory.fromState(newState);
        if (newStrategy != null) {
            this.strategy = newStrategy;
            this.remainingDelay = newStrategy.getMovementDelay();
        }
        notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_STATE_CHANGED, this));
    }

    /**
     * Asks the current strategy which node to move to next.
     *
     * @param graph the venue graph
     * @param state the current simulation state
     * @return the chosen next node (may be the current node or {@code null})
     */
    public Node chooseNextNode(fr.cytech.safeexit.model.graph.Graph graph,
                               fr.cytech.safeexit.model.simulation.SimulationState state) {
        if (strategy == null) {
            return null;
        }
        return strategy.chooseNextNode(this, graph, state);
    }

    /**
     * Indicates whether the agent is allowed to move this cycle (its cooldown
     * has elapsed).
     *
     * @return {@code true} if the agent may move
     */
    public boolean canMove() {
        return remainingDelay <= 0;
    }

    /**
     * Decrements the movement cooldown by one cycle (never below zero).
     */
    public void decrementDelay() {
        if (remainingDelay > 0) {
            remainingDelay--;
        }
    }

    /**
     * Records an event in the agent's personal history.
     *
     * @param event the event to remember
     */
    public void recordHistory(SimulationEvent event) {
        if (event != null) {
            history.add(event);
        }
    }

    /**
     * Reacts to a simulation event: an agent keeps in its history the events
     * that concern it directly.
     *
     * @param event the event broadcast by the model
     */
    @Override
    public void update(SimulationEvent event) {
        if (event != null && event.getSource() == this) {
            recordHistory(event);
        }
    }

    // ---------------------------------------------------------------------
    // Getters and setters
    // ---------------------------------------------------------------------

    /**
     * Returns the unique identifier of the agent.
     *
     * @return the agent id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the ticket sector of the agent (A, B, C, D...).
     *
     * @return the ticket sector, possibly {@code null}
     */
    public String getTicketSector() {
        return ticketSector;
    }

    /**
     * Sets the ticket sector of the agent.
     *
     * @param ticketSector the ticket sector
     */
    public void setTicketSector(String ticketSector) {
        this.ticketSector = ticketSector;
    }

    /**
     * Returns the initial seat row.
     *
     * @return the seat row
     */
    public int getSeatRow() {
        return seatRow;
    }

    /**
     * Sets the initial seat row.
     *
     * @param seatRow the seat row
     */
    public void setSeatRow(int seatRow) {
        this.seatRow = seatRow;
    }

    /**
     * Returns the initial seat number.
     *
     * @return the seat number
     */
    public int getSeatNumber() {
        return seatNumber;
    }

    /**
     * Sets the initial seat number.
     *
     * @param seatNumber the seat number
     */
    public void setSeatNumber(int seatNumber) {
        this.seatNumber = seatNumber;
    }

    /**
     * Returns the node the agent stands on, or {@code null} if it is on an edge.
     *
     * @return the current node
     */
    public Node getCurrentNode() {
        return currentNode;
    }

    /**
     * Sets the node the agent stands on.
     *
     * @param currentNode the new current node
     */
    public void setCurrentNode(Node currentNode) {
        this.currentNode = currentNode;
    }

    /**
     * Returns the edge the agent is travelling on, or {@code null} if it is on
     * a node.
     *
     * @return the current edge
     */
    public Edge getCurrentEdge() {
        return currentEdge;
    }

    /**
     * Sets the edge the agent is travelling on.
     *
     * @param currentEdge the new current edge
     */
    public void setCurrentEdge(Edge currentEdge) {
        this.currentEdge = currentEdge;
    }

    /**
     * Returns the node the agent is heading to while travelling on an edge.
     *
     * @return the edge destination node, or {@code null} if on a node
     */
    public Node getEdgeDestination() {
        return edgeDestination;
    }

    /**
     * Sets the node the agent is heading to while travelling on an edge.
     *
     * @param edgeDestination the destination node
     */
    public void setEdgeDestination(Node edgeDestination) {
        this.edgeDestination = edgeDestination;
    }

    /**
     * Returns the progress along the current edge, in {@code [0, 1]}.
     *
     * @return the progress on the edge
     */
    public double getProgressOnEdge() {
        return progressOnEdge;
    }

    /**
     * Sets the progress along the current edge.
     *
     * @param progressOnEdge the new progress, clamped to {@code [0, 1]}
     */
    public void setProgressOnEdge(double progressOnEdge) {
        this.progressOnEdge = Math.max(0.0, Math.min(1.0, progressOnEdge));
    }

    /**
     * Returns the maximum speed of the agent (in nodes per cycle).
     *
     * @return the maximum speed
     */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Sets the maximum speed of the agent.
     *
     * @param maxSpeed the new maximum speed (must be &gt; 0)
     */
    public void setMaxSpeed(double maxSpeed) {
        if (maxSpeed <= 0) {
            throw new IllegalArgumentException("Max speed must be positive");
        }
        this.maxSpeed = maxSpeed;
    }

    /**
     * Returns the current speed of the agent (may vary with congestion).
     *
     * @return the current speed
     */
    public double getCurrentSpeed() {
        return currentSpeed;
    }

    /**
     * Sets the current speed of the agent.
     *
     * @param currentSpeed the new current speed (must be &gt;= 0)
     */
    public void setCurrentSpeed(double currentSpeed) {
        this.currentSpeed = Math.max(0.0, currentSpeed);
    }

    /**
     * Returns the current state of the agent.
     *
     * @return the agent state
     */
    public AgentState getState() {
        return state;
    }

    /**
     * Returns the current behaviour strategy of the agent.
     *
     * @return the strategy, or {@code null} if the agent has exited
     */
    public BehaviorStrategy getStrategy() {
        return strategy;
    }

    /**
     * Assigns a behaviour strategy explicitly, independently of the state (used
     * for state-independent strategies such as {@link LeaderFollowStrategy}).
     *
     * @param strategy the strategy to assign; ignored if {@code null}
     */
    public void setStrategy(BehaviorStrategy strategy) {
        if (strategy != null) {
            this.strategy = strategy;
            this.remainingDelay = strategy.getMovementDelay();
        }
    }

    /**
     * Returns the density tolerance of the agent (0.0 flees crowds, 1.0
     * insensitive).
     *
     * @return the density tolerance
     */
    public double getDensityTolerance() {
        return densityTolerance;
    }

    /**
     * Sets the density tolerance of the agent.
     *
     * @param densityTolerance the new tolerance, clamped to {@code [0, 1]}
     */
    public void setDensityTolerance(double densityTolerance) {
        this.densityTolerance = Math.max(0.0, Math.min(1.0, densityTolerance));
    }

    /**
     * Returns the planned path (the remaining route toward the exit). The
     * returned list is the live list and may be modified by the router.
     *
     * @return the planned path
     */
    public List<Node> getPlannedPath() {
        return plannedPath;
    }

    /**
     * Replaces the planned path with a new one.
     *
     * @param path the new planned path (ignored if {@code null})
     */
    public void setPlannedPath(List<Node> path) {
        plannedPath.clear();
        if (path != null) {
            plannedPath.addAll(path);
        }
    }

    /**
     * Returns the exit currently assigned to this agent.
     *
     * @return the target exit, or {@code null} if none
     */
    public Node getTargetExit() {
        return targetExit;
    }

    /**
     * Sets the exit assigned to this agent.
     *
     * @param targetExit the new target exit
     */
    public void setTargetExit(Node targetExit) {
        this.targetExit = targetExit;
    }

    /**
     * Returns the timestamp at which the agent started evacuating.
     *
     * @return the evacuation start time (milliseconds since epoch)
     */
    public long getEvacuationStartTime() {
        return evacuationStartTime;
    }

    /**
     * Sets the evacuation start time.
     *
     * @param evacuationStartTime the start time in milliseconds since epoch
     */
    public void setEvacuationStartTime(long evacuationStartTime) {
        this.evacuationStartTime = evacuationStartTime;
    }

    /**
     * Returns the agent's event history.
     *
     * @return the list of recorded events
     */
    public List<SimulationEvent> getHistory() {
        return history;
    }

    /**
     * Indicates whether the agent has reached an exit and left the simulation.
     *
     * @return {@code true} if the state is {@link AgentState#EXITED}
     */
    public boolean hasExited() {
        return state == AgentState.EXITED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return (o instanceof Agent other) && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + "[" + state + "@" + (currentNode != null ? currentNode : currentEdge) + "]";
    }
}
