package fr.cytech.safeexit.model.agent;

/**
 * Factory translating an {@link AgentState} into the matching
 * {@link BehaviorStrategy}.
 * <p>
 * Only the state-driven strategies are produced here (calm, panicked, frozen).
 * {@link LeaderFollowStrategy} is intentionally absent: it is not tied to a
 * state and is assigned to an agent explicitly. The {@link AgentState#EXITED}
 * state has no behaviour and yields {@code null}.
 *
 * @author GROUPE E
 * @version 1.0
 */
public final class StrategyFactory {

    /** Utility class: no instances. */
    private StrategyFactory() {
    }

    /**
     * Returns a fresh strategy instance corresponding to the given state.
     *
     * @param state the agent state
     * @return the matching strategy, or {@code null} for {@link AgentState#EXITED}
     */
    public static BehaviorStrategy fromState(AgentState state) {
        if (state == null) {
            return new CalmEvacuationStrategy();
        }
        return switch (state) {
            case CALM -> new CalmEvacuationStrategy();
            case PANICKED -> new PanickedEvacuationStrategy();
            case FROZEN -> new FrozenStrategy();
            case EXITED -> null;
        };
    }
}
