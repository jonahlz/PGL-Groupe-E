package fr.cytech.safeexit.model.graph;

import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.SimulationEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The graph modelling the concert hall: a set of {@link Node}s connected by
 * {@link Edge}s.
 * <p>
 * The graph supports live editing during the simulation (adding, removing and
 * moving nodes and edges). Removing a node also removes its incident edges. It
 * is {@link fr.cytech.safeexit.model.observer.Observable} so that views and the
 * router can react to structural changes.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class Graph extends AbstractObservable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Edge> edges = new LinkedHashMap<>();

    /**
     * Adds a node to the graph.
     *
     * @param node the node to add; never {@code null}
     * @throws GraphException if a node with the same id already exists
     */
    public void addNode(Node node) throws GraphException {
        if (node == null) {
            throw new GraphException("Cannot add a null node");
        }
        if (nodes.containsKey(node.getId())) {
            throw new GraphException("A node with id '" + node.getId() + "' already exists");
        }
        nodes.put(node.getId(), node);
        notifyObservers(new SimulationEvent(SimulationEvent.Type.NODE_STATE_CHANGED, node));
    }

    /**
     * Adds an edge to the graph. Both endpoints must already belong to the graph.
     *
     * @param edge the edge to add; never {@code null}
     * @throws GraphException if the id is taken or an endpoint is unknown
     */
    public void addEdge(Edge edge) throws GraphException {
        if (edge == null) {
            throw new GraphException("Cannot add a null edge");
        }
        if (edges.containsKey(edge.getId())) {
            throw new GraphException("An edge with id '" + edge.getId() + "' already exists");
        }
        if (!nodes.containsKey(edge.getSource().getId())
                || !nodes.containsKey(edge.getTarget().getId())) {
            throw new GraphException("Both endpoints of edge '" + edge.getId()
                    + "' must belong to the graph");
        }
        edges.put(edge.getId(), edge);
        notifyObservers(new SimulationEvent(SimulationEvent.Type.NODE_STATE_CHANGED, edge));
    }

    /**
     * Removes a node and all of its incident edges.
     *
     * @param node the node to remove
     * @return the list of edges that were removed as a side effect (so callers
     *         can relocate the agents that were on them)
     * @throws GraphException if the node does not belong to the graph
     */
    public List<Edge> removeNode(Node node) throws GraphException {
        if (node == null || !nodes.containsKey(node.getId())) {
            throw new GraphException("Cannot remove unknown node");
        }
        List<Edge> removed = new ArrayList<>();
        for (Edge edge : new ArrayList<>(edges.values())) {
            if (edge.getSource().equals(node) || edge.getTarget().equals(node)) {
                edges.remove(edge.getId());
                removed.add(edge);
            }
        }
        nodes.remove(node.getId());
        notifyObservers(new SimulationEvent(SimulationEvent.Type.NODE_STATE_CHANGED, node));
        return removed;
    }

    /**
     * Removes an edge from the graph.
     *
     * @param edge the edge to remove
     * @throws GraphException if the edge does not belong to the graph
     */
    public void removeEdge(Edge edge) throws GraphException {
        if (edge == null || edges.remove(edge.getId()) == null) {
            throw new GraphException("Cannot remove unknown edge");
        }
        notifyObservers(new SimulationEvent(SimulationEvent.Type.NODE_STATE_CHANGED, edge));
    }

    /**
     * Returns the edges incident to a node (respecting direction): edges along
     * which an agent located at {@code node} is allowed to travel.
     *
     * @param node the node to inspect
     * @return a new list of usable edges (possibly empty)
     */
    public List<Edge> getIncidentEdges(Node node) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges.values()) {
            if (edge.allowsTravelFrom(node)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Returns the nodes reachable from the given node in one edge step.
     *
     * @param node the node to inspect
     * @return a new list of neighbour nodes (possibly empty)
     */
    public List<Node> getNeighbors(Node node) {
        List<Node> result = new ArrayList<>();
        for (Edge edge : getIncidentEdges(node)) {
            result.add(edge.getOpposite(node));
        }
        return result;
    }

    /**
     * Finds the edge directly connecting two nodes, if any.
     *
     * @param from the starting node
     * @param to   the destination node
     * @return the connecting edge, or {@code null} if none allows that travel
     */
    public Edge getEdgeBetween(Node from, Node to) {
        for (Edge edge : edges.values()) {
            if (edge.allowsTravelFrom(from) && edge.getOpposite(from).equals(to)) {
                return edge;
            }
        }
        return null;
    }

    /**
     * Looks up a node by its identifier.
     *
     * @param id the node id
     * @return the node, or {@code null} if not found
     */
    public Node getNode(String id) {
        return nodes.get(id);
    }

    /**
     * Returns all nodes of type {@link NodeType#EXIT}.
     *
     * @return a new list of exit nodes (possibly empty)
     */
    public List<Node> getExits() {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (node.isExit()) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Returns an unmodifiable view of all nodes.
     *
     * @return all nodes
     */
    public List<Node> getNodes() {
        return Collections.unmodifiableList(new ArrayList<>(nodes.values()));
    }

    /**
     * Returns an unmodifiable view of all edges.
     *
     * @return all edges
     */
    public List<Edge> getEdges() {
        return Collections.unmodifiableList(new ArrayList<>(edges.values()));
    }

    /**
     * Returns the number of nodes in the graph.
     *
     * @return the node count
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Returns the number of edges in the graph.
     *
     * @return the edge count
     */
    public int edgeCount() {
        return edges.size();
    }
}
