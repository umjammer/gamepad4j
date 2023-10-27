/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j.ouya;

import java.util.HashMap;
import java.util.Map;

import org.gamepad4j.ControllerListenerAdapter;
import org.gamepad4j.IController;
import org.gamepad4j.IControllerListener;
import org.gamepad4j.IControllerProvider;

import tv.ouya.console.api.OuyaController;


/**
 * Provides controller instances on OUYA.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class OuyaControllerProvider implements IControllerProvider {

    private static final int MAX_CONTROLLERS = 8;

    /** Stores controller listeners. */
    private ControllerListenerAdapter listeners = new ControllerListenerAdapter();

    /** Map of all connected OUYA controllers. */
    private Map<OuyaController, OuyaControllerWrapper> connected = new HashMap<OuyaController, OuyaControllerWrapper>();

    @Override
    public void initialize() {
        System.out.println("OUYA controller provider ready.");
        updateControllers();
    }

    @Override
    public void release() {
        // do nothing
    }

    @Override
    public void checkControllers() {
        boolean update = false;
        for (int ct = 0; ct < MAX_CONTROLLERS; ct++) {
            if (OuyaController.getControllerByPlayer(ct) != null) {
                if (connected.get(OuyaController.getControllerByPlayer(ct)) == null) {
                    // Newly connected controller found
                    update = true;
                    System.out.println("Newly connected OUYA controller found.");
                }
            }
        }
        if (update) {
            updateControllers();
        }
        // Now update the button and axes states
        for (int ct = 0; ct < MAX_CONTROLLERS; ct++) {
            if (OuyaController.getControllerByPlayer(ct) != null) {
                OuyaController ouyaController = OuyaController.getControllerByPlayer(ct);
                OuyaControllerWrapper wrapper = this.connected.get(ouyaController);
                wrapper.updateValues();
            }
        }
    }

    /**
     * Updates the array of controllers. Invoke only when
     * an update is really necessary (because this method creates new
     * wrapper objects and changes the map of controllers).
     */
    private void updateControllers() {
        this.connected.clear();
        for (int ct = 0; ct < MAX_CONTROLLERS; ct++) {
            if (OuyaController.getControllerByPlayer(ct) != null) {
                OuyaController ouyaController = OuyaController.getControllerByPlayer(ct);
                OuyaControllerWrapper controller = new OuyaControllerWrapper(ouyaController);
                this.connected.put(ouyaController, controller);
                for (IControllerListener listener : this.listeners.getListeners()) {
                    listener.connected(controller);
                }
            }
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
