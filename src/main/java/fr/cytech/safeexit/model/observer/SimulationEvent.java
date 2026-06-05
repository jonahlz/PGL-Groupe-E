package fr.cytech.safeexit.model.observer;

import java.io.Serializable;

/**
 * Immutable event emitted by the model whenever its state changes.
 * <p>
 * Every state change in the model (a node being blocked, an agent moving or
 * changing behaviour, a density threshold being reached...) produces a
 * {@code SimulationEvent}. Observers receive it through
 * {@link Observer#update(SimulationEvent)} and react accordingly. Events also
 * carry a timestamp so the simulation can keep a chronological history (log).
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SimulationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Categories of events that can occur during a simulation.
     */
    public enum Type {
        /** A node changed state (blocked, saturated, released...). */
        NODE_STATE_CHANGED,
        /** An edge became impassable (fire, debris, crowd). */
        EDGE_BLOCKED,
        /** A previously blocked edge became passable again. */
        EDGE_UNBLOCKED,
        /** An agent progressed along an edge or entered a new node. */
        AGENT_MOVED,
        /** An agent changed state (and therefore its behaviour strategy). */
        AGENT_STATE_CHANGED,
        /** An agent reached an exit and left the simulation. */
        AGENT_REACHED_EXIT,
        /** A density threshold was exceeded somewhere in the venue. */
        DENSITY_ALERT,
        /** Emergency evacuation mode was triggered. */
        EVACUATION_TRIGGERED,
        /** Voronoi routes were recomputed after an obstacle appeared. */
        ROUTE_RECALCULATED,
        /** A seat sensor changed status (occupied, free, away, moved). */
        SEAT_STATE_CHANGED,
        /** A row's overall occupancy changed. */
        ROW_OCCUPANCY_CHANGED
    }

    private final Type type;
    private final Object source;
    private final long timestamp;

    /**
     * Creates a new event stamped with the current system time.
     *
     * @param type   the category of the event; never {@code null}
     * @param source the model element concerned (node, edge, agent...),
     *               may be {@code null} for global events
     */
    public SimulationEvent(Type type, Object source) {
        if (type == null) {
            throw new IllegalArgumentException("Event type must not be null");
        }
        this.type = type;
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Returns the category of this event.
     *
     * @return the event type
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns the model element this event refers to.
     *
     * @return the source object, possibly {@code null}
     */
    public Object getSource() {
        return source;
    }

    /**
     * Returns the creation time of this event (milliseconds since epoch).
     *
     * @return the timestamp used for history ordering
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + type + (source != null ? " (" + source + ")" : "");
    }
}
