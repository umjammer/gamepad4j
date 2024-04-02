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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
import org.gamepad4j.desktop.BaseGamepad;
import org.gamepad4j.util.PlatformUtil;

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


/**
 * @author Alex Diener adiener@sacredsoftware.net
 */
public class LinuxGamepad extends BaseGamepad {

    private static final Logger logger = Logger.getLogger(LinuxGamepad.class.getName());

    private static class LinuxDevice extends Device {
        ExecutorService inputEs = Executors.newSingleThreadExecutor();

        LinuxDevice(List<GamepadListener> listeners) {
            super(listeners);
        }

        int fd;
        String path;
        int[] buttonMap = new int[KEY_CNT - BTN_MISC];
        int[] axisMap = new int[ABS_CNT];
        input_absinfo[] axisInfo = new input_absinfo[ABS_CNT];

        @Override
        public void write(byte[] data, int length, int reportId) throws IOException {
            throw new UnsupportedOperationException("not implemented yet");
        }
    }

    private final ScheduledExecutorService detectSes = Executors.newSingleThreadScheduledExecutor();

    private final List<LinuxDevice> devices = new ArrayList<>();
    private int nextDeviceID = 0;

    private boolean inited = false;

    /** */
    private static boolean testBit(int bitIndex, int[] array) {
        return ((array[bitIndex / (Integer.BYTES * 8)] >> (bitIndex % (Integer.BYTES * 8))) & 0x1) != 0;
    }

    @Override
    public void open() {
logger.fine("init...");
        if (!inited) {
            detectSes.schedule(this::detectDevices, 1000, TimeUnit.MILLISECONDS);

            inited = true;
logger.fine("initialized");
        }
    }

    @Override
    public void close() {
logger.fine("shutdown...");
        if (inited) {
            detectSes.shutdownNow();

            devices.clear();
            inited = false;
        }
    }

    @Override
    public int size() {
        synchronized (devices) {
            return devices.size();
        }
    }

    @Override
    public Device get(int deviceId) {
        synchronized (devices) {
            for (LinuxDevice device : devices) {
                if (device.deviceID == deviceId) {
                    return devices.get(deviceId);
                }
            }
logger.warning("no such deviceId: " + deviceId);
            return null;
        }
    }

    /** device input report thread */
    private void deviceThread(LinuxDevice device) {

        input_event event = new input_event();
        while (LinuxIO.INSTANCE.read(device.fd, event.getPointer(), new NativeLong(event.size())).intValue() > 0) {
            event.read();
            if (event.type == EV_ABS) {
                if (event.code > ABS_MAX || device.axisMap[event.code] == -1) {
                    continue;
                }

                float value = (event.value - device.axisInfo[event.code].minimum) / (float) (device.axisInfo[event.code].maximum - device.axisInfo[event.code].minimum) * 2.0f - 1.0f;
                device.fireAxisMove(device.axisMap[event.code], value);

                device.axisStates[device.axisMap[event.code]] = value;

            } else if (event.type == EV_KEY) {
                if (event.code < BTN_MISC || event.code > KEY_MAX || device.buttonMap[event.code - BTN_MISC] == -1) {
                    continue;
                }

                if (event.value != 0) {
                    device.fireButtonDown(device.buttonMap[event.code - BTN_MISC]);
                } else {
                    device.fireButtonUp(device.buttonMap[event.code - BTN_MISC]);
                }

                device.buttonStates[device.buttonMap[event.code - BTN_MISC]] = event.value != 0;
            }
        }

        removeDevice(device);
    }

    /** device removal */
    private void removeDevice(LinuxDevice device) {
        device.fireDeviceRemove();

        synchronized (devices) {
            Iterator<LinuxDevice> i = devices.iterator();
            while (i.hasNext()) {
                LinuxDevice toBeRemoved = i.next();
                if (toBeRemoved == device) {
                    logger.fine("dispose device...");
                    LinuxIO.INSTANCE.close(device.fd);

                    i.remove();
                    break;
                }
            }
        }
    }

