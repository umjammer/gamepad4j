/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;

import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Handles instantiating controller instances.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public final class Controllers {

    private static final Logger logger = Logger.getLogger(Controllers.class.getName());

    /** The controller provider implementation. */
    private final IControllerProvider controllerProvider;

    /** Singleton instance of this class. */
    private static final Controllers instance = new Controllers();

    /**
     * Initializes the controller factory. Must be called once
     * before the controllers can be used.
     *
     * @throws IllegalStateException no provider or something wrong.
     */
    private Controllers() {
        try {
            for (IControllerProvider controllerProvider : ServiceLoader.load(IControllerProvider.class)) {
                // TODO only one provider is activated
                if (controllerProvider.isSupported()) {
                    controllerProvider.open();
                    this.controllerProvider = controllerProvider;
                    logger.fine("Controller provider ready: " + controllerProvider.getClass().getName());
                    return;
                }
            }
            throw new IllegalStateException("no suitable provider");
        } catch (Exception e) {
            logger.log(Level.FINER, e.toString(), e);
            throw new IllegalStateException("Failed to initialize controller provider instance", e);
        }
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

    /** */
    public IController getController(int mid, int pid) {
        for (IController controller : getControllers()) {
logger.fine(String.format("%s: %4x, %4x%n", controller.getDescription(), controller.getVendorID(), controller.getProductID()));
            if (controller.getVendorID() == mid && controller.getProductID() == pid) {
                return controller;
            }
        }
        throw new NoSuchElementException(String.format("no device: mid: %1$d(0x%1$x), pid: %2$d(0x%2$x))", mid, pid));
    }

    /**
     * Registers a listener for controllers events.
     *
     * @param listener controllers listener.
     */
    public void addListener(IControllersListener listener) {
        this.controllerProvider.addListener(listener);
    }

    /**
     * Removes a listener for controller events.
     *
     * @param listener The controller listener to remove.
     */
    public void removeListener(IControllersListener listener) {
        this.controllerProvider.removeListener(listener);
    }
}
