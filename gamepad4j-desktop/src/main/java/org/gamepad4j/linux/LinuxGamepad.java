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

package org.gamepad4j.linux;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import net.java.games.input.linux.LinuxIO;
import net.java.games.input.linux.LinuxIO.dirent;
import net.java.games.input.linux.LinuxIO.input_absinfo;
import net.java.games.input.linux.LinuxIO.input_event;
import net.java.games.input.linux.LinuxIO.input_id;
import net.java.games.input.linux.LinuxIO.stat;
import org.gamepad4j.shared.BaseGamepad;
import org.gamepad4j.shared.Gamepad.Device.Private;

import static net.java.games.input.linux.LinuxIO.ABS_CNT;
import static net.java.games.input.linux.LinuxIO.ABS_MAX;
import static net.java.games.input.linux.LinuxIO.BTN_MISC;
import static net.java.games.input.linux.LinuxIO.EVIOCGABS;
import static net.java.games.input.linux.LinuxIO.EVIOCGBIT;
import static net.java.games.input.linux.LinuxIO.EVIOCGID;
import static net.java.games.input.linux.LinuxIO.EVIOCGNAME;
import static net.java.games.input.linux.LinuxIO.KEY_MAX;
import static net.java.games.input.linux.LinuxIO.O_RDONLY;
import static net.java.games.input.linux.NativeDefinitions.ABS_X;
import static net.java.games.input.linux.NativeDefinitions.ABS_Y;
import static net.java.games.input.linux.NativeDefinitions.BTN_1;
import static net.java.games.input.linux.NativeDefinitions.BTN_A;
import static net.java.games.input.linux.NativeDefinitions.BTN_TRIGGER;
import static net.java.games.input.linux.NativeDefinitions.EV_ABS;
import static net.java.games.input.linux.NativeDefinitions.EV_CNT;
import static net.java.games.input.linux.NativeDefinitions.EV_KEY;
import static net.java.games.input.linux.NativeDefinitions.KEY_CNT;
import static org.gamepad4j.shared.Gamepad.EventType.AXIS_MOVED;
import static org.gamepad4j.shared.Gamepad.EventType.BUTTON_DOWN;
import static org.gamepad4j.shared.Gamepad.EventType.BUTTON_UP;
import static org.gamepad4j.shared.Gamepad.EventType.DEVICE_REMOVED;


/**
 * @author Alex Diener adiener@sacredsoftware.net
 */
public class LinuxGamepad extends BaseGamepad {

    private static final Logger logger = Logger.getLogger(LinuxGamepad.class.getName());

    static class DevicePrivate implements Private {
        ExecutorService es = Executors.newSingleThreadExecutor();
        int fd;
        String path;
        int[] buttonMap = new int[KEY_CNT - BTN_MISC];
        int[] axisMap = new int[ABS_CNT];
        input_absinfo[] axisInfo = new input_absinfo[ABS_CNT];
    }

    private Device[] devices = null;
    private int numDevices = 0;
    private int nextDeviceID = 0;
    private final Object devicesMutex = new Object();

    private QueuedEvent[] eventQueue = null;
    private int eventQueueSize = 0;
    private int eventCount = 0;
    private final Object eventQueueMutex = new Object();

    private boolean inited = false;

    /** */
    private static boolean test_bit(int bitIndex, int[] array) {
        return ((array[bitIndex / (Integer.BYTES * 8)] >> (bitIndex % (Integer.BYTES * 8))) & 0x1) != 0;
    }

    @Override
    public void init() {
logger.fine("init...");
        if (!inited) {
            inited = true;
logger.fine("initialized");
            detectDevices();
        }
    }

    private void disposeDevice(Device device) {
logger.fine("despose device...");
        LinuxIO.INSTANCE.close(((DevicePrivate) device.privateData).fd);
    }

