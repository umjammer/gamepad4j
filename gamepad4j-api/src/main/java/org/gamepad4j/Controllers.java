/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.gamepad4j.util.PlatformUtil;


/**
 * Handles instantiating controller instances.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class Controllers implements IControllerListener {

    static final Logger logger = Logger.getLogger(Controllers.class.getName());

    /** Stores controller listeners. */
    private ControllerListenerAdapter listenerAdapter = new ControllerListenerAdapter();

    /** The list of available controllers. */
    private IController[] controllers = new IController[0];

    /** The controller provider implementation. */
    private static IControllerProvider controllerProvider = null;

    /** Singleton instance of this class. */
    private static Controllers instance = new Controllers();

    /**
     * Initializes the controller factory. Must be called once
     * before the controllers can be used.
     */
    public static void initialize() {

        String providerType = "Desktop";
        if (PlatformUtil.isOuya()) {
            providerType = "Ouya";
        }
        // TODO: Add more Android types (generic 4.x, Xperia Play, GameStik)

        try {
            String providerClassName = "org.gamepad4j." + providerType.toLowerCase() + "." + providerType + "ControllerProvider";
            Class<?> providerClass = Class.forName(providerClassName);
            controllerProvider = (IControllerProvider) providerClass.getDeclaredConstructor().newInstance();
            controllerProvider.addListener(instance);
            controllerProvider.initialize();
            logger.fine("Controller provider ready: " + controllerProvider.getClass().getName());
        } catch (Exception e) {
            logger.log(Level.FINER, e.toString(), e);
            throw new IllegalStateException("Failed to initialize controller provider instance: " + e);
        }
    }

    /**
     * Shuts down the controller handler (releases all resources that
     * might be held by any native wrapper / library). This should be
     * called when the game is terminated.
     */
    public static void shutdown() {
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
    public static void checkControllers() {
        controllerProvider.checkControllers();
    }

    @Override
    public void connected(IController controller) {
        // First make sure it's not already in the list
        boolean addIt = true;
        for (IController oldController : this.controllers) {
            if (oldController.getDeviceID() == controller.getDeviceID()) {
                addIt = false;
            }
        }
        if (addIt) {
            IController[] newControllers = new IController[this.controllers.length + 1];
            System.arraycopy(this.controllers, 0, newControllers, 0, this.controllers.length);
            newControllers[newControllers.length - 1] = controller;
            this.controllers = newControllers;
            for (IControllerListener listener : this.listenerAdapter.getListeners()) {
                listener.connected(controller);
            }
        }
    }

    @Override
    public void disConnected(IController controller) {
        if (this.controllers.length > 0) {
            IController[] newControllers = new IController[this.controllers.length - 1];
            int ct = 0;
            for (IController oldController : this.controllers) {
                if (oldController.getDeviceID() != controller.getDeviceID()) {
                    newControllers[ct] = oldController;
                }
            }
            this.controllers = newControllers;
            for (IControllerListener listener : this.listenerAdapter.getListeners()) {
                listener.disConnected(controller);
            }
        }
    }

    @Override
    public void buttonDown(IController controller, IButton button,
                           ButtonID buttonID) {
        for (IControllerListener listener : this.listenerAdapter.getListeners()) {
            listener.buttonDown(controller, button, buttonID);
        }
    }

    @Override
    public void buttonUp(IController controller, IButton button,
                         ButtonID buttonID) {
        for (IControllerListener listener : this.listenerAdapter.getListeners()) {
            listener.buttonUp(controller, button, buttonID);
        }
    }

    @Override
    public void moveStick(IController controller, StickID stick) {
        for (IControllerListener listener : this.listenerAdapter.getListeners()) {
            listener.moveStick(controller, stick);
        }
    }

    /**
     * Returns all the available controllers.
     *
     * @return The available controllers.
     */
    public static IController[] getControllers() {
        return instance.controllers;
    }

    /**
     * Registers a listener for controller events.
     *
     * @param listener The controller listener.
     */
    public void addListener(IControllerListener listener) {
        this.listenerAdapter.addListener(listener);
    }

    /**
     * Removes a listener for controller events.
     *
     * @param listener The controller listener to remove.
     */
    public void removeListener(IControllerListener listener) {
        this.listenerAdapter.removeListener(listener);
    }
}
