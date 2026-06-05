package fr.cytech.safeexit.controller;

import fr.cytech.safeexit.model.simulation.SimulationEngine;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Controller driving the simulation over time for the graphical interface.
 * <p>
 * It owns the {@link SimulationEngine} and a JavaFX {@link Timeline} that calls
 * {@link SimulationEngine#tick()} at the speed defined by the engine's clock.
 * The view only calls high-level actions (play, pause, step, change speed); the
 * controller never draws anything, and after each cycle it triggers a repaint
 * callback supplied by the view. This is the bridge of the MVC pattern.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SimulationController {

    private final SimulationEngine engine;
    private Timeline timeline;
    private Runnable onTick;

    /**
     * Creates a controller for the given engine and builds the timeline.
     *
     * @param engine the simulation engine to drive; never {@code null}
     */
    public SimulationController(SimulationEngine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("Controller requires an engine");
        }
        this.engine = engine;
        rebuildTimeline();
    }

    /**
     * (Re)creates the timeline using the current clock interval.
     */
    private void rebuildTimeline() {
        boolean wasRunning = timeline != null && timeline.getStatus() == Animation.Status.RUNNING;
        if (timeline != null) {
            timeline.stop();
        }
        int interval = engine.getClock().getIntervalMs();
        timeline = new Timeline(new KeyFrame(Duration.millis(interval), e -> frame()));
        timeline.setCycleCount(Animation.INDEFINITE);
        if (wasRunning) {
            timeline.play();
        }
    }

    /**
     * Runs one simulation cycle and repaints; pauses automatically once the
     * evacuation is complete.
     */
    private void frame() {
        engine.tick();
        if (onTick != null) {
            onTick.run();
        }
        if (engine.isEvacuationComplete()) {
            pause();
        }
    }

    /**
     * Sets the callback run after each cycle (typically the view repaint).
     *
     * @param onTick the repaint callback
     */
    public void setOnTick(Runnable onTick) {
        this.onTick = onTick;
    }

    /**
     * Starts (or resumes) the automatic simulation.
     */
    public void play() {
        engine.setPaused(false);
        engine.getClock().setRunning(true);
        timeline.play();
    }

    /**
     * Pauses the automatic simulation.
     */
    public void pause() {
        timeline.pause();
        engine.getClock().setRunning(false);
    }

    /**
     * Runs exactly one simulation cycle (step mode) and repaints.
     */
    public void step() {
        pause();
        engine.setPaused(false);
        engine.tick();
        if (onTick != null) {
            onTick.run();
        }
    }

    /**
     * Changes the simulation speed (interval between two cycles).
     *
     * @param intervalMs the new interval in milliseconds
     */
    public void setIntervalMs(int intervalMs) {
        engine.getClock().setIntervalMs(intervalMs);
        rebuildTimeline();
    }

    /**
     * Returns the engine driven by this controller.
     *
     * @return the simulation engine
     */
    public SimulationEngine getEngine() {
        return engine;
    }

    /**
     * Indicates whether the simulation is currently running.
     *
     * @return {@code true} if the timeline is playing
     */
    public boolean isRunning() {
        return timeline.getStatus() == Animation.Status.RUNNING;
    }
}
