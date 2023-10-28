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


/**
 * Handles instantiating controller instances.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class Controllers {

    private static final Logger logger = Logger.getLogger(Controllers.class.getName());

    /** The list of available controllers. */
    private final List<IController> controllers = new ArrayList<>();

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
                    controllerProvider.addListener(mainListener);
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
     * Lets the backend check the state of all controllers.
     */
    public void checkControllers() {
        controllerProvider.checkControllers();
    }

    /** Manages controllers connect/disconnect. */
    private final IControllerListener mainListener = new IControllerListener() {
        @Override
        public void connected(IController controller) {
            // First make sure it's not already in the list
            boolean addIt = true;
            for (IController oldController : controllers) {
                if (oldController.getDeviceID() == controller.getDeviceID()) {
                    addIt = false;
                }
            }
            if (addIt) {
                controllers.add(controller);
            }
        }

        @Override
        public void disConnected(IController controller) {
            if (!controllers.isEmpty()) {
                Iterator<IController> i = controllers.iterator();
                while (i.hasNext()) {
                    IController oldController = i.next();
                    if (oldController.getDeviceID() != controller.getDeviceID()) {
                        i.remove();
                        break;
                    }
                }
            }
        }

        @Override
        public void buttonDown(IController controller, IButton button, ButtonID buttonID) {
        }

        @Override
        public void buttonUp(IController controller, IButton button, ButtonID buttonID) {
        }

        @Override
        public void moveStick(IController controller, StickID stick) {
        }
    };

    /**
     * Returns all the available controllers.
     *
     * @return The available controllers.
     */
    public IController[] getControllers() {
        return controllers.toArray(IController[]::new);
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
