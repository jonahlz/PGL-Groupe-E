package fr.cytech.safeexit.model.observer;

/**
 * Observable side of the Observer design pattern.
 * <p>
 * Classes of the model layer that can change state (graph, agents, simulation
 * engine) implement this interface so that views and other observers can react
 * without the model ever referencing the view layer.
 *
 * @author GROUPE E
 * @version 1.0
 */
public interface Observable {

    /**
     * Registers an observer that will be notified of future events.
     * Implementations should ignore {@code null} or duplicate observers.
     *
     * @param observer the observer to add
     */
    void addObserver(Observer observer);

    /**
     * Removes a previously registered observer.
     *
     * @param observer the observer to remove
     */
    void removeObserver(Observer observer);

    /**
     * Notifies every registered observer of an event.
     *
     * @param event the event to broadcast; never {@code null}
     */
    void notifyObservers(SimulationEvent event);
}
