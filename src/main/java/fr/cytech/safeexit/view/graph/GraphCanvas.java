package fr.cytech.safeexit.view.graph;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;
import fr.cytech.safeexit.model.sensor.SeatSensor;
import fr.cytech.safeexit.model.sensor.SeatStatus;
import fr.cytech.safeexit.model.sensor.SensorNetwork;
import fr.cytech.safeexit.model.simulation.SimulationState;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * JavaFX view that renders the concert hall and the agents on a 2D canvas.
 * <p>
 * Two display modes help reading the evacuation:
 * <ul>
 *   <li>{@link DisplayMode#EXIT_ZONES}: each area and each agent is coloured by
 *       the exit it is assigned to (the Voronoi cell), and small arrows show the
 *       flow direction toward that exit;</li>
 *   <li>{@link DisplayMode#DENSITY}: a heatmap colours nodes from their base
 *       colour to red as they get crowded.</li>
 * </ul>
 * The view supports zoom (wheel), pan (drag) and picking (click). It only reads
 * the model to draw it and contains no business logic (MVC separation).
 *
 * @author GROUPE E
 * @version 1.0
 */
public class GraphCanvas extends Pane {

    /** Available rendering modes. */
    public enum DisplayMode {
        /** Colour by assigned exit (Voronoi cells) with flow arrows. */
        EXIT_ZONES,
        /** Colour by crowd density (heatmap). */
        DENSITY,
        /** Colour seats by their occupancy sensor (occupied / free / away). */
        OCCUPANCY
    }

    private static final Color BACKGROUND = Color.web("#1a1a2e");
    private static final Color EDGE_COLOR = Color.web("#3a4566");
    private static final Color BLOCKED_COLOR = Color.web("#e74c3c");
    private static final Color NEUTRAL_AGENT = Color.web("#cfd8e6");

    private final Canvas canvas;
    private SimulationState state;
    private DisplayMode mode = DisplayMode.EXIT_ZONES;

    // Layer visibility (used by the dashboard filter buttons)
    private boolean showAgents = true;
    private boolean showFlow = true;

    // Camera transform
    private double scale = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;
    private double lastMouseX;
    private double lastMouseY;
    private boolean dragged;

    // Called with the picked Node or Edge when the user clicks an element.
    private java.util.function.Consumer<Object> onPick;

    /**
     * Creates an empty canvas wired with zoom, pan and pick interactions.
     */
    public GraphCanvas() {
        this.canvas = new Canvas();
        getChildren().add(canvas);
        setStyle("-fx-background-color: #1a1a2e;");

        widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(n.doubleValue());
            redraw();
        });
        heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(n.doubleValue());
            redraw();
        });

        setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            scale = Math.max(0.3, Math.min(4.0, scale * factor));
            redraw();
        });

        setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            dragged = false;
        });
        setOnMouseDragged(e -> {
            offsetX += e.getX() - lastMouseX;
            offsetY += e.getY() - lastMouseY;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            dragged = true;
            redraw();
        });
        setOnMouseReleased(e -> {
            if (!dragged && onPick != null) {
                Object picked = pick(e.getX(), e.getY());
                if (picked != null) {
                    onPick.accept(picked);
                }
            }
        });
    }

    /**
     * Sets the simulation state to display and triggers a redraw.
     *
     * @param state the simulation state (graph, agents, routes)
     */
    public void setModel(SimulationState state) {
        this.state = state;
        redraw();
    }

    /**
     * Sets the rendering mode and redraws.
     *
     * @param mode the display mode
     */
    public void setDisplayMode(DisplayMode mode) {
        this.mode = mode;
        redraw();
    }

    /**
     * Registers the callback invoked with the picked {@link Node} or {@link
     * Edge} when the user clicks an element.
     *
     * @param onPick the pick callback
     */
    public void setOnPick(java.util.function.Consumer<Object> onPick) {
        this.onPick = onPick;
    }

    /**
     * Returns the displayed graph, or {@code null} if no model is set.
     *
     * @return the graph
     */
    private Graph graph() {
        return state == null ? null : state.getGraph();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Redraws the whole scene (background, edges, flow, nodes, agents).
     */
    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(BACKGROUND);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        Graph graph = graph();
        if (graph == null) {
            return;
        }
        drawEdges(g, graph);
        if (mode == DisplayMode.EXIT_ZONES && showFlow) {
            drawFlowArrows(g, graph);
        }
        drawNodes(g, graph);
        if (showAgents) {
            drawAgents(g);
        }
    }

    /**
     * Shows or hides the agents layer.
     *
     * @param showAgents {@code true} to draw agents
     */
    public void setShowAgents(boolean showAgents) {
        this.showAgents = showAgents;
        redraw();
    }

    /**
     * Shows or hides the flow-direction arrows.
     *
     * @param showFlow {@code true} to draw the arrows
     */
    public void setShowFlow(boolean showFlow) {
        this.showFlow = showFlow;
        redraw();
    }

    /**
     * Draws every edge as a line, thicker when the edge is wider.
     *
     * @param g     the graphics context
     * @param graph the graph
     */
    private void drawEdges(GraphicsContext g, Graph graph) {
        for (Edge edge : graph.getEdges()) {
            g.setStroke(edge.isBlocked() ? BLOCKED_COLOR : EDGE_COLOR);
            g.setLineWidth((1 + edge.getMaxConcurrentAgents() * 0.5) * scale);
            g.strokeLine(tx(edge.getSource().getX()), ty(edge.getSource().getY()),
                    tx(edge.getTarget().getX()), ty(edge.getTarget().getY()));
        }
    }

    /**
     * Draws a small arrow on each node pointing to its next hop toward the exit,
     * coloured by the assigned exit, to make the evacuation flow readable.
     *
     * @param g     the graphics context
     * @param graph the graph
     */
    private void drawFlowArrows(GraphicsContext g, Graph graph) {
        for (Node node : graph.getNodes()) {
            if (node.isExit() || node.isBlocked() || node.getType() == NodeType.STAGE) {
                continue;
            }
            Node next = state.getNextHop(node);
            if (next == null || next.equals(node)) {
                continue;
            }
            Color c = exitColor(state.getAssignedExit(node)).deriveColor(0, 1, 1, 0.55);
            drawArrow(g, node.getX(), node.getY(), next.getX(), next.getY(), c);
        }
    }

    /**
     * Draws an arrow from one model point toward another (head at 60% of the way).
     *
     * @param g     the graphics context
     * @param ax    start x (model)
     * @param ay    start y (model)
     * @param bx    end x (model)
     * @param by    end y (model)
     * @param color the arrow colour
     */
    private void drawArrow(GraphicsContext g, double ax, double ay, double bx, double by,
                           Color color) {
        double sx = tx(ax);
        double sy = ty(ay);
        double hx = tx(ax + (bx - ax) * 0.6);
        double hy = ty(ay + (by - ay) * 0.6);
        double angle = Math.atan2(hy - sy, hx - sx);
        double head = 5 * scale;

        g.setStroke(color);
        g.setLineWidth(1.6 * scale);
        g.strokeLine(sx, sy, hx, hy);
        g.strokeLine(hx, hy, hx - head * Math.cos(angle - 0.4), hy - head * Math.sin(angle - 0.4));
        g.strokeLine(hx, hy, hx - head * Math.cos(angle + 0.4), hy - head * Math.sin(angle + 0.4));
    }

    /**
     * Draws every node as a coloured circle sized and coloured by its type.
     *
     * @param g     the graphics context
     * @param graph the graph
     */
    private void drawNodes(GraphicsContext g, Graph graph) {
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font(11 * scale));
        for (Node node : graph.getNodes()) {
            double r = radiusFor(node) * scale;
            double x = tx(node.getX());
            double y = ty(node.getY());

            g.setFill(fillColorFor(node));
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
            g.setStroke(Color.web("#0d0d1a"));
            g.setLineWidth(1);
            g.strokeOval(x - r, y - r, 2 * r, 2 * r);

            if (node.isExit()) {
                g.setFill(Color.WHITE);
                g.fillText(node.getId().replace("EXIT_", ""), x, y - r - 4 * scale);
            }
        }
    }

    /**
     * Draws every active agent as a small dot, interpolating its position along
     * the edge when travelling and colouring it according to the display mode.
     *
     * @param g the graphics context
     */
    private void drawAgents(GraphicsContext g) {
        if (state == null) {
            return;
        }
        double rMove = 5 * scale;   // person moving in the corridors
        double rSeat = 2.5 * scale; // small dot on an occupied seat
        SensorNetwork network = state.getSensorNetwork();
        java.util.Map<Node, Integer> stackIndex = new java.util.HashMap<>();
        for (Agent agent : state.getAgents()) {
            if (agent.hasExited()) {
                continue;
            }
            double mx;
            double my;
            double r;
            if (agent.getCurrentNode() != null) {
                Node node = agent.getCurrentNode();
                if (node.getType() == NodeType.SEAT && network != null) {
                    SeatSensor sensor = network.getSensor(node);
                    // No person on an empty or temporarily-vacated seat.
                    if (sensor != null && sensor.getStatus() != SeatStatus.OCCUPIED) {
                        continue;
                    }
                    // Small dot so the seat's occupancy colour stays visible.
                    mx = node.getX();
                    my = node.getY();
                    r = rSeat;
                } else {
                    // Fan out the people gathered on a corridor node.
                    int idx = stackIndex.merge(node, 1, Integer::sum) - 1;
                    double angle = idx * 2.39996;
                    double radius = idx == 0 ? 0 : 4 + 2.2 * Math.sqrt(idx);
                    mx = node.getX() + radius * Math.cos(angle);
                    my = node.getY() + radius * Math.sin(angle);
                    r = rMove;
                }
            } else if (agent.getCurrentEdge() != null && agent.getEdgeDestination() != null) {
                Node dest = agent.getEdgeDestination();
                Node start = agent.getCurrentEdge().getOpposite(dest);
                double t = agent.getProgressOnEdge();
                mx = start.getX() + (dest.getX() - start.getX()) * t;
                my = start.getY() + (dest.getY() - start.getY()) * t;
                r = rMove;
            } else {
                continue;
            }
            double x = tx(mx);
            double y = ty(my);
            g.setFill(agentColor(agent));
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
            g.setStroke(agent.getState() == fr.cytech.safeexit.model.agent.AgentState.PANICKED
                    ? Color.web("#ff7043") : Color.WHITE);
            g.setLineWidth(agent.getState() == fr.cytech.safeexit.model.agent.AgentState.PANICKED
                    ? 2 : 1);
            g.strokeOval(x - r, y - r, 2 * r, 2 * r);
        }
    }

    /**
     * Returns the colour of an agent: by assigned exit in exit-zone mode, by
     * state in density mode.
     *
     * @param agent the agent
     * @return the fill colour
     */
    private Color agentColor(Agent agent) {
        if (mode == DisplayMode.EXIT_ZONES) {
            Node exit = agent.getTargetExit();
            if (exit == null && agent.getCurrentNode() != null) {
                exit = state.getAssignedExit(agent.getCurrentNode());
            }
            return exit == null ? NEUTRAL_AGENT : exitColor(exit).brighter();
        }
        return switch (agent.getState()) {
            case PANICKED -> Color.web("#ff7043");
            case FROZEN -> Color.web("#9e9e9e");
            default -> Color.web("#00b0ff");
        };
    }

    /**
     * Returns the fill colour of a node, depending on the display mode.
     *
     * @param node the node
     * @return the fill colour to use
     */
    private Color fillColorFor(Node node) {
        if (node.isBlocked()) {
            return BLOCKED_COLOR;
        }
        if (node.isExit()) {
            return exitColor(node);
        }
        if (node.getType() == NodeType.STAGE) {
            return Color.web("#f1c40f");
        }
        Color base = colorFor(node);
        if (mode == DisplayMode.OCCUPANCY) {
            if (node.getType() == NodeType.SEAT && state.getSensorNetwork() != null) {
                SeatSensor sensor = state.getSensorNetwork().getSensor(node);
                if (sensor != null) {
                    return seatStatusColor(sensor.getStatus());
                }
            }
            return base;
        }
        if (mode == DisplayMode.DENSITY) {
            if (node.getMaxCapacity() <= 0) {
                return base;
            }
            double ratio = Math.min(1.0,
                    (double) node.getCurrentAgentCount() / node.getMaxCapacity());
            return ratio <= 0 ? base : base.interpolate(Color.web("#ff3b30"), Math.min(0.85, ratio));
        }
        // EXIT_ZONES: tint by the assigned exit (Voronoi cell).
        Node exit = state.getAssignedExit(node);
        return exit == null ? base : base.interpolate(exitColor(exit), 0.45);
    }

    /**
     * Returns the base colour for a node type.
     *
     * @param node the node
     * @return the base fill colour
     */
    private Color colorFor(Node node) {
        return switch (node.getType()) {
            case SEAT -> Color.web("#8fa1c7");
            case AISLE -> Color.web("#b0b8cc");
            case CORRIDOR -> Color.web("#6d7ca6");
            case CROSS_SECTION -> Color.web("#9aa7c0");
            case EXIT -> Color.web("#2ecc71");
            case STAGE -> Color.web("#f1c40f");
            case BLOCKED_ZONE -> BLOCKED_COLOR;
        };
    }

    /**
     * Returns the colour representing a seat occupancy status.
     *
     * @param status the seat status
     * @return green = occupied, orange = away, dim = free
     */
    private Color seatStatusColor(SeatStatus status) {
        return switch (status) {
            case OCCUPIED -> Color.web("#e74c3c");
            case AWAY -> Color.web("#f1c40f");
            case FREE -> Color.web("#2ecc71");
        };
    }

    /**
     * Returns the colour associated with an exit (stable per exit id).
     *
     * @param exit the exit node (may be {@code null})
     * @return a distinct colour per exit
     */
    private Color exitColor(Node exit) {
        if (exit == null) {
            return Color.web("#7f8c9b");
        }
        return switch (exit.getId()) {
            case "EXIT_S1" -> Color.web("#4da6ff");
            case "EXIT_S2" -> Color.web("#f39c12");
            case "EXIT_S3" -> Color.web("#9b59b6");
            case "EXIT_S4" -> Color.web("#1abc9c");
            default -> Color.web("#95a5a6");
        };
    }

    /**
     * Returns the drawing radius for a node type.
     *
     * @param node the node
     * @return the radius in pixels (before scaling)
     */
    private double radiusFor(Node node) {
        return switch (node.getType()) {
            case EXIT -> 13;
            case STAGE -> 16;
            case CORRIDOR, CROSS_SECTION -> 8;
            default -> 6;
        };
    }

    // ------------------------------------------------------------------
    // Picking and transforms
    // ------------------------------------------------------------------

    /**
     * Hit-tests a screen position: returns the node under the cursor, or the
     * closest edge if no node matches, or {@code null}.
     *
     * @param screenX x in screen pixels
     * @param screenY y in screen pixels
     * @return the picked {@link Node}, {@link Edge}, or {@code null}
     */
    private Object pick(double screenX, double screenY) {
        Graph graph = graph();
        if (graph == null) {
            return null;
        }
        double mx = (screenX - offsetX) / scale;
        double my = (screenY - offsetY) / scale;

        for (Node node : graph.getNodes()) {
            double dx = node.getX() - mx;
            double dy = node.getY() - my;
            double r = radiusFor(node) + 4;
            if (dx * dx + dy * dy <= r * r) {
                return node;
            }
        }
        Edge best = null;
        double bestDist = 7.0;
        for (Edge edge : graph.getEdges()) {
            double d = distanceToSegment(mx, my,
                    edge.getSource().getX(), edge.getSource().getY(),
                    edge.getTarget().getX(), edge.getTarget().getY());
            if (d < bestDist) {
                bestDist = d;
                best = edge;
            }
        }
        return best;
    }

    /**
     * Computes the distance from a point to a segment.
     *
     * @param px point x
     * @param py point y
     * @param ax segment start x
     * @param ay segment start y
     * @param bx segment end x
     * @param by segment end y
     * @return the shortest distance from the point to the segment
     */
    private double distanceToSegment(double px, double py,
                                     double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx;
        double cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    /**
     * Transforms a model x coordinate into a screen x coordinate.
     *
     * @param x model x
     * @return screen x
     */
    private double tx(double x) {
        return x * scale + offsetX;
    }

    /**
     * Transforms a model y coordinate into a screen y coordinate.
     *
     * @param y model y
     * @return screen y
     */
    private double ty(double y) {
        return y * scale + offsetY;
    }
}
