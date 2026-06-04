package fr.cytech.safeexit.model.agent;

import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.simulation.SimulationState;

import java.io.Serializable;

/**
 * Strategy side of the Strategy design pattern: encapsulates how an agent
 * decides where to go next.
 * <p>
 * Each {@link AgentState} maps to a concrete strategy (see
 * {@link StrategyFactory}), and additional strategies (such as
 * {@code LeaderFollowStrategy}) can be assigned to an agent explicitly without
 * being tied to a state.
 *
 * @author GROUPE E
 * @version 1.0
 */
public interface BehaviorStrategy extends Serializable {

    /**
     * Decides the node the agent should move to next.
     *
     * @param agent the agent making the decision
     * @param graph the current venue graph
     * @param state the current simulation state (routes, other agents...)
     * @return the chosen next node, or {@code null} / the current node to stay put
     */
    Node chooseNextNode(Agent agent, Graph graph, SimulationState state);

    /**
     * Number of simulation cycles the agent waits before it may move again.
     *
     * @return the movement delay in cycles ({@code 0} means no extra delay)
     */
    int getMovementDelay();

    /**
     * Indicates whether the agent tries to avoid dense areas.
     *
     * @return {@code true} if the agent avoids congestion
     */
    boolean shouldAvoidDensity();

    /**
     * Indicates whether the agent forces its way through instead of yielding.
     *
     * @return {@code true} if the agent takes priority over others
     */
    boolean takePriority();
}
