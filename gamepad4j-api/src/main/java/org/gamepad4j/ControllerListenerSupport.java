/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;

import java.util.ArrayList;
import java.util.List;


/**
 * Handles accessing controller listeners.
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

    /** @see IControllerListener#buttonDown(IButton, ButtonID) */
    public void fireButtonDown(IButton button, ButtonID buttonID) {
        for (IControllerListener listener : listeners) {
            listener.buttonDown(button, buttonID);
        }
    }

    /** @see IControllerListener#buttonUp(IButton, ButtonID) */
    public void fireButtonUp(IButton button, ButtonID buttonID) {
        for (IControllerListener listener : listeners) {
            listener.buttonUp(button, buttonID);
        }
    }

    /** @see IControllerListener#moveStick(IAxis, StickID) */
    public void fireMoveStick(IAxis axis, StickID stick) {
        for (IControllerListener listener : listeners) {
            listener.moveStick(axis, stick);
        }
    }
}
