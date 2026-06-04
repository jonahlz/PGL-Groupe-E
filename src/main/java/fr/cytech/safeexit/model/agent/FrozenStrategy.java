package fr.cytech.safeexit.model.agent;

import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.simulation.SimulationState;

/**
 * Behaviour of a frozen agent.
 * <p>
 * A frozen agent is temporarily stuck (the crowd is too dense): it stays on its
 * current node and waits a fixed number of cycles before it may move again.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class FrozenStrategy extends AbstractBehaviorStrategy {

    /** Number of cycles a frozen agent waits before retrying. */
    public static final int FREEZE_CYCLES = 2;

    @Override
    public Node chooseNextNode(Agent agent, Graph graph, SimulationState state) {
        // Stay in place while frozen.
        return agent.getCurrentNode();
    }

    @Override
    public int getMovementDelay() {
        return FREEZE_CYCLES;
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
