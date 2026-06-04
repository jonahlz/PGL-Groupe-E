package fr.cytech.safeexit.model.graph;

import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.SimulationEvent;

/**
 * An edge connecting two {@link Node}s in the concert-hall graph.
 * <p>
 * Edges are where bottlenecks happen: at most {@link #maxConcurrentAgents}
 * agents (both directions combined) may travel along an edge at the same time.
 * An edge may be directed (one-way), may speed up or slow agents down, and may
 * be blocked dynamically (fire, debris, crowd).
 * <p>
 * The chosen capacity policy is "check before entering": an agent only steps
 * onto an edge if {@link #hasRoom()} is {@code true}; otherwise it waits.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class Edge extends AbstractObservable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final Node source;
    private final Node target;
    private boolean directed;
    private double length;
    private int maxConcurrentAgents;
    private double speedModifier;
    private boolean blocked;
    private int agentPassCount;
    private int currentAgentCount;

    /**
     * Creates an edge between two nodes. The length defaults to the Euclidean
     * distance between the two endpoints.
     *
     * @param id                  unique identifier of the edge
     * @param source              source node; never {@code null}
     * @param target              target node; never {@code null}
     * @param directed            {@code true} if the edge is one-way (source to target)
     * @param maxConcurrentAgents how many agents may travel on it simultaneously (&gt;= 1)
     * @throws IllegalArgumentException if endpoints are null, equal, or capacity &lt; 1
     */
    public Edge(String id, Node source, Node target, boolean directed, int maxConcurrentAgents) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Edge id must not be null or blank");
        }
        if (source == null || target == null) {
            throw new IllegalArgumentException("Edge endpoints must not be null");
        }
        if (source.equals(target)) {
            throw new IllegalArgumentException("An edge cannot link a node to itself");
        }
        if (maxConcurrentAgents < 1) {
            throw new IllegalArgumentException("Edge width must allow at least one agent");
        }
        this.id = id;
        this.source = source;
        this.target = target;
        this.directed = directed;
        this.maxConcurrentAgents = maxConcurrentAgents;
        this.speedModifier = 1.0;
        this.length = euclideanDistance(source, target);
    }

    /**
     * Computes the Euclidean distance between two nodes.
     *
     * @param a first node
     * @param b second node
     * @return the straight-line distance between {@code a} and {@code b}
     */
    private static double euclideanDistance(Node a, Node b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Indicates whether another agent may step onto this edge right now,
     * following the "check before entering" capacity policy.
     *
     * @return {@code true} if the edge is not blocked and below its width limit
     */
    public boolean hasRoom() {
        return !blocked && currentAgentCount < maxConcurrentAgents;
    }

    /**
     * Tells whether an agent may travel from the given node along this edge,
     * taking direction into account.
     *
     * @param from the node the agent is starting from
     * @return {@code true} if travel from {@code from} is allowed
     */
    public boolean allowsTravelFrom(Node from) {
        if (directed) {
            return source.equals(from);
        }
        return source.equals(from) || target.equals(from);
    }

    /**
     * Returns the node opposite to the given endpoint.
     *
     * @param from one endpoint of the edge
     * @return the other endpoint
     * @throws IllegalArgumentException if {@code from} is not an endpoint
     */
    public Node getOpposite(Node from) {
        if (source.equals(from)) {
            return target;
        }
        if (target.equals(from)) {
            return source;
        }
        throw new IllegalArgumentException("Node " + from + " is not an endpoint of edge " + id);
    }

    /**
     * Registers that an agent entered the edge.
     */
    public void onAgentEnter() {
        currentAgentCount++;
    }

    /**
     * Registers that an agent left the edge (and reached its target node),
     * updating the throughput statistic.
     */
    public void onAgentLeave() {
        if (currentAgentCount > 0) {
            currentAgentCount--;
        }
        agentPassCount++;
    }

    /**
     * Returns the unique identifier of this edge.
     *
     * @return the edge id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the source endpoint.
     *
     * @return source node
     */
    public Node getSource() {
        return source;
    }

    /**
     * Returns the target endpoint.
     *
     * @return target node
     */
    public Node getTarget() {
        return target;
    }

    /**
     * Indicates whether the edge is one-way.
     *
     * @return {@code true} if directed
     */
    public boolean isDirected() {
        return directed;
    }

    /**
     * Sets whether the edge is one-way.
     *
     * @param directed the new directed flag
     */
    public void setDirected(boolean directed) {
        this.directed = directed;
    }

    /**
     * Returns the metric length of the edge.
     *
     * @return the length
     */
    public double getLength() {
        return length;
    }

    /**
     * Sets the metric length of the edge.
     *
     * @param length the new length (must be &gt; 0)
     */
    public void setLength(double length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Edge length must be positive");
        }
        this.length = length;
    }

    /**
     * Returns how many agents may travel on the edge simultaneously.
     *
     * @return the edge width in agents
     */
    public int getMaxConcurrentAgents() {
        return maxConcurrentAgents;
    }

    /**
     * Sets the edge width.
     *
     * @param maxConcurrentAgents the new width (must be &gt;= 1)
     */
    public void setMaxConcurrentAgents(int maxConcurrentAgents) {
        if (maxConcurrentAgents < 1) {
            throw new IllegalArgumentException("Edge width must allow at least one agent");
        }
        this.maxConcurrentAgents = maxConcurrentAgents;
    }

    /**
     * Returns the speed modifier (1.0 = normal, 0.5 = slowed, 1.5 = faster).
     *
     * @return the speed modifier
     */
    public double getSpeedModifier() {
        return speedModifier;
    }

    /**
     * Sets the speed modifier applied to agents crossing this edge.
     *
     * @param speedModifier the new modifier (must be &gt; 0)
     */
    public void setSpeedModifier(double speedModifier) {
        if (speedModifier <= 0) {
            throw new IllegalArgumentException("Speed modifier must be positive");
        }
        this.speedModifier = speedModifier;
    }

    /**
     * Indicates whether the edge is blocked.
     *
     * @return {@code true} if blocked
     */
    public boolean isBlocked() {
        return blocked;
    }

    /**
     * Blocks or unblocks the edge and notifies observers with the matching
     * event type, so the router can recompute affected routes.
     *
     * @param blocked the new blocked state
     */
    public void setBlocked(boolean blocked) {
        if (this.blocked == blocked) {
            return;
        }
        this.blocked = blocked;
        SimulationEvent.Type t = blocked
                ? SimulationEvent.Type.EDGE_BLOCKED
                : SimulationEvent.Type.EDGE_UNBLOCKED;
        notifyObservers(new SimulationEvent(t, this));
    }

    /**
     * Returns the total number of agents that have crossed this edge.
     *
     * @return the throughput statistic
     */
    public int getAgentPassCount() {
        return agentPassCount;
    }

    /**
     * Returns the number of agents currently travelling on the edge.
     *
     * @return the current agent count
     */
    public int getCurrentAgentCount() {
        return currentAgentCount;
    }

    /**
     * Returns the cost of crossing this edge in simulation time, used as the
     * weight for shortest-time routing (length divided by the speed modifier).
     *
     * @return the time-based traversal cost
     */
    public double getTraversalCost() {
        return length / speedModifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return (o instanceof Edge other) && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + "(" + source + (directed ? " -> " : " <-> ") + target + ")";
    }
}
