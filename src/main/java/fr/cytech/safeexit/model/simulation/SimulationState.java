package fr.cytech.safeexit.model.simulation;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.sensor.SensorNetwork;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of everything that makes up a running simulation: the {@link Graph},
 * the list of {@link Agent}s and the routing information.
 * <p>
 * It is the object handed to behaviour strategies (so they can inspect other
 * agents and the recommended routes) and the object serialized to disk for the
 * save / restore feature. It is intentionally a plain data holder: the
 * simulation logic lives in {@code SimulationEngine}.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SimulationState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Graph graph;
    private final List<Agent> agents;

    /**
     * For each node, the next node to step to in order to reach the assigned
     * exit (the "next hop" of the Voronoi shortest path). Filled by the router.
     */
    private final Map<Node, Node> nextHopToExit;

    /** For each node, the exit it is currently assigned to (Voronoi cell). */
    private final Map<Node, Node> assignedExit;

    /** Optional occupancy-sensor layer (may be {@code null}). */
    private SensorNetwork sensorNetwork;

    /** Number of simulation cycles elapsed since the start. */
    private long currentCycle;

    /**
     * Creates a simulation state wrapping a graph and an (initially empty) set
     * of agents.
     *
     * @param graph the venue graph; never {@code null}
     */
    public SimulationState(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Simulation state requires a graph");
        }
        this.graph = graph;
        this.agents = new ArrayList<>();
        this.nextHopToExit = new HashMap<>();
        this.assignedExit = new HashMap<>();
    }

    /**
     * Returns the venue graph.
     *
     * @return the graph
     */
    public Graph getGraph() {
        return graph;
    }

    /**
     * Returns the live list of agents (modifiable).
     *
     * @return the agents
     */
    public List<Agent> getAgents() {
        return agents;
    }

    /**
     * Returns the recommended next node from the given node toward its exit.
     *
     * @param node the node the agent is currently on
     * @return the next hop, or {@code null} if no route is known
     */
    public Node getNextHop(Node node) {
        return nextHopToExit.get(node);
    }

    /**
     * Returns the exit assigned to the given node by the router.
     *
     * @param node the node to look up
     * @return the assigned exit, or {@code null} if none
     */
    public Node getAssignedExit(Node node) {
        return assignedExit.get(node);
    }

    /**
     * Replaces the routing information with a freshly computed one.
     *
     * @param nextHop  map node &rarr; next node toward exit
     * @param exitOf   map node &rarr; assigned exit
     */
    public void updateRoutes(Map<Node, Node> nextHop, Map<Node, Node> exitOf) {
        nextHopToExit.clear();
        assignedExit.clear();
        if (nextHop != null) {
            nextHopToExit.putAll(nextHop);
        }
        if (exitOf != null) {
            assignedExit.putAll(exitOf);
        }
    }

    /**
     * Returns the occupancy-sensor layer, if any.
     *
     * @return the sensor network, or {@code null}
     */
    public SensorNetwork getSensorNetwork() {
        return sensorNetwork;
    }

    /**
     * Attaches an occupancy-sensor layer to this state.
     *
     * @param sensorNetwork the sensor network
     */
    public void setSensorNetwork(SensorNetwork sensorNetwork) {
        this.sensorNetwork = sensorNetwork;
    }

    /**
     * Returns the number of simulation cycles elapsed.
     *
     * @return the current cycle index
     */
    public long getCurrentCycle() {
        return currentCycle;
    }

    /**
     * Advances the cycle counter by one.
     */
    public void incrementCycle() {
        currentCycle++;
    }
}
