package fr.cytech.safeexit.controller;

import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.simulation.SimulationEngine;

/**
 * Controller handling live edits of the graph during the simulation.
 * <p>
 * For this milestone it lets the user block or unblock a node or an edge by
 * clicking it. Blocking emits the matching model event, and the controller asks
 * the engine to recompute the Voronoi routes immediately so the agents reroute
 * on screen, even while the simulation is paused. The controller performs the
 * model action; the picking (hit-testing) is done by the view.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class GraphController {

    private final SimulationEngine engine;
    private Runnable onChange;

    /**
     * Creates a graph controller bound to the running engine.
     *
     * @param engine the simulation engine to refresh after an edit
     */
    public GraphController(SimulationEngine engine) {
        this.engine = engine;
    }

    /**
     * Sets the callback run after a successful edit (typically the view repaint).
     *
     * @param onChange the callback
     */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Toggles the blocked state of a picked element (node or edge) and reroutes.
     *
     * @param element the picked {@link Node} or {@link Edge}, or {@code null}
     */
    public void toggleBlocked(Object element) {
        if (element instanceof Node node) {
            node.setBlocked(!node.isBlocked());
        } else if (element instanceof Edge edge) {
            edge.setBlocked(!edge.isBlocked());
        } else {
            return;
        }
        // Reroute immediately so the change is visible at once.
        engine.recomputeRoutes();
        if (onChange != null) {
            onChange.run();
        }
    }
}
