/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j.base;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.gamepad4j.AxisID;
import org.gamepad4j.ButtonID;
import org.gamepad4j.DpadDirection;
import org.gamepad4j.IAxis;
import org.gamepad4j.IButton;
import org.gamepad4j.IController;
import org.gamepad4j.IStick;
import org.gamepad4j.ITrigger;
import org.gamepad4j.StickID;
import org.gamepad4j.TriggerID;


/**
 * Base class for controller wrappers. Handles creation of buttons,
 * sticks, triggers and d-pad based on mapping configuration.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public abstract class AbstractBaseController implements IController {

    static final Logger logger = Logger.getLogger(AbstractBaseController.class.getName());

    /** Stores the deviceID of this controller. */
    protected final int deviceID;

    /** Stores the vendorID of this controller. */
    protected int vendorID = -1;

    /** Stores the productID of this controller. */
    protected int productID = -1;

    /** Stores the description of this controller. */
    protected String description = "";

    /** Stores the last d-pad direction. */
    protected DpadDirection lastDirection = null;

    // d-pad

    /** Stores a map for the axes used by an analog d-pad */
    protected Map<AxisID, BaseAxis> dpadAxisMap = new HashMap<>();

    // buttons

    /** Lookup map for buttons based on their type. */
    protected Map<ButtonID, IButton> buttonMap = new HashMap<>();

    /** Lookup map for button aliases. */
    protected Map<ButtonID, IButton> buttonAliasMap = new HashMap<>();

    /** Stores the buttons of this controller. */
    protected BaseButton[] buttons = null;

    // triggers

    /** Lookup map for buttons based on their type. */
    protected Map<TriggerID, ITrigger> triggerMap = new HashMap<>();

    /** Stores the buttons of this controller. */
    protected BaseTrigger[] triggers = null;

    // sticks

    /** Lookup map for sticks based on their type. */
    protected Map<StickID, IStick> stickMap = new HashMap<>();

    /** Stores the sticks of this controller. */
    protected BaseStick[] sticks = null;

    // axes (for triggers AND sticks)

    /** Stores the axes of this controller. */
    protected BaseAxis[] axes = null;

    /**
     * Creates a controller wrapper.
     *
     * @param deviceID The deviceID of the controller.
     */
    protected AbstractBaseController(int deviceID) {
        this(deviceID, "");
    }

    /**
     * Creates a controller wrapper.
     *
     * @param deviceID The deviceID of the controller.
     */
    protected AbstractBaseController(int deviceID, String description) {
        this.deviceID = deviceID;
        if (this.deviceID < 0) {
            throw new IllegalArgumentException("Device ID must be positive integer value.");
        }
        if (description != null) {
            this.description = description;
        }
    }

    /**
     * Adds a button to the map of buttons.
     *
     * @param button The button to add.
     */
    protected void addButton(IButton button) {
        this.buttonMap.put(button.getID(), button);
    }

    @Override
    public IButton getButton(int buttonCode) {
        return this.buttons[buttonCode];
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the description for this controller.
     *
     * @param description The description text.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int getDeviceID() {
        return this.deviceID;
    }

    @Override
    public int getVendorID() {
        return vendorID;
    }

    /**
     * @param vendorID the vendorID to set
     */
    public void setVendorID(int vendorID) {
        logger.fine("Set controller vendor ID: " + Integer.toHexString(vendorID));
        this.vendorID = vendorID;
    }

    @Override
    public int getProductID() {
        return productID;
    }

    /**
     * @param productID the productID to set
     */
    public void setProductID(int productID) {
        logger.fine("Set controller product ID: " + Integer.toHexString(productID));
        this.productID = productID;
    }

    @Override
    public long getDeviceTypeIdentifier() {
        if (this.vendorID == -1) {
            throw new IllegalArgumentException("Vendor ID not set for controller " + this.deviceID + " / " + this.description);
        }
        if (this.productID == -1) {
            throw new IllegalArgumentException("Product ID not set for controller " + this.deviceID + " / " + this.description);
        }
        return ((long) vendorID << 16) + productID;
    }

    @Override
    public DpadDirection getDpadDirectionOnce() {
        DpadDirection current = getDpadDirection();
        if (current != lastDirection) {
            lastDirection = current;
            return current;
        }
        return null;
    }

    @Override
    public boolean isButtonPressedOnce(ButtonID buttonID) {
        return getButton(buttonID).isPressedOnce();
    }

    @Override
    public IButton[] getButtons() {
        return this.buttons;
    }

    @Override
    public DpadDirection getDpadDirection() {
        int value = 0;
        if (!this.dpadAxisMap.isEmpty()) {
            // It's an analog axes d-pad
            BaseAxis xAxis = this.dpadAxisMap.get(AxisID.D_PAD_X);
            if (xAxis != null) {
                if (xAxis.getValue() == -1) {
                    value += DpadDirection.LEFT.getValue();
                } else if (xAxis.getValue() == 1) {
                    value += DpadDirection.RIGHT.getValue();
                }
            }
            BaseAxis yAxis = this.dpadAxisMap.get(AxisID.D_PAD_Y);
            if (yAxis != null) {
                if (yAxis.getValue() == -1) {
                    value += DpadDirection.UP.getValue();
                } else if (yAxis.getValue() == 1) {
                    value += DpadDirection.DOWN.getValue();
                }
            }
        } else {
            // It's a digital button d-pad
            IButton dpadUp = this.buttonMap.get(ButtonID.D_PAD_UP);
            IButton dpadRight = this.buttonMap.get(ButtonID.D_PAD_RIGHT);
            IButton dpadDown = this.buttonMap.get(ButtonID.D_PAD_DOWN);
            IButton dpadLeft = this.buttonMap.get(ButtonID.D_PAD_LEFT);
            if (dpadUp != null && dpadUp.isPressed()) {
                value += DpadDirection.UP.getValue();
            }
            if (dpadDown != null && dpadDown.isPressed()) {
                value += DpadDirection.DOWN.getValue();
            }
            if (dpadLeft != null && dpadLeft.isPressed()) {
                value += DpadDirection.LEFT.getValue();
            }
            if (dpadRight != null && dpadRight.isPressed()) {
                value += DpadDirection.RIGHT.getValue();
            }
        }
        return DpadDirection.fromIntValue(value);
    }

    @Override
    public ITrigger[] getTriggers() {
        return this.triggers;
    }

    @Override
    public ITrigger getTrigger(TriggerID triggerID) {
        return this.triggerMap.get(triggerID);
    }

    @Override
    public IButton getButton(ButtonID buttonID) {
        IButton button = this.buttonMap.get(buttonID);
        if (button == null) {
            button = this.buttonAliasMap.get(buttonID);
        }
        return button;
    }

    @Override
    public boolean isButtonPressed(ButtonID buttonID) {
        return this.buttonMap.get(buttonID).isPressed();
    }

    @Override
    public float getTriggerPressure(TriggerID buttonID)
            throws IllegalArgumentException {
        return this.triggerMap.get(buttonID).analogValue();
    }

    @Override
    public IStick[] getSticks() {
        return this.sticks;
    }

    @Override
    public IStick getStick(StickID stick) throws IllegalArgumentException {
        return stickMap.get(stick);
    }

    @Override
    public IAxis[] getAxes() {
        if (this.axes == null) {
            this.axes = new BaseAxis[0];
        }
        return this.axes;
    }
}
