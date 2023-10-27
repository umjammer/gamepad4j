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

package org.gamepad4j.windows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.sun.jna.platform.win32.WinNT.LARGE_INTEGER;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.ptr.IntByReference;
import net.java.games.input.windows.User32Ex;
import net.java.games.input.windows.User32Ex.JOYCAPS;
import net.java.games.input.windows.User32Ex.JOYINFOEX;
import org.gamepad4j.shared.BaseGamepad;
import org.gamepad4j.shared.Gamepad.Device.Private;

import static com.sun.jna.platform.win32.WinError.ERROR_SUCCESS;
import static com.sun.jna.platform.win32.WinNT.KEY_READ;
import static com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER;
import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;
import static net.java.games.input.windows.User32Ex.JOYCAPS_HASPOV;
import static net.java.games.input.windows.User32Ex.JOYCAPS_HASR;
import static net.java.games.input.windows.User32Ex.JOYCAPS_HASU;
import static net.java.games.input.windows.User32Ex.JOYCAPS_HASV;
import static net.java.games.input.windows.User32Ex.JOYCAPS_HASZ;
import static net.java.games.input.windows.User32Ex.JOYERR_NOERROR;
import static net.java.games.input.windows.User32Ex.JOYERR_UNPLUGGED;
import static net.java.games.input.windows.User32Ex.JOYSTICKID1;
import static net.java.games.input.windows.User32Ex.JOY_POVBACKWARD;
import static net.java.games.input.windows.User32Ex.JOY_POVCENTERED;
import static net.java.games.input.windows.User32Ex.JOY_POVFORWARD;
import static net.java.games.input.windows.User32Ex.JOY_POVLEFT;
import static net.java.games.input.windows.User32Ex.JOY_POVRIGHT;
import static net.java.games.input.windows.User32Ex.JOY_RETURNALL;
import static net.java.games.input.windows.User32Ex.REGSTR_KEY_JOYCURR;
import static net.java.games.input.windows.User32Ex.REGSTR_PATH_JOYCONFIG;
import static net.java.games.input.windows.User32Ex.REGSTR_PATH_JOYOEM;
import static net.java.games.input.windows.User32Ex.REGSTR_VAL_JOYOEMNAME;


/**
 * @author Alex Diener adiener@sacredsoftware.net
 */
public class WindowsGamepad extends BaseGamepad {

    private static class DevicePrivate implements Private {

        int joystickID;
        JOYINFOEX lastState;
        int xAxisIndex;
        int yAxisIndex;
        int zAxisIndex;
        int rAxisIndex;
        int uAxisIndex;
        int vAxisIndex;
        int povXAxisIndex;
        int povYAxisIndex;
        int[][] axisRanges = new int[2][];
    }

    private Device[] devices = null;
    private int numDevices = 0;
    private int nextDeviceID = 0;

    private boolean inited = false;

    @Override
    public void init() {
        if (!inited) {
            inited = true;
            detectDevices();
        }
    }

    @Override
    public void shutdown() {
        if (inited) {
            devices = null;
            numDevices = 0;
            inited = false;
        }
    }

    @Override
    public int numDevices() {
        return numDevices;
    }

    @Override
    public Device deviceAtIndex(int deviceIndex) {
        if (deviceIndex >= numDevices) {
            return null;
        }
        return devices[deviceIndex];
    }

    static final int REG_STRING_MAX = 256;

    private byte[] getDeviceDescription(int joystickID, JOYCAPS caps) {
        byte[] description = null;
        HKEY topKey, key = new HKEY();

        String subkey = String.format("%s\\%s\\%s", REGSTR_PATH_JOYCONFIG, new String(caps.szRegKey, StandardCharsets.UTF_8).replace("\u0000", ""), REGSTR_KEY_JOYCURR);
        long result = User32Ex.INSTANCE.RegOpenKeyEx(topKey = HKEY_LOCAL_MACHINE, subkey.getBytes(), 0, KEY_READ, key);
        if (result != ERROR_SUCCESS) {
            result = User32Ex.INSTANCE.RegOpenKeyEx(topKey = HKEY_CURRENT_USER, subkey.getBytes(), 0, KEY_READ, key);
        }
        if (result == ERROR_SUCCESS) {
            byte[] name = new byte[REG_STRING_MAX];
            IntByReference nameSize = new IntByReference();

            String value = String.format("Joystick%d%s", joystickID + 1, REGSTR_VAL_JOYOEMNAME);
            nameSize.setValue(name.length);
            result = User32Ex.INSTANCE.RegQueryValueEx(key, value.getBytes(), null, null, name, nameSize);
            User32Ex.INSTANCE.RegCloseKey(key);

            if (result == ERROR_SUCCESS) {
                subkey = String.format("%s\\%s", REGSTR_PATH_JOYOEM, new String(name, StandardCharsets.UTF_8).replace("\u0000", ""));
                result = User32Ex.INSTANCE.RegOpenKeyEx(topKey, subkey.getBytes(), 0, KEY_READ, key);

                if (result == ERROR_SUCCESS) {
                    nameSize.setValue(name.length);
                    result = User32Ex.INSTANCE.RegQueryValueEx(key, REGSTR_VAL_JOYOEMNAME.getBytes(), null, null, null, nameSize);

                    if (result == ERROR_SUCCESS) {
                        description = new byte[nameSize.getValue()];
                        result = User32Ex.INSTANCE.RegQueryValueEx(key, REGSTR_VAL_JOYOEMNAME.getBytes(), null, null, description, nameSize);
                    }
                    User32Ex.INSTANCE.RegCloseKey(key);

                    if (result == ERROR_SUCCESS) {
                        return description;
                    }
                }
            }
        }

        description = new byte[caps.szPname.length + 1];
        System.arraycopy(caps.szPname, 0, description, 0, caps.szPname.length);

        return description;
    }

    @Override
    public void detectDevices() {
        int numPadsSupported;
        int deviceIndex, deviceIndex2;
        JOYINFOEX info = new JOYINFOEX();
        JOYCAPS caps = new JOYCAPS();
        boolean duplicate;
        Device deviceRecord;
        DevicePrivate deviceRecordPrivate;
        int joystickID;
        int axisIndex;

        if (!inited) {
            return;
        }

        numPadsSupported = User32Ex.INSTANCE.joyGetNumDevs();
        for (deviceIndex = 0; deviceIndex < numPadsSupported; deviceIndex++) {
            info.dwSize = info.size();
            info.dwFlags = JOY_RETURNALL;
            joystickID = JOYSTICKID1 + deviceIndex;
            if (User32Ex.INSTANCE.joyGetPosEx(joystickID, info) == JOYERR_NOERROR &&
                    User32Ex.INSTANCE.joyGetDevCaps(joystickID, caps, caps.size()) == JOYERR_NOERROR) {

                duplicate = false;
                for (deviceIndex2 = 0; deviceIndex2 < numDevices; deviceIndex2++) {
                    if (((DevicePrivate) devices[deviceIndex2].privateData).joystickID == joystickID) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) {
                    continue;
                }

                deviceRecord = new Device();
                deviceRecord.deviceID = nextDeviceID++;
                deviceRecord.description = new String(getDeviceDescription(joystickID, caps), StandardCharsets.UTF_8).replace("\u0000", "");
                deviceRecord.vendorID = caps.wMid;
                deviceRecord.productID = caps.wPid;
                deviceRecord.numAxes = caps.wNumAxes + ((caps.wCaps & JOYCAPS_HASPOV) != 0 ? 2 : 0);
                deviceRecord.numButtons = caps.wNumButtons;
                deviceRecord.axisStates = new float[deviceRecord.numAxes];
                deviceRecord.buttonStates = new boolean[deviceRecord.numButtons];
                devices = Arrays.copyOf(devices, numDevices + 1);
                devices[numDevices++] = deviceRecord;

                deviceRecordPrivate = new DevicePrivate();
                deviceRecordPrivate.joystickID = joystickID;
                deviceRecordPrivate.lastState = info;

                deviceRecordPrivate.xAxisIndex = 0;
                deviceRecordPrivate.yAxisIndex = 1;
                axisIndex = 2;
                deviceRecordPrivate.zAxisIndex = (caps.wCaps & JOYCAPS_HASZ) != 0 ? axisIndex++ : -1;
                deviceRecordPrivate.rAxisIndex = (caps.wCaps & JOYCAPS_HASR) != 0 ? axisIndex++ : -1;
                deviceRecordPrivate.uAxisIndex = (caps.wCaps & JOYCAPS_HASU) != 0 ? axisIndex++ : -1;
                deviceRecordPrivate.vAxisIndex = (caps.wCaps & JOYCAPS_HASV) != 0 ? axisIndex++ : -1;

                deviceRecordPrivate.axisRanges[0] = new int[axisIndex];
                deviceRecordPrivate.axisRanges[1] = new int[axisIndex];
                deviceRecordPrivate.axisRanges[0][0] = caps.wXmin;
                deviceRecordPrivate.axisRanges[0][1] = caps.wXmax;
                deviceRecordPrivate.axisRanges[1][0] = caps.wYmin;
                deviceRecordPrivate.axisRanges[1][1] = caps.wYmax;
                if (deviceRecordPrivate.zAxisIndex != -1) {
                    deviceRecordPrivate.axisRanges[deviceRecordPrivate.zAxisIndex][0] = caps.wZmin;
                    deviceRecordPrivate.axisRanges[deviceRecordPrivate.zAxisIndex][1] = caps.wZmax;
                }
                if (deviceRecordPrivate.rAxisIndex != -1) {
                    deviceRecordPrivate.axisRanges[deviceRecordPrivate.rAxisIndex][0] = caps.wRmin;
                    deviceRecordPrivate.axisRanges[deviceRecordPrivate.rAxisIndex][1] = caps.wRmax;
                }
                if (deviceRecordPrivate.uAxisIndex != -1) {
                    deviceRecordPrivate.axisRanges[deviceRecordPrivate.uAxisIndex][0] = caps.wUmin;
                    deviceRecordPrivate.axisRanges[deviceRecordPrivate.uAxisIndex][1] = caps.wUmax;
                }
                if (deviceRecordPrivate.vAxisIndex != -1) {
                    deviceRecordPrivate.axisRanges[deviceRecordPrivate.vAxisIndex][0] = caps.wVmin;
                    deviceRecordPrivate.axisRanges[deviceRecordPrivate.vAxisIndex][1] = caps.wVmax;
                }

                deviceRecordPrivate.povXAxisIndex = (caps.wCaps & JOYCAPS_HASPOV) != 0 ? axisIndex++ : -1;
                deviceRecordPrivate.povYAxisIndex = (caps.wCaps & JOYCAPS_HASPOV) != 0 ? axisIndex++ : -1;

                deviceRecord.privateData = deviceRecordPrivate;

                fireDeviceAttach(deviceRecord);
            }
        }
    }

    private final LARGE_INTEGER frequency = new LARGE_INTEGER();

    private double currentTime() {
        // HACK: No timestamp data from joyGetInfoEx, so we make it up
        LARGE_INTEGER currentTime = new LARGE_INTEGER();

        if (frequency.getValue() == 0) {
            User32Ex.INSTANCE.QueryPerformanceFrequency(frequency);
        }
        User32Ex.INSTANCE.QueryPerformanceCounter(currentTime);

        return (double) currentTime.getValue() / frequency.getValue();
    }

    private void handleAxisChange(Device device, int axisIndex, int ivalue) {
        if (axisIndex < 0 || axisIndex >= (int) device.numAxes) {
            return;
        }

        DevicePrivate devicePrivate = (DevicePrivate) device.privateData;
        float value = (ivalue - devicePrivate.axisRanges[axisIndex][0]) / (float) (devicePrivate.axisRanges[axisIndex][1] - devicePrivate.axisRanges[axisIndex][0]) * 2.0f - 1.0f;

        device.axisStates[axisIndex] = value;
        fireAxisMove(device, axisIndex, value, currentTime());
    }

    private void handleButtonChange(Device device, int lastValue, int value) {
        for (int buttonIndex = 0; buttonIndex < device.numButtons; buttonIndex++) {
            if ((lastValue ^ value) != 0 & (1 << buttonIndex) != 0) {
                boolean down = (value & (1 << buttonIndex)) != 0;

                device.buttonStates[buttonIndex] = down;
                if (down) {
                    fireButtonDown(device, buttonIndex, currentTime());
                }
            }
        }
    }

    private static void povToXY(int pov, int[] outX, int[] outY) {
        if (pov == JOY_POVCENTERED) {
            outX[0] = outY[0] = 0;

        } else {
            if (pov > JOY_POVFORWARD && pov < JOY_POVBACKWARD) {
                outX[0] = 1;

            } else if (pov > JOY_POVBACKWARD) {
                outX[0] = -1;

            } else {
                outX[0] = 0;
            }

            if (pov > JOY_POVLEFT || pov < JOY_POVRIGHT) {
                outY[0] = -1;

            } else if (pov > JOY_POVRIGHT && pov < JOY_POVLEFT) {
                outY[0] = 1;

            } else {
                outY[0] = 0;
            }
        }
    }

    private void handlePOVChange(Device device, int lastValue, int value) {
        DevicePrivate devicePrivate;
        int[] lastX = new int[1], lastY = new int[1], newX = new int[1], newY = new int[1];

        devicePrivate = (DevicePrivate) device.privateData;

        if (devicePrivate.povXAxisIndex == -1 || devicePrivate.povYAxisIndex == -1) {
            return;
        }

        povToXY(lastValue, lastX, lastY);
        povToXY(value, newX, newY);

        if (newX != lastX) {
            device.axisStates[devicePrivate.povXAxisIndex] = newX[0];
            fireAxisMove(device, devicePrivate.povXAxisIndex, newX[0], currentTime());
        }
        if (newY != lastY) {
            device.axisStates[devicePrivate.povYAxisIndex] = newY[0];
            fireAxisMove(device, devicePrivate.povYAxisIndex, newY[0], currentTime());
        }
    }

    private boolean inProcessEvents;

    @Override
    public void processEvents() {
        if (!inited || inProcessEvents) {
            return;
        }

        inProcessEvents = true;
        for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
            Device device = devices[deviceIndex];
            DevicePrivate devicePrivate = (DevicePrivate) device.privateData;

            JOYINFOEX info = new JOYINFOEX();
            info.dwSize = info.size();
            info.dwFlags = JOY_RETURNALL;
            int /* MMRESULT */ result = User32Ex.INSTANCE.joyGetPosEx(devicePrivate.joystickID, info);
            if (result == JOYERR_UNPLUGGED) {
                fireDeviceRemove(device);

                numDevices--;
                for (; deviceIndex < numDevices; deviceIndex++) {
                    devices[deviceIndex] = devices[deviceIndex + 1];
                }

            } else if (result == JOYERR_NOERROR) {
                if (info.dwXpos != devicePrivate.lastState.dwXpos) {
                    handleAxisChange(device, devicePrivate.xAxisIndex, info.dwXpos);
                }
                if (info.dwYpos != devicePrivate.lastState.dwYpos) {
                    handleAxisChange(device, devicePrivate.yAxisIndex, info.dwYpos);
                }
                if (info.dwZpos != devicePrivate.lastState.dwZpos) {
                    handleAxisChange(device, devicePrivate.zAxisIndex, info.dwZpos);
                }
                if (info.dwRpos != devicePrivate.lastState.dwRpos) {
                    handleAxisChange(device, devicePrivate.rAxisIndex, info.dwRpos);
                }
                if (info.dwUpos != devicePrivate.lastState.dwUpos) {
                    handleAxisChange(device, devicePrivate.uAxisIndex, info.dwUpos);
                }
                if (info.dwVpos != devicePrivate.lastState.dwVpos) {
                    handleAxisChange(device, devicePrivate.vAxisIndex, info.dwVpos);
                }
                if (info.dwPOV != devicePrivate.lastState.dwPOV) {
                    handlePOVChange(device, devicePrivate.lastState.dwPOV, info.dwPOV);
                }
                if (info.dwButtons != devicePrivate.lastState.dwButtons) {
                    handleButtonChange(device, devicePrivate.lastState.dwButtons, info.dwButtons);
                }
                devicePrivate.lastState = info;
            }
        }
        inProcessEvents = false;
    }
}