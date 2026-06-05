package fr.cytech.safeexit.controller;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.GraphException;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;
import fr.cytech.safeexit.model.simulation.SimulationState;
import fr.cytech.safeexit.model.venue.ConcertHallBuilder;

/**
 * Controller in charge of preparing the venue and its occupants.
 * <p>
 * It bridges the view and the model for everything related to building a hall:
 * it asks the {@link ConcertHallBuilder} for a graph, wraps it in a {@link
 * SimulationState} and seats the agents. Keeping this out of the view honours
 * the MVC separation (no setup logic inside the JavaFX classes).
 *
 * @author GROUPE E
 * @version 1.0
 */
public class VenueController {

    private final ConcertHallBuilder builder = new ConcertHallBuilder();

    /**
     * Builds a concert hall, seats one agent per seat (up to {@code maxAgents})
     * and returns the ready-to-run simulation state.
     *
     * @param rows        number of seat rows
     * @param seatsPerRow number of seats per row
     * @param maxAgents   maximum number of agents to seat
     * @return a populated simulation state
     * @throws GraphException if the hall cannot be built
     */
    public SimulationState createHall(int rows, int seatsPerRow, int maxAgents)
            throws GraphException {
        Graph graph = builder.buildHall(rows, seatsPerRow);
        SimulationState state = new SimulationState(graph);
        seatAgents(state, maxAgents);
        return state;
    }

    /**
     * Seats agents on the first available seats of the graph and keeps the node
     * occupancy counters consistent.
     *
     * @param state     the simulation state to populate
     * @param maxAgents the maximum number of agents to seat
     */
    public void seatAgents(SimulationState state, int maxAgents) {
        int created = 0;
        for (Node node : state.getGraph().getNodes()) {
            if (created >= maxAgents) {
                break;
            }
            if (node.getType() == NodeType.SEAT) {
                Agent agent = new Agent(String.format("AGT_%03d", created + 1), node);
                node.incrementAgentCount();
                state.getAgents().add(agent);
                created++;
            }
        }
    }

    /**
     * Blocks an exit (or any node) by its identifier, if it exists.
     *
     * @param state the simulation state
     * @param nodeId the identifier of the node to block
     */
    public void blockNode(SimulationState state, String nodeId) {
        Node node = state.getGraph().getNode(nodeId);
        if (node != null) {
            node.setBlocked(true);
        }
    }
}
