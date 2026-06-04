package fr.cytech.safeexit.model.simulation;

import java.io.Serializable;

/**
 * Holds the timing parameters of the simulation: the interval between two
 * cycles (in milliseconds) and whether the clock is running.
 * <p>
 * The clock itself does not schedule anything; the actual scheduling is done by
 * the view (a JavaFX {@code Timeline}) or by a plain loop in command-line mode.
 * Keeping the parameters here lets both modes share the same configuration.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SimulationClock implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default interval between two simulation cycles, in milliseconds. */
    public static final int DEFAULT_INTERVAL_MS = 100;

    private int intervalMs;
    private boolean running;

    /**
     * Creates a clock with the default interval, initially stopped.
     */
    public SimulationClock() {
        this.intervalMs = DEFAULT_INTERVAL_MS;
        this.running = false;
    }

    /**
     * Returns the interval between two cycles.
     *
     * @return the interval in milliseconds
     */
    public int getIntervalMs() {
        return intervalMs;
    }

    /**
     * Sets the interval between two cycles (the simulation speed).
     *
     * @param intervalMs the new interval in milliseconds (must be &gt;= 1)
     */
    public void setIntervalMs(int intervalMs) {
        if (intervalMs < 1) {
            throw new IllegalArgumentException("Interval must be at least 1 ms");
        }
        this.intervalMs = intervalMs;
    }

    /**
     * Indicates whether the clock is running.
     *
     * @return {@code true} if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Sets the running state of the clock.
     *
     * @param running the new running state
     */
    public void setRunning(boolean running) {
        this.running = running;
    }
}
