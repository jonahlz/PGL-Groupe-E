package fr.cytech.safeexit.model.venue;

import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.GraphException;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;

/**
 * Builds the graph of a concert hall: rows of seats, a left, centre and right
 * corridor, a stage and four emergency exits (S1..S4).
 * <p>
 * Coordinates are expressed in pixels so the resulting graph can be drawn
 * directly by the view. The builder is purely a model component: it produces a
 * {@link Graph} and never touches the user interface.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class ConcertHallBuilder {

    private static final double MARGIN_X = 140;
    private static final double TOP_Y = 150;
    private static final double ROW_SPACING = 48;
    private static final double SEAT_SPACING = 32;
    private static final double CENTRE_GAP = 60;
    private static final double CORRIDOR_OFFSET = 70;

    private int edgeCounter;

    /**
     * Builds a hall with the given number of rows and seats per row, a centre
     * aisle, two side corridors and four exits.
     *
     * @param rows        number of seat rows (&gt;= 1)
     * @param seatsPerRow number of seats per row (&gt;= 2)
     * @return the fully connected concert-hall graph
     * @throws GraphException if the graph cannot be assembled
     * @throws IllegalArgumentException if the dimensions are too small
     */
    public Graph buildHall(int rows, int seatsPerRow) throws GraphException {
        if (rows < 1 || seatsPerRow < 2) {
            throw new IllegalArgumentException("Hall needs at least 1 row and 2 seats per row");
        }
        edgeCounter = 0;
        Graph graph = new Graph();

        int half = seatsPerRow / 2;
        double rightBlockStartX = MARGIN_X + (half - 1) * SEAT_SPACING + CENTRE_GAP + CENTRE_GAP;
        double centreX = MARGIN_X + (half - 1) * SEAT_SPACING + CENTRE_GAP;
        double leftCorrX = MARGIN_X - CORRIDOR_OFFSET;
        double rightCorrX = rightBlockStartX + (seatsPerRow - half - 1) * SEAT_SPACING + CORRIDOR_OFFSET;
        double bottomY = TOP_Y + (rows - 1) * ROW_SPACING;

        Node[] leftCorr = new Node[rows];
        Node[] centreCorr = new Node[rows];
        Node[] rightCorr = new Node[rows];

        for (int i = 0; i < rows; i++) {
            double y = TOP_Y + i * ROW_SPACING;
            char rowLetter = (char) ('A' + (i % 26));

            // Corridor nodes for this row
            leftCorr[i] = addNode(graph, "CORR_L_" + i, NodeType.CORRIDOR, leftCorrX, y, 12);
            centreCorr[i] = addNode(graph, "CORR_C_" + i, NodeType.CORRIDOR, centreX, y, 12);
            rightCorr[i] = addNode(graph, "CORR_R_" + i, NodeType.CORRIDOR, rightCorrX, y, 12);

            // Vertical corridor chains
            if (i > 0) {
                connect(graph, leftCorr[i - 1], leftCorr[i], 4);
                connect(graph, centreCorr[i - 1], centreCorr[i], 5);
                connect(graph, rightCorr[i - 1], rightCorr[i], 4);
            }

            // Seats of the row, split into a left and a right block
            Node previous = null;
            for (int j = 0; j < seatsPerRow; j++) {
                double x;
                if (j < half) {
                    x = MARGIN_X + j * SEAT_SPACING;
                } else {
                    x = rightBlockStartX + (j - half) * SEAT_SPACING;
                }
                Node seat = addNode(graph, "SEAT_" + rowLetter + "_" + (j + 1),
                        NodeType.SEAT, x, y, 1);

                if (j == 0) {
                    connect(graph, leftCorr[i], seat, 2);          // left aisle access
                } else {
                    if (j == half) {
                        connect(graph, centreCorr[i], seat, 2);    // centre aisle (right block)
                    } else {
                        connect(graph, previous, seat, 1);         // seat to seat
                    }
                }
                if (j == half - 1) {
                    connect(graph, seat, centreCorr[i], 2);        // centre aisle (left block)
                }
                if (j == seatsPerRow - 1) {
                    connect(graph, seat, rightCorr[i], 2);         // right aisle access
                }
                previous = seat;
            }
        }

        // Stage at the top centre: attractive in normal mode.
        Node stage = addNode(graph, "STAGE", NodeType.STAGE, centreX, TOP_Y - 95, 0);
        stage.setAttractive(true);
        connect(graph, stage, centreCorr[0], 6);

        // Four emergency exits at the corners.
        Node s1 = addNode(graph, "EXIT_S1", NodeType.EXIT, leftCorrX, TOP_Y - 70, 60);
        Node s2 = addNode(graph, "EXIT_S2", NodeType.EXIT, rightCorrX, TOP_Y - 70, 60);
        Node s3 = addNode(graph, "EXIT_S3", NodeType.EXIT, leftCorrX, bottomY + 70, 60);
        Node s4 = addNode(graph, "EXIT_S4", NodeType.EXIT, rightCorrX, bottomY + 70, 60);
        connect(graph, s1, leftCorr[0], 6);
        connect(graph, s2, rightCorr[0], 6);
        connect(graph, s3, leftCorr[rows - 1], 6);
        connect(graph, s4, rightCorr[rows - 1], 6);

        return graph;
    }

    /**
     * Creates a node and adds it to the graph.
     *
     * @param graph    the graph to fill
     * @param id       node identifier
     * @param type     node type
     * @param x        x coordinate
     * @param y        y coordinate
     * @param capacity maximum capacity
     * @return the created node
     * @throws GraphException if the node cannot be added
     */
    private Node addNode(Graph graph, String id, NodeType type, double x, double y, int capacity)
            throws GraphException {
        Node node = new Node(id, type, x, y, capacity);
        graph.addNode(node);
        return node;
    }

    /**
     * Creates an undirected edge between two nodes and adds it to the graph.
     *
     * @param graph the graph to fill
     * @param a     first endpoint
     * @param b     second endpoint
     * @param width how many agents may travel on it at once
     * @throws GraphException if the edge cannot be added
     */
    private void connect(Graph graph, Node a, Node b, int width) throws GraphException {
        graph.addEdge(new Edge("E" + (edgeCounter++), a, b, false, width));
    }
}
