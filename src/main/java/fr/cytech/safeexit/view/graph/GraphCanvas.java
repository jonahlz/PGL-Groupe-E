package fr.cytech.safeexit.view.graph;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * JavaFX view that renders a {@link Graph} (the concert hall) and the agents on
 * a 2D canvas.
 * <p>
 * Nodes are drawn as coloured circles depending on their {@link
 * fr.cytech.safeexit.model.graph.NodeType}, edges as lines whose thickness is
 * proportional to their width, and agents as small dots. The view supports
 * zooming with the mouse wheel and panning by dragging. It only reads the model
 * to draw it and contains no business logic, in line with the MVC separation.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class GraphCanvas extends Pane {

    private static final Color BACKGROUND = Color.web("#1a1a2e");
    private static final Color EDGE_COLOR = Color.web("#44506b");
    private static final Color BLOCKED_COLOR = Color.web("#e74c3c");
    private static final Color AGENT_COLOR = Color.web("#00b0ff");

    private final Canvas canvas;
    private Graph graph;
    private List<Agent> agents;

    // Camera transform
    private double scale = 1.0;
    private double offsetX = 0;
    private double offsetY = 0;
    private double lastMouseX;
    private double lastMouseY;

    /**
     * Creates an empty canvas wired with zoom and pan interactions.
     */
    public GraphCanvas() {
        this.canvas = new Canvas();
        getChildren().add(canvas);
        setStyle("-fx-background-color: #1a1a2e;");

        // Resize the canvas with the pane.
        widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(n.doubleValue());
            redraw();
        });
        heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(n.doubleValue());
            redraw();
        });

        // Zoom with the mouse wheel.
        setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            scale = Math.max(0.3, Math.min(4.0, scale * factor));
            redraw();
        });

        // Pan by dragging.
        setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });
        setOnMouseDragged(e -> {
            offsetX += e.getX() - lastMouseX;
            offsetY += e.getY() - lastMouseY;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            redraw();
        });
    }

    /**
     * Sets the graph and the agents to display and triggers a redraw.
     *
     * @param graph  the venue graph to render
     * @param agents the agents to render (may be {@code null})
     */
    public void setModel(Graph graph, List<Agent> agents) {
        this.graph = graph;
        this.agents = agents;
        redraw();
    }

    /**
     * Redraws the whole scene (background, edges, nodes, agents).
     */
    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        g.setFill(BACKGROUND);
        g.fillRect(0, 0, w, h);

        if (graph == null) {
            return;
        }

        drawEdges(g);
        drawNodes(g);
        drawAgents(g);
    }

    /**
     * Draws every edge as a line, thicker when the edge is wider.
     *
     * @param g the graphics context
     */
    private void drawEdges(GraphicsContext g) {
        for (Edge edge : graph.getEdges()) {
            double x1 = tx(edge.getSource().getX());
            double y1 = ty(edge.getSource().getY());
            double x2 = tx(edge.getTarget().getX());
            double y2 = ty(edge.getTarget().getY());
            g.setStroke(edge.isBlocked() ? BLOCKED_COLOR : EDGE_COLOR);
            g.setLineWidth((1 + edge.getMaxConcurrentAgents() * 0.5) * scale);
            g.strokeLine(x1, y1, x2, y2);
        }
    }

    /**
     * Draws every node as a coloured circle sized and coloured by its type.
     *
     * @param g the graphics context
     */
    private void drawNodes(GraphicsContext g) {
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font(11 * scale));
        for (Node node : graph.getNodes()) {
            double r = radiusFor(node) * scale;
            double x = tx(node.getX());
            double y = ty(node.getY());

            g.setFill(node.isBlocked() ? BLOCKED_COLOR : colorFor(node));
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
            g.setStroke(Color.web("#0d0d1a"));
            g.setLineWidth(1);
            g.strokeOval(x - r, y - r, 2 * r, 2 * r);

            // Label the exits.
            if (node.isExit()) {
                g.setFill(Color.WHITE);
                g.fillText(node.getId().replace("EXIT_", ""), x, y - r - 4 * scale);
            }
        }
    }

    /**
     * Draws every active agent as a small dot at its current node.
     *
     * @param g the graphics context
     */
    private void drawAgents(GraphicsContext g) {
        if (agents == null) {
            return;
        }
        double r = 5 * scale;
        for (Agent agent : agents) {
            Node node = agent.getCurrentNode();
            if (node == null || agent.hasExited()) {
                continue;
            }
            double x = tx(node.getX());
            double y = ty(node.getY());
            g.setFill(AGENT_COLOR);
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
            g.setStroke(Color.WHITE);
            g.setLineWidth(1);
            g.strokeOval(x - r, y - r, 2 * r, 2 * r);
        }
    }

    /**
     * Returns the drawing colour for a node type.
     *
     * @param node the node
     * @return the fill colour
     */
    private Color colorFor(Node node) {
        return switch (node.getType()) {
            case EXIT -> Color.web("#2ecc71");
            case SEAT -> Color.web("#8fa1c7");
            case AISLE -> Color.web("#b0b8cc");
            case CORRIDOR -> Color.web("#6d7ca6");
            case CROSS_SECTION -> Color.web("#9aa7c0");
            case STAGE -> Color.web("#f1c40f");
            case BLOCKED_ZONE -> BLOCKED_COLOR;
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
