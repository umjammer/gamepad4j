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


import java.util.ServiceLoader;


/**
 * This represents not a device but whole devices.
 *
 * @author Alex Diener adiener@sacredsoftware.net
 */
public interface Gamepad {

    /** */
    static Gamepad getGamepad() {
        for (Gamepad gamepad : ServiceLoader.load(Gamepad.class)) {
            if (gamepad.isSupported()) {
                return gamepad;
            }
        }
        throw new IllegalStateException("no suitable native adapter");
    }

    interface EventData {
    }

    class Device implements EventData {

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

        public interface Private {
        }

        /**
         * Platform-specific device data storage. Don't touch unless you know what you're doing and don't
         * mind your code breaking in future versions of this library.
         */
        public Private privateData;
    }

    enum EventType {
        DEVICE_ATTACHED,
        DEVICE_REMOVED,
        BUTTON_DOWN,
        BUTTON_UP,
        AXIS_MOVED
    }

    class ButtonEventData implements EventData {

        /** Device that generated the event */
        public Device device;
        /** Relative time of the event, in seconds */
        public double timestamp;
        /** Button being pushed or released */
        public int buttonID;
        /** True if button is down */
        public boolean down;

        public ButtonEventData(Device device, double timestamp, int buttonID, boolean down) {
            this.device = device;
            this.timestamp = timestamp;
            this.buttonID = buttonID;
            this.down = down;
        }
    }

    class AxisEventData implements EventData {

        /** Device that generated the event */
        public Device device;
        /** Relative time of the event, in seconds */
        public double timestamp;
        /** Axis being moved */
        public int axisID;
        /** Axis position value, in the range [-1..1] */
        public float value;

        public AxisEventData(Device device, double timestamp, int axisID, float value) {
            this.device = device;
            this.timestamp = timestamp;
            this.axisID = axisID;
            this.value = value;
        }
    }

    /**
     * Initializes gamepad library and detects initial devices. Call this before any other Gamepad_*()
     * function, other than callback registration functions. If you want to receive deviceAttachFunc
     * callbacks from devices detected in Gamepad_init(), you must call Gamepad_deviceAttachFunc()
     * before calling Gamepad_init().
     */
    void init();

    /**
     * Tears down all data structures created by the gamepad library and releases any memory that was
     * allocated. It is not necessary to call this function at application termination, but it's
     * provided in case you want to free memory associated with gamepads at some earlier time.
     */
    void shutdown();

    /** Returns the number of currently attached gamepad devices. */
    int numDevices();

    /** Returns the specified Device struct, or NULL if deviceIndex is out of bounds. */
    Device deviceAtIndex(int deviceIndex);

    /**
     * Polls for any devices that have been attached since the last call to Gamepad_detectDevices() or
     * Gamepad_init(). If any new devices are found, the callback registered with
     * Gamepad_deviceAttachFunc() (if any) will be called once per newly detected device.
     * <p>
     * Note that depending on implementation, you may receive button and axis event callbacks for
     * devices that have not yet been detected with Gamepad_detectDevices(). You can safely ignore
     * these events, but be aware that your callbacks might receive a device ID that hasn't been seen
     * by your deviceAttachFunc.
     */
    void detectDevices();

    /** ??? */
    void processEvents();

    /** */
    boolean isSupported();

    /** */
    void addGamepadListener(GamepadListener l);

    /** */
    void removeGamepadListener(GamepadListener l);

    /** */
    interface GamepadListener {

        /**
         * a function to be called whenever a device is attached. The specified function will be
         * called only during calls to init() and detectDevices(), in the thread from
         * which those functions were called. Calling this function with a NULL argument will stop any
         * previously registered callback from being called subsequently.
         */
        void deviceAttach(Device device);

        /**
         * a function to be called whenever a device is detached. The specified function can be
         * called at any time, and will not necessarily be called from the main thread. Calling this
         * function with a NULL argument will stop any previously registered callback from being called
         * subsequently.
         */
        void deviceRemove(Device device);

        /**
         * a function to be called whenever a button on any attached device is pressed. The
         * specified function will be called only during calls to processEvents(), in the
         * thread from which processEvents() was called. Calling this function with a null
         * argument will stop any previously registered callback from being called subsequently.
         */
        void buttonDown(Device device, int buttonID, double timestamp);

        /**
         * a function to be called whenever a button on any attached device is released. The
         * specified function will be called only during calls to processEvents(), in the
         * thread from which processEvents() was called. Calling this function with a null
         * argument will stop any previously registered callback from being called subsequently.
         */
        void buttonUp(Device device, int buttonID, double timestamp);

        /**
         * a function to be called whenever an axis on any attached device is moved. The
         * specified function will be called only during calls to processEvents(), in the
         * thread from which processEvents() was called. Calling this function with a null
         * argument will stop any previously registered callback from being called subsequently.
         */
        void axisMove(Device device, int axisID, float value, double timestamp);
    }
}