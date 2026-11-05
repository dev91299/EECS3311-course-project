package com.eecs3311.codereview.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OBSERVER PATTERN - Subject (Observable) role.
 *
 * <p>Holds the observer registry and broadcasts change events. Subclasses call
 * {@link #notifyObservers} whenever their state changes; they never hold a reference to a
 * concrete view class, so the model layer stays independent of Swing and of the console.</p>
 *
 * <p>A {@link CopyOnWriteArrayList} is used because the agent notifies observers from a
 * background worker thread while the Swing event thread may be adding or removing views.</p>
 */
public abstract class Subject {

    private final List<Observer> observers = new CopyOnWriteArrayList<>();

    /** Registers a view to receive updates. Duplicate registrations are ignored. */
    public void addObserver(Observer observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /** Unregisters a view. */
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    /** Returns how many views are currently attached (used by tests and the status bar). */
    public int getObserverCount() {
        return observers.size();
    }

    /** Pushes an event to every registered observer. */
    protected void notifyObservers(Observer.UpdateEvent event, Object payload) {
        for (Observer observer : observers) {
            try {
                observer.updateUI(event, payload);
            } catch (RuntimeException e) {
                // One misbehaving view must never stop the others from being updated.
                System.err.println("[observer] " + observer.getClass().getSimpleName()
                        + " failed to handle " + event + ": " + e.getMessage());
            }
        }
    }

    /** Public entry point allowing controllers to re-broadcast an event. */
    public void publish(Observer.UpdateEvent event, Object payload) {
        notifyObservers(event, payload);
    }
}
