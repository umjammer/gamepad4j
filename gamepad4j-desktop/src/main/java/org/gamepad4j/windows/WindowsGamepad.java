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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.ptr.IntByReference;
import net.java.games.input.windows.WinAPI;
import net.java.games.input.windows.WinAPI.JOYCAPS;
import net.java.games.input.windows.WinAPI.JOYINFOEX;
import org.gamepad4j.desktop.BaseGamepad;
import org.gamepad4j.util.PlatformUtil;

import static com.sun.jna.platform.win32.WinError.ERROR_SUCCESS;
import static com.sun.jna.platform.win32.WinNT.KEY_READ;
import static com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER;
import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;
import static net.java.games.input.windows.WinAPI.JOYCAPS_HASPOV;
import static net.java.games.input.windows.WinAPI.JOYCAPS_HASR;
import static net.java.games.input.windows.WinAPI.JOYCAPS_HASU;
import static net.java.games.input.windows.WinAPI.JOYCAPS_HASV;
import static net.java.games.input.windows.WinAPI.JOYCAPS_HASZ;
import static net.java.games.input.windows.WinAPI.JOYERR_NOERROR;
import static net.java.games.input.windows.WinAPI.JOYERR_UNPLUGGED;
import static net.java.games.input.windows.WinAPI.JOYSTICKID1;
import static net.java.games.input.windows.WinAPI.JOY_POVBACKWARD;
import static net.java.games.input.windows.WinAPI.JOY_POVCENTERED;
import static net.java.games.input.windows.WinAPI.JOY_POVFORWARD;
import static net.java.games.input.windows.WinAPI.JOY_POVLEFT;
import static net.java.games.input.windows.WinAPI.JOY_POVRIGHT;
import static net.java.games.input.windows.WinAPI.JOY_RETURNALL;
import static net.java.games.input.windows.WinAPI.REGSTR_KEY_JOYCURR;
import static net.java.games.input.windows.WinAPI.REGSTR_PATH_JOYCONFIG;
import static net.java.games.input.windows.WinAPI.REGSTR_PATH_JOYOEM;
import static net.java.games.input.windows.WinAPI.REGSTR_VAL_JOYOEMNAME;


/**
 * @author Alex Diener adiener@sacredsoftware.net
 */
public class WindowsGamepad extends BaseGamepad {

    private static class WindowsDevice extends Device {

        ScheduledExecutorService inputSes = Executors.newSingleThreadScheduledExecutor();

        WindowsDevice(List<GamepadListener> listeners) {
            super(listeners);
        }

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

        @Override
        public void write(byte[] data, int length, int reportId) throws IOException {
            throw new UnsupportedOperationException("not implemented yet");
        }
    }

    private final ScheduledExecutorService detectSes = Executors.newSingleThreadScheduledExecutor();

    private List<WindowsDevice> devices = new ArrayList<>();
    private int nextDeviceID = 0;

    private boolean inited = false;

    @Override
    public void open() {
        if (!inited) {
            detectSes.schedule(this::detectDevices, 1000, TimeUnit.MILLISECONDS);

            inited = true;
        }
    }

    @Override
    public void close() {
        if (inited) {
            detectSes.shutdownNow();

            devices = null;
            inited = false;
        }
    }

    @Override
    public int size() {
        return devices.size();
    }

    @Override
    public Device get(int deviceId) {
        for (WindowsDevice device : devices) {
            if (device.deviceID == deviceId) {
                return devices.get(deviceId);
            }
        }
logger.warning("no such deviceId: " + deviceId);
        return null;
    }

    private static final int REG_STRING_MAX = 256;

    /** device description */
    private static byte[] getDeviceDescription(int joystickID, JOYCAPS caps) {
        byte[] description = null;
        HKEY topKey, key = new HKEY();

        String subkey = String.format("%s\\%s\\%s", REGSTR_PATH_JOYCONFIG, new String(caps.szRegKey, StandardCharsets.UTF_8).replace("\u0000", ""), REGSTR_KEY_JOYCURR);
        long result = WinAPI.User32Ex.INSTANCE.RegOpenKeyEx(topKey = HKEY_LOCAL_MACHINE, subkey.getBytes(), 0, KEY_READ, key);
        if (result != ERROR_SUCCESS) {
            result = WinAPI.User32Ex.INSTANCE.RegOpenKeyEx(topKey = HKEY_CURRENT_USER, subkey.getBytes(), 0, KEY_READ, key);
        }
        if (result == ERROR_SUCCESS) {
            byte[] name = new byte[REG_STRING_MAX];
            IntByReference nameSize = new IntByReference();

            String value = String.format("Joystick%d%s", joystickID + 1, REGSTR_VAL_JOYOEMNAME);
            nameSize.setValue(name.length);
            result = WinAPI.User32Ex.INSTANCE.RegQueryValueEx(key, value.getBytes(), null, null, name, nameSize);
            WinAPI.User32Ex.INSTANCE.RegCloseKey(key);

            if (result == ERROR_SUCCESS) {
                subkey = String.format("%s\\%s", REGSTR_PATH_JOYOEM, new String(name, StandardCharsets.UTF_8).replace("\u0000", ""));
                result = WinAPI.User32Ex.INSTANCE.RegOpenKeyEx(topKey, subkey.getBytes(), 0, KEY_READ, key);

                if (result == ERROR_SUCCESS) {
                    nameSize.setValue(name.length);
                    result = WinAPI.User32Ex.INSTANCE.RegQueryValueEx(key, REGSTR_VAL_JOYOEMNAME.getBytes(), null, null, null, nameSize);

                    if (result == ERROR_SUCCESS) {
                        description = new byte[nameSize.getValue()];
                        result = WinAPI.User32Ex.INSTANCE.RegQueryValueEx(key, REGSTR_VAL_JOYOEMNAME.getBytes(), null, null, description, nameSize);
                    }
                    WinAPI.User32Ex.INSTANCE.RegCloseKey(key);

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

    /** device detection thread */
    private void detectDevices() {

        if (!inited) {
            return;
        }

        int numPadsSupported = WinAPI.User32Ex.INSTANCE.joyGetNumDevs();
        for (int deviceIndex = 0; deviceIndex < numPadsSupported; deviceIndex++) {
            JOYINFOEX info = new JOYINFOEX();
            info.dwSize = info.size();
            info.dwFlags = JOY_RETURNALL;
            int joystickID = JOYSTICKID1 + deviceIndex;
            JOYCAPS caps = new JOYCAPS();
            if (WinAPI.User32Ex.INSTANCE.joyGetPosEx(joystickID, info) == JOYERR_NOERROR &&
                    WinAPI.User32Ex.INSTANCE.joyGetDevCaps(joystickID, caps, caps.size()) == JOYERR_NOERROR) {

                boolean duplicate = false;
                for (WindowsDevice windowsDevice : devices) {
                    if (windowsDevice.joystickID == joystickID) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) {
                    continue;
                }

                WindowsDevice device = new WindowsDevice(listeners);
                device.deviceID = nextDeviceID++;
                device.description = new String(getDeviceDescription(joystickID, caps), StandardCharsets.UTF_8).replace("\u0000", "");
                device.vendorID = caps.wMid;
                device.productID = caps.wPid;
                device.numAxes = caps.wNumAxes + ((caps.wCaps & JOYCAPS_HASPOV) != 0 ? 2 : 0);
                device.numButtons = caps.wNumButtons;
                device.axisStates = new float[device.numAxes];
                device.buttonStates = new boolean[device.numButtons];

                device.joystickID = joystickID;
                device.lastState = info;

                device.xAxisIndex = 0;
                device.yAxisIndex = 1;
                int axisIndex = 2;
                device.zAxisIndex = (caps.wCaps & JOYCAPS_HASZ) != 0 ? axisIndex++ : -1;
                device.rAxisIndex = (caps.wCaps & JOYCAPS_HASR) != 0 ? axisIndex++ : -1;
                device.uAxisIndex = (caps.wCaps & JOYCAPS_HASU) != 0 ? axisIndex++ : -1;
                device.vAxisIndex = (caps.wCaps & JOYCAPS_HASV) != 0 ? axisIndex++ : -1;

                device.axisRanges[0] = new int[axisIndex];
                device.axisRanges[1] = new int[axisIndex];
                device.axisRanges[0][0] = caps.wXmin;
                device.axisRanges[0][1] = caps.wXmax;
                device.axisRanges[1][0] = caps.wYmin;
                device.axisRanges[1][1] = caps.wYmax;
                if (device.zAxisIndex != -1) {
                    device.axisRanges[device.zAxisIndex][0] = caps.wZmin;
                    device.axisRanges[device.zAxisIndex][1] = caps.wZmax;
                }
                if (device.rAxisIndex != -1) {
                    device.axisRanges[device.rAxisIndex][0] = caps.wRmin;
                    device.axisRanges[device.rAxisIndex][1] = caps.wRmax;
                }
                if (device.uAxisIndex != -1) {
                    device.axisRanges[device.uAxisIndex][0] = caps.wUmin;
                    device.axisRanges[device.uAxisIndex][1] = caps.wUmax;
                }
                if (device.vAxisIndex != -1) {
                    device.axisRanges[device.vAxisIndex][0] = caps.wVmin;
                    device.axisRanges[device.vAxisIndex][1] = caps.wVmax;
                }

                device.povXAxisIndex = (caps.wCaps & JOYCAPS_HASPOV) != 0 ? axisIndex++ : -1;
                device.povYAxisIndex = (caps.wCaps & JOYCAPS_HASPOV) != 0 ? axisIndex++ : -1;

                devices.add(device);

                device.fireDeviceAttach();

                device.inputSes.schedule(this::processEvents, 20, TimeUnit.MICROSECONDS); // TODO interval
            }
        }
    }

    /** axis */
    private static void handleAxisChange(WindowsDevice device, int axisIndex, int ivalue) {
        if (axisIndex < 0 || axisIndex >= device.numAxes) {
            return;
        }

        float value = (ivalue - device.axisRanges[axisIndex][0]) / (float) (device.axisRanges[axisIndex][1] - device.axisRanges[axisIndex][0]) * 2.0f - 1.0f;

        device.axisStates[axisIndex] = value;
        device.fireAxisMove(axisIndex, value);
    }

    /** button */
    private static void handleButtonChange(WindowsDevice device, int lastValue, int value) {
        for (int buttonIndex = 0; buttonIndex < device.numButtons; buttonIndex++) {
            if ((lastValue ^ value) != 0 & (1 << buttonIndex) != 0) {
                boolean down = (value & (1 << buttonIndex)) != 0;

                device.buttonStates[buttonIndex] = down;
                if (down) {
                    device.fireButtonDown(buttonIndex);
                } else {
                    device.fireButtonUp(buttonIndex);
                }
            }
        }
    }

    /** hat */
    private static Hat povToXY(int pov) {
        int x, y;
        if (pov == JOY_POVCENTERED) {
            x = y = 0;

        } else {
            if (pov > JOY_POVFORWARD && pov < JOY_POVBACKWARD) {
                x = 1;

            } else if (pov > JOY_POVBACKWARD) {
                x = -1;

            } else {
                x = 0;
            }

            if (pov > JOY_POVLEFT || pov < JOY_POVRIGHT) {
                y = -1;

            } else if (pov > JOY_POVRIGHT && pov < JOY_POVLEFT) {
                y = 1;

            } else {
                y = 0;
            }
        }
        return new Hat(x, y);
    }

    /** hat */
    private static void handlePOVChange(WindowsDevice device, int lastValue, int value) {
        if (device.povXAxisIndex == -1 || device.povYAxisIndex == -1) {
            return;
        }

        Hat lastHat = povToXY(lastValue);
        Hat newHat = povToXY(value);

        if (newHat.x() != lastHat.x()) {
            device.axisStates[device.povXAxisIndex] = newHat.x();
            device.fireAxisMove(device.povXAxisIndex, newHat.x());
        }
        if (newHat.y() != lastHat.y()) {
            device.axisStates[device.povYAxisIndex] = newHat.y();
            device.fireAxisMove(device.povYAxisIndex, newHat.y());
        }
    }

    /** device input report thread */
    private void processEvents() {
        if (!inited) {
            return;
        }

        for (WindowsDevice device : devices) {
            JOYINFOEX info = new JOYINFOEX();
            info.dwSize = info.size();
            info.dwFlags = JOY_RETURNALL;
            int /* MMRESULT */ result = WinAPI.User32Ex.INSTANCE.joyGetPosEx(device.joystickID, info);
            if (result == JOYERR_UNPLUGGED) {
                removeDevice(device);
            } else if (result == JOYERR_NOERROR) {
                if (info.dwXpos != device.lastState.dwXpos) {
                    handleAxisChange(device, device.xAxisIndex, info.dwXpos);
                }
                if (info.dwYpos != device.lastState.dwYpos) {
                    handleAxisChange(device, device.yAxisIndex, info.dwYpos);
                }
                if (info.dwZpos != device.lastState.dwZpos) {
                    handleAxisChange(device, device.zAxisIndex, info.dwZpos);
                }
                if (info.dwRpos != device.lastState.dwRpos) {
                    handleAxisChange(device, device.rAxisIndex, info.dwRpos);
                }
                if (info.dwUpos != device.lastState.dwUpos) {
                    handleAxisChange(device, device.uAxisIndex, info.dwUpos);
                }
                if (info.dwVpos != device.lastState.dwVpos) {
                    handleAxisChange(device, device.vAxisIndex, info.dwVpos);
                }
                if (info.dwPOV != device.lastState.dwPOV) {
                    handlePOVChange(device, device.lastState.dwPOV, info.dwPOV);
                }
                if (info.dwButtons != device.lastState.dwButtons) {
                    handleButtonChange(device, device.lastState.dwButtons, info.dwButtons);
                }
                device.lastState = info;
            }
        }
    }

    /** device removal */
    private void removeDevice(WindowsDevice device) {
        device.inputSes.shutdownNow();

        device.fireDeviceRemove();

        Iterator<WindowsDevice> i = devices.iterator();
        while (i.hasNext()) {
            WindowsDevice toBeDeleted = i.next();
            if (toBeDeleted == device) {
                i.remove();
                break;
            }
        }
    }

    @Override
    public boolean isSupported() {
        return PlatformUtil.isWindows();
    }
}