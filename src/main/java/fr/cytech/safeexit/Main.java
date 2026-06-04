package fr.cytech.safeexit;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.agent.AgentState;
import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.GraphException;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;
import fr.cytech.safeexit.model.simulation.SimulationState;

/**
 * Entry point of the SafeExit application.
 * <p>
 * Two execution modes are planned, both driving the very same model:
 * <ul>
 *   <li>graphical mode (JavaFX) &mdash; the default, evaluated mode;</li>
 *   <li>command-line mode (argument {@code --cli}) &mdash; lets the model be
 *       tested independently of the user interface, as required by the brief.</li>
 * </ul>
 * For now only a small self-test of the model is wired in; the JavaFX
 * application and the full CLI are added in later steps.
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
            System.out.println("SafeExit - command-line mode (model self-test)");
            runModelSelfTest();
        } else {
            // Graphical mode: launch the JavaFX application.
            SafeExitApp.main(args);
        }
    }

    /**
     * Builds a tiny graph (two seats, an aisle and an exit) and prints it, just
     * to confirm the model layer compiles and behaves as expected.
     */
    private static void runModelSelfTest() {
        try {
            Graph graph = new Graph();

            Node seatA = new Node("ROW_A_SEAT_1", NodeType.SEAT, 100, 100, 1);
            Node seatB = new Node("ROW_A_SEAT_2", NodeType.SEAT, 140, 100, 1);
            Node aisle = new Node("AISLE_CENTER", NodeType.AISLE, 120, 160, 8);
            Node exit = new Node("EXIT_S1", NodeType.EXIT, 120, 240, 50);

            graph.addNode(seatA);
            graph.addNode(seatB);
            graph.addNode(aisle);
            graph.addNode(exit);

            graph.addEdge(new Edge("E1", seatA, aisle, false, 2));
            graph.addEdge(new Edge("E2", seatB, aisle, false, 2));
            graph.addEdge(new Edge("E3", aisle, exit, false, 4));

            System.out.printf("Graph built: %d nodes, %d edges%n",
                    graph.nodeCount(), graph.edgeCount());
            System.out.println("Exits: " + graph.getExits());
            System.out.println("Neighbours of AISLE_CENTER: " + graph.getNeighbors(aisle));
            System.out.println("Edge length E3 = "
                    + String.format("%.1f", graph.getEdgeBetween(aisle, exit).getLength()));

            // --- Agent / Strategy self-test ---
            SimulationState state = new SimulationState(graph);
            Agent agent = new Agent("AGT_001", seatA);
            state.getAgents().add(agent);

            System.out.println();
            System.out.println("Agent created: " + agent);
            System.out.println("  state=" + agent.getState()
                    + ", strategy=" + agent.getStrategy().getClass().getSimpleName());
            System.out.println("  calm next node from " + agent.getCurrentNode()
                    + " -> " + agent.chooseNextNode(graph, state));

            agent.setState(AgentState.PANICKED);
            System.out.println("After panic: strategy="
                    + agent.getStrategy().getClass().getSimpleName()
                    + ", next node -> " + agent.chooseNextNode(graph, state));

            agent.setState(AgentState.FROZEN);
            System.out.println("After freeze: canMove=" + agent.canMove()
                    + " (delay), stays at -> " + agent.chooseNextNode(graph, state));
            System.out.println("History size after state changes: "
                    + agent.getHistory().size());
        } catch (GraphException e) {
            System.err.println("Model self-test failed: " + e.getMessage());
        }
    }
}
