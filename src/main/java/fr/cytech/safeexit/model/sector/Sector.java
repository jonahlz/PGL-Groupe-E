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
 * {@link RowSensor}s and, whenever one of them changes, it recomputes how many
 * of its spectators are currently out of their seats. A full but seated audience
 * is calm, so the alert is driven by <em>movement</em>, not by seat occupancy:
 * when too many spectators are up and moving in the aisles at once (the real
 * congestion signal) it puts its {@link DisplayPanel} into {@link PanelMode#ALERT}
 * and fires a {@code SECTOR_CONGESTION_ALERT} event for the supervisor; when it
 * clears, the panel returns to standby. A manual panic scenario can also raise
 * the alert directly.
 * <p>
 * Monitoring can be switched off (see {@link #setMonitoring(boolean)}) so that
 * an ongoing evacuation message is not overwritten by congestion handling.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class Sector extends AbstractObservable implements Observer {

    private static final long serialVersionUID = 1L;

    /**
     * Default movement ratio (0..1) above which the sector raises an alert, i.e.
     * the fraction of the sector's spectators that must be out of their seats at
     * once to count as real congestion. A calm, fully seated audience has a
     * movement ratio of 0, so it never triggers a false alarm.
     */
    public static final double DEFAULT_CONGESTION_THRESHOLD = 0.40;

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
     * Recomputes the sector's crowd movement and updates the panel accordingly:
     * escalates to {@link PanelMode#ALERT} when too many spectators are out of
     * their seats at once (real congestion), de-escalates to standby when it
     * clears. Safe to call at any time (e.g. right after building the sector).
     */
    public void refresh() {
        if (panel == null) {
            return;
        }
        double movement = computeMovementRatio();
        if (movement >= congestionThreshold) {
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
     * Fraction of the sector's spectators currently away from their seat (up and
     * moving in the aisles). Near 0 for a calm, seated audience; it rises only
     * when many people move at once, which is the real congestion signal.
     *
     * @return a value in {@code [0, 1]} (0 if the sector has no seat)
     */
    public double computeMovementRatio() {
        int seats = 0;
        int away = 0;
        for (RowSensor sensor : rowSensors) {
            seats += sensor.seatCount();
            away += sensor.awayCount();
        }
        return seats == 0 ? 0.0 : (double) away / seats;
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
     * @param threshold the movement ratio above which an alert is raised
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
