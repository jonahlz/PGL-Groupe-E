package fr.cytech.safeexit.model.agent;

/**
 * Possible states of an agent during an evacuation.
 * <p>
 * The state drives the {@link BehaviorStrategy} used by the agent: changing the
 * state automatically swaps the strategy through
 * {@link StrategyFactory#fromState(AgentState)}.
 *
 * @author GROUPE E
 * @version 1.0
 */
public enum AgentState {
    /** Follows the optimal Voronoi route and yields the way to others. */
    CALM,
    /** Rushes toward the nearest exit (straight-line), ignores congestion. */
    PANICKED,
    /** Blocked by the crowd: waits a couple of cycles before retrying. */
    FROZEN,
    /** Has reached an exit and is removed from the simulation. */
    EXITED
}
