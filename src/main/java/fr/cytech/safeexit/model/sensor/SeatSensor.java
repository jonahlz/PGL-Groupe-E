package fr.cytech.safeexit.model.sensor;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.SimulationEvent;

/**
 * Occupancy sensor attached to a single seat.
 * <p>
 * It holds the real-time {@link SeatStatus} of its seat and, when occupied, a
 * reference to the spectator sitting there. Any status change notifies the
 * observers (the row sensor, the views) through a {@code SEAT_STATE_CHANGED}
 * event, so occupancy is tracked live, exactly like a physical sensor would.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SeatSensor extends AbstractObservable {

    private static final long serialVersionUID = 1L;

    private final Node seat;
    private SeatStatus status;
    private Agent occupant;

    /**
     * Creates a sensor for the given seat, initially free.
     *
     * @param seat the seat node monitored by this sensor; never {@code null}
     * @throws IllegalArgumentException if {@code seat} is null
     */
    public SeatSensor(Node seat) {
        if (seat == null) {
            throw new IllegalArgumentException("A seat sensor needs a seat node");
        }
        this.seat = seat;
        this.status = SeatStatus.FREE;
    }

    /**
     * Records that a spectator is sitting on the seat.
     *
     * @param agent the occupant
     */
    public void markOccupied(Agent agent) {
        this.occupant = agent;
        changeStatus(SeatStatus.OCCUPIED);
    }

    /**
     * Records that the seat is now empty and not reserved.
     */
    public void markFree() {
        this.occupant = null;
        changeStatus(SeatStatus.FREE);
    }

    /**
     * Records that the occupant is temporarily away (the seat stays reserved).
     */
    public void markAway() {
        changeStatus(SeatStatus.AWAY);
    }

    /**
     * Changes the status and notifies observers if it actually changed.
     *
     * @param newStatus the new status
     */
    private void changeStatus(SeatStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }
        this.status = newStatus;
        notifyObservers(new SimulationEvent(SimulationEvent.Type.SEAT_STATE_CHANGED, this));
    }

    /**
     * Indicates whether the seat is physically taken right now.
     *
     * @return {@code true} if the status is {@link SeatStatus#OCCUPIED}
     */
    public boolean isOccupied() {
        return status == SeatStatus.OCCUPIED;
    }

    /**
     * Returns the monitored seat.
     *
     * @return the seat node
     */
    public Node getSeat() {
        return seat;
    }

    /**
     * Returns the current occupancy status.
     *
     * @return the seat status
     */
    public SeatStatus getStatus() {
        return status;
    }

    /**
     * Returns the current occupant, if any.
     *
     * @return the occupant agent, or {@code null}
     */
    public Agent getOccupant() {
        return occupant;
    }

    @Override
    public String toString() {
        return seat.getId() + "=" + status;
    }
}
