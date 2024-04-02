/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;

import java.util.ArrayList;
import java.util.List;


/**
 * Handles adding, removing controller listeners.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public final class ControllersListenerSupport {

    /** The list of registered listeners. */
    private final List<IControllersListener> listeners = new ArrayList<>();

    /**
     * Registers a listener for controller events.
     *
     * @param listener The controller listener.
     */
    public void addListener(IControllersListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener for controller events.
     *
     * @param listener The controller listener to remove.
     */
    public void removeListener(IControllersListener listener) {
        listeners.remove(listener);
    }

    /** @see IControllersListener#connected(IController) */
    public void fireConnected(IController controller) {
        for (IControllersListener listener : listeners) {
            listener.connected(controller);
        }
    }

    /** @see IControllersListener#disConnected(IController) */
    public void fireDisconnected(IController controller) {
        for (IControllersListener listener : listeners) {
            listener.disConnected(controller);
        }
    }
}