    @Override
    public void shutdown() {
logger.fine("shutdown...");
        if (inited) {
            int eventIndex;
            int devicesLeft;
            int gamepadIndex;

            do {
                synchronized (devicesMutex) {
                    devicesLeft = numDevices;
                    if (devicesLeft > 0) {

                         ((DevicePrivate) devices[0].privateData).es.shutdownNow();

                        numDevices--;
                        for (gamepadIndex = 0; gamepadIndex < numDevices; gamepadIndex++) {
                            devices[gamepadIndex] = devices[gamepadIndex + 1];
                        }
                    }
                }
            } while (devicesLeft > 0);

            devices = null;

            for (eventIndex = 0; eventIndex < eventCount; eventIndex++) {
                if (eventQueue[eventIndex].eventType == DEVICE_REMOVED) {
                    disposeDevice((Device) eventQueue[eventIndex].eventData);
                }
            }

            eventQueueSize = 0;
            eventCount = 0;
            eventQueue = null;

            inited = false;
        }
    }

    @Override
    public int numDevices() {
        int result;

        synchronized (devicesMutex) {
            result = numDevices;
        }
        return result;
    }

    @Override
    public Device deviceAtIndex(int deviceIndex) {
        Device result;

        synchronized (devicesMutex) {
            if (deviceIndex >= numDevices) {
                result = null;
            } else {
                result = devices[deviceIndex];
            }
        }

        return result;
    }

    private void queueEvent(int deviceID, EventType eventType, EventData eventData) {
        QueuedEvent queuedEvent = new QueuedEvent();

        queuedEvent.deviceID = deviceID;
        queuedEvent.eventType = eventType;
        queuedEvent.eventData = eventData;

        synchronized (eventQueueMutex) {
            if (eventCount >= eventQueueSize) {
                eventQueueSize = eventQueueSize == 0 ? 1 : eventQueueSize * 2;
                eventQueue = Arrays.copyOf(eventQueue, eventQueueSize);
            }
            eventQueue[eventCount++] = queuedEvent;
        }
    }

    private void deviceThread(Device device) {
        DevicePrivate devicePrivate = (DevicePrivate) device.privateData;

        input_event event = new input_event();
        while (LinuxIO.INSTANCE.read(devicePrivate.fd, event.getPointer(), new NativeLong(event.size())).intValue() > 0) {
            event.read();
            if (event.type == EV_ABS) {
                float value;

                if (event.code > ABS_MAX || devicePrivate.axisMap[event.code] == -1) {
                    continue;
                }

                value = (event.value - devicePrivate.axisInfo[event.code].minimum) / (float) (devicePrivate.axisInfo[event.code].maximum - devicePrivate.axisInfo[event.code].minimum) * 2.0f - 1.0f;
                AxisEventData axisEvent = new AxisEventData(device,
                        event.time.tv_sec + event.time.tv_usec * 0.000001,
                        devicePrivate.axisMap[event.code],
                        value);
                queueEvent(device.deviceID, AXIS_MOVED, axisEvent);

                device.axisStates[devicePrivate.axisMap[event.code]] = value;

            } else if (event.type == EV_KEY) {
                if (event.code < BTN_MISC || event.code > KEY_MAX || devicePrivate.buttonMap[event.code - BTN_MISC] == -1) {
                    continue;
                }

                ButtonEventData buttonEvent = new ButtonEventData(device,
                        event.time.tv_sec + event.time.tv_usec * 0.000001,
                        devicePrivate.buttonMap[event.code - BTN_MISC],
                        event.value != 0);
                queueEvent(device.deviceID, event.value != 0 ? BUTTON_DOWN : BUTTON_UP, buttonEvent);

                device.buttonStates[devicePrivate.buttonMap[event.code - BTN_MISC]] = event.value != 0;
            }
        }

        queueEvent(device.deviceID, DEVICE_REMOVED, device);

        synchronized (devicesMutex) {
            for (int gamepadIndex = 0; gamepadIndex < numDevices; gamepadIndex++) {
                if (devices[gamepadIndex] == device) {
                    int gamepadIndex2;

                    numDevices--;
                    for (gamepadIndex2 = gamepadIndex; gamepadIndex2 < numDevices; gamepadIndex2++) {
                        devices[gamepadIndex2] = devices[gamepadIndex2 + 1];
                    }
                    gamepadIndex--;
                }
            }
        }
    }

