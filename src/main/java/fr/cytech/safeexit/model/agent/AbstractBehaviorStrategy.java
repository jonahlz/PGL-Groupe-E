package fr.cytech.safeexit.model.agent;

import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;

import java.util.List;

/**
 * Base class shared by the concrete behaviour strategies.
 * <p>
 * It gathers geometric helpers (nearest exit, Euclidean distance) and a simple
 * greedy move toward the closest exit, reused by several strategies and as a
 * fallback when no precomputed route is available yet.
 *
 * @author GROUPE E
 * @version 1.0
 */
public abstract class AbstractBehaviorStrategy implements BehaviorStrategy {

    /**
     * Euclidean distance between two nodes.
     *
     * @param a first node
     * @param b second node
     * @return the straight-line distance
     */
    protected static double distance(Node a, Node b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Finds the geographically closest usable exit to a node.
     *
     * @param graph the venue graph
     * @param from  the reference node
     * @return the nearest non-blocked exit, or {@code null} if none exists
     */
    protected static Node nearestExit(Graph graph, Node from) {
        Node best = null;
        double bestDist = Double.MAX_VALUE;
        for (Node exit : graph.getExits()) {
            if (exit.isBlocked()) {
                continue;
            }
            double d = distance(from, exit);
            if (d < bestDist) {
                bestDist = d;
                best = exit;
            }
        }
        return best;
    }

    /**
     * Greedy step: among the neighbours reachable from the agent's current
     * node, returns the one that gets geographically closest to the nearest
     * exit. Blocked target nodes are skipped.
     *
     * @param agent the agent deciding where to go
     * @param graph the venue graph
     * @return the best neighbour, or the current node if none is suitable
     */
    protected Node greedyTowardNearestExit(Agent agent, Graph graph) {
        Node current = agent.getCurrentNode();
        Node exit = nearestExit(graph, current);
        if (exit == null) {
            return current;
        }
        Node best = current;
        double bestDist = distance(current, exit);
        for (Edge edge : graph.getIncidentEdges(current)) {
            if (edge.isBlocked()) {
                continue;
            }
            Node neighbour = edge.getOpposite(current);
            if (neighbour.isBlocked()) {
                continue;
            }
            double d = distance(neighbour, exit);
            if (d < bestDist) {
                bestDist = d;
                best = neighbour;
            }
        }
        return best;
    }

    /**
     * Helper returning the neighbours of a node, skipping blocked ones.
     *
     * @param graph the venue graph
     * @param node  the node to inspect
     * @return the list of reachable, non-blocked neighbours
     */
    protected static List<Node> usableNeighbours(Graph graph, Node node) {
        List<Node> neighbours = graph.getNeighbors(node);
        neighbours.removeIf(Node::isBlocked);
        return neighbours;
    }
}
