package fr.cytech.safeexit.model.tracking;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.observer.Observer;
import fr.cytech.safeexit.model.observer.SimulationEvent;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records the movement history of every agent.
 * <p>
 * It is an {@link Observer} of the simulation engine: each time an agent moves
 * ({@code AGENT_MOVED}) or reaches an exit ({@code AGENT_REACHED_EXIT}) it stores
 * a {@link PositionRecord} (which also captures the agent's state at that
 * instant). To stay bounded in memory it keeps only the last
 * {@link #MAX_HISTORY} records per agent (a circular buffer). From this history
 * it can rebuild an agent trajectory or a passage heatmap for the views.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class AgentTracker implements Observer, Serializable {

    private static final long serialVersionUID = 1L;

    /** Maximum number of records kept per agent (oldest are dropped). */
    public static final int MAX_HISTORY = 200;

    private final Map<String, Deque<PositionRecord>> history = new LinkedHashMap<>();
    private long totalRecords;

    /**
     * Records the agent position whenever it moves (every {@code AGENT_MOVED}
     * and the final {@code AGENT_REACHED_EXIT}). Each record also captures the
     * agent's current state, so a trajectory shows where panic happened.
     *
     * @param event the event received from the simulation engine
     */
    @Override
    public void update(SimulationEvent event) {
        if (event == null) {
            return;
        }
        if (event.getType() != SimulationEvent.Type.AGENT_MOVED
                && event.getType() != SimulationEvent.Type.AGENT_REACHED_EXIT) {
            return;
        }
        if (event.getSource() instanceof Agent agent) {
            record(agent);
        }
    }

    /**
     * Appends a snapshot of the agent and trims the buffer to {@link #MAX_HISTORY}.
     *
     * @param agent the agent to record
     */
    private void record(Agent agent) {
        Deque<PositionRecord> records =
                history.computeIfAbsent(agent.getId(), key -> new ArrayDeque<>());
        records.addLast(new PositionRecord(
                agent.getId(),
                agent.getCurrentNode(),
                agent.getCurrentEdge(),
                agent.getProgressOnEdge(),
                agent.getState()));
        while (records.size() > MAX_HISTORY) {
            records.pollFirst();
        }
        totalRecords++;
    }

    /**
     * Returns the recorded path of an agent, oldest first.
     *
     * @param agentId the agent identifier
     * @return a copy of the agent's records (empty if the agent is unknown)
     */
    public List<PositionRecord> getHistory(String agentId) {
        Deque<PositionRecord> records = history.get(agentId);
        return (records == null) ? new ArrayList<>() : new ArrayList<>(records);
    }

    /**
     * Builds a heatmap counting how many times each node was recorded across
     * all agents.
     *
     * @return a map node &rarr; number of passages
     */
    public Map<Node, Integer> buildPassageHeatmap() {
        Map<Node, Integer> heatmap = new HashMap<>();
        for (Deque<PositionRecord> records : history.values()) {
            for (PositionRecord record : records) {
                Node node = record.getNode();
                if (node != null) {
                    heatmap.merge(node, 1, Integer::sum);
                }
            }
        }
        return heatmap;
    }

    /**
     * Returns the node with the most recorded passages (the busiest spot).
     *
     * @return the most congested node, or {@code null} if nothing was recorded
     */
    public Node mostCongestedNode() {
        Node busiest = null;
        int best = -1;
        for (Map.Entry<Node, Integer> entry : buildPassageHeatmap().entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                busiest = entry.getKey();
            }
        }
        return busiest;
    }

    /**
     * Returns the identifiers of every tracked agent.
     *
     * @return the set of agent ids
     */
    public Set<String> getTrackedAgentIds() {
        return history.keySet();
    }

    /**
     * Returns the total number of records stored since the last reset.
     *
     * @return the total record count
     */
    public long getTotalRecords() {
        return totalRecords;
    }

    /**
     * Clears all recorded history.
     */
    public void clear() {
        history.clear();
        totalRecords = 0;
    }
}