    private class MyRunnable implements Runnable {
        Device device;
        public MyRunnable(Device device) {
            this.device = device;
        }

        @Override
        public void run() {
            deviceThread(device);
        }
    }

    private long lastInputStatTime;

    @Override
    public void detectDevices() {
        int[] evCapBits = new int[(EV_CNT - 1) / Integer.BYTES * 8 + 1];
        int[] evKeyBits = new int[(KEY_CNT - 1) / Integer.BYTES * 8 + 1];
        int[] evAbsBits = new int[(ABS_CNT - 1) / Integer.BYTES * 8 + 1];

        if (!inited) {
            return;
        }

        synchronized (devicesMutex) {

            Pointer /* DIR */ dev_input = LinuxIO.INSTANCE.opendir("/dev/input");
            long currentTime = System.nanoTime();
            if (dev_input != null) {
                dirent entity;
                while ((entity = LinuxIO.INSTANCE.readdir(dev_input)) != null) {
                    IntByReference charsConsumed = new IntByReference();
                    IntByReference num = new IntByReference();
                    if (LinuxIO.INSTANCE.sscanf(entity.d_name, "event%d%n", num, charsConsumed) != 0 && charsConsumed.getValue() == LinuxIO.INSTANCE.strlen(entity.d_name).intValue()) {
                        String fileName = String.format("/dev/input/%s", new String(entity.d_name, StandardCharsets.UTF_8).replace("\u0000", ""));
                        stat statBuf = new stat();
                        if (LinuxIO.INSTANCE.stat(fileName, statBuf) != 0 || statBuf.st_mtim.toNanos() < lastInputStatTime) {
                            continue;
                        }

                        boolean duplicate = false;
                        for (int gamepadIndex = 0; gamepadIndex < numDevices; gamepadIndex++) {
                            if (((DevicePrivate) devices[gamepadIndex].privateData).path.equals(fileName)) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (duplicate) {
                            continue;
                        }

                        int fd = LinuxIO.INSTANCE.open(fileName, O_RDONLY, 0);
                        Arrays.fill(evCapBits, 0, evCapBits.length, (byte) 0);
                        Arrays.fill(evKeyBits, 0, evKeyBits.length, (byte) 0);
                        Arrays.fill(evAbsBits, 0, evAbsBits.length, (byte) 0);
                        if (LinuxIO.INSTANCE.ioctl(fd, EVIOCGBIT(0, evCapBits.length), evCapBits) < 0 ||
                                LinuxIO.INSTANCE.ioctl(fd, EVIOCGBIT(EV_KEY, evKeyBits.length), evKeyBits) < 0 ||
                                LinuxIO.INSTANCE.ioctl(fd, EVIOCGBIT(EV_ABS, evAbsBits.length), evAbsBits) < 0) {
                            LinuxIO.INSTANCE.close(fd);
                            continue;
                        }
                        if (!test_bit(EV_KEY, evCapBits) || !test_bit(EV_ABS, evCapBits) ||
                                !test_bit(ABS_X, evAbsBits) || !test_bit(ABS_Y, evAbsBits) ||
                                (!test_bit(BTN_TRIGGER, evKeyBits) && !test_bit(BTN_A, evKeyBits) && !test_bit(BTN_1, evKeyBits))) {
                            LinuxIO.INSTANCE.close(fd);
                            continue;
                        }

                        Device deviceRecord = new Device();
                        deviceRecord.deviceID = nextDeviceID++;
                        devices = Arrays.copyOf(devices, numDevices + 1);
                        devices[numDevices++] = deviceRecord;

                        DevicePrivate deviceRecordPrivate = new DevicePrivate();
                        deviceRecordPrivate.fd = fd;
                        deviceRecordPrivate.path = fileName;
                        Arrays.fill(deviceRecordPrivate.buttonMap, 0, deviceRecordPrivate.buttonMap.length, (byte) 0xFF);
                        Arrays.fill(deviceRecordPrivate.axisMap, 0, deviceRecordPrivate.axisMap.length, (byte) 0xFF);
                        deviceRecord.privateData = deviceRecordPrivate;

                        String description;
                        byte[] name = new byte[128];
                        if (LinuxIO.INSTANCE.ioctl(fd, EVIOCGNAME(name.length), name) > 0) {
                            description = new String(name, StandardCharsets.UTF_8);
                        } else {
                            description = fileName;
                        }
                        deviceRecord.description = description;

                        input_id id = new input_id();
                        if (LinuxIO.INSTANCE.ioctl(fd, EVIOCGID(id.size()), id.getPointer()) == 0) {
                            deviceRecord.vendorID = id.vendor;
                            deviceRecord.productID = id.product;
                        } else {
                            deviceRecord.vendorID = deviceRecord.productID = 0;
                        }

                        Arrays.fill(evKeyBits, 0, evKeyBits.length, (byte) 0);
                        Arrays.fill(evAbsBits, 0, evAbsBits.length, (byte) 0);
                        LinuxIO.INSTANCE.ioctl(fd, EVIOCGBIT(EV_KEY, evKeyBits.length), evKeyBits);
                        LinuxIO.INSTANCE.ioctl(fd, EVIOCGBIT(EV_ABS, evAbsBits.length), evAbsBits);

                        deviceRecord.numAxes = 0;
                        for (int bit = 0; bit < ABS_CNT; bit++) {
                            if (test_bit(bit, evAbsBits)) {
                                if (LinuxIO.INSTANCE.ioctl(fd, EVIOCGABS(bit, deviceRecordPrivate.axisInfo[bit].size()),
                                        deviceRecordPrivate.axisInfo[bit].getPointer()) < 0 ||
                                        deviceRecordPrivate.axisInfo[bit].minimum == deviceRecordPrivate.axisInfo[bit].maximum) {
                                    continue;
                                }
                                deviceRecordPrivate.axisMap[bit] = deviceRecord.numAxes;
                                deviceRecord.numAxes++;
                            }
                        }
                        deviceRecord.numButtons = 0;
                        for (int bit = BTN_MISC; bit < KEY_CNT; bit++) {
                            if (test_bit(bit, evKeyBits)) {
                                deviceRecordPrivate.buttonMap[bit - BTN_MISC] = deviceRecord.numButtons;
                                deviceRecord.numButtons++;
                            }
                        }

                        deviceRecord.axisStates = new float[deviceRecord.numAxes];
                        deviceRecord.buttonStates = new boolean[deviceRecord.numButtons];

                        fireDeviceAttach(deviceRecord);

                        deviceRecordPrivate.es.submit(new MyRunnable(deviceRecord));
                    }
                }
                LinuxIO.INSTANCE.closedir(dev_input);
            }

            lastInputStatTime = currentTime;
        }
    }

    private boolean inProcessEvents;

    @Override
    public void processEvents() {
        int eventIndex;

        if (!inited || inProcessEvents) {
            return;
        }

        inProcessEvents = true;
        synchronized (eventQueueMutex) {
            for (eventIndex = 0; eventIndex < eventCount; eventIndex++) {
                processQueuedEvent(eventQueue[eventIndex]);
                if (eventQueue[eventIndex].eventType == DEVICE_REMOVED) {
                    disposeDevice((Device) eventQueue[eventIndex].eventData);
                }
            }
            eventCount = 0;
        }
        inProcessEvents = false;
    }
}