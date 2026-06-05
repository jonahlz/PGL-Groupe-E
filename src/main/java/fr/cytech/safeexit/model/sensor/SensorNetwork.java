package fr.cytech.safeexit.model.sensor;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole occupancy-sensor layer of the venue.
 * <p>
 * It creates one {@link SeatSensor} per seat node and groups them into
 * {@link RowSensor}s (deduced from the seat identifiers, e.g. "SEAT_A_3"
 * belongs to row "A"). It is the entry point the simulation uses to keep the
 * sensors in sync with the agents in real time (a seat is freed as soon as its
 * occupant leaves).
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SensorNetwork implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<Node, SeatSensor> sensorBySeat = new LinkedHashMap<>();
    private final Map<String, RowSensor> rowSensors = new LinkedHashMap<>();

    /**
     * Builds the sensor network from a graph and the agents seated in it.
     * Each seat node gets a sensor, initially occupied if an agent is sitting
     * on it. Sensors are grouped into row sensors.
     *
     * @param graph  the venue graph
     * @param agents the agents (used to set the initial occupancy)
     * @return the populated sensor network
     */
    public static SensorNetwork build(Graph graph, List<Agent> agents) {
        SensorNetwork network = new SensorNetwork();

        for (Node node : graph.getNodes()) {
            if (node.getType() == NodeType.SEAT) {
                SeatSensor sensor = new SeatSensor(node);
                network.sensorBySeat.put(node, sensor);
                network.rowSensorFor(node.getId()).addSeatSensor(sensor);
            }
        }
        // Initial occupancy from the seated agents.
        for (Agent agent : agents) {
            Node seat = agent.getCurrentNode();
            SeatSensor sensor = seat == null ? null : network.sensorBySeat.get(seat);
            if (sensor != null) {
                sensor.markOccupied(agent);
            }
        }
        return network;
    }

    /**
     * Returns (creating it if needed) the row sensor matching a seat id such as
     * "SEAT_A_3".
     *
     * @param seatId the seat identifier
     * @return the row sensor for that row
     */
    private RowSensor rowSensorFor(String seatId) {
        String rowId = parseRowId(seatId);
        return rowSensors.computeIfAbsent(rowId, RowSensor::new);
    }

    /**
     * Extracts the row label from a seat identifier ("SEAT_A_3" -&gt; "A").
     *
     * @param seatId the seat identifier
     * @return the row label, or the whole id if it does not match the pattern
     */
    private static String parseRowId(String seatId) {
        String[] parts = seatId.split("_");
        return parts.length >= 2 ? parts[1] : seatId;
    }

    /**
     * Frees the sensor of a seat whose occupant just left it.
     *
     * @param seat the seat node being vacated
     */
    public void onAgentLeftSeat(Node seat) {
        SeatSensor sensor = sensorBySeat.get(seat);
        if (sensor != null && sensor.isOccupied()) {
            sensor.markFree();
        }
    }

    /**
     * Returns the sensor monitoring a given seat.
     *
     * @param seat the seat node
     * @return the sensor, or {@code null} if the node is not a monitored seat
     */
    public SeatSensor getSensor(Node seat) {
        return sensorBySeat.get(seat);
    }

    /**
     * Returns all seat sensors.
     *
     * @return the list of seat sensors
     */
    public List<SeatSensor> getSeatSensors() {
        return new ArrayList<>(sensorBySeat.values());
    }

    /**
     * Returns all row sensors.
     *
     * @return the list of row sensors
     */
    public List<RowSensor> getRowSensors() {
        return new ArrayList<>(rowSensors.values());
    }

    /**
     * Counts the seats currently occupied across the whole venue.
     *
     * @return the number of occupied seats
     */
    public int totalOccupied() {
        int n = 0;
        for (SeatSensor s : sensorBySeat.values()) {
            if (s.isOccupied()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Returns the total number of monitored seats.
     *
     * @return the seat count
     */
    public int totalSeats() {
        return sensorBySeat.size();
    }
}
