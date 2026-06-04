package fr.cytech.safeexit;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.agent.AgentState;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.GraphException;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;
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
        boolean cli = args.length > 0 && "--cli".equalsIgnoreCase(args[0]);
        if (cli) {
            runEvacuationDemo();
        } else {
            // Graphical mode: launch the JavaFX application.
            SafeExitApp.main(args);
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
