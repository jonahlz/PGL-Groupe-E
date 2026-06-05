package fr.cytech.safeexit.model.sensor;

/**
 * Real-time occupancy status reported by a seat sensor.
 * <p>
 * A seat sensor physically measures presence, so it reports a seat as taken or
 * empty. The {@code AWAY} status is a useful interpretation of an empty-but-
 * reserved seat (the ticket holder is temporarily away, e.g. at the toilets).
 *
 * @author GROUPE E
 * @version 1.0
 */
public enum SeatStatus {
    /** The seat is occupied by its spectator. */
    OCCUPIED,
    /** The seat is empty and not reserved. */
    FREE,
    /** The spectator is temporarily away (toilets...); the seat stays reserved. */
    AWAY
}
