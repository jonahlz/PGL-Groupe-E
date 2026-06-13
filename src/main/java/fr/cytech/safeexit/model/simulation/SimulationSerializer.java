package fr.cytech.safeexit.model.simulation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;

/**
 * Saves and restores a {@link SimulationState} to and from a binary file, as
 * required by the brief.
 * <p>
 * The whole model object graph reachable from a {@code SimulationState} (graph,
 * agents, sensors, sectors, movement tracker, clock) is {@link
 * java.io.Serializable}; the view-side observers are kept {@code transient} in
 * {@link fr.cytech.safeexit.model.observer.AbstractObservable}, so only the model
 * is written. A restored state is therefore self-contained: the caller just has
 * to build a fresh {@link SimulationEngine} around it and re-wire the views.
 * <p>
 * The simulation <em>phase</em> (normal / evacuation) lives in the engine, not in
 * the state, so a reloaded simulation resumes in the normal monitoring phase.
 *
 * @author GROUPE E
 * @version 1.0
 */
public final class SimulationSerializer {

    /** Recommended file extension for saved simulations. */
    public static final String EXTENSION = ".sim";

    /** Utility class: no instances. */
    private SimulationSerializer() {
    }

    /**
     * Writes a simulation state to a binary file, overwriting it if it exists.
     *
     * @param state the state to save; never {@code null}
     * @param file  the destination file; never {@code null}
     * @throws IOException if the file cannot be written
     */
    public static void save(SimulationState state, Path file) throws IOException {
        if (state == null) {
            throw new IllegalArgumentException("Cannot save a null state");
        }
        if (file == null) {
            throw new IllegalArgumentException("Cannot save to a null file");
        }
        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
            out.writeObject(state);
        }
    }

    /**
     * Reads a simulation state previously written by {@link #save}.
     *
     * @param file the source file; never {@code null}
     * @return the restored simulation state
     * @throws IOException            if the file cannot be read
     * @throws ClassNotFoundException if the file does not hold a known model
     * @throws InvalidSimulationFileException if the file is not a SafeExit save
     */
    public static SimulationState load(Path file) throws IOException, ClassNotFoundException {
        if (file == null) {
            throw new IllegalArgumentException("Cannot load from a null file");
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file.toFile())))) {
            Object read = in.readObject();
            if (!(read instanceof SimulationState state)) {
                throw new InvalidSimulationFileException(
                        "File does not contain a SafeExit simulation: " + file);
            }
            return state;
        }
    }

    /**
     * Thrown when a file does not contain a valid SafeExit simulation.
     */
    public static class InvalidSimulationFileException extends IOException {
        private static final long serialVersionUID = 1L;

        /**
         * Creates the exception with a human-readable message.
         *
         * @param message the detail message
         */
        public InvalidSimulationFileException(String message) {
            super(message);
        }
    }
}
