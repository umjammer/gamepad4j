/*
 * @Copyright: Marcel Schoen, Switzerland, 2014, All Rights Reserved.
 */

package org.gamepad4j.desktop;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.gamepad4j.ControllerListenerAdapter;
import org.gamepad4j.IControllerListener;
import org.gamepad4j.IControllerProvider;


/**
 * Controller provider for desktop systems (Linux, MacOS X, Windows).
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class DesktopControllerProvider implements IControllerProvider {

    static final Logger logger = Logger.getLogger(DesktopControllerProvider.class.getName());

    /** Stores controller listeners. */
    private ControllerListenerAdapter listeners = new ControllerListenerAdapter();

    public static GamepadJniWrapper jniWrapper = null;

    /** Map of all connected controllers (deviceID / controller). */
    private static Map<Integer, DesktopController> connected = new HashMap<>();

    /** Stores the controllers instance pool. */
    private static DesktopController[] controllerPool = new DesktopController[16];

    /** Stores the number of connected controllers. */
    private static int numberOfControllers = -1;

    @Override
    public void initialize() {
        jniWrapper = new GamepadJniWrapper();
        jniWrapper.initialize();
        for (int i = 0; i < controllerPool.length; i++) {
            controllerPool[i] = new DesktopController(-1);
        }
        System.out.flush();
    }

    @Override
    public void release() {
        jniWrapper.natRelease();
        jniWrapper.close();
    }

    /**
     * Returns a controller holder instance from the pool and
     * sets its code to the given value.
     *
     * @param index The code to set for the controller instance.
     * @return The controller holder (or null if there was none free).
     */
    private synchronized static DesktopController getInstanceFromPool(int index) {
        for (int i = 0; i < controllerPool.length; i++) {
            if (controllerPool[i] != null) {
                DesktopController reference = controllerPool[i];
                reference.setIndex(index);
//				connected.put(code, reference);
                controllerPool[i] = null;
                return reference;
            }
        }
        return null;
    }

    /**
     * Returns the given controller instance to the pool.
     *
     * @param controller The controller instance to return (must not be null).
     */
    private synchronized static void returnInstanceToPool(DesktopController controller) {
        for (int i = 0; i < controllerPool.length; i++) {
            if (controllerPool[i] == null) {
                controllerPool[i] = controller;
            }
        }
    }

    @Override
    public synchronized void checkControllers() {
        jniWrapper.natDetectPads();
        for (DesktopController controller : connected.values()) {
            controller.setChecked(false);
        }


        float value = jniWrapper.natGetControllerAxisState(0, 8);
        logger.finer(">> Axis 8: " + value);


        // 1st check which controllers are (still) connected
        int newNumberOfControllers = jniWrapper.natGetNumberOfPads();
        if (newNumberOfControllers != numberOfControllers) {
            numberOfControllers = newNumberOfControllers;
            logger.fine("Number of controllers: " + numberOfControllers);
        }
		logger.finest("Check for newly connected controllers...");
        for (int ct = 0; ct < numberOfControllers; ct++) {
            int connectedId = jniWrapper.natGetDeviceID(ct);
            if (connectedId != -1) {
                DesktopController controller = connected.get(connectedId);
                if (controller != null) {
                    controller.setChecked(true);
                } else {
                    DesktopController newController = getInstanceFromPool(ct);
                    if (newController == null) {
                        throw new IllegalStateException("** DesktopController instance pool exceeded! **");
                    }
                    newController.setChecked(true);
                    jniWrapper.updateControllerInfo(newController);
                    DesktopControllerProvider.connected.put(newController.getDeviceID(), newController);
                    logger.info("***********************************************************************");
                    logger.info("Newly connected controller found: " + newController.getDeviceID()
                            + " (" + Integer.toHexString(newController.getVendorID()) + "/"
                            + Integer.toHexString(newController.getProductID())
                            + ") / " + newController.getDescription());
                    logger.info("***********************************************************************");
                    listeners.getListeners().get(0).connected(newController);
                }
            }
        }

        // 2nd remove the controllers not found in the first loop
		logger.finest("Check for disconnected controllers...");
        Iterator<Entry<Integer, DesktopController>> iter = connected.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<Integer, DesktopController> entry = iter.next();
            DesktopController controller = entry.getValue();
            if (!controller.isChecked()) {
                logger.info("Controller disconnected: " + controller.getDeviceID() + " / " + controller.getDescription());
                listeners.getListeners().get(0).disConnected(controller);
                returnInstanceToPool(controller);
                // Must be removed from map with iterator, otherwise
                // ConcurrentModificationException will occur
                iter.remove();
            }
        }

        // 3rd update the state of all remaining controllers
		logger.finest("Update controllers...");
        for (DesktopController controller : connected.values()) {
            jniWrapper.updateControllerStatus(controller);
        }
    }

    @Override
    public void addListener(IControllerListener listener) {
        this.listeners.addListener(listener);
    }

    @Override
    public void removeListener(IControllerListener listener) {
        this.listeners.removeListener(listener);
    }
}
