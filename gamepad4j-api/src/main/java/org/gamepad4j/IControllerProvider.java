/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;


import java.io.Closeable;


/**
 * Interface for platform-specific provider for instances
 * of IController.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public interface IControllerProvider extends Closeable {

    /**
     * Invoked once; may perform any kind of initialization.
     */
    void open();

    /**
     * Registers a listener for controller events.
     *
     * @param listener controllers listener.
     */
    void addListener(IControllersListener listener);

    /**
     * Removes a listener for controller events.
     *
     * @param listener controllers listener to remove.
     */
    void removeListener(IControllersListener listener);

    /** Is used at {@link Controllers} constructor. */
    boolean isSupported();

    /**
     * Returns all the available controllers.
     *
     * @return The available controllers.
     */
    IController[] getControllers();
}
