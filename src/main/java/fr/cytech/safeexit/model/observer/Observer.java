package fr.cytech.safeexit.model.observer;

/**
 * Observer side of the Observer design pattern.
 * <p>
 * Any class that needs to react to changes in the model (a graphical view,
 * an agent, a statistics collector, the command-line logger...) implements
 * this interface and subscribes to an {@link Observable}.
 *
 * @author GROUPE E
 * @version 1.0
 */
public interface Observer {

    /**
     * Called by an {@link Observable} whenever a relevant change occurs.
     *
     * @param event the event describing what happened in the model;
     *              never {@code null}
     */
    void update(SimulationEvent event);
}
