package fr.cytech.safeexit.model.simulation;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.agent.AgentState;
import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.Observer;
import fr.cytech.safeexit.model.observer.SimulationEvent;
import fr.cytech.safeexit.model.routing.VoronoiEvacuationRouter;
import fr.cytech.safeexit.model.sensor.SeatSensor;
import fr.cytech.safeexit.model.sensor.SensorNetwork;

import java.util.List;
import java.util.Random;

/**
 * Drives the simulation one cycle at a time.
 * <p>
 * Each call to {@link #tick()} advances every active agent by one step,
 * applying the "check before entering" capacity policy: an agent only steps
 * onto an edge if it still has room, otherwise it freezes for a couple of
 * cycles. Movement inside a node is instantaneous; bottlenecks happen on the
 * edges. The engine notifies observers of the events it produces and recomputes
 * the Voronoi routes when an obstacle appears.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SimulationEngine extends AbstractObservable implements Observer {

    /** Scales pixel distances into per-cycle progress. */
    private static final double SPEED_SCALE = 25.0;

    private final SimulationState state;
    private final Graph graph;
    private final VoronoiEvacuationRouter router;
    private final SimulationClock clock;

    private boolean paused;
    private boolean routesDirty;
    private EventPhase phase = EventPhase.NORMAL;
    private final Random random = new Random();

    /**
     * Creates an engine for the given simulation state and computes the initial
     * routes.
     *
     * @param state the simulation state (graph and agents); never {@code null}
     */
    public SimulationEngine(SimulationState state) {
        if (state == null) {
            throw new IllegalArgumentException("Engine requires a simulation state");
        }
        this.state = state;
        this.graph = state.getGraph();
        this.router = new VoronoiEvacuationRouter(graph, state);
        this.clock = new SimulationClock();
        attachToModel();
        recomputeRoutes();
    }

    /**
     * Subscribes the engine to the graph and to every node and edge so it is
     * notified when something is blocked or unblocked.
     */
    private void attachToModel() {
        graph.addObserver(this);
        for (Node node : graph.getNodes()) {
            node.addObserver(this);
        }
        for (Edge edge : graph.getEdges()) {
            edge.addObserver(this);
        }
    }

    /**
     * Recomputes the Voronoi routes and refreshes each agent's assigned exit.
     */
    public void recomputeRoutes() {
        router.computeRoutes();
        for (Agent agent : state.getAgents()) {
            if (agent.getCurrentNode() != null) {
                agent.setTargetExit(state.getAssignedExit(agent.getCurrentNode()));
            }
        }
        routesDirty = false;
        notifyObservers(new SimulationEvent(SimulationEvent.Type.ROUTE_RECALCULATED, this));
    }

    /**
     * Advances the simulation by one cycle.
     */
    public void tick() {
        if (paused) {
            return;
        }
        if (routesDirty) {
            recomputeRoutes();
        }
        if (phase == EventPhase.NORMAL) {
            // Event in progress: only monitor the venue (ambient occupancy changes).
            ambientTick();
        } else {
            // Emergency: move every agent toward the exits.
            for (Agent agent : state.getAgents()) {
                stepAgent(agent);
            }
        }
        checkDensityAlerts();
        state.incrementCycle();
    }

    /**
     * Simulates the living venue during the {@link EventPhase#NORMAL} phase:
     * once in a while a seated spectator leaves their seat (toilets...) and
     * another comes back, so the supervisor sees occupancy change in real time.
     */
    private void ambientTick() {
        SensorNetwork network = state.getSensorNetwork();
        if (network == null || state.getCurrentCycle() % 4 != 0) {
            return;
        }
        List<SeatSensor> sensors = network.getSeatSensors();
        if (sensors.isEmpty()) {
            return;
        }
        // One spectator leaves their seat for a moment.
        SeatSensor leaving = sensors.get(random.nextInt(sensors.size()));
        if (leaving.isOccupied()) {
            leaving.markAway();
        }
        // One spectator returns to their seat.
        SeatSensor returning = sensors.get(random.nextInt(sensors.size()));
        if (returning.getStatus() == fr.cytech.safeexit.model.sensor.SeatStatus.AWAY) {
            returning.markOccupied(returning.getOccupant());
        }
    }

    /**
     * Triggers the emergency evacuation: switches to the evacuation phase and
     * notifies observers. Seated spectators (even those marked away) now head
     * for the exits.
     */
    public void triggerEvacuation() {
        if (phase == EventPhase.EVACUATION) {
            return;
        }
        phase = EventPhase.EVACUATION;
        notifyObservers(new SimulationEvent(SimulationEvent.Type.EVACUATION_TRIGGERED, this));
    }

    /**
     * Returns the current event phase.
     *
     * @return the phase
     */
    public EventPhase getPhase() {
        return phase;
    }

    /**
     * Indicates whether an evacuation is in progress.
     *
     * @return {@code true} if the phase is {@link EventPhase#EVACUATION}
     */
    public boolean isEvacuating() {
        return phase == EventPhase.EVACUATION;
    }

    /**
     * Advances a single agent by one cycle.
     *
     * @param agent the agent to move
     */
    private void stepAgent(Agent agent) {
        if (agent.hasExited()) {
            return;
        }
        // Agent currently travelling along an edge: advance its progress.
        if (agent.getCurrentEdge() != null) {
            advanceOnEdge(agent);
            return;
        }
        // Frozen agent: wait, then retry by becoming calm again.
        if (agent.getState() == AgentState.FROZEN) {
            if (agent.canMove()) {
                agent.setState(AgentState.CALM);
            } else {
                agent.decrementDelay();
            }
            return;
        }
        // Agent standing on a node: decide where to go and try to enter an edge.
        Node current = agent.getCurrentNode();
        Node next = agent.chooseNextNode(graph, state);
        if (next == null || next.equals(current)) {
            return;
        }
        Edge edge = graph.getEdgeBetween(current, next);
        if (edge == null) {
            return;
        }
        if (edge.hasRoom() && !next.isBlocked()) {
            enterEdge(agent, current, edge, next);
        } else {
            // No room: wait a couple of cycles (heavy congestion).
            agent.setState(AgentState.FROZEN);
        }
    }

    /**
     * Makes an agent leave its node and step onto an edge.
     *
     * @param agent   the agent
     * @param from    the node it is leaving
     * @param edge    the edge it enters
     * @param toNode  the node at the other end of the edge
     */
    private void enterEdge(Agent agent, Node from, Edge edge, Node toNode) {
        from.decrementAgentCount();
        // Free the seat sensor in real time when its occupant leaves.
        if (from.getType() == fr.cytech.safeexit.model.graph.NodeType.SEAT
                && state.getSensorNetwork() != null) {
            state.getSensorNetwork().onAgentLeftSeat(from);
        }
        edge.onAgentEnter();
        agent.setCurrentNode(null);
        agent.setCurrentEdge(edge);
        agent.setEdgeDestination(toNode);
        agent.setProgressOnEdge(0.0);
        notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
    }

    /**
     * Advances an agent already travelling on an edge, handling arrival.
     *
     * @param agent the agent
     */
    private void advanceOnEdge(Agent agent) {
        Edge edge = agent.getCurrentEdge();
        double increment = (agent.getCurrentSpeed() * edge.getSpeedModifier() * SPEED_SCALE)
                / Math.max(1.0, edge.getLength());
        double progress = agent.getProgressOnEdge() + increment;

        if (progress < 1.0) {
            agent.setProgressOnEdge(progress);
            notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
            return;
        }

        // Arrived at the destination node.
        Node destination = agent.getEdgeDestination();
        edge.onAgentLeave();
        agent.setCurrentEdge(null);
        agent.setEdgeDestination(null);
        agent.setProgressOnEdge(0.0);
        agent.setCurrentNode(destination);
        destination.incrementAgentCount();

        if (destination.isExit()) {
            agent.setState(AgentState.EXITED);
            notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_REACHED_EXIT, agent));
        } else {
            notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
        }
    }

    /**
     * Emits a density alert for every node currently in heavy congestion.
     */
    private void checkDensityAlerts() {
        for (Node node : graph.getNodes()) {
            if (node.isCongested()) {
                notifyObservers(new SimulationEvent(SimulationEvent.Type.DENSITY_ALERT, node));
            }
        }
    }

    /**
     * Returns the number of agents that have reached an exit.
     *
     * @return the count of evacuated agents
     */
    public int countEvacuated() {
        int n = 0;
        for (Agent agent : state.getAgents()) {
            if (agent.hasExited()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Indicates whether every agent has evacuated.
     *
     * @return {@code true} if all agents have exited
     */
    public boolean isEvacuationComplete() {
        return countEvacuated() == state.getAgents().size();
    }

    /**
     * Reacts to model events: marks the routes as needing a recompute when an
     * obstacle appears or disappears.
     *
     * @param event the event broadcast by the model
     */
    @Override
    public void update(SimulationEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case EDGE_BLOCKED, EDGE_UNBLOCKED, NODE_STATE_CHANGED -> routesDirty = true;
            default -> { /* ignored */ }
        }
    }

    /**
     * Returns the simulation state driven by this engine.
     *
     * @return the simulation state
     */
    public SimulationState getState() {
        return state;
    }

    /**
     * Returns the simulation clock.
     *
     * @return the clock
     */
    public SimulationClock getClock() {
        return clock;
    }

    /**
     * Indicates whether the simulation is paused.
     *
     * @return {@code true} if paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Pauses or resumes the simulation.
     *
     * @param paused the new paused state
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
