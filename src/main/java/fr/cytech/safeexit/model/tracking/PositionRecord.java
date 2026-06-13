package fr.cytech.safeexit.model.tracking;

import fr.cytech.safeexit.model.agent.AgentState;
import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Node;

import java.io.Serializable;

/**
 * Immutable snapshot of an agent position at a given instant.
 * <p>
 * The {@link AgentTracker} stores a chronological list of these records per
 * agent, which lets the views draw a trajectory and build a passage heatmap.
 * An agent is either standing on a {@link Node} ({@code edge == null}) or
 * travelling on an {@link Edge} ({@code node == null}).
 *
 * @author GROUPE E
 * @version 1.0
 */
public final class PositionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String agentId;
    private final Node node;
    private final Edge edge;
    private final double edgeProgress;
    private final AgentState state;
    private final long timestamp;

    /**
     * Creates a position record.
     *
     * @param agentId      the agent identifier
     * @param node         the node the agent stands on, or {@code null}
     * @param edge         the edge the agent travels on, or {@code null}
     * @param edgeProgress progress along the edge in {@code [0, 1]}
     * @param state        the agent state at that instant
     */
    public PositionRecord(String agentId, Node node, Edge edge, double edgeProgress, AgentState state) {
        this.agentId = agentId;
        this.node = node;
        this.edge = edge;
        this.edgeProgress = edgeProgress;
        this.state = state;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Returns the agent identifier.
     *
     * @return the agent id
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Returns the node the agent was standing on.
     *
     * @return the node, or {@code null} if the agent was on an edge
     */
    public Node getNode() {
        return node;
    }

    /**
     * Returns the edge the agent was travelling on.
     *
     * @return the edge, or {@code null} if the agent was on a node
     */
    public Edge getEdge() {
        return edge;
    }

    /**
     * Returns the progress along the edge.
     *
     * @return a value in {@code [0, 1]}
     */
    public double getEdgeProgress() {
        return edgeProgress;
    }

    /**
     * Returns the agent state at the time of the record.
     *
     * @return the agent state
     */
    public AgentState getState() {
        return state;
    }

    /**
     * Returns the creation time of this record (milliseconds since epoch).
     *
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        String where = (node != null) ? node.getId()
                : (edge != null) ? edge.getId() + "@" + String.format("%.2f", edgeProgress)
                : "?";
        return agentId + "->" + where + " (" + state + ")";
    }
}
