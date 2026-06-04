package fr.cytech.safeexit.model.agent;

import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.simulation.SimulationState;

/**
 * Behaviour of an agent that follows a leader.
 * <p>
 * This strategy is not tied to an {@link AgentState}; it is assigned explicitly
 * to an agent. The agent looks for the closest other active agent that is
 * "ahead" of it (geographically closer to an exit) and moves toward that
 * leader's position. When no suitable leader is found, it falls back to a
 * greedy move toward the nearest exit.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class LeaderFollowStrategy extends AbstractBehaviorStrategy {

    @Override
    public Node chooseNextNode(Agent agent, Graph graph, SimulationState state) {
        Node current = agent.getCurrentNode();
        if (current == null) {
            return null;
        }
        Node exit = nearestExit(graph, current);
        if (exit == null || state == null) {
            return greedyTowardNearestExit(agent, graph);
        }

        double myDistanceToExit = distance(current, exit);
        Agent leader = findNearestLeader(agent, current, exit, myDistanceToExit, state);
        if (leader == null || leader.getCurrentNode() == null) {
            return greedyTowardNearestExit(agent, graph);
        }

        // Move to the reachable neighbour closest to the leader's position.
        Node leaderNode = leader.getCurrentNode();
        Node best = current;
        double bestDist = distance(current, leaderNode);
        for (Node neighbour : usableNeighbours(graph, current)) {
            double d = distance(neighbour, leaderNode);
            if (d < bestDist) {
                bestDist = d;
                best = neighbour;
            }
        }
        // If we are already next to (or at) the leader, keep heading to the exit.
        return best.equals(current) ? greedyTowardNearestExit(agent, graph) : best;
    }

    /**
     * Finds the closest active agent that is ahead of the given agent on the
     * way to the exit.
     *
     * @param self              the following agent
     * @param from              the follower's current node
     * @param exit              the reference exit
     * @param selfDistToExit    the follower's distance to the exit
     * @param state             the simulation state holding all agents
     * @return the chosen leader, or {@code null} if none qualifies
     */
    private Agent findNearestLeader(Agent self, Node from, Node exit,
                                    double selfDistToExit, SimulationState state) {
        Agent leader = null;
        double bestDist = Double.MAX_VALUE;
        for (Agent other : state.getAgents()) {
            if (other == self || other.getState() == AgentState.EXITED) {
                continue;
            }
            Node otherNode = other.getCurrentNode();
            if (otherNode == null) {
                continue;
            }
            // A leader must be closer to the exit than we are (ahead of us).
            if (distance(otherNode, exit) >= selfDistToExit) {
                continue;
            }
            double d = distance(from, otherNode);
            if (d < bestDist) {
                bestDist = d;
                leader = other;
            }
        }
        return leader;
    }

    @Override
    public int getMovementDelay() {
        return 0;
    }

    @Override
    public boolean shouldAvoidDensity() {
        return false;
    }

    @Override
    public boolean takePriority() {
        return false;
    }
}
