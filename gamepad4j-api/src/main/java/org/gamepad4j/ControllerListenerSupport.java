/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;

import java.util.ArrayList;
import java.util.List;


/**
 * Handles adding, removing and accessing controller listeners.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public final class ControllerListenerSupport {

    /** The list of registered listeners. */
    private final List<IControllerListener> listeners = new ArrayList<>();

    /**
     * Registers a listener for controller events.
     *
     * @param listener The controller listener.
     */
    public void addListener(IControllerListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener for controller events.
     *
     * @param listener The controller listener to remove.
     */
    public void removeListener(IControllerListener listener) {
        listeners.remove(listener);
    }

    /** @see IControllerListener#connected(IController) */
    public void fireConnected(IController controller) {
        for (IControllerListener listener : listeners) {
            listener.connected(controller);
        }
    }

    /** @see IControllerListener#disConnected(IController) */
    public void fireDisconnected(IController controller) {
        for (IControllerListener listener : listeners) {
            listener.disConnected(controller);
        }
    }

    /** @see IControllerListener#buttonDown(IController, IButton, ButtonID) */
    public void fireButtonDown(IController controller, IButton button, ButtonID buttonID) {
        for (IControllerListener listener : listeners) {
            listener.buttonDown(controller, button, buttonID);
        }
    }

    /** @see IControllerListener#buttonUp(IController, IButton, ButtonID) */
    public void fireButtonUp(IController controller, IButton button, ButtonID buttonID) {
        for (IControllerListener listener : listeners) {
            listener.buttonUp(controller, button, buttonID);
        }
    }

    /** @see IControllerListener#moveStick(IController, StickID) */
    public void fireMoveStick(IController controller, StickID stick) {
        for (IControllerListener listener : listeners) {
            listener.moveStick(controller, stick);
        }
    }
}
