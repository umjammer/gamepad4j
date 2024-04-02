/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.input.gamepad4j.spi.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.java.games.input.Component;
import net.java.games.input.Component.Identifier;
import net.java.games.input.Component.Identifier.Axis;
import net.java.games.input.Component.Identifier.Button;
import net.java.games.input.Controller;
import net.java.games.input.DeviceSupportPlugin;
import net.java.games.input.Rumbler;
import net.java.games.input.plugin.DualShock4PluginBase.DualShock4Output;
import org.gamepad4j.AxisID;
import org.gamepad4j.ButtonID;
import org.gamepad4j.IAxis;
import org.gamepad4j.IComponent;
import org.gamepad4j.IController;
import org.gamepad4j.IStick;
import org.gamepad4j.ITrigger;
import org.gamepad4j.StickID;
import org.gamepad4j.TriggerID;
import org.gamepad4j.base.BaseAxis;
import vavi.games.input.gamepad4j.spi.Gamepad4jRumbler;
import vavi.games.input.gamepad4j.spi.IdConvertible;


/**
 * DualShock4Plugin.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-01-23 nsano initial version <br>
 * @see "https://www.psdevwiki.com/ps4/DS4-USB"
 */
public class DualShock4Plugin implements DeviceSupportPlugin, IdConvertible {

    private static final Logger logger = Logger.getLogger(DualShock4Plugin.class.getName());

    /** @param object HidDevice */
    @Override
    public boolean match(Object object) {
        if (object instanceof IController device) {
logger.finer(String.format("%04x, %s, %04x, %s", device.getVendorID(), device.getVendorID() == 0x54c, device.getProductID(), device.getProductID() == 0x9cc));
            return device.getVendorID() == 0x54c && device.getProductID() == 0x9cc;
        } else {
            return false;
        }
    }

    @Override
    public Collection<Component> getExtraComponents(Object object) {
        return Collections.emptyList();
    }

    @Override
    public Collection<Controller> getExtraChildControllers(Object object) {
        return Collections.emptyList();
    }

    @Override
    public Collection<Rumbler> getExtraRumblers(Object object) {
        return List.of(
                new Gamepad4jRumbler(5, DualShock4Output.SMALL_RUMBLE, 3),
                new Gamepad4jRumbler(5, DualShock4Output.BIG_RUMBLE, 4),
                new Gamepad4jRumbler(5, DualShock4Output.LED_RED, 5),
                new Gamepad4jRumbler(5, DualShock4Output.LED_BLUE, 6),
                new Gamepad4jRumbler(5, DualShock4Output.LED_GREEN,7),
                new Gamepad4jRumbler(5, DualShock4Output.FLASH_LED1, 8),
                new Gamepad4jRumbler(5, DualShock4Output.FLASH_LED2, 9)
        );
    }

    @Override
    public Identifier normalize(IComponent component) {
logger.finest(component.getID() + ": " + component.getClass().getName());
        if (component.getID() instanceof ButtonID bid) {
            return switch (bid) {
                case FACE_LEFT -> Button._1;
                case FACE_DOWN -> Button._2;
                case FACE_RIGHT -> Button._3;
                case FACE_UP -> Button._4;
                case SHOULDER_LEFT_UP -> Button._5;
                case SHOULDER_RIGHT_UP -> Button._6;
                case SHOULDER_LEFT_DOWN -> Button._7;
                case SHOULDER_RIGHT_DOWN -> Button._8;
                case SHARE -> Button._9;
                case OPTION -> Button._10;
                case LEFT_ANALOG_STICK -> Button._11;
                case RIGHT_ANALOG_STICK -> Button._12;
                case HOME -> Button._13;
                case TOUCHPAD -> Button._14;
                case ACCEPT, CANCEL, MENU, BACK, PAUSE, START, UNKNOWN,
                        D_PAD_UP, D_PAD_DOWN, D_PAD_LEFT, D_PAD_RIGHT -> throw new IllegalArgumentException(bid.name());
            };
        } else if (component.getID() instanceof AxisID aid) {
            return switch (aid) {
                case X -> {
                    IStick stick = ((IStick) ((BaseAxis) component).getParent());
                    yield switch (stick.getID()) {
                        case LEFT -> Axis.X;
                        case RIGHT -> Axis.Z;
                        case D_PAD, UNKNOWN -> throw new IllegalArgumentException(aid.name());
                    };
                }
                case Y -> {
                    IStick stick = ((IStick) ((BaseAxis) component).getParent());
                    yield switch (stick.getID()) {
                        case LEFT -> Axis.Y;
                        case RIGHT -> Axis.RX;
                        case D_PAD, UNKNOWN -> throw new IllegalArgumentException(aid.name());
                    };
                }
                case D_PAD -> Axis.POV;
                case TRIGGER -> {
                    ITrigger trigger = ((ITrigger) ((BaseAxis) component).getParent());
                    yield switch (trigger.getID()) {
                        case LEFT_DOWN -> Axis.RY;
                        case RIGHT_DOWN -> Axis.RZ;
                        case LEFT_UP, RIGHT_UP, UNKNOWN -> throw new IllegalArgumentException(aid.name());
                    };
                }
                case D_PAD_X, D_PAD_Y -> throw new IllegalArgumentException(aid.name());
            };
        } else if (component.getID() instanceof StickID || component.getID() instanceof TriggerID) {
            throw new IllegalArgumentException(component.getID().toString());
        } else {
            throw new IllegalArgumentException("unknown component: " + component);
        }
    }
}
