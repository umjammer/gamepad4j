/*
 * @Copyright: Marcel Schoen, Switzerland, 2014, All Rights Reserved.
 */

package org.gamepad4j.desktop;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import org.gamepad4j.AxisID;
import org.gamepad4j.ButtonID;
import org.gamepad4j.ControllerListenerSupport;
import org.gamepad4j.IControllerListener;
import org.gamepad4j.IStick;
import org.gamepad4j.StickID;
import org.gamepad4j.TriggerID;
import org.gamepad4j.base.AbstractBaseController;
import org.gamepad4j.base.BaseAxis;
import org.gamepad4j.base.BaseButton;
import org.gamepad4j.base.BaseStick;
import org.gamepad4j.base.BaseTrigger;
import org.gamepad4j.desktop.Gamepad.Device;
import org.gamepad4j.desktop.Gamepad.GamepadAdapter;
import org.gamepad4j.desktop.Gamepad.GamepadListener;
import org.gamepad4j.desktop.Mapping.MappingType;


/**
 * Holder for status information about a desktop controller.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class DesktopController extends AbstractBaseController {

    private static final Logger logger = Logger.getLogger(DesktopController.class.getName());

    /** Stores controller listener support. */
    private final ControllerListenerSupport listenerSupport = new ControllerListenerSupport();

    /** Default deadzone value for desktop controller axes. */
    public static final float DEFAULT_DEADZONE = 0.1f;

    /** Stores the controller code value. */
    private final Device device;

    /** Default deadzone value. */
    private float defaultDeadZone = DEFAULT_DEADZONE;

    private Gamepad gamepad;

    /**
     * Creates a desktop controller holder for a certain code.
     *
     * @param device The device number of the controller.
     */
    public DesktopController(Device device, Gamepad gamepad) {
        // For now, create instance with "wrong" device ID 0.
        // Real value will be updated later through setter method.
        super(device.deviceID);

        this.device = device;
        this.gamepad = gamepad;

        this.description = device.description;
        this.vendorID = device.vendorID;
        this.productID = device.productID;

        // TODO below
        this.initializeMapping();
        this.createButtons(device.numButtons);
        this.createAxes(device.numAxes);
    }

    /**
     * Initialized the mapping for this controller.
     */
    public void initializeMapping() {
        Mapping.loadMapping(this);
    }

    /**
     * @param deadZone
     */
    public void setDefaultDeadZone(float deadZone) {
        this.defaultDeadZone = deadZone;
    }

    /** */
    private final GamepadListener inputListener = new GamepadAdapter() {
        @Override
        public void buttonDown(Device device, int buttonID, double timestamp) {
logger.finest("buttonDown: " + buttonID);
            BaseButton button = (BaseButton) DesktopController.this.getButton(buttonID);
            button.setPressed(true);
            DesktopController.this.listenerSupport.fireButtonDown(button, ButtonID.UNKNOWN);
        }

        @Override
        public void buttonUp(Device device, int buttonID, double timestamp) {
            BaseButton button = (BaseButton) DesktopController.this.getButton(buttonID);
            button.setPressed(false);
            DesktopController.this.listenerSupport.fireButtonUp(button, ButtonID.UNKNOWN);
        }

        @Override
        public void axisMove(Device device, int axisID, float value, double timestamp) {
            BaseAxis axes = (BaseAxis) DesktopController.this.getAxes()[axisID];
            if (axes == null) {
logger.finest("no axis for: " + axisID);
            } else {
logger.finest("axis: " + axes + ", " + value);
                axes.setValue(value);
                DesktopController.this.listenerSupport.fireMoveStick(axes, StickID.UNKNOWN);
            }
        }
    };

    @Override
    public void open() {
        gamepad.addGamepadListener(inputListener);
    }

    /**
     * Returns the code value of this desktop controller holder.
     *
     * @return The code value.
     */
    @Override
    public int getDeviceID() {
        return this.deviceID;
    }

    /**
     * Creates the given number of buttons, based on the
     * mapping configuration.
     *
     * @param numberOfButtons The number of buttons.
     */
    public void createButtons(int numberOfButtons) {
        logger.fine("Create " + numberOfButtons + " buttons for pad...");

        // TODO Use pooling for button instances
        this.buttons = new BaseButton[numberOfButtons];
        for (int buttonNo = 0; buttonNo < numberOfButtons; buttonNo++) {
            this.buttons[buttonNo] = new BaseButton(this, buttonNo, "", "");
        }

        for (int buttonNo = 0; buttonNo < numberOfButtons; buttonNo++) {
            String mapping = Mapping.getMapping(this, MappingType.BUTTON, buttonNo);
            if (mapping != null) {
                ButtonID buttonID = ButtonID.getButtonIDfromString(mapping);
                logger.finer("Map button no. " + buttonNo + " from mapping " + mapping + " to button ID " + buttonID);
                this.buttons[buttonNo].setID(buttonID);
                this.buttonMap.put(buttonID, this.buttons[buttonNo]);
                String label = Mapping.getButtonLabel(this, buttonID);
                if (label == null) {
                    label = Mapping.getDefaultButtonLabel(buttonID);
                }
                if (label != null) {
                    this.buttons[buttonNo].setDefaultLabel(label);
                }
                String labelKey = Mapping.getButtonLabelKey(this, buttonID);
                if (labelKey != null) {
                    this.buttons[buttonNo].setLabelKey(labelKey);
                }
            }
        }
    }

    /**
     * Creates the given number of axes, and then the sticks,
     * triggers and d-pad based on the mapping configuration.
     *
     * @param numberOfAxes The number of axes.
     */
    public void createAxes(int numberOfAxes) {
        logger.fine("Process " + numberOfAxes + " analog axes...");

        // TODO Use pooling for these
        this.axes = new BaseAxis[numberOfAxes];
        this.triggers = new BaseTrigger[Mapping.getNumberOfTriggers(this)];

        int triggerNo = 0;
        for (int axisNo = 0; axisNo < axes.length; axisNo++) {
            String mapping = Mapping.getMapping(this, Mapping.MappingType.TRIGGER_AXIS, axisNo);
            if (mapping != null) {
                processTriggerAxis(mapping, axisNo, triggerNo++);
            }
            mapping = Mapping.getMapping(this, Mapping.MappingType.STICK_AXIS, axisNo);
            if (mapping != null) {
                processStickAxis(mapping, axisNo);
            }
            mapping = Mapping.getMapping(this, Mapping.MappingType.DPAD_AXIS, axisNo);
            if (mapping != null) {
                processDpadAxis(mapping, axisNo);
            }
        }

        this.sticks = stickMap.values().toArray(BaseStick[]::new);
    }

    /**
     * Processes D-Pad mappings.
     *
     * @param mapping The mapping value.
     * @param axisNo  The number of the analog axis.
     */
    private void processDpadAxis(String mapping, int axisNo) {
        // Cut off the axis type part, like "X"
        String axisTypePart = mapping.substring(mapping.indexOf(".") + 1);
        if (axisTypePart.equalsIgnoreCase("X")) {
            logger.fine("Map axis no. " + axisNo + " from mapping " + mapping + " to dpad axis X");
            this.axes[axisNo] = new BaseAxis(AxisID.D_PAD_X, axisNo);
            this.dpadAxisMap.put(AxisID.D_PAD_X, this.axes[axisNo]);
        } else if (axisTypePart.equalsIgnoreCase("Y")) {
            logger.fine("Map axis no. " + axisNo + " from mapping " + mapping + " to dpad axis Y");
            this.axes[axisNo] = new BaseAxis(AxisID.D_PAD_Y, axisNo);
            this.dpadAxisMap.put(AxisID.D_PAD_Y, this.axes[axisNo]);
        } else {
            logger.fine("Map axis no. " + axisNo + " from mapping " + mapping + " to dpad");
            this.axes[axisNo] = new BaseAxis(AxisID.D_PAD, axisNo);
            this.dpadAxisMap.put(AxisID.D_PAD, this.axes[axisNo]);
        }
    }

    /**
     * Processes stick mappings.
     *
     * @param mapping The mapping value.
     * @param axisNo  The number of the analog axis.
     */
    private void processStickAxis(String mapping, int axisNo) {
logger.finer(mapping + ", " + axisNo);
        // Cut off the ID part, like "LEFT"
        String stickIDpart = mapping.substring(0, !mapping.contains(".") ? mapping.length() : mapping.indexOf("."));
        // Cut off the axis type part, like "X"
        String axisTypePart = mapping.substring(mapping.indexOf(".") + 1);
        StickID stickID = StickID.getStickIDfromString(stickIDpart);
        BaseStick stick = (BaseStick) stickMap.get(stickID);
        if (stick == null) {
            stick = new BaseStick(stickID);
            stickMap.put(stickID, stick);
        }
        if (axisTypePart.equalsIgnoreCase("X")) {
            logger.finer("Map axis no. " + axisNo + " from mapping " + mapping + " to stick " + stickID + " axis X");
            this.axes[axisNo] = new BaseAxis(AxisID.X, axisNo);
            stick.setAxis(this.axes[axisNo]);
        } else {
            logger.finer("Map axis no. " + axisNo + " from mapping " + mapping + " to stick " + stickID + " axis Y");
            this.axes[axisNo] = new BaseAxis(AxisID.Y, axisNo);
            stick.setAxis(this.axes[axisNo]);
        }
        this.axes[axisNo].setDeadZone(this.defaultDeadZone);
        this.axes[axisNo].setParent(stick);
    }

    /**
     * Processes trigger mappings.
     *
     * @param mapping The mapping value.
     * @param axisNo  The number of the analog axis.
     */
    private void processTriggerAxis(String mapping, int axisNo, int triggerNo) {
        TriggerID mappedID = TriggerID.getTriggerIDfromString(mapping);
        if (mappedID != null) {
            logger.finer("Map axis no. " + axisNo + " from mapping " + mapping + " to trigger " + mappedID);
            this.axes[axisNo] = new BaseAxis(AxisID.TRIGGER, axisNo);

            // TODO: SET LABELS AND RESOURCE KEYS

            this.triggers[triggerNo] = new BaseTrigger(this, triggerNo, this.axes[axisNo], "", "");
            this.triggers[triggerNo].setID(mappedID);

            this.triggerMap.put(mappedID, this.triggers[triggerNo]);
            String label = Mapping.getTriggerLabel(this, mappedID);
            if (label == null) {
                label = Mapping.getDefaultTriggerLabel(mappedID);
            }
            if (label != null) {
                this.triggers[triggerNo].setDefaultLabel(label);
            }
            String labelKey = Mapping.getTriggerLabelKey(this, mappedID);
            if (labelKey != null) {
                this.triggers[triggerNo].setLabelKey(labelKey);
            }

            this.axes[axisNo].setParent(this.triggers[triggerNo]);
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
    public void write(byte[] data, int length, int reportId) throws IOException {
        this.device.write(data, length, reportId);
    }

    @Override
    public void close() throws IOException {
        gamepad.removeGamepadListener(inputListener);
    }
}
