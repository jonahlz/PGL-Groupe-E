package fr.cytech.safeexit.view.dashboard;

import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.observer.Observer;
import fr.cytech.safeexit.model.observer.SimulationEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Supervision alert log. It is an {@link Observer}: it subscribes to the
 * simulation engine and turns the meaningful {@link SimulationEvent}s into
 * human-readable, time-stamped lines, newest first.
 * <p>
 * This panel is a direct illustration of the Observer pattern: it never queries
 * the model, it only reacts to the events the model emits. High-frequency events
 * (agent moves) are ignored, and density alerts are throttled to avoid flooding.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class AlertPanel extends VBox implements Observer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_ENTRIES = 40;
    private static final long DENSITY_COOLDOWN_MS = 3000;

    private final ListView<String> list = new ListView<>();
    private final Map<String, Long> lastDensityLog = new HashMap<>();

    /**
     * Builds the alert panel with its header and list.
     */
    public AlertPanel() {
        Label header = new Label("Journal d'alertes");
        header.setStyle("-fx-text-fill: #aab2cc; -fx-font-weight: bold; -fx-padding: 6;");
        list.setStyle("-fx-control-inner-background: #12122a; -fx-background-color: #12122a; "
                + "-fx-text-fill: #dddddd; -fx-font-size: 11px;");
        VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(header, list);
        setStyle("-fx-background-color: #1a1a2e; -fx-background-radius: 8;");
    }

    /**
     * Clears the log (used when the simulation is reset).
     */
    public void clear() {
        list.getItems().clear();
        lastDensityLog.clear();
    }

    @Override
    public void update(SimulationEvent event) {
        if (event == null) {
            return;
        }
        String message = format(event);
        if (message != null) {
            addEntry(message);
        }
    }

    /**
     * Turns an event into a log line, or {@code null} if it should be ignored.
     *
     * @param event the event
     * @return the formatted line, or {@code null}
     */
    private String format(SimulationEvent event) {
        String time = LocalTime.now().format(TIME);
        switch (event.getType()) {
            case EDGE_BLOCKED:
                return time + "  ⛔  Arête bloquée — itinéraires recalculés";
            case EDGE_UNBLOCKED:
                return time + "  ✓  Arête rétablie";
            case EVACUATION_TRIGGERED:
                return time + "  !  Évacuation déclenchée";
            case NODE_STATE_CHANGED:
                if (event.getSource() instanceof Node node && node.isExit()) {
                    String name = node.getId().replace("EXIT_", "");
                    return time + (node.isBlocked()
                            ? "  ⛔  Sortie " + name + " bloquée"
                            : "  ✓  Sortie " + name + " rétablie");
                }
                return null;
            case DENSITY_ALERT:
                if (event.getSource() instanceof Node node && passesDensityCooldown(node)) {
                    return time + "  ⚠  Densité élevée — " + node.getId();
                }
                return null;
            default:
                // AGENT_MOVED, AGENT_STATE_CHANGED, AGENT_REACHED_EXIT,
                // ROUTE_RECALCULATED: too frequent to log here.
                return null;
        }
    }

    /**
     * Throttles density alerts so the same node is not logged too often.
     *
     * @param node the congested node
     * @return {@code true} if enough time has passed to log it again
     */
    private boolean passesDensityCooldown(Node node) {
        long now = System.currentTimeMillis();
        Long last = lastDensityLog.get(node.getId());
        if (last == null || now - last > DENSITY_COOLDOWN_MS) {
            lastDensityLog.put(node.getId(), now);
            return true;
        }
        return false;
    }

    /**
     * Adds a line at the top of the log and trims the history.
     *
     * @param message the line to add
     */
    private void addEntry(String message) {
        list.getItems().add(0, message);
        while (list.getItems().size() > MAX_ENTRIES) {
            list.getItems().remove(list.getItems().size() - 1);
        }
    }
}
