package fr.cytech.safeexit.model.sector;

import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.sensor.RowSensor;
import fr.cytech.safeexit.model.sensor.SensorNetwork;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates and owns the sectors of a venue.
 * <p>
 * It groups every {@code ROWS_PER_SECTOR} consecutive rows (taken from the
 * {@link SensorNetwork}) into one {@link Sector}, each with its own
 * {@link DisplayPanel}. Each panel is anchored on the centre-corridor node of
 * the sector's middle row, so it sits in the central aisle just like a real
 * suspended sign. This is the entry point the dashboard and controllers use to
 * read or drive every panel at once.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SectorManager implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default number of consecutive rows grouped into a single sector. */
    public static final int DEFAULT_ROWS_PER_SECTOR = 3;

    private final List<Sector> sectors;

    /**
     * Private constructor; instances are built with {@link #build}.
     *
     * @param sectors the sectors to manage
     */
    private SectorManager(List<Sector> sectors) {
        this.sectors = sectors;
    }

    /**
     * Builds sectors from a sensor network, using the default group size.
     *
     * @param network the sensor network of the venue
     * @param graph   the venue graph (used to find each panel's anchor node)
     * @return the populated sector manager
     */
    public static SectorManager build(SensorNetwork network, Graph graph) {
        return build(network, graph, DEFAULT_ROWS_PER_SECTOR);
    }

    /**
     * Builds sectors from a sensor network, grouping rows by {@code rowsPerSector}.
     *
     * @param network       the sensor network of the venue; never {@code null}
     * @param graph         the venue graph (may be {@code null}: panels then
     *                      simply have no anchor node)
     * @param rowsPerSector number of consecutive rows per sector ({@literal >=} 1)
     * @return the populated sector manager
     * @throws IllegalArgumentException if {@code network} is null
     */
    public static SectorManager build(SensorNetwork network, Graph graph, int rowsPerSector) {
        if (network == null) {
            throw new IllegalArgumentException("A sector manager needs a sensor network");
        }
        int step = Math.max(1, rowsPerSector);
        List<RowSensor> rows = network.getRowSensors();
        List<Sector> built = new ArrayList<>();
        int index = 1;
        for (int start = 0; start < rows.size(); start += step) {
            int end = Math.min(start + step, rows.size());
            List<RowSensor> group = new ArrayList<>(rows.subList(start, end));
            String sectorId = "SEC_" + index;
            RowSensor midRow = group.get(group.size() / 2);
            Node anchor = anchorNodeFor(graph, midRow.getRowId());
            DisplayPanel panel = new DisplayPanel("PANEL_" + sectorId, anchor);
            built.add(new Sector(sectorId, group, panel, anchor));
            index++;
        }
        return new SectorManager(built);
    }

    /**
     * Finds the best node to anchor a sector panel on: the centre corridor of
     * the given row if it exists, otherwise the first seat of the row.
     *
     * @param graph the venue graph (may be {@code null})
     * @param rowId the row label (e.g. "A")
     * @return the anchor node, or {@code null} if none can be found
     */
    private static Node anchorNodeFor(Graph graph, String rowId) {
        if (graph == null || rowId == null || rowId.isEmpty()) {
            return null;
        }
        int rowIndex = rowId.charAt(0) - 'A';
        Node centre = graph.getNode("CORR_C_" + rowIndex);
        if (centre != null) {
            return centre;
        }
        return graph.getNode("SEAT_" + rowId + "_1");
    }

    /**
     * Returns all sectors.
     *
     * @return a copy of the sector list
     */
    public List<Sector> getSectors() {
        return new ArrayList<>(sectors);
    }

    /**
     * Returns the number of sectors.
     *
     * @return the sector count
     */
    public int sectorCount() {
        return sectors.size();
    }

    /**
     * Returns the sector with the given id.
     *
     * @param sectorId the sector identifier (e.g. "SEC_2")
     * @return the matching sector, or {@code null} if none
     */
    public Sector getSectorById(String sectorId) {
        for (Sector sector : sectors) {
            if (sector.getSectorId().equals(sectorId)) {
                return sector;
            }
        }
        return null;
    }

    /**
     * Returns the sector containing a given row label.
     *
     * @param rowId the row label (e.g. "B")
     * @return the sector covering that row, or {@code null} if none
     */
    public Sector getSectorForRow(String rowId) {
        for (Sector sector : sectors) {
            if (sector.getRowLabels().contains(rowId)) {
                return sector;
            }
        }
        return null;
    }

    /**
     * Resets every panel to the standby message.
     */
    public void resetAllPanels() {
        for (Sector sector : sectors) {
            sector.getPanel().reset();
        }
    }

    /**
     * Enables or disables congestion monitoring on every sector at once.
     * Disabled during an evacuation so guidance messages are not overwritten.
     *
     * @param enabled {@code true} to monitor, {@code false} to mute
     */
    public void setMonitoring(boolean enabled) {
        for (Sector sector : sectors) {
            sector.setMonitoring(enabled);
        }
    }
}
