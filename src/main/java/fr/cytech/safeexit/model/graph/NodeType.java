package fr.cytech.safeexit.model.graph;

/**
 * Kinds of node that can appear in a concert-hall graph.
 *
 * @author GROUPE E
 * @version 1.0
 */
public enum NodeType {
    /** A numbered seat (row + number). */
    SEAT,
    /** A small aisle between two rows. */
    AISLE,
    /** A main corridor (horizontal or vertical). */
    CORRIDOR,
    /** An intersection between corridors. */
    CROSS_SECTION,
    /** An emergency exit (S1, S2, S3, S4). */
    EXIT,
    /** The stage: attractive in normal mode, to be avoided during evacuation. */
    STAGE,
    /** A zone dynamically blocked during the simulation. */
    BLOCKED_ZONE
}
