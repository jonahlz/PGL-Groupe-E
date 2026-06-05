package fr.cytech.safeexit.model.simulation;

/**
 * Phase of the event being supervised.
 * <p>
 * SafeExit is not only an evacuation simulator: it monitors the venue in real
 * time during the whole event. The evacuation is just one phase.
 *
 * @author GROUPE E
 * @version 1.0
 */
public enum EventPhase {
    /** The event is running normally; the dashboard monitors live occupancy. */
    NORMAL,
    /** An emergency evacuation has been triggered; agents head for the exits. */
    EVACUATION
}
