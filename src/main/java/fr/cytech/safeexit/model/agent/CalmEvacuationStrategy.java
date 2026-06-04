package fr.cytech.safeexit.model.agent;

import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.simulation.SimulationState;

/**
 * Behaviour of a calm agent.
 * <p>
 * A calm agent follows the optimal Voronoi route precomputed by the router (the
 * "next hop" stored in the {@link SimulationState}). It avoids dense areas and
 * yields the way to other agents (does not take priority). If no route is known
 * yet, it falls back to a greedy move toward the nearest exit.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class CalmEvacuationStrategy extends AbstractBehaviorStrategy {

    @Override
    public Node chooseNextNode(Agent agent, Graph graph, SimulationState state) {
        Node current = agent.getCurrentNode();
        if (current == null) {
            return null;
        }
        // Preferred: follow the Voronoi route computed by the router.
        Node nextHop = state != null ? state.getNextHop(current) : null;
        if (nextHop != null && !nextHop.isBlocked()) {
            return nextHop;
        }
        // Fallback while no route is available.
        return greedyTowardNearestExit(agent, graph);
    }

    @Override
    public int getMovementDelay() {
        return 0;
    }

    @Override
    public boolean shouldAvoidDensity() {
        return true;
    }

    @Override
    public boolean takePriority() {
        return false;
    }
}
