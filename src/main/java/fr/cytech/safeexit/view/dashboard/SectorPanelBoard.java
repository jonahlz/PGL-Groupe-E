package fr.cytech.safeexit.view.dashboard;

import fr.cytech.safeexit.model.sector.DisplayPanel;
import fr.cytech.safeexit.model.sector.PanelMessage;
import fr.cytech.safeexit.model.sector.PanelMode;
import fr.cytech.safeexit.model.sector.Sector;
import fr.cytech.safeexit.model.sector.SectorManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard widget showing the physical sector display panels of the venue.
 * <p>
 * It renders one card per {@link Sector}: the sector id and its rows, a coloured
 * badge for the current {@link PanelMode}, the message the panel is currently
 * broadcasting (arrow + text) and the sector occupancy. This makes the
 * centralised "sensors &rarr; sectors &rarr; panels" chain visible to the
 * supervisor. The widget only reads the model and is refreshed by the dashboard
 * after each cycle, keeping the view free of any business logic (MVC).
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SectorPanelBoard extends VBox {

    private static final String PANEL_BG = "#1a1a2e";
    private static final String CARD_BG = "#12122a";
    private static final String TEXT_SEC = "#aab2cc";

    private final VBox cards;
    private SectorManager manager;

    private final List<Sector> sectors = new ArrayList<>();
    private final List<Label> modeBadges = new ArrayList<>();
    private final List<Label> messageLabels = new ArrayList<>();
    private final List<Label> occupancyLabels = new ArrayList<>();

    /**
     * Builds an empty board; call {@link #setModel(SectorManager)} to populate it.
     */
    public SectorPanelBoard() {
        setSpacing(0);
        setStyle("-fx-background-color: " + PANEL_BG + "; -fx-background-radius: 8;");

        Label header = new Label("Panneaux de secteur");
        header.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-weight: bold; -fx-padding: 6;");

        cards = new VBox(6);
        cards.setPadding(new Insets(0, 6, 6, 6));
        getChildren().addAll(header, cards);
    }

    /**
     * Sets the sector manager to display and rebuilds the cards.
     *
     * @param manager the sector manager (may be {@code null})
     */
    public void setModel(SectorManager manager) {
        this.manager = manager;
        rebuild();
    }

    /**
     * Rebuilds one card per sector and keeps references for live refresh.
     */
    private void rebuild() {
        cards.getChildren().clear();
        sectors.clear();
        modeBadges.clear();
        messageLabels.clear();
        occupancyLabels.clear();
        if (manager == null) {
            return;
        }
        for (Sector sector : manager.getSectors()) {
            Label name = new Label(sector.getSectorId() + "  ·  " + sector.getRowLabels());
            name.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");

            Label badge = new Label();
            badge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 2 6 2 6; "
                    + "-fx-background-radius: 10;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label occupancy = new Label();
            occupancy.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 9px;");

            HBox headerLine = new HBox(8, name, spacer, badge, occupancy);
            headerLine.setAlignment(Pos.CENTER_LEFT);

            Label message = new Label();
            message.setWrapText(true);
            message.setStyle("-fx-text-fill: #e8ecf5; -fx-font-size: 11px;");

            VBox card = new VBox(4, headerLine, message);
            card.setPadding(new Insets(8, 10, 8, 10));
            card.setStyle("-fx-background-color: " + CARD_BG + "; -fx-background-radius: 8;");

            cards.getChildren().add(card);
            sectors.add(sector);
            modeBadges.add(badge);
            messageLabels.add(message);
            occupancyLabels.add(occupancy);
        }
        refresh();
    }

    /**
     * Refreshes every card from the current panel state (mode, message,
     * occupancy). Safe to call on every simulation cycle.
     */
    public void refresh() {
        for (int i = 0; i < sectors.size(); i++) {
            Sector sector = sectors.get(i);
            DisplayPanel panel = sector.getPanel();
            PanelMode mode = (panel != null) ? panel.getMode() : PanelMode.STANDBY;
            PanelMessage message = (panel != null) ? panel.getCurrentMessage() : null;

            Label badge = modeBadges.get(i);
            badge.setText(label(mode));
            badge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 2 6 2 6; "
                    + "-fx-background-radius: 10; -fx-text-fill: white; "
                    + "-fx-background-color: " + color(mode) + ";");

            String text = (message != null)
                    ? message.getDirection().symbol() + "  " + message.getText()
                    : "";
            messageLabels.get(i).setText(text);

            occupancyLabels.get(i).setText(sector.totalOccupied() + "/" + sector.totalCapacity());
        }
    }

    /**
     * Returns the badge background colour for a panel mode (matching the model's
     * documented colour code).
     *
     * @param mode the panel mode
     * @return a CSS colour string
     */
    private static String color(PanelMode mode) {
        return switch (mode) {
            case STANDBY -> "#1D9E75";
            case MONITORING -> "#2d6cdf";
            case ALERT -> "#e67e22";
            case DIRECTIONAL_GUIDANCE -> "#0F9D58";
            case ROUTE_BLOCKED -> "#A32D2D";
        };
    }

    /**
     * Returns a short French label for a panel mode.
     *
     * @param mode the panel mode
     * @return the label shown on the badge
     */
    private static String label(PanelMode mode) {
        return switch (mode) {
            case STANDBY -> "NORMAL";
            case MONITORING -> "SURVEILLANCE";
            case ALERT -> "ALERTE";
            case DIRECTIONAL_GUIDANCE -> "GUIDAGE";
            case ROUTE_BLOCKED -> "BLOQUÉ";
        };
    }
}
