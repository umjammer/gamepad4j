/*
 * Copyright (c) 2013 Alex Diener
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package org.gamepad4j.desktop;

import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader;


/**
 * This represents not a device but whole devices.
 *
 * @author Alex Diener adiener@sacredsoftware.net
 */
public interface Gamepad {

    /** Returns a suitable service provider. */
    static Gamepad getGamepad() {
        for (Gamepad gamepad : ServiceLoader.load(Gamepad.class)) {
            if (gamepad.isSupported()) {
                return gamepad;
            }
        }
        throw new IllegalStateException("no suitable native adapter");
    }

    /** Represents an input device */
    abstract class Device {

        /**
         * Unique device identifier for application session, starting at 0 for the first device attached and
         * incrementing by 1 for each additional device. If a device is removed and subsequently reattached
         * during the same application session, it will have a new deviceID.
         */
        public int deviceID;

        /** Human-readable device name */
        public String description;

        /** USB vendor/product IDs as returned by the driver. Can be used to determine the particular model of device represented. */
        public int vendorID;
        public int productID;

        /** Number of axis elements belonging to the device */
        public int numAxes;

        /** Number of button elements belonging to the device */
        public int numButtons;

        /** Array[numAxes] of values representing the current state of each axis, in the range [-1..1] */
        public float[] axisStates;

        /** Array[numButtons] of values representing the current state of each button */
        public boolean[] buttonStates;

        /** */
        private final List<GamepadListener> listeners;

        /** */
        protected Device(List<GamepadListener> listeners) {
            this.listeners = listeners;
        }

        /** */
        public abstract void write(byte[] data, int length, int reportId) throws IOException;

        /** */
        public void fireDeviceAttach() {
            listeners.forEach(l -> l.deviceAttach(this));
        }

        /** */
        public void fireDeviceRemove() {
            listeners.forEach(l -> l.deviceRemove(this));
        }

        /** */
        public void fireButtonDown(int buttonID) {
            long ts = System.nanoTime();
            listeners.forEach(l -> l.buttonDown(this, buttonID, ts));
        }

        /** */
        public void fireButtonUp(int buttonID) {
            long ts = System.nanoTime();
            listeners.forEach(l -> l.buttonUp(this, buttonID, ts));
        }

        /** */
        public void fireAxisMove(int axisID, float value) {
            long ts = System.nanoTime();
            listeners.forEach(l -> l.axisMove(this, axisID, value, ts));
        }
    }

    /**
     * Initializes gamepad library and detects initial devices. Call this before any other
     * methods, other than callback registration functions. If you want to receive deviceAttachFunc
     * callbacks from devices detected in init(), you must call fireDeviceAttach()
     * before calling init().
     */
    void open();

    /**
     * Tears down all data structures created by the gamepad library and releases any memory that was
     * allocated. It is not necessary to call this function at application termination, but it's
     * provided in case you want to free memory associated with gamepads at some earlier time.
     */
    void close();

    /** Returns the number of currently attached gamepad devices. */
    int size();

    /** Returns the specified Device, or null if {@link Device#deviceID} is not found. */
    Device get(int deviceIndex);

    /** tell this service provider is supported */
    boolean isSupported();

    /** add an event listener for native devices */
    void addGamepadListener(GamepadListener l);

    /** remove an event listener for native devices */
    void removeGamepadListener(GamepadListener l);

    /** listener for native devices */
    interface GamepadListener {

        /**
         * a function to be called whenever a device is attached.
         */
        void deviceAttach(Device device);

        /**
         * a function to be called whenever a device is detached.
         */
        void deviceRemove(Device device);

        /**
         * a function to be called whenever a button on any attached device is pressed.
         */
        void buttonDown(Device device, int buttonID, double timestamp);

        /**
         * a function to be called whenever a button on any attached device is released.
         */
        void buttonUp(Device device, int buttonID, double timestamp);

        /**
         * a function to be called whenever an axis on any attached device is moved.
         */
        void axisMove(Device device, int axisID, float value, double timestamp);
    }

    class GamepadAdapter implements GamepadListener {

        /**
         * a function to be called whenever a device is attached.
         */
        @Override public void deviceAttach(Device device) {}

        /**
         * a function to be called whenever a device is detached.
         */
        @Override public void deviceRemove(Device device) {}

        /**
         * a function to be called whenever a button on any attached device is pressed.
         */
        @Override public void buttonDown(Device device, int buttonID, double timestamp) {}

        /**
         * a function to be called whenever a button on any attached device is released.
         */
        @Override public void buttonUp(Device device, int buttonID, double timestamp) {}

        /**
         * a function to be called whenever an axis on any attached device is moved.
         */
        @Override public void axisMove(Device device, int axisID, float value, double timestamp) {}
    }
}