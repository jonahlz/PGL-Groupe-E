package fr.cytech.safeexit.model.graph;

import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.SimulationEvent;

/**
 * A node in the concert-hall graph.
 * <p>
 * A node is a point in 2D space (pixel coordinates on the canvas) that can be a
 * seat, an aisle, a corridor, an exit, the stage or a blocked zone. Agents are
 * considered to move through a node instantaneously; congestion only happens on
 * the {@link Edge}s. A node has a maximum capacity but may temporarily exceed it
 * (heavy-congestion mode) when agents are pushed into it.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class Node extends AbstractObservable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private NodeType type;
    private double x;
    private double y;
    private int maxCapacity;
    private boolean blocked;
    private boolean attractive;
    private int currentAgentCount;

    /**
     * Creates a node.
     *
     * @param id          unique identifier (e.g. "ROW_A_SEAT_12", "EXIT_S1")
     * @param type        the kind of node
     * @param x           x coordinate in the 2D plane
     * @param y           y coordinate in the 2D plane
     * @param maxCapacity maximum number of agents the node should hold
     * @throws IllegalArgumentException if {@code id} is null/blank or capacity is negative
     */
    public Node(String id, NodeType type, double x, double y, int maxCapacity) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Node id must not be null or blank");
        }
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("Node capacity must not be negative");
        }
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.maxCapacity = maxCapacity;
    }

    /**
     * Indicates whether this node can accept one more agent without becoming
     * congested.
     *
     * @return {@code true} if the node is not blocked and is below capacity
     */
    public boolean hasCapacity() {
        return !blocked && currentAgentCount < maxCapacity;
    }

    /**
     * Indicates whether the node currently holds more agents than its maximum
     * capacity (heavy-congestion mode).
     *
     * @return {@code true} if {@code currentAgentCount > maxCapacity}
     */
    public boolean isCongested() {
        return currentAgentCount > maxCapacity;
    }

    /**
     * Increments the agent counter (an agent entered the node).
     */
    public void incrementAgentCount() {
        currentAgentCount++;
    }

    /**
     * Decrements the agent counter (an agent left the node), never going below 0.
     */
    public void decrementAgentCount() {
        if (currentAgentCount > 0) {
            currentAgentCount--;
        }
    }

    /**
     * Returns the unique identifier of this node.
     *
     * @return the node id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the kind of this node.
     *
     * @return the node type
     */
    public NodeType getType() {
        return type;
    }

    /**
     * Changes the kind of this node and notifies observers.
     *
     * @param type the new node type
     */
    public void setType(NodeType type) {
        this.type = type;
        notifyObservers(new SimulationEvent(SimulationEvent.Type.NODE_STATE_CHANGED, this));
    }

    /**
     * Returns the x coordinate.
     *
     * @return x position
     */
    public double getX() {
        return x;
    }

    /**
     * Returns the y coordinate.
     *
     * @return y position
     */
    public double getY() {
        return y;
    }

    /**
     * Moves the node to a new position and notifies observers.
     *
     * @param x new x coordinate
     * @param y new y coordinate
     */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
        notifyObservers(new SimulationEvent(SimulationEvent.Type.NODE_STATE_CHANGED, this));
    }

    /**
     * Returns the maximum number of agents this node should hold.
     *
     * @return the maximum capacity
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * Sets the maximum capacity of this node.
     *
     * @param maxCapacity the new maximum capacity (must be &gt;= 0)
     */
    public void setMaxCapacity(int maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("Node capacity must not be negative");
        }
        this.maxCapacity = maxCapacity;
    }

    /**
     * Indicates whether the node is blocked (closed exit, danger zone...).
     *
     * @return {@code true} if blocked
     */
    public boolean isBlocked() {
        return blocked;
    }

    /**
     * Blocks or unblocks the node and notifies observers.
     *
     * @param blocked the new blocked state
     */
    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
        notifyObservers(new SimulationEvent(SimulationEvent.Type.NODE_STATE_CHANGED, this));
    }

    /**
     * Indicates whether the node is attractive (stage, animation) as opposed to
     * repulsive (danger zone).
     *
     * @return {@code true} if attractive
     */
    public boolean isAttractive() {
        return attractive;
    }

    /**
     * Sets whether the node is attractive.
     *
     * @param attractive the new attractive flag
     */
    public void setAttractive(boolean attractive) {
        this.attractive = attractive;
    }

    /**
     * Returns the number of agents currently in the node.
     *
     * @return the current agent count
     */
    public int getCurrentAgentCount() {
        return currentAgentCount;
    }

    /**
     * Convenience method telling whether this node is an emergency exit.
     *
     * @return {@code true} if the node type is {@link NodeType#EXIT}
     */
    public boolean isExit() {
        return type == NodeType.EXIT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return (o instanceof Node other) && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
