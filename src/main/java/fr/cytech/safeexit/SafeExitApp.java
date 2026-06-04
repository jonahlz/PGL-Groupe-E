package fr.cytech.safeexit;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.GraphException;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;
import fr.cytech.safeexit.model.venue.ConcertHallBuilder;
import fr.cytech.safeexit.view.graph.GraphCanvas;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX entry point of SafeExit.
 * <p>
 * For this first visual milestone it builds a concert hall with {@link
 * ConcertHallBuilder}, places a few agents on seats, blocks one exit to show
 * the colour coding, and renders everything statically with {@link GraphCanvas}.
 * The dynamic simulation (Voronoi routing and movement) is added later.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SafeExitApp extends Application {

    private static final int ROWS = 8;
    private static final int SEATS_PER_ROW = 12;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        Label header = new Label("SafeExit  ·  Aperçu de la salle (rendu statique)");
        header.setStyle("-fx-text-fill: #00ff88; -fx-font-size: 16px; -fx-font-weight: bold; "
                + "-fx-padding: 10;");
        root.setTop(header);

        try {
            ConcertHallBuilder builder = new ConcertHallBuilder();
            Graph graph = builder.buildHall(ROWS, SEATS_PER_ROW);

            // Demonstrate the colour coding: block one exit.
            Node blockedExit = graph.getNode("EXIT_S4");
            if (blockedExit != null) {
                blockedExit.setBlocked(true);
            }

            List<Agent> agents = createAgentsOnSeats(graph, 30);

            GraphCanvas canvas = new GraphCanvas();
            canvas.setModel(graph, agents);
            root.setCenter(canvas);

            Label legend = new Label("Vert = sortie libre   ·   Rouge = sortie/bloquée   ·   "
                    + "Jaune = scène   ·   Bleu = agent   ·   Molette = zoom, glisser = déplacer");
            legend.setStyle("-fx-text-fill: #aab2cc; -fx-font-size: 12px; -fx-padding: 8;");
            root.setBottom(legend);
        } catch (GraphException e) {
            // The application must never crash: report the error in the window.
            Label error = new Label("Erreur lors de la construction de la salle : " + e.getMessage());
            error.setStyle("-fx-text-fill: #e74c3c; -fx-padding: 20;");
            root.setCenter(error);
        }

        Scene scene = new Scene(root, 1100, 720);
        stage.setTitle("SafeExit");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Places up to {@code count} agents on the first available seats of the graph.
     *
     * @param graph the venue graph
     * @param count the maximum number of agents to create
     * @return the list of created agents
     */
    private List<Agent> createAgentsOnSeats(Graph graph, int count) {
        List<Agent> agents = new ArrayList<>();
        int created = 0;
        for (Node node : graph.getNodes()) {
            if (created >= count) {
                break;
            }
            if (node.getType() == NodeType.SEAT) {
                agents.add(new Agent(String.format("AGT_%03d", created + 1), node));
                created++;
            }
        }
        return agents;
    }

    /**
     * Allows launching the graphical application directly.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
