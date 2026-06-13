package fr.cytech.safeexit;

import fr.cytech.safeexit.controller.GraphController;
import fr.cytech.safeexit.controller.SimulationController;
import fr.cytech.safeexit.controller.VenueController;
import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.GraphException;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.sector.Sector;
import fr.cytech.safeexit.model.sensor.RowSensor;
import fr.cytech.safeexit.model.sensor.SensorNetwork;
import fr.cytech.safeexit.model.simulation.SimulationEngine;
import fr.cytech.safeexit.model.simulation.SimulationState;
import fr.cytech.safeexit.model.simulation.SimulationSerializer;
import fr.cytech.safeexit.view.dashboard.AlertPanel;
import fr.cytech.safeexit.view.dashboard.SectorPanelBoard;
import fr.cytech.safeexit.view.graph.GraphCanvas;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX supervision dashboard of SafeExit.
 * <p>
 * Inspired by the team's Design mock-ups, the window shows a metrics bar
 * (people present, evacuated, active exits, cycle), the live hall map, an exit
 * control panel (block/unblock each exit), an alert log and the simulation
 * controls. User actions go through the controllers ({@link
 * SimulationController}, {@link VenueController}, {@link GraphController}); the
 * model drives the view through events. This ties the three MVC layers together.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SafeExitApp extends Application {

    private static final int ROWS = 8;
    private static final int SEATS_PER_ROW = 12;
    private static final int SPEED_BASE = 316;
    private static final int DEFAULT_INTERVAL_MS = 230;

    // Design palette (from the team mock-ups)
    private static final String BG = "#0a0a12";
    private static final String PANEL = "#1a1a2e";
    private static final String GREEN = "#1D9E75";
    private static final String GREEN_L = "#9FE1CB";
    private static final String RED = "#A32D2D";
    private static final String RED_L = "#F09595";
    private static final String TEXT_SEC = "#aab2cc";

    private final VenueController venueController = new VenueController();
    private Stage stage;
    private GraphCanvas canvas;
    private SimulationController simulation;
    private GraphController graphController;
    private SimulationState state;
    private AlertPanel alertPanel;
    private SectorPanelBoard sectorBoard;
    private Slider speedSlider;
    private ComboBox<String> panicSectorBox;

    // Header phase badge
    private Label phaseBadge;

    // Metric value labels
    private Label mPresent;
    private Label mEvac;
    private Label mExits;
    private Label mCycle;

    // Exit control rows
    private VBox exitRows;
    private final List<Button> exitButtons = new ArrayList<>();
    private final List<Node> exitNodes = new ArrayList<>();

    // Row occupancy rows (seat sensors)
    private VBox occupancyRows;
    private final List<RowSensor> rowSensors = new ArrayList<>();
    private final List<Region> rowFills = new ArrayList<>();
    private final List<Label> rowCounts = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");

        root.setTop(buildTopBar());

        canvas = new GraphCanvas();
        BorderPane.setMargin(canvas, new Insets(0, 8, 0, 8));
        root.setCenter(canvas);

        alertPanel = new AlertPanel();
        root.setRight(buildRightPanel());

        root.setBottom(buildControlBar());

        setupSimulation();

        // Size the window to the screen's work area (capped), and open maximized so
        // the whole dashboard is visible whatever the resolution / display scaling.
        javafx.geometry.Rectangle2D visual = javafx.stage.Screen.getPrimary().getVisualBounds();
        double w = Math.min(1280, visual.getWidth());
        double h = Math.min(800, visual.getHeight());
        Scene scene = new Scene(root, w, h);
        stage.setTitle("SafeExit — Supervision");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setMaximized(true);
        stage.show();
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    /**
     * Builds the title and the metrics bar.
     *
     * @return the top node
     */
    private VBox buildTopBar() {
        Label title = new Label("SafeExit — supervision  ·  Bercy");
        title.setStyle("-fx-text-fill: " + GREEN_L + "; -fx-font-size: 16px; -fx-font-weight: bold;");
        phaseBadge = new Label("ÉVÉNEMENT EN COURS");
        phaseBadge.setStyle("-fx-text-fill: " + GREEN_L + "; -fx-background-color: #0F6E56; "
                + "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; "
                + "-fx-background-radius: 12;");
        HBox titleLine = new HBox(10, title, phaseBadge);
        titleLine.setAlignment(Pos.CENTER_LEFT);

        mPresent = new Label("—");
        mEvac = new Label("—");
        mExits = new Label("—");
        mCycle = new Label("—");

        HBox metrics = new HBox(8,
                metricCard("Présents", mPresent),
                metricCard("Évacués", mEvac),
                metricCard("Sorties actives", mExits),
                metricCard("Cycle", mCycle));
        metrics.setPadding(new Insets(8, 0, 0, 0));

        VBox box = new VBox(2, titleLine, metrics);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: " + PANEL + ";");
        return box;
    }

    /**
     * Builds a small metric card holding a title and a value label.
     *
     * @param title the metric name
     * @param value the value label kept by the caller for updates
     * @return the card node
     */
    private VBox metricCard(String title, Label value) {
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 10px;");
        value.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        VBox card = new VBox(2, t, value);
        card.setPadding(new Insets(8, 14, 8, 14));
        card.setStyle("-fx-background-color: #12122a; -fx-background-radius: 8;");
        card.setMinWidth(120);
        return card;
    }

    /**
     * Builds the right column: exit controls, sector panels, row occupancy and
     * the alert log. It is wrapped in a scroll pane so that, on a short screen,
     * the column scrolls instead of pushing the bottom control bar off-screen.
     *
     * @return the right node
     */
    private Region buildRightPanel() {
        Label exitHeader = new Label("Contrôle des sorties");
        exitHeader.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-weight: bold; -fx-padding: 6;");
        exitRows = new VBox(6);
        exitRows.setPadding(new Insets(6));
        VBox exitPanel = new VBox(exitHeader, exitRows);
        exitPanel.setStyle("-fx-background-color: " + PANEL + "; -fx-background-radius: 8;");

        Label occHeader = new Label("Occupation des rangées (capteurs)");
        occHeader.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-weight: bold; -fx-padding: 6;");
        occupancyRows = new VBox(4);
        occupancyRows.setPadding(new Insets(6));
        VBox occPanel = new VBox(occHeader, occupancyRows);
        occPanel.setStyle("-fx-background-color: " + PANEL + "; -fx-background-radius: 8;");

        sectorBoard = new SectorPanelBoard();

        VBox.setVgrow(alertPanel, Priority.ALWAYS);
        VBox right = new VBox(8, exitPanel, sectorBoard, occPanel, alertPanel);
        right.setPadding(new Insets(0, 8, 8, 0));
        right.setPrefWidth(270);

        ScrollPane scroll = new ScrollPane(right);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMinHeight(0);
        scroll.setPrefViewportWidth(286);
        scroll.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + ";");
        return scroll;
    }

    /**
     * Rebuilds the per-row occupancy bars from the sensor network.
     */
    private void buildOccupancyControls() {
        occupancyRows.getChildren().clear();
        rowSensors.clear();
        rowFills.clear();
        rowCounts.clear();
        if (state.getSensorNetwork() == null) {
            return;
        }
        for (RowSensor row : state.getSensorNetwork().getRowSensors()) {
            Label name = new Label(row.getRowId());
            name.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
            name.setMinWidth(16);

            Region track = new Region();
            track.setPrefHeight(8);
            track.setStyle("-fx-background-color: #12122a; -fx-background-radius: 4;");
            HBox.setHgrow(track, Priority.ALWAYS);
            track.setMaxWidth(Double.MAX_VALUE);
            Region fill = new Region();
            fill.setPrefHeight(8);
            fill.setStyle("-fx-background-color: " + GREEN + "; -fx-background-radius: 4;");
            StackPane bar = new StackPane(track, fill);
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            HBox.setHgrow(bar, Priority.ALWAYS);

            Label count = new Label();
            count.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 9px;");
            count.setMinWidth(40);

            HBox r = new HBox(8, name, bar, count);
            r.setAlignment(Pos.CENTER_LEFT);
            occupancyRows.getChildren().add(r);

            rowSensors.add(row);
            rowFills.add(fill);
            rowCounts.add(count);
        }
    }

    /**
     * Refreshes the per-row occupancy bars (fill width follows the bar width).
     */
    private void refreshOccupancy() {
        for (int i = 0; i < rowSensors.size(); i++) {
            RowSensor row = rowSensors.get(i);
            double ratio = row.occupancyRatio();
            Region fill = rowFills.get(i);
            double barWidth = ((StackPane) fill.getParent()).getWidth();
            fill.setPrefWidth(barWidth * ratio);
            rowCounts.get(i).setText(row.occupiedCount() + "/" + row.seatCount());
        }
    }

    /**
     * Builds the bottom simulation control bar. It wraps onto several lines on a
     * narrow window so no control is ever cut off.
     *
     * @return the control bar node
     */
    private FlowPane buildControlBar() {
        Button play = themedButton("▶ Play", GREEN);
        Button pause = themedButton("⏸ Pause", "#394260");
        Button step = themedButton("⏭ Step", "#394260");
        Button reset = themedButton("⏮ Reset", "#394260");
        Button save = themedButton("💾 Sauvegarder", "#394260");
        Button load = themedButton("📂 Charger", "#394260");
        Button evac = themedButton("Déclencher évacuation", RED);

        play.setOnAction(e -> simulation.play());
        pause.setOnAction(e -> simulation.pause());
        step.setOnAction(e -> simulation.step());
        reset.setOnAction(e -> setupSimulation());
        save.setOnAction(e -> onSave());
        load.setOnAction(e -> onLoad());
        evac.setOnAction(e -> {
            simulation.getEngine().triggerEvacuation();
            simulation.play();
        });

        // Manual scenario: trigger panic in a chosen sector.
        Label sectorLabel = new Label("Secteur");
        sectorLabel.setStyle("-fx-text-fill: " + TEXT_SEC + ";");
        panicSectorBox = new ComboBox<>();
        panicSectorBox.setPromptText("Secteur");
        Button panic = themedButton("⚠ Paniquer", "#e67e22");
        panic.setOnAction(e -> onPanicSector());
        Button calm = themedButton("✓ Calmer", "#394260");
        calm.setOnAction(e -> onCalmSector());

        Label speedLabel = new Label("Vitesse");
        speedLabel.setStyle("-fx-text-fill: " + TEXT_SEC + ";");
        speedSlider = new Slider(16, 300, SPEED_BASE - DEFAULT_INTERVAL_MS);
        speedSlider.setPrefWidth(150);
        speedSlider.valueProperty().addListener((obs, o, n) ->
                simulation.setIntervalMs((int) (SPEED_BASE - n.doubleValue())));

        Label modeLabel = new Label("Affichage");
        modeLabel.setStyle("-fx-text-fill: " + TEXT_SEC + ";");
        ComboBox<GraphCanvas.DisplayMode> modeBox = new ComboBox<>();
        modeBox.getItems().addAll(GraphCanvas.DisplayMode.values());
        modeBox.setValue(GraphCanvas.DisplayMode.OCCUPANCY);
        modeBox.setOnAction(e -> canvas.setDisplayMode(modeBox.getValue()));
        // Start on the occupancy view (live venue monitoring).
        canvas.setDisplayMode(GraphCanvas.DisplayMode.OCCUPANCY);

        FlowPane bar = new FlowPane(8, 8, play, pause, step, reset, save, load, evac,
                sectorLabel, panicSectorBox, panic, calm, speedLabel, speedSlider, modeLabel, modeBox);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        bar.setStyle("-fx-background-color: " + PANEL + ";");
        return bar;
    }

    /**
     * Triggers panic in the sector selected in the control bar (manual scenario).
     */
    private void onPanicSector() {
        if (simulation == null || panicSectorBox == null) {
            return;
        }
        String sectorId = panicSectorBox.getValue();
        if (sectorId == null) {
            return;
        }
        simulation.getEngine().triggerPanicInSector(sectorId);
        onTick();
    }

    /**
     * Calms the sector selected in the control bar (undoes a panic scenario).
     */
    private void onCalmSector() {
        if (simulation == null || panicSectorBox == null) {
            return;
        }
        String sectorId = panicSectorBox.getValue();
        if (sectorId == null) {
            return;
        }
        simulation.getEngine().calmSector(sectorId);
        onTick();
    }

    /**
     * Fills the panic-sector selector with the ids of the current sectors.
     */
    private void populatePanicSectors() {
        if (panicSectorBox == null) {
            return;
        }
        panicSectorBox.getItems().clear();
        if (state.getSectorManager() != null) {
            for (Sector sector : state.getSectorManager().getSectors()) {
                panicSectorBox.getItems().add(sector.getSectorId());
            }
        }
        if (!panicSectorBox.getItems().isEmpty()) {
            panicSectorBox.setValue(panicSectorBox.getItems().get(0));
        }
    }

    /**
     * Creates a button with the given background colour.
     *
     * @param text  the button label
     * @param color the background colour
     * @return the styled button
     */
    private Button themedButton(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-background-radius: 16; -fx-cursor: hand;");
        return b;
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    /**
     * (Re)builds the hall, the engine and the controllers, wires the observers
     * and refreshes the view. Used at startup and by the reset button.
     */
    private void setupSimulation() {
        try {
            SimulationState fresh =
                    venueController.createHall(ROWS, SEATS_PER_ROW, ROWS * SEATS_PER_ROW);
            venueController.blockNode(fresh, "EXIT_S4");

            // Build the seat-occupancy sensor layer and attach it to the state.
            fresh.setSensorNetwork(SensorNetwork.build(fresh.getGraph(), fresh.getAgents()));

            // Build the sector / display-panel layer on top of the sensors.
            fresh.setSectorManager(fr.cytech.safeexit.model.sector.SectorManager.build(
                    fresh.getSensorNetwork(), fresh.getGraph()));

            installState(fresh);
        } catch (GraphException e) {
            System.err.println("Setup failed: " + e.getMessage());
        }
    }

    /**
     * Installs a simulation state (freshly built or loaded from disk): creates a
     * new engine and controllers around it, wires the observers and refreshes the
     * whole view. Shared by startup, reset and load.
     *
     * @param newState the state to drive the dashboard with
     */
    private void installState(SimulationState newState) {
        if (simulation != null) {
            simulation.pause();
        }
        state = newState;

        SimulationEngine engine = new SimulationEngine(state);
        simulation = new SimulationController(engine);
        simulation.setOnTick(this::onTick);
        int interval = speedSlider != null
                ? (int) (SPEED_BASE - speedSlider.getValue())
                : DEFAULT_INTERVAL_MS;
        simulation.setIntervalMs(interval);

        graphController = new GraphController(engine);
        graphController.setOnChange(this::onTick);
        canvas.setOnPick(graphController::toggleBlocked);

        // Wire the alert log as an observer of the engine and of the elements.
        alertPanel.clear();
        engine.addObserver(alertPanel);
        for (Node node : state.getGraph().getNodes()) {
            node.addObserver(alertPanel);
        }
        for (Edge edge : state.getGraph().getEdges()) {
            edge.addObserver(alertPanel);
        }

        buildExitControls();
        buildOccupancyControls();
        sectorBoard.setModel(state.getSectorManager());
        populatePanicSectors();
        canvas.setModel(state);
        onTick();
    }

    /**
     * Saves the current simulation to a binary file chosen by the supervisor.
     */
    private void onSave() {
        if (state == null) {
            return;
        }
        if (simulation != null) {
            simulation.pause();
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sauvegarder la simulation");
        chooser.setInitialFileName("simulation" + SimulationSerializer.EXTENSION);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Simulation SafeExit (*" + SimulationSerializer.EXTENSION + ")",
                "*" + SimulationSerializer.EXTENSION));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            SimulationSerializer.save(state, file.toPath());
        } catch (IOException ex) {
            showError("Sauvegarde impossible : " + ex.getMessage());
        }
    }

    /**
     * Loads a previously saved simulation, replacing the current one.
     */
    private void onLoad() {
        if (simulation != null) {
            simulation.pause();
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Charger une simulation");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Simulation SafeExit (*" + SimulationSerializer.EXTENSION + ")",
                "*" + SimulationSerializer.EXTENSION));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            SimulationState loaded = SimulationSerializer.load(file.toPath());
            installState(loaded);
        } catch (IOException | ClassNotFoundException ex) {
            showError("Chargement impossible : " + ex.getMessage());
        }
    }

    /**
     * Shows a non-blocking error dialog so a failed save / load never crashes the
     * application.
     *
     * @param message the message to display
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("SafeExit");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /**
     * Rebuilds the exit control rows for the current graph.
     */
    private void buildExitControls() {
        exitRows.getChildren().clear();
        exitButtons.clear();
        exitNodes.clear();

        List<Node> exits = new ArrayList<>(state.getGraph().getExits());
        exits.sort((a, b) -> a.getId().compareTo(b.getId()));
        for (Node exit : exits) {
            Button btn = new Button();
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> graphController.toggleBlocked(exit));
            exitNodes.add(exit);
            exitButtons.add(btn);
            exitRows.getChildren().add(btn);
        }
        refreshExitButtons();
    }

    /**
     * Refreshes the exit buttons to reflect each exit's blocked state.
     */
    private void refreshExitButtons() {
        for (int i = 0; i < exitButtons.size(); i++) {
            Node exit = exitNodes.get(i);
            Button btn = exitButtons.get(i);
            String name = exit.getId().replace("EXIT_", "");
            boolean blocked = exit.isBlocked();
            btn.setText(name + "  ·  " + (blocked ? "Bloquée" : "Ouverte"));
            btn.setStyle("-fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold; "
                    + (blocked
                    ? "-fx-background-color: " + RED + "; -fx-text-fill: " + RED_L + ";"
                    : "-fx-background-color: #0F6E56; -fx-text-fill: " + GREEN_L + ";"));
        }
    }

    /**
     * Called after each simulation cycle or edit: repaints and refreshes stats.
     */
    private void onTick() {
        canvas.redraw();
        updateMetrics();
        refreshExitButtons();
        refreshOccupancy();
        if (sectorBoard != null) {
            sectorBoard.refresh();
        }
    }

    /**
     * Updates the metric labels from the current state.
     */
    private void updateMetrics() {
        if (simulation == null || state == null) {
            return;
        }
        int total = state.getAgents().size();
        int evac = simulation.getEngine().countEvacuated();
        int totalExits = state.getGraph().getExits().size();
        int activeExits = 0;
        for (Node exit : state.getGraph().getExits()) {
            if (!exit.isBlocked()) {
                activeExits++;
            }
        }
        // "Présents" = seats reported occupied by the sensors (live occupancy).
        int present = state.getSensorNetwork() != null
                ? state.getSensorNetwork().totalOccupied()
                : total - evac;
        mPresent.setText(String.valueOf(present));
        mEvac.setText(evac + " / " + total);
        mExits.setText(activeExits + " / " + totalExits);
        mCycle.setText(String.valueOf(state.getCurrentCycle()));

        // Phase badge.
        boolean evacuating = simulation.getEngine().isEvacuating();
        phaseBadge.setText(evacuating ? "ÉVACUATION" : "ÉVÉNEMENT EN COURS");
        phaseBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; "
                + "-fx-background-radius: 12; " + (evacuating
                ? "-fx-text-fill: " + RED_L + "; -fx-background-color: " + RED + ";"
                : "-fx-text-fill: " + GREEN_L + "; -fx-background-color: #0F6E56;"));
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
