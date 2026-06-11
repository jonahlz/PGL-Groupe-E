package fr.cytech.safeexit.model.sector;

/**
 * Operating mode of a physical sector display panel.
 * <p>
 * The mode drives the colour code shown to spectators (green = normal,
 * blue = monitoring, orange = alert, green = guidance, red = blocked route)
 * and is what the supervisor dashboard reads to render each panel.
 *
 * @author GROUPE E
 * @version 1.0
 */
public enum PanelMode {
    /** Concert running normally, no alert: the panel shows a standby message. */
    STANDBY,
    /** Occupancy is rising; the panel keeps an eye on the sector. */
    MONITORING,
    /** Congestion detected in the sector: the panel warns the crowd. */
    ALERT,
    /** Evacuation: the panel shows a directional arrow towards the target exit. */
    DIRECTIONAL_GUIDANCE,
    /** The usual route is cut: the panel redirects towards an alternative exit. */
    ROUTE_BLOCKED
}
