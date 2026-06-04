package fr.cytech.safeexit.model.graph;

/**
 * Thrown when an illegal operation is attempted on the graph, such as adding a
 * duplicate node, linking an edge to an unknown node, or removing an element
 * that does not exist.
 * <p>
 * It is a checked exception on purpose: callers (controllers) must decide how
 * to report the problem to the user, in accordance with the project rule that
 * the application must never crash.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class GraphException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new graph exception with an explanatory message.
     *
     * @param message a human-readable description of the problem
     */
    public GraphException(String message) {
        super(message);
    }
}
