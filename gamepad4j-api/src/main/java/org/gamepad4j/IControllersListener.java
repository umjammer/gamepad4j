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
public interface IControllersListener {

    /**
     * Is invoked when a controller is connected.
     *
     * @param controller The controller that was connected.
     */
    void connected(IController controller);

    /**
     * Is invoked when a controller is disconnected.
     *
     * @param controller The controller that was disconnected.
     */
    void disConnected(IController controller);
}
