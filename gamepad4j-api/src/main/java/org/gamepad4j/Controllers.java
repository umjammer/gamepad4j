/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gamepad4j.IControllerListener.IControllerAdapter;


/**
 * Handles instantiating controller instances.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class Controllers {

    private static final Logger logger = Logger.getLogger(Controllers.class.getName());

    /** The controller provider implementation. */
    private final IControllerProvider controllerProvider;

    /** Singleton instance of this class. */
    private static final Controllers instance = new Controllers();

    /**
     * Initializes the controller factory. Must be called once
     * before the controllers can be used.
     */
    private Controllers() {
        try {
            for (IControllerProvider controllerProvider : ServiceLoader.load(IControllerProvider.class)) {
                if (controllerProvider.isSupported()) {
                    controllerProvider.initialize();
                    this.controllerProvider = controllerProvider;
                    logger.fine("Controller provider ready: " + controllerProvider.getClass().getName());
                    return;
                }
            }
            throw new NoSuchElementException("no suitable provider");
        } catch (Exception e) {
            logger.log(Level.FINER, e.toString(), e);
            throw new IllegalStateException("Failed to initialize controller provider instance", e);
        }
    }

    /**
     * Shuts down the controller handler (releases all resources that
     * might be held by any native wrapper / library). This should be
     * called when the game is terminated.
     */
    public void shutdown() {
        controllerProvider.release();
    }

    /**
     * Returns the Controllers instance.
     */
    public static Controllers instance() {
        return instance;
    }

    /**
     * Returns all the available controllers.
     *
     * @return The available controllers.
     */
    public IController[] getControllers() {
        return controllerProvider.getControllers();
    }

    /**
     * Registers a listener for controller events.
     *
     * @param listener The controller listener.
     */
    public void addListener(IControllerListener listener) {
        this.controllerProvider.addListener(listener);
    }

    /**
     * Removes a listener for controller events.
     *
     * @param listener The controller listener to remove.
     */
    public void removeListener(IControllerListener listener) {
        this.controllerProvider.removeListener(listener);
    }
}
