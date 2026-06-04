package fr.cytech.safeexit.model.routing;

import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.observer.Observer;
import fr.cytech.safeexit.model.observer.SimulationEvent;
import fr.cytech.safeexit.model.simulation.SimulationState;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Computes evacuation routes using a multi-source Dijkstra from the exits.
 * <p>
 * Running Dijkstra simultaneously from every usable exit partitions the graph
 * into Voronoi cells: each node is assigned to the exit it can reach with the
 * smallest travel cost, and gets a "next hop" telling an agent which neighbour
 * to step to in order to head for that exit. Blocked nodes, blocked edges and
 * blocked exits are excluded, so blocking something and recomputing naturally
 * reroutes everyone.
 * <p>
 * Edge cost is {@link Edge#getTraversalCost()} (length divided by the speed
 * modifier), so the routes minimise travel time rather than raw distance.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class VoronoiEvacuationRouter implements Observer {

    private Graph graph;
    private SimulationState state;

    /**
     * Creates a router bound to a graph and the simulation state it updates.
     *
     * @param graph the venue graph
     * @param state the simulation state whose routes are refreshed
     */
    public VoronoiEvacuationRouter(Graph graph, SimulationState state) {
        this.graph = graph;
        this.state = state;
    }

    /**
     * Recomputes the optimal routes and writes them into the simulation state.
     * <p>
     * For each reachable node it determines the assigned exit and the next node
     * to move to.
     */
    public void computeRoutes() {
        Map<Node, Double> dist = new HashMap<>();
        Map<Node, Node> nextHop = new HashMap<>();
        Map<Node, Node> exitOf = new HashMap<>();

        PriorityQueue<Node> pq = new PriorityQueue<>(
                (a, b) -> Double.compare(dist.getOrDefault(a, Double.MAX_VALUE),
                        dist.getOrDefault(b, Double.MAX_VALUE)));

        // Seed the queue with every usable exit (distance 0).
        for (Node exit : graph.getExits()) {
            if (!exit.isBlocked()) {
                dist.put(exit, 0.0);
                exitOf.put(exit, exit);
                nextHop.put(exit, exit);
                pq.add(exit);
            }
        }

        // Propagate outward; an agent at v will travel v -> u toward the exit.
        while (!pq.isEmpty()) {
            Node u = pq.poll();
            double du = dist.getOrDefault(u, Double.MAX_VALUE);
            for (Edge edge : edgesTouching(u)) {
                Node v = edge.getOpposite(u);
                if (v.isBlocked() || edge.isBlocked()) {
                    continue;
                }
                // The agent at v moves to u, so that travel direction must be allowed.
                if (!edge.allowsTravelFrom(v)) {
                    continue;
                }
                double nd = du + edge.getTraversalCost();
                if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, nd);
                    nextHop.put(v, u);
                    exitOf.put(v, exitOf.get(u));
                    pq.remove(v);
                    pq.add(v);
                }
            }
        }

        state.updateRoutes(nextHop, exitOf);
    }

    /**
     * Implements the brief's signature: returns, for each node, its assigned
     * exit. Delegates to {@link #computeRoutes()}.
     *
     * @param graph the venue graph (must match the bound graph)
     * @param exits ignored; the exits are read from the graph
     * @return a map from node to its assigned exit
     */
    public Map<Node, Node> computeOptimalRoutes(Graph graph, java.util.List<Node> exits) {
        this.graph = graph;
        computeRoutes();
        Map<Node, Node> result = new HashMap<>();
        for (Node node : graph.getNodes()) {
            Node exit = state.getAssignedExit(node);
            if (exit != null) {
                result.put(node, exit);
            }
        }
        return result;
    }

    /**
     * Reacts to obstacle events by recomputing the routes. Called when an edge
     * or a node is blocked or unblocked.
     *
     * @param event the simulation event
     */
    public void onObstacleDetected(SimulationEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case EDGE_BLOCKED, EDGE_UNBLOCKED, NODE_STATE_CHANGED -> computeRoutes();
            default -> { /* nothing to do */ }
        }
    }

    @Override
    public void update(SimulationEvent event) {
        onObstacleDetected(event);
    }

    /**
     * Returns every edge that has the given node as one of its endpoints,
     * regardless of edge direction.
     *
     * @param node the node to inspect
     * @return a list of edges touching the node
     */
    private java.util.List<Edge> edgesTouching(Node node) {
        java.util.List<Edge> result = new java.util.ArrayList<>();
        for (Edge edge : graph.getEdges()) {
            if (edge.getSource().equals(node) || edge.getTarget().equals(node)) {
                result.add(edge);
            }
        }
        return result;
    }
}
