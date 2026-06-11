package fr.cytech.safeexit.model.sector;

import java.io.Serializable;

/**
 * Instruction displayed on a physical sector panel.
 * <p>
 * Immutable value object: once created, a message never changes. A panel
 * switches instruction by broadcasting a new {@code PanelMessage}. The static
 * factory methods build the standard messages used by the system (standby,
 * alert, evacuation...), so callers never have to remember which
 * {@link PanelMode} goes with which text.
 *
 * @author GROUPE E
 * @version 1.0
 */
public final class PanelMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Direction of the arrow drawn on the panel. {@link #symbol()} returns the
     * matching Unicode glyph, handy both for the CLI and the JavaFX view.
     */
    public enum ArrowDirection {
        /** Upwards arrow. */
        NORTH("\u2191"),
        /** Downwards arrow. */
        SOUTH("\u2193"),
        /** Rightwards arrow. */
        EAST("\u2192"),
        /** Leftwards arrow. */
        WEST("\u2190"),
        /** "Stay where you are" symbol. */
        STAY("\u25A0"),
        /** No arrow. */
        NONE("\u2014");

        private final String symbol;

        ArrowDirection(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Returns the Unicode glyph representing this direction.
         *
         * @return the arrow symbol as a string
         */
        public String symbol() {
            return symbol;
        }
    }

    private final String text;
    private final ArrowDirection direction;
    private final PanelMode mode;
    private final long timestamp;

    /**
     * Creates a panel message. Null arguments are replaced by safe defaults so
     * a panel can never end up showing {@code null}.
     *
     * @param text      the line of text to display
     * @param direction the arrow to draw
     * @param mode      the mode (and therefore the colour code) of the message
     */
    public PanelMessage(String text, ArrowDirection direction, PanelMode mode) {
        this.text = (text == null) ? "" : text;
        this.direction = (direction == null) ? ArrowDirection.NONE : direction;
        this.mode = (mode == null) ? PanelMode.STANDBY : mode;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Standby message shown during a normal concert.
     *
     * @return a STANDBY message with no arrow
     */
    public static PanelMessage standby() {
        return new PanelMessage("Bienvenue \u2014 profitez du concert",
                ArrowDirection.NONE, PanelMode.STANDBY);
    }

    /**
     * Monitoring message shown when occupancy starts to rise.
     *
     * @return a MONITORING message with no arrow
     */
    public static PanelMessage monitoring() {
        return new PanelMessage("Affluence en hausse \u2014 surveillance active",
                ArrowDirection.NONE, PanelMode.MONITORING);
    }

    /**
     * Alert message shown when the sector is congested.
     *
     * @param text the warning to display
     * @return an ALERT message with no arrow
     */
    public static PanelMessage alert(String text) {
        return new PanelMessage(text, ArrowDirection.NONE, PanelMode.ALERT);
    }

    /**
     * Evacuation message pointing to the recommended exit.
     *
     * @param exitLabel the label of the target exit (e.g. "S2")
     * @param direction the arrow towards that exit
     * @return a DIRECTIONAL_GUIDANCE message
     */
    public static PanelMessage evacuation(String exitLabel, ArrowDirection direction) {
        return new PanelMessage("\u00C9VACUATION \u2192 " + exitLabel,
                direction, PanelMode.DIRECTIONAL_GUIDANCE);
    }

    /**
     * Message shown when the usual route is blocked and the crowd is redirected.
     *
     * @param alternativeExit the alternative exit label
     * @param direction       the arrow towards that exit
     * @return a ROUTE_BLOCKED message
     */
    public static PanelMessage routeBlocked(String alternativeExit, ArrowDirection direction) {
        return new PanelMessage("CHEMIN BLOQU\u00C9 \u2192 " + alternativeExit,
                direction, PanelMode.ROUTE_BLOCKED);
    }

    /**
     * Builds a custom message, used by the supervisor to override a panel.
     *
     * @param text      free text
     * @param direction the arrow to draw
     * @param mode      the mode of the message
     * @return the custom message
     */
    public static PanelMessage custom(String text, ArrowDirection direction, PanelMode mode) {
        return new PanelMessage(text, direction, mode);
    }

    /**
     * Returns the displayed text.
     *
     * @return the message text (never {@code null})
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the arrow direction.
     *
     * @return the direction (never {@code null})
     */
    public ArrowDirection getDirection() {
        return direction;
    }

    /**
     * Returns the mode (colour code) of this message.
     *
     * @return the panel mode (never {@code null})
     */
    public PanelMode getMode() {
        return mode;
    }

    /**
     * Returns the creation time of this message (milliseconds since epoch).
     *
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return direction.symbol() + " " + text + " [" + mode + "]";
    }
}
