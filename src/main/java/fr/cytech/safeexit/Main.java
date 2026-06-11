package fr.cytech.safeexit;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.agent.AgentState;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.GraphException;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;
import fr.cytech.safeexit.model.sensor.RowSensor;
import fr.cytech.safeexit.model.sensor.SeatSensor;
import fr.cytech.safeexit.model.sensor.SensorNetwork;
import fr.cytech.safeexit.model.simulation.SimulationEngine;
import fr.cytech.safeexit.model.simulation.SimulationState;
import fr.cytech.safeexit.model.venue.ConcertHallBuilder;

/**
 * Entry point of the SafeExit application.
 * <p>
 * Two execution modes are planned, both driving the very same model:
 * <ul>
 *   <li>graphical mode (JavaFX) &mdash; the default, evaluated mode;</li>
 *   <li>command-line mode (argument {@code --cli}) &mdash; lets the model be
 *       tested independently of the user interface, as required by the brief.</li>
 * </ul>
 * In command-line mode it runs a full evacuation simulation on a generated
 * concert hall and prints the progress, which lets the model be validated
 * without the graphical interface.
 *
 * @author GROUPE E
 * @version 1.0
 */
public final class Main {

    /** Utility class: no instances. */
    private Main() {
    }

    /**
     * Program entry point.
     *
     * @param args command-line arguments; {@code --cli} selects command-line mode
     */
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase() : "";
        switch (mode) {
            case "--cli" -> runEvacuationDemo();
            case "--sensors" -> runSensorDemo();
            default -> SafeExitApp.main(args); // graphical mode
        }
    }

    /**
     * Demonstrates the seat-occupancy sensor layer from the console: it builds a
     * hall, shows the initial occupancy, simulates a couple of real-life cases
     * (a spectator goes to the toilets, another changes seats) and then runs the
     * evacuation, showing the seats being freed in real time.
     */
    private static void runSensorDemo() {
        System.out.println("SafeExit - seat occupancy sensors (command-line test)");
        try {
            Graph graph = new ConcertHallBuilder().buildHall(4, 8);
            SimulationState state = new SimulationState(graph);
            int created = 0;
            for (Node node : graph.getNodes()) {
                if (node.getType() == NodeType.SEAT) {
                    Agent agent = new Agent(String.format("AGT_%03d", ++created), node);
                    node.incrementAgentCount();
                    state.getAgents().add(agent);
                }
            }

            // Build and attach the sensor layer.
            SensorNetwork sensors = SensorNetwork.build(graph, state.getAgents());
            state.setSensorNetwork(sensors);

            // Build the sector / display-panel layer on top of the sensors and
            // show it in the console (proves the centralised system runs without JavaFX).
            fr.cytech.safeexit.model.sector.SectorManager sectorManager =
                    fr.cytech.safeexit.model.sector.SectorManager.build(sensors, graph);
            state.setSectorManager(sectorManager);
            System.out.printf("%nSectors created: %d (%d rows per sector)%n",
                    sectorManager.sectorCount(),
                    fr.cytech.safeexit.model.sector.SectorManager.DEFAULT_ROWS_PER_SECTOR);
            for (fr.cytech.safeexit.model.sector.Sector sector : sectorManager.getSectors()) {
                System.out.println("  " + sector);
            }

            System.out.printf("%nInitial occupancy: %d / %d seats occupied%n",
                    sensors.totalOccupied(), sensors.totalSeats());
            printRowOccupancy(sensors);

            // Case: a spectator goes to the toilets (seat stays reserved).
            Agent a0 = state.getAgents().get(0);
            SeatSensor s0 = sensors.getSensor(a0.getCurrentNode());
            s0.markAway();
            System.out.printf("%nCas 'toilettes' : %s -> %s (siège réservé)%n",
                    s0.getSeat().getId(), s0.getStatus());
            System.out.printf("Occupancy now: %d / %d%n",
                    sensors.totalOccupied(), sensors.totalSeats());

            // Then the spectator comes back to their seat.
            s0.markOccupied(a0);
            System.out.printf("Retour à sa place : %s -> %s%n",
                    s0.getSeat().getId(), s0.getStatus());
            System.out.printf("Occupancy now: %d / %d%n",
                    sensors.totalOccupied(), sensors.totalSeats());

            // Run the evacuation: seats are freed live as occupants leave.
            System.out.println("\nÉvacuation — les sièges se libèrent en temps réel :");
            SimulationEngine engine = new SimulationEngine(state);
            int total = state.getAgents().size();
            int cycle = 0;
            while (cycle < 5000 && !engine.isEvacuationComplete()) {
                engine.tick();
                cycle++;
                if (cycle % 20 == 0) {
                    System.out.printf("  cycle %4d : %d sièges encore occupés, %d évacués%n",
                            cycle, sensors.totalOccupied(), engine.countEvacuated());
                }
            }
            System.out.printf("%nTerminé en %d cycles : %d sièges occupés, %d / %d évacués.%n",
                    cycle, sensors.totalOccupied(), engine.countEvacuated(), total);
        } catch (GraphException e) {
            System.err.println("Sensor demo failed: " + e.getMessage());
        }
    }

    /**
     * Prints the occupancy of each row from the sensor network.
     *
     * @param sensors the sensor network
     */
    private static void printRowOccupancy(SensorNetwork sensors) {
        for (RowSensor row : sensors.getRowSensors()) {
            System.out.printf("  Rangée %-2s : %d / %d occupés (%.0f%%)%n",
                    row.getRowId(), row.occupiedCount(), row.seatCount(),
                    row.occupancyRatio() * 100);
        }
    }

    /**
     * Builds a concert hall, fills it with agents, blocks one exit and runs the
     * evacuation to completion, printing the progress. This exercises the whole
     * model (graph, agents, Voronoi router and simulation engine) without the
     * graphical interface.
     */
    private static void runEvacuationDemo() {
        System.out.println("SafeExit - command-line evacuation simulation");
        try {
            int rows = 6;
            int seatsPerRow = 10;
            Graph graph = new ConcertHallBuilder().buildHall(rows, seatsPerRow);

            // Block exit S4 to demonstrate dynamic Voronoi rerouting.
            Node blocked = graph.getNode("EXIT_S4");
            if (blocked != null) {
                blocked.setBlocked(true);
            }

            SimulationState state = new SimulationState(graph);
            int created = 0;
            for (Node node : graph.getNodes()) {
                if (node.getType() == NodeType.SEAT) {
                    Agent agent = new Agent(String.format("AGT_%03d", ++created), node);
                    node.incrementAgentCount();
                    // A few spectators panic.
                    if (created % 7 == 0) {
                        agent.setState(AgentState.PANICKED);
                    }
                    state.getAgents().add(agent);
                }
            }

            SimulationEngine engine = new SimulationEngine(state);
            int total = state.getAgents().size();
            System.out.printf("Hall: %d rows x %d seats -> %d nodes, %d edges%n",
                    rows, seatsPerRow, graph.nodeCount(), graph.edgeCount());
            System.out.printf("Exits available: %d (S4 blocked)%n", graph.getExits().size() - 1);
            System.out.printf("Agents to evacuate: %d%n%n", total);

            int maxCycles = 5000;
            int cycle = 0;
            while (cycle < maxCycles && !engine.isEvacuationComplete()) {
                engine.tick();
                cycle++;
                if (cycle % 25 == 0) {
                    System.out.printf("  cycle %4d : %d / %d evacuated%n",
                            cycle, engine.countEvacuated(), total);
                }
            }

            System.out.println();
            if (engine.isEvacuationComplete()) {
                System.out.printf("Evacuation complete in %d cycles (%d agents).%n",
                        cycle, total);
            } else {
                System.out.printf("Stopped after %d cycles: %d / %d evacuated.%n",
                        cycle, engine.countEvacuated(), total);
            }
        } catch (GraphException e) {
            System.err.println("Simulation failed: " + e.getMessage());
        }
    }
}
