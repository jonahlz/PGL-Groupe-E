package fr.cytech.safeexit.model.sensor;

import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.Observer;
import fr.cytech.safeexit.model.observer.SimulationEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated occupancy sensor for a whole row.
 * <p>
 * A row sensor is both an {@link Observer} of its seat sensors and an
 * {@link fr.cytech.safeexit.model.observer.Observable}: whenever one of its
 * seats changes, it recomputes the row's occupancy and notifies its own
 * observers with a {@code ROW_OCCUPANCY_CHANGED} event. This gives the
 * supervisor a live, row-level view of how full the venue is.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class RowSensor extends AbstractObservable implements Observer {

    private static final long serialVersionUID = 1L;

    private final String rowId;
    private final List<SeatSensor> seatSensors = new ArrayList<>();

    /**
     * Creates a row sensor for the given row identifier.
     *
     * @param rowId the row label (e.g. "A")
     */
    public RowSensor(String rowId) {
        this.rowId = rowId;
    }

    /**
     * Adds a seat sensor to this row and subscribes to its changes.
     *
     * @param sensor the seat sensor to monitor
     */
    public void addSeatSensor(SeatSensor sensor) {
        seatSensors.add(sensor);
        sensor.addObserver(this);
    }

    /**
     * Reacts to a seat change by propagating a row occupancy update.
     *
     * @param event the seat event
     */
    @Override
    public void update(SimulationEvent event) {
        if (event != null && event.getType() == SimulationEvent.Type.SEAT_STATE_CHANGED) {
            notifyObservers(new SimulationEvent(SimulationEvent.Type.ROW_OCCUPANCY_CHANGED, this));
        }
    }

    /**
     * Counts the seats currently occupied in this row.
     *
     * @return the number of occupied seats
     */
    public int occupiedCount() {
        int n = 0;
        for (SeatSensor s : seatSensors) {
            if (s.isOccupied()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Counts the seats whose occupant has temporarily left (status
     * {@link SeatStatus#AWAY}): spectators currently up and moving in the aisles.
     *
     * @return the number of seats currently marked away
     */
    public int awayCount() {
        int n = 0;
        for (SeatSensor s : seatSensors) {
            if (s.getStatus() == SeatStatus.AWAY) {
                n++;
            }
        }
        return n;
    }

    /**
     * Returns the occupancy ratio of the row, in {@code [0, 1]}.
     *
     * @return occupied seats divided by total seats (0 if the row is empty)
     */
    public double occupancyRatio() {
        return seatSensors.isEmpty() ? 0.0 : (double) occupiedCount() / seatSensors.size();
    }

    /**
     * Returns the row identifier.
     *
     * @return the row label
     */
    public String getRowId() {
        return rowId;
    }

    /**
     * Returns the number of seats in this row.
     *
     * @return the seat count
     */
    public int seatCount() {
        return seatSensors.size();
    }

    @Override
    public String toString() {
        return "Row " + rowId + ": " + occupiedCount() + "/" + seatCount() + " occupied";
    }
}
