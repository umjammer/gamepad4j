/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;


/**
 * Interface for platform-specific provider for instances
 * of IController.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public interface IControllerProvider {

    /**
     * Invoked once; may perform any kind of initialization.
     */
    void initialize();

    /**
     * Invoked once when the game terminates. Can be used
     * to free native stuff in JNI wrappers etc.
     */
    void release();

    /**
     * Registers a listener for controller events.
     *
     * @param listener The controller listener.
     */
    void addListener(IControllerListener listener);

    /**
     * Removes a listener for controller events.
     *
     * @param listener The controller listener to remove.
     */
    void removeListener(IControllerListener listener);

    /** Is used at {@link Controllers} constructor. */
    boolean isSupported();

    /**
     * Returns all the available controllers.
     *
     * @return The available controllers.
     */
    IController[] getControllers();
}
