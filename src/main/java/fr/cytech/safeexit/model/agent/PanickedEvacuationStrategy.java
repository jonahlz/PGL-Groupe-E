package fr.cytech.safeexit.model.agent;

import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.simulation.SimulationState;

/**
 * Behaviour of a panicked agent.
 * <p>
 * A panicked agent ignores the optimal route and congestion: it always heads
 * straight for the geographically nearest exit and forces its way through
 * (takes priority, does not avoid density). The capacity policy of the engine
 * may still leave it waiting if an edge is full.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class PanickedEvacuationStrategy extends AbstractBehaviorStrategy {

    @Override
    public Node chooseNextNode(Agent agent, Graph graph, SimulationState state) {
        if (agent.getCurrentNode() == null) {
            return null;
        }
        return greedyTowardNearestExit(agent, graph);
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
        return true;
    }
}
