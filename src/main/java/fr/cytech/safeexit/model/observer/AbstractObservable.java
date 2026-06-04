package fr.cytech.safeexit.model.observer;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reusable base implementation of {@link Observable}.
 * <p>
 * It manages the list of observers and the notification loop so that concrete
 * model classes (graph, agents, simulation engine) only have to extend it and
 * call {@link #notifyObservers(SimulationEvent)} when their state changes.
 * <p>
 * A {@link CopyOnWriteArrayList} is used so observers can subscribe or
 * unsubscribe while a notification is in progress without throwing a
 * {@link java.util.ConcurrentModificationException}.
 *
 * @author GROUPE E
 * @version 1.0
 */
public abstract class AbstractObservable implements Observable, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Registered observers. Marked {@code transient} because observers belong
     * to the view layer and must not be saved when the model is serialized.
     */
    private transient List<Observer> observers = new CopyOnWriteArrayList<>();

    @Override
    public void addObserver(Observer observer) {
        if (observer != null && !getObservers().contains(observer)) {
            getObservers().add(observer);
        }
    }

    @Override
    public void removeObserver(Observer observer) {
        getObservers().remove(observer);
    }

    @Override
    public void notifyObservers(SimulationEvent event) {
        if (event == null) {
            return;
        }
        for (Observer observer : getObservers()) {
            try {
                observer.update(event);
            } catch (RuntimeException e) {
                // A faulty observer must never crash the simulation.
                System.err.println("Observer failed on " + event + ": " + e.getMessage());
            }
        }
    }

    /**
     * Lazily re-creates the observer list after deserialization (the field is
     * {@code transient} and therefore {@code null} on a restored object).
     *
     * @return the live, non-null list of observers
     */
    private List<Observer> getObservers() {
        if (observers == null) {
            observers = new CopyOnWriteArrayList<>();
        }
        return observers;
    }
}
