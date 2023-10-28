/*
 * @Copyright: Marcel Schoen, Switzerland, 2014, All Rights Reserved.
 */

package org.gamepad4j.desktop;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.gamepad4j.ControllerListenerSupport;
import org.gamepad4j.IControllerListener;
import org.gamepad4j.IControllerProvider;
import org.gamepad4j.desktop.Gamepad.GamepadListener;


/**
 * Controller provider for desktop systems (Linux, MacOS X, Windows).
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class DesktopControllerProvider implements IControllerProvider {

    private static final Logger logger = Logger.getLogger(DesktopControllerProvider.class.getName());

    /** Stores controller listener support. */
    private final ControllerListenerSupport listenerSupport = new ControllerListenerSupport();

    public static NativeGamepad nativeGamepad = null;

    /** Map of all connected controllers (deviceID / controller). */
    private static final Map<Integer, DesktopController> connected = new HashMap<>();

    /** Stores the controllers instance pool. */
    private static final DesktopController[] controllerPool = new DesktopController[16];

    /** Stores the number of connected controllers. */
    private static int numberOfControllers = -1;

    @Override
    public void initialize() {
        nativeGamepad = new NativeGamepad();
        // i couldn't find code do this in the original code. how do i do this?
        nativeGamepad.setGamepadListener(new GamepadListener() {
            @Override
            public void deviceAttach(Gamepad.Device device) {
logger.fine("deviceAttach:");
                listenerSupport.fireConnected(connected.get(device.deviceID));
            }

            @Override
            public void deviceRemove(Gamepad.Device device) {
logger.fine("deviceRemove:");
                listenerSupport.fireDisconnected(connected.get(device.deviceID));
            }

            @Override
            public void buttonDown(Gamepad.Device device, int buttonID, double timestamp) {
logger.fine("buttonDown:");
                listenerSupport.fireButtonDown(connected.get(device.deviceID), null, null);
            }

            @Override
            public void buttonUp(Gamepad.Device device, int buttonID, double timestamp) {
logger.fine("buttonUp:");
                listenerSupport.fireButtonUp(connected.get(device.deviceID), null, null);
            }

            @Override
            public void axisMove(Gamepad.Device device, int axisID, float value, double timestamp) {
logger.fine("axisMove:");
                listenerSupport.fireMoveStick(connected.get(device.deviceID), null);
            }
        });
        for (int i = 0; i < controllerPool.length; i++) {
            controllerPool[i] = new DesktopController(-1);
        }
    }

    @Override
    public void release() {
        nativeGamepad.close();
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
        nativeGamepad.detectPads();
        for (DesktopController controller : connected.values()) {
            controller.setChecked(false);
        }

        float value = nativeGamepad.getControllerAxisState(0, 8);
        logger.finer(">> Axis 8: " + value);

        // 1st check which controllers are (still) connected
        int newNumberOfControllers = nativeGamepad.getNumberOfPads();
        if (newNumberOfControllers != numberOfControllers) {
            numberOfControllers = newNumberOfControllers;
            logger.fine("Number of controllers: " + numberOfControllers);
        }
        logger.finest("Check for newly connected controllers...");
        for (int ct = 0; ct < numberOfControllers; ct++) {
            int connectedId = nativeGamepad.getDeviceID(ct);
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
                    nativeGamepad.updateControllerInfo(newController);
                    DesktopControllerProvider.connected.put(newController.getDeviceID(), newController);
                    logger.info("***********************************************************************");
                    logger.info("Newly connected controller found: " + newController.getDeviceID()
                            + " (" + Integer.toHexString(newController.getVendorID()) + "/"
                            + Integer.toHexString(newController.getProductID())
                            + ") / " + newController.getDescription());
                    logger.info("***********************************************************************");
                    listenerSupport.fireConnected(newController);
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
                listenerSupport.fireDisconnected(controller);
                returnInstanceToPool(controller);
                // Must be removed from map with iterator, otherwise
                // ConcurrentModificationException will occur
                iter.remove();
            }
        }

        // 3rd update the state of all remaining controllers
        logger.finest("Update controllers...");
        for (DesktopController controller : connected.values()) {
            nativeGamepad.updateControllerStatus(controller);
        }
    }

    @Override
    public void addListener(IControllerListener listener) {
        this.listenerSupport.addListener(listener);
    }

    @Override
    public void removeListener(IControllerListener listener) {
        this.listenerSupport.removeListener(listener);
    }

    @Override
    public boolean isSupported() {
        return true; // TODO
    }
}
