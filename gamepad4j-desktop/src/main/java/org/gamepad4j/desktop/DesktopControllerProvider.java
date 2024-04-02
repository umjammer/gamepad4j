/*
 * @Copyright: Marcel Schoen, Switzerland, 2014, All Rights Reserved.
 */

package org.gamepad4j.desktop;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.gamepad4j.ControllersListenerSupport;
import org.gamepad4j.IController;
import org.gamepad4j.IControllerProvider;
import org.gamepad4j.IControllersListener;
import org.gamepad4j.desktop.Gamepad.Device;
import org.gamepad4j.desktop.Gamepad.GamepadAdapter;


/**
 * Controller provider for desktop systems (Linux, MacOS X, Windows).
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class DesktopControllerProvider implements IControllerProvider {

    private static final Logger logger = Logger.getLogger(DesktopControllerProvider.class.getName());

    /** Stores controllers listener support. */
    private final ControllersListenerSupport listenerSupport = new ControllersListenerSupport();

    /** native */
    private final Gamepad gamepad;

    /** Map of all connected controllers (deviceID / controller). */
    private static final Map<Integer, DesktopController> connected = new HashMap<>();

    public DesktopControllerProvider() {
        gamepad = Gamepad.getGamepad();
    }

    @Override
    public void open() {
        logger.fine("initialize: native...: " + gamepad.getClass().getName());
        gamepad.open();
        logger.fine("initialize: done.");
        gamepad.addGamepadListener(new GamepadAdapter() {
            @Override
            public void deviceAttach(Device device) {
logger.finer("deviceAttach: " + device.deviceID);
                DesktopController controller = new DesktopController(device, gamepad);

                listenerSupport.fireConnected(controller);

                connected.put(device.deviceID, controller);
logger.fine(String.format("newly connected controller found: %d (%x/%x) / %s",
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
        });
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    @Override
    public void close() {
        logger.fine("Shutdown native Gamepad API.");
        gamepad.close();
    }

    @Override
    public void addListener(IControllersListener listener) {
        this.listenerSupport.addListener(listener);
    }

    @Override
    public void removeListener(IControllersListener listener) {
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
