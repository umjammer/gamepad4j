/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.input.gamepad4j.spi;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Logger;

import net.java.games.input.AbstractController;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Rumbler;
import net.java.games.input.usb.HidController;
import org.gamepad4j.ButtonID;
import org.gamepad4j.IAxis;
import org.gamepad4j.IButton;
import org.gamepad4j.IComponent;
import org.gamepad4j.IController;
import org.gamepad4j.IControllerListener;
import org.gamepad4j.StickID;


/**
 * Gamepad4jController.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-01-23 nsano initial version <br>
 */
public class Gamepad4jController extends AbstractController implements HidController, Closeable {

    private static final Logger logger = Logger.getLogger(Gamepad4jController.class.getName());

    /** */
    private final IController device;

    /**
     * Protected constructor for a controller containing the specified
     * axes, child controllers, and rumblers
     *
     * @param device     the controller
     * @param components components for the controller
     * @param children   child controllers for the controller
     * @param rumblers   rumblers for the controller
     */
    protected Gamepad4jController(IController device, Component[] components, Controller[] children, Rumbler[] rumblers) {
        super(device.getVendorID() + "/" + device.getProductID(), components, children, rumblers);
        this.device = device;
//logger.fine("device: " + device + ", " + device.isOpen());
    }

    @Override
    public int getVendorId() {
        return device.getVendorID();
    }

    @Override
    public int getProductId() {
        return device.getProductID();
    }

    @Override
    public void open() throws IOException {
        super.open();
        device.open();

        device.addListener(new IControllerListener() {
            @Override public void buttonDown(IButton button, ButtonID buttonID) {

            }

            @Override public void buttonUp(IButton button, ButtonID buttonID) {

            }

            @Override public void moveStick(IAxis axis, StickID stick) {
            }

            private void fireOnInput(IComponent component) {
//                component.getID()
//                fireOnInput(new Gamepad4jInputEvent(Gamepad4jController.this, getComponents(), 0));
            }
        });
    }

    @Override
    public void close() throws IOException {
        device.close();
        super.close();
    }

    @Override
    public Type getType() {
        return Type.GAMEPAD;
    }

    @Override
    public void output(Report report) throws IOException {
        ((HidReport) report).setup(getRumblers());

        int reportId = ((HidReport) report).getReportId();
        byte[] data = ((HidReport) report).getData();
        device.write(data, data.length, reportId);
    }
}
