package fr.cytech.safeexit.model.sector;

import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.Observer;
import fr.cytech.safeexit.model.observer.SimulationEvent;
import fr.cytech.safeexit.model.sensor.RowSensor;

import java.util.ArrayList;
import java.util.List;

/**
 * A sector groups several consecutive seat rows under a single display panel.
 * <p>
 * It is the heart of the centralised system: it <b>observes</b> its
 * {@link RowSensor}s and, whenever the occupancy of one of them changes, it
 * recomputes its own occupancy. When congestion goes above
 * {@code congestionThreshold} it puts its {@link DisplayPanel} into
 * {@link PanelMode#ALERT} and fires a {@code SECTOR_CONGESTION_ALERT} event for
 * the supervisor; when it clears, the panel returns to standby.
 * <p>
 * Monitoring can be switched off (see {@link #setMonitoring(boolean)}) so that
 * an ongoing evacuation message is not overwritten by congestion handling.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class Sector extends AbstractObservable implements Observer {

    private static final long serialVersionUID = 1L;

    /** Default occupancy ratio (0..1) above which the sector raises an alert. */
    public static final double DEFAULT_CONGESTION_THRESHOLD = 0.75;

    private final String sectorId;
    private final List<RowSensor> rowSensors;
    private final DisplayPanel panel;
    private final Node centreNode;
    private double congestionThreshold = DEFAULT_CONGESTION_THRESHOLD;
    private boolean monitoring = true;

    /**
     * Builds a sector and subscribes it to every one of its row sensors.
     *
     * @param sectorId   unique identifier (e.g. "SEC_1"); never {@code null}
     * @param rowSensors the consecutive row sensors covered by this sector
     * @param panel      the display panel of the sector
     * @param centreNode the graph node at the centre of the sector (used for
     *                   the panel anchor and direction computation)
     * @throws IllegalArgumentException if {@code sectorId} is null
     */
    public Sector(String sectorId, List<RowSensor> rowSensors, DisplayPanel panel, Node centreNode) {
        if (sectorId == null) {
            throw new IllegalArgumentException("A sector needs an id");
        }
        this.sectorId = sectorId;
        this.rowSensors = new ArrayList<>(rowSensors == null ? List.of() : rowSensors);
        this.panel = panel;
        this.centreNode = centreNode;
        for (RowSensor sensor : this.rowSensors) {
            sensor.addObserver(this);
        }
        // Reflect the current occupancy immediately, in case the sector is
        // built on a venue that is already (partly) full.
        refresh();
    }

    /**
     * Reacts to a row occupancy change by refreshing the sector state.
     *
     * @param event the event received from a row sensor
     */
    @Override
    public void update(SimulationEvent event) {
        if (event == null || !monitoring) {
            return;
        }
        if (event.getType() == SimulationEvent.Type.ROW_OCCUPANCY_CHANGED) {
            refresh();
        }
    }

    /**
     * Recomputes the sector occupancy and updates the panel accordingly:
     * escalates to {@link PanelMode#ALERT} above the threshold, de-escalates to
     * standby below it. Safe to call at any time (e.g. right after building the
     * sector to reflect the initial occupancy).
     */
    public void refresh() {
        if (panel == null) {
            return;
        }
        double ratio = computeOccupancyRatio();
        if (ratio >= congestionThreshold) {
            if (panel.getMode() != PanelMode.ALERT) {
                panel.broadcast(PanelMessage.alert("ZONE DENSE \u2014 RALENTISSEZ"));
                notifyObservers(new SimulationEvent(
                        SimulationEvent.Type.SECTOR_CONGESTION_ALERT, this));
            }
        } else if (panel.getMode() == PanelMode.ALERT) {
            panel.reset();
        }
    }

    /**
     * Average of the occupancy ratios of the sector's rows.
     *
     * @return a value in {@code [0, 1]} (0 if the sector has no row)
     */
    public double computeOccupancyRatio() {
        if (rowSensors.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (RowSensor sensor : rowSensors) {
            sum += sensor.occupancyRatio();
        }
        return sum / rowSensors.size();
    }

    /**
     * Total number of occupied seats across the sector.
     *
     * @return the occupied seat count
     */
    public int totalOccupied() {
        int total = 0;
        for (RowSensor sensor : rowSensors) {
            total += sensor.occupiedCount();
        }
        return total;
    }

    /**
     * Total number of seats across the sector.
     *
     * @return the seat count
     */
    public int totalCapacity() {
        int total = 0;
        for (RowSensor sensor : rowSensors) {
            total += sensor.seatCount();
        }
        return total;
    }

    /**
     * Enables or disables congestion monitoring (disabled during an evacuation
     * so the guidance message is not overwritten).
     *
     * @param enabled {@code true} to monitor congestion, {@code false} to mute
     */
    public void setMonitoring(boolean enabled) {
        this.monitoring = enabled;
    }

    /**
     * Indicates whether congestion monitoring is active.
     *
     * @return {@code true} if monitoring
     */
    public boolean isMonitoring() {
        return monitoring;
    }

    /**
     * Sets the congestion threshold, clamped to {@code [0, 1]}.
     *
     * @param threshold the occupancy ratio above which an alert is raised
     */
    public void setCongestionThreshold(double threshold) {
        this.congestionThreshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    /**
     * Returns the congestion threshold.
     *
     * @return the threshold in {@code [0, 1]}
     */
    public double getCongestionThreshold() {
        return congestionThreshold;
    }

    /**
     * Returns the sector identifier.
     *
     * @return the sector id
     */
    public String getSectorId() {
        return sectorId;
    }

    /**
     * Returns the display panel of the sector.
     *
     * @return the panel
     */
    public DisplayPanel getPanel() {
        return panel;
    }

    /**
     * Returns the centre node of the sector.
     *
     * @return the centre node, possibly {@code null}
     */
    public Node getCentreNode() {
        return centreNode;
    }

    /**
     * Returns the row sensors covered by this sector.
     *
     * @return a copy of the row sensor list
     */
    public List<RowSensor> getRowSensors() {
        return new ArrayList<>(rowSensors);
    }

    /**
     * Returns the labels of the rows in this sector (e.g. ["A", "B", "C"]).
     *
     * @return the list of row labels
     */
    public List<String> getRowLabels() {
        List<String> labels = new ArrayList<>();
        for (RowSensor sensor : rowSensors) {
            labels.add(sensor.getRowId());
        }
        return labels;
    }

    @Override
    public String toString() {
        return sectorId + " (" + getRowLabels() + "): "
                + totalOccupied() + "/" + totalCapacity()
                + " \u2192 " + (panel == null ? "no panel" : panel.getMode());
    }
}
