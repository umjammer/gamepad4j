/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.input.gamepad4j.spi;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.java.games.input.Component;
import net.java.games.input.Component.Identifier;
import net.java.games.input.Controller;
import net.java.games.input.ControllerListenerSupport;
import net.java.games.input.DeviceSupportPlugin;
import net.java.games.input.Rumbler;
import net.java.games.input.usb.HidControllerEnvironment;
import org.gamepad4j.Controllers;
import org.gamepad4j.IComponent;
import org.gamepad4j.IController;


/**
 * The Gamepad4j ControllerEnvironment.
 *
 * @author <a href="mailto:vavivavi@yahoo.co.jp">Naohide Sano</a> (nsano)
 * @version 0.00 2024-01-23 nsano initial version <br>
 */
public final class Gamepad4jEnvironmentPlugin extends ControllerListenerSupport implements HidControllerEnvironment, Closeable {

    private static final Logger logger = Logger.getLogger(Gamepad4jEnvironmentPlugin.class.getName());
    
    /** */
    private List<Gamepad4jController> controllers;

    /** */
    private void enumerate() throws IOException {
        boolean r = isSupported(); // don't touch, instantiates environment
logger.fine("isSupported: " + r);
        while (environment.getControllers().length == 0) {
            Thread.yield();
        }
logger.fine("devices: " + environment.getControllers().length);
        controllers = new ArrayList<>();
        Arrays.stream(environment.getControllers()).forEach(device -> {
            try {
                attach(device);
            } catch (IOException e) {
logger.log(Level.FINE, e.getMessage(), e);
            }
        });
    }

    /** */
    private Gamepad4jController attach(IController device) throws IOException {

        List<Component> components = new ArrayList<>();
        List<Controller> children = new ArrayList<>();
        List<Rumbler> rumblers = new ArrayList<>();

        DeviceSupportPlugin supportPlugin = null;
        for (DeviceSupportPlugin plugin : DeviceSupportPlugin.getPlugins()) {
logger.finer("plugin: " + plugin + ", " + plugin.match(device));
            if (plugin.match(device)) {
logger.fine("@@@ plugin for extra: " + plugin.getClass().getName());
                supportPlugin = plugin;
            }
        }

        for (IComponent component : device.getComponents()) {
            if (component != null) {
                try {
                    Identifier id = Identifier.Button.UNKNOWN;
                    if (supportPlugin instanceof IdConvertible idConvertible) {
                        id = idConvertible.normalize(component);
                    } else {
logger.finer("no id converter plugin for device: " + device.getDescription());
                    }
                    components.add(new Gamepad4jComponent(component.getID().toString(), id, component));
                } catch (IllegalArgumentException e) {
logger.finer("no id converter plugin for device: " + device.getDescription());
                }
            }
        }

        // extra elements by plugin
        if (supportPlugin != null) {
            components.addAll(supportPlugin.getExtraComponents(device));
            children.addAll(supportPlugin.getExtraChildControllers(device));
            rumblers.addAll(supportPlugin.getExtraRumblers(device));
        }

        Gamepad4jController controller = new Gamepad4jController(device,
                components.toArray(Component[]::new),
                children.toArray(Controller[]::new),
                rumblers.toArray(Rumbler[]::new));
        controllers.add(controller);
logger.fine(String.format("@@@@@@@@@@@ add: %s ... %d", device.getDescription(), controllers.size()));
logger.fine(String.format("    components: %d, %s", components.size(), components));
//logger.fine(String.format("    children: %d", children.size()));
logger.fine(String.format("    rumblers: %d, %s", rumblers.size(), rumblers));
        return controller;
    }

    /** */
    @SuppressWarnings("WhileLoopReplaceableByForEach") // for remove
    private Gamepad4jController detach(IController controller) {
        Iterator<Gamepad4jController> i = controllers.iterator();
        while (i.hasNext()) {
            Gamepad4jController c = i.next();
            if (c.getProductId() == controller.getProductID() && c.getVendorId() == controller.getVendorID()) {
                controllers.remove(c);
                return c;
            }
        }
        return null;
    }

    @Override
    public Gamepad4jController[] getControllers() {
        if (controllers == null) {
            try {
                enumerate();
            } catch (IOException e) {
logger.log(Level.FINE, e.getMessage(), e);
                return new Gamepad4jController[0];
            }
        }
        return controllers.toArray(Gamepad4jController[]::new);
    }

    @Override
    public boolean isSupported() {
        if (environment == null) {
            environment = Controllers.instance();
logger.fine("starting Controllers.");
        }
        return true;
    }

    /** */
    private Controllers environment;

    @Override
    public void close() {
    }

    @Override
    public Gamepad4jController getController(int mid, int pid) {
logger.fine("controllers: " + getControllers().length);
        for (Gamepad4jController controller : getControllers()) {
logger.fine(String.format("%s: %4x, %4x%n", controller.getName(), controller.getVendorId(), controller.getProductId()));
            if (controller.getVendorId() == mid && controller.getProductId() == pid) {
                return controller;
            }
        }
        throw new NoSuchElementException(String.format("no device: mid: %1$d(0x%1$x), pid: %2$d(0x%2$x))", mid, pid));
    }
}
