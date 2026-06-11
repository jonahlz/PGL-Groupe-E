package fr.cytech.safeexit.model.sector;

import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.SimulationEvent;

/**
 * Physical display panel covering one sector of the venue.
 * <p>
 * A panel always shows exactly one {@link PanelMessage}. Calling
 * {@link #broadcast(PanelMessage)} replaces the current message and notifies
 * the observers (the supervisor dashboard, the canvas) with a
 * {@code PANEL_MODE_CHANGED} event, so the views repaint without the model
 * ever referencing JavaFX.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class DisplayPanel extends AbstractObservable {

    private static final long serialVersionUID = 1L;

    private final String panelId;
    private final Node anchorNode;
    private PanelMode mode;
    private PanelMessage currentMessage;

    /**
     * Creates a panel anchored on a graph node, initially in standby.
     *
     * @param panelId    unique identifier of the panel (e.g. "PANEL_SEC_1")
     * @param anchorNode the node where the panel is drawn (may be {@code null}
     *                   if no suitable anchor exists; the view will skip it)
     * @throws IllegalArgumentException if {@code panelId} is null
     */
    public DisplayPanel(String panelId, Node anchorNode) {
        if (panelId == null) {
            throw new IllegalArgumentException("A panel needs an id");
        }
        this.panelId = panelId;
        this.anchorNode = anchorNode;
        this.currentMessage = PanelMessage.standby();
        this.mode = this.currentMessage.getMode();
    }

    /**
     * Displays a new message and notifies observers if a message is given.
     *
     * @param message the message to display; ignored if {@code null}
     */
    public void broadcast(PanelMessage message) {
        if (message == null) {
            return;
        }
        this.currentMessage = message;
        this.mode = message.getMode();
        notifyObservers(new SimulationEvent(SimulationEvent.Type.PANEL_MODE_CHANGED, this));
    }

    /**
     * Resets the panel to the standby message (used when an alert clears or an
     * evacuation is called off).
     */
    public void reset() {
        broadcast(PanelMessage.standby());
    }

    /**
     * Returns the panel identifier.
     *
     * @return the panel id
     */
    public String getPanelId() {
        return panelId;
    }

    /**
     * Returns the node the panel is drawn on.
     *
     * @return the anchor node, possibly {@code null}
     */
    public Node getAnchorNode() {
        return anchorNode;
    }

    /**
     * Returns the current mode (colour code) of the panel.
     *
     * @return the panel mode
     */
    public PanelMode getMode() {
        return mode;
    }

    /**
     * Returns the message currently displayed.
     *
     * @return the current message (never {@code null})
     */
    public PanelMessage getCurrentMessage() {
        return currentMessage;
    }

    @Override
    public String toString() {
        return panelId + ":" + mode;
    }
}
