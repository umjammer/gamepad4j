/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j.desktop;

import java.util.logging.Logger;

import org.gamepad4j.base.BaseAxis;
import org.gamepad4j.base.BaseButton;
import org.gamepad4j.shared.Gamepad;
import org.gamepad4j.shared.Gamepad.Device;


/**
 * JNI wrapper for Gamepad library by Alex Diener.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class NativeGamepad {

    static final Logger logger = Logger.getLogger(NativeGamepad.class.getName());

    private Gamepad gamepad = Gamepad.getInstance();

    /**
     * Array which temporarily holds controller ID information.
     */
    private static int[] idArray = new int[3];

    /**
     * Array which temporarily holds controller button state information.
     */
    private static boolean[] buttonArray = new boolean[64];

    /**
     * Array which temporarily holds controller axis state information.
     */
    private static float[] axisArray = new float[64];

    /**
     * Updates the status information on the given controller holder object.
     *
     * @param controller
     */
    public void updateControllerStatus(DesktopController controller) {
        getControllerButtonStates(controller.getIndex(), buttonArray);
        int numberOfButtons = getNumberOfButtons(controller.getIndex());
        for (int i = 0; i < numberOfButtons; i++) {
            BaseButton button = (BaseButton) controller.getButton(i);
            if (button != null) {
                boolean isPressed = getControllerButtonState(controller.getIndex(), button.getCode());
                button.setPressed(isPressed);
            }
        }

        getControllerAxesStates(controller.getIndex(), axisArray);
        BaseAxis[] axes = (BaseAxis[]) controller.getAxes();
        for (int i = 0; i < axes.length; i++) {
            if (axes[i] != null) {
                float axisState = getControllerAxisState(controller.getIndex(), i);
                axes[i].setValue(axisState);
            }
        }
    }

    /**
     * Updates the ID information on the given controller holder object.
     *
     * @param controller
     */
    public void updateControllerInfo(DesktopController controller) {
        String description = getControllerDescription(controller.getIndex());
        controller.setDescription(description);
        getControllerIDs(controller.getIndex(), idArray);
        controller.setDeviceID(idArray[0]);
        controller.setVendorID(idArray[1]);
        controller.setProductID(idArray[2]);
        controller.initializeMapping();

        int numberOfButtons = getNumberOfButtons(controller.getIndex());
        controller.createButtons(numberOfButtons);

        int numberOfAxes = getNumberOfAxes(controller.getIndex());
        controller.createAxes(numberOfAxes);
    }

    /**
     * Initializes the native gamepad library.
     */
    public void initialize() {
        logger.fine("initialize: native...");
        gamepad.init();
        logger.fine("initialize: detect pads...");
        detectPads();
        logger.fine("initialize: done.");
    }

    /**
     * Releases all resources held by the native gamepad library.
     */
    public void close() {
        logger.fine("Shutdown native Gamepad API.");
        gamepad.shutdown();
    }

    /**
     * Returns the device ID of the given pad.
     *
     * @param index The code of the pad.
     * @return The device ID (or -1 if the code was invalid).
     */
    public int getDeviceID(int index) {
        Device device = gamepad.deviceAtIndex(index);
        if(device == null) {
            return -1;
        }
        return device.deviceID;
    }

    /**
     * Returns the number of gamepads currently connected.
     *
     * @return The number of pads.
     */
    public int getNumberOfPads() {
        return gamepad.numDevices();
    }

    /**
     * Returns the number of analog axes on the given pad.
     *
     * @param index The code of the pad.
     * @return The number of axes.
     */
    public int getNumberOfAxes(int index) {
        Gamepad.Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            return -1;
        }
        return device.numAxes;
    }

    /**
     * Returns the number of digital buttons on the given pad.
     * NOTE: Analog trigger buttons are handled like axes, not
     * like buttons.
     *
     * @param index The code of the pad.
     * @return The number of buttons.
     */
    public int getNumberOfButtons(int index) {
        Gamepad.Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            return -1;
        }
        return device.numButtons;
    }

    /**
     * Returns the ID of the pad with the given code.
     *
     * @param index The code of the pad.
     * @return The ID (if such a pad exists).
     */
    public int getIdOfPad(int index) {
        Gamepad.Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            return -1;
        }
        return device.deviceID;
    }

    /**
     * Forces detection of connected gamepads.
     */
    public void detectPads()  {
        gamepad.detectDevices();
        // The following call is required on Windows
        gamepad.processEvents();
    }

    /**
     * Returns the description text for a given pad.
     *
     * @param index The code of the pad.
     * @return The description text (or null).
     */
    public String getControllerDescription(int index) {
        Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            return null;
        }
        return device.description;
    }

    /**
     * Updates the various IDs for a given pad in the given array.
     *
     * @param index   The code of the pad.
     * @param idArray The array which gets the updated values, where [0] is
     *                the device ID, [1] the vendor ID and [2] the product ID.
     */
    public void getControllerIDs(int index, int[] idArray) {
        Gamepad.Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            return;
        }
        idArray[0] = device.deviceID;
        idArray[1] = device.vendorID;
        idArray[2] = device.productID;
    }

    /**
     * Updates the state of all digital buttons on the given controller.
     *
     * @param index      The code of the pad.
     * @param stateArray An array where every entry represents a button, and if it's
     *                   TRUE, the button is pressed.
     */
    public void getControllerButtonStates(int index, boolean[] stateArray){
        Gamepad.Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            return;
        }

        System.arraycopy(device.buttonStates, 0, stateArray, 0, device.numButtons);
    }

    /**
     * Updates the state of all analog axes on the given controller.
     *
     * @param index     The code of the pad.
     * @param axesArray An array where every entry represents an axis.
     */
    public void getControllerAxesStates(int index, float[] axesArray) {
        Gamepad.Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            logger.warning("ERROR: Can't update axes of device: " + index);
            return;
        }

        System.arraycopy(device.axisStates, 0, axesArray, 0, device.numAxes);
    }

    /**
     * Polls the state of a certain button of a certain controller.
     *
     * @param index       The code of the controller.
     * @param buttonIndex The code of the button.
     */
    public boolean getControllerButtonState(int index, int buttonIndex){
        Gamepad.Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            return false;
        }
        return device.buttonStates[buttonIndex];
    }

    /**
     * Polls the state of a certain axis of a certain controller.
     *
     * @param index     The code of the controller.
     * @param axisIndex The code of the axis.
     */
    public float getControllerAxisState(int index, int axisIndex){
        Gamepad.Device device = gamepad.deviceAtIndex(index);
        if (device == null) {
            logger.warning("ERROR: Can't update axis of device: " + index);
            return -1;
        }
        // TODO: Bounds check for "axisIndex" variable
        return device.axisStates[axisIndex];
    }
}
