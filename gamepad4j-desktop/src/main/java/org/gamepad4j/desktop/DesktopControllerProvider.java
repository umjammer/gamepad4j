/*
 * @Copyright: Marcel Schoen, Switzerland, 2014, All Rights Reserved.
 */

package org.gamepad4j.desktop;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.gamepad4j.ButtonID;
import org.gamepad4j.ControllerListenerSupport;
import org.gamepad4j.IController;
import org.gamepad4j.IControllerListener;
import org.gamepad4j.IControllerProvider;
import org.gamepad4j.IStick;
import org.gamepad4j.StickID;
import org.gamepad4j.base.BaseAxis;
import org.gamepad4j.base.BaseButton;
import org.gamepad4j.base.BaseStick;
import org.gamepad4j.desktop.Gamepad.Device;
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

    /** native */
    private final Gamepad gamepad;

    /** Map of all connected controllers (deviceID / controller). */
    private static final Map<Integer, DesktopController> connected = new HashMap<>();

    public DesktopControllerProvider() {
        gamepad = Gamepad.getGamepad();
    }

    @Override
    public void initialize() {
        logger.fine("initialize: native...: " + gamepad.getClass().getName());
        gamepad.open();
        logger.fine("initialize: done.");
        gamepad.addGamepadListener(new GamepadListener() {
            @Override
            public void deviceAttach(Device device) {
logger.finer("deviceAttach: " + device.deviceID);
                DesktopController controller = new DesktopController(device);

                listenerSupport.fireConnected(controller);

                connected.put(device.deviceID, controller);
logger.info(String.format("newly connected controller found: %d (%x/%x) / %s",
        controller.getDeviceID(),
        controller.getVendorID(),
        controller.getProductID(),
        controller.getDescription()));
            }

            @Override
            public void deviceRemove(Device device) {
                if (connected.isEmpty() || connected.get(device.deviceID) == null) {
logger.finer("deviceRemove: " + connected + ", " + device.deviceID);
                    return;
                }
                DesktopController controller = connected.get(device.deviceID);

                listenerSupport.fireDisconnected(controller);

                connected.remove(device.deviceID);
            }

            @Override
            public void buttonDown(Device device, int buttonID, double timestamp) {
                if (connected.isEmpty() || connected.get(device.deviceID) == null) {
logger.finer("buttonDown: " + connected + ", " + device.deviceID);
                    return;
                }
                DesktopController controller = connected.get(device.deviceID);

logger.fine("buttonDown: " + buttonID);
                BaseButton button = (BaseButton) controller.getButton(buttonID);
                button.setPressed(true);
                listenerSupport.fireButtonDown(controller, button, ButtonID.UNKNOWN);
            }

            @Override
            public void buttonUp(Device device, int buttonID, double timestamp) {
                if (connected.isEmpty() || connected.get(device.deviceID) == null) {
logger.finer("buttonUp: " + connected + ", " + device.deviceID);
                    return;
                }
logger.finer("buttonUp: " + buttonID);
                DesktopController controller = connected.get(device.deviceID);

                BaseButton button = (BaseButton) controller.getButton(buttonID);
                button.setPressed(false);
                listenerSupport.fireButtonUp(controller, button, ButtonID.UNKNOWN);
            }

            @Override
            public void axisMove(Device device, int axisID, float value, double timestamp) {
                if (connected.isEmpty() || connected.get(device.deviceID) == null) {
logger.finer("axisMove: " + connected + ", " + device.deviceID);
                    return;
                }
logger.finer("axisMove: " + axisID + ", " + value);
                DesktopController controller = connected.get(device.deviceID);

//                BaseAxis axes = (BaseAxis) controller.getAxes()[axisID];
//                axes.setValue(value);
                listenerSupport.fireMoveStick(controller, StickID.UNKNOWN);
            }
        });
    }

    @Override
    public void release() {
        logger.fine("Shutdown native Gamepad API.");
        gamepad.close();
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
        return true; // desktop is always available.
    }

    @Override
    public IController[] getControllers() {
        return connected.values().toArray(IController[]::new);
    }
}