    /** @see "https://stackoverflow.com/a/5853198" */
    private class MyRunnable implements Runnable {
        LinuxDevice device;
        public MyRunnable(LinuxDevice device) {
            this.device = device;
        }

        @Override
        public void run() {
            deviceThread(device);
        }
    }

    /** seemed for polling */
    private long lastInputStatTime;

    /** device detection thread */
    private void detectDevices() {
        if (!inited) {
            return;
        }

        int[] evCapBits = new int[(EV_CNT - 1) / Integer.BYTES * 8 + 1];
        int[] evKeyBits = new int[(KEY_CNT - 1) / Integer.BYTES * 8 + 1];
        int[] evAbsBits = new int[(ABS_CNT - 1) / Integer.BYTES * 8 + 1];

        synchronized (devices) {

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
                        for (LinuxDevice device : devices) {
                            if (device.path.equals(fileName)) {
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
                        if (!testBit(EV_KEY, evCapBits) || !testBit(EV_ABS, evCapBits) ||
                                !testBit(ABS_X, evAbsBits) || !testBit(ABS_Y, evAbsBits) ||
                                (!testBit(BTN_TRIGGER, evKeyBits) && !testBit(BTN_A, evKeyBits) && !testBit(BTN_1, evKeyBits))) {
                            LinuxIO.INSTANCE.close(fd);
                            continue;
                        }

                        LinuxDevice device = new LinuxDevice(listeners);
                        device.deviceID = nextDeviceID++;

                        device.fd = fd;
                        device.path = fileName;

                        devices.add(device);

                        Arrays.fill(device.buttonMap, 0, device.buttonMap.length, (byte) 0xFF);
                        Arrays.fill(device.axisMap, 0, device.axisMap.length, (byte) 0xFF);

                        String description;
                        byte[] name = new byte[128];
                        if (LinuxIO.INSTANCE.ioctl(fd, EVIOCGNAME(name.length), name) > 0) {
                            description = new String(name, StandardCharsets.UTF_8);
                        } else {
                            description = fileName;
                        }
                        device.description = description;

                        input_id id = new input_id();
                        if (LinuxIO.INSTANCE.ioctl(fd, EVIOCGID(id.size()), id.getPointer()) == 0) {
                            device.vendorID = id.vendor;
                            device.productID = id.product;
                        } else {
                            device.vendorID = device.productID = 0;
                        }

                        Arrays.fill(evKeyBits, 0, evKeyBits.length, (byte) 0);
                        Arrays.fill(evAbsBits, 0, evAbsBits.length, (byte) 0);
                        LinuxIO.INSTANCE.ioctl(fd, EVIOCGBIT(EV_KEY, evKeyBits.length), evKeyBits);
                        LinuxIO.INSTANCE.ioctl(fd, EVIOCGBIT(EV_ABS, evAbsBits.length), evAbsBits);

                        device.numAxes = 0;
                        for (int bit = 0; bit < ABS_CNT; bit++) {
                            if (testBit(bit, evAbsBits)) {
                                if (LinuxIO.INSTANCE.ioctl(fd, EVIOCGABS(bit, device.axisInfo[bit].size()),
                                        device.axisInfo[bit].getPointer()) < 0 ||
                                        device.axisInfo[bit].minimum == device.axisInfo[bit].maximum) {
                                    continue;
                                }
                                device.axisMap[bit] = device.numAxes;
                                device.numAxes++;
                            }
                        }
                        device.numButtons = 0;
                        for (int bit = BTN_MISC; bit < KEY_CNT; bit++) {
                            if (testBit(bit, evKeyBits)) {
                                device.buttonMap[bit - BTN_MISC] = device.numButtons;
                                device.numButtons++;
                            }
                        }

                        device.axisStates = new float[device.numAxes];
                        device.buttonStates = new boolean[device.numButtons];

                        device.fireDeviceAttach();

                        device.inputEs.submit(new MyRunnable(device));
                    }
                }
                LinuxIO.INSTANCE.closedir(dev_input);
            }

            lastInputStatTime = currentTime;
        }
    }

    @Override
    public boolean isSupported() {
        return PlatformUtil.isLinux();
    }
}