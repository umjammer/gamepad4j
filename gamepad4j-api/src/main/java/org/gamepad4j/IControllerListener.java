/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;


/**
 * Interface for handling controller callback (events).
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public interface IControllerListener {

    /**
     * Notifies a button press.
     *
     * @param button     The button that was pressed. If an analog pressure value
     *                   is required, it can be queried from this object.
     * @param buttonID   The deviceID of the button that was pressed.
     */
    void buttonDown(IButton button, ButtonID buttonID);

    /**
     * Notifies a button release.
     *
     * @param button     The button that was released.
     * @param buttonID   The deviceID of the button that was released.
     */
    void buttonUp(IButton button, ButtonID buttonID);

    /**
     * Notifies a stick move.
     *
     * @param axis
     * @param stick
     */
    void moveStick(IAxis axis, StickID stick);

    /** */
    class IControllerAdapter implements IControllerListener {

        @Override public void buttonDown(IButton button, ButtonID buttonID) {
        }

        @Override public void buttonUp(IButton button, ButtonID buttonID) {
        }

        @Override public void moveStick(IAxis axis, StickID stick) {
        }
    }
}
