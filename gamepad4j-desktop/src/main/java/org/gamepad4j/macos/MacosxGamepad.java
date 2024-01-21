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

package org.gamepad4j.macos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import org.gamepad4j.desktop.BaseGamepad;
import org.gamepad4j.util.PlatformUtil;
import vavix.rococoa.corefoundation.CFArray;
import vavix.rococoa.corefoundation.CFDictionary;
import vavix.rococoa.corefoundation.CFIndex;
import vavix.rococoa.corefoundation.CFLib;
import vavix.rococoa.corefoundation.CFNumber;
import vavix.rococoa.corefoundation.CFString;
import vavix.rococoa.corefoundation.CFType;
import vavix.rococoa.iokit.IOKitLib;

import static vavix.rococoa.corefoundation.CFAllocator.kCFAllocatorDefault;
import static vavix.rococoa.corefoundation.CFLib.CFNumberType.kCFNumberSInt32Type;
import static vavix.rococoa.corefoundation.CFLib.kCFRunLoopDefaultMode;
import static vavix.rococoa.corefoundation.CFLib.kCFTypeArrayCallBacks;
import static vavix.rococoa.corefoundation.CFLib.kCFTypeDictionaryKeyCallBacks;
import static vavix.rococoa.corefoundation.CFLib.kCFTypeDictionaryValueCallBacks;
import static vavix.rococoa.corefoundation.CFString.CFSTR;
import static vavix.rococoa.iokit.IOKitLib.kHIDPage_GenericDesktop;
import static vavix.rococoa.iokit.IOKitLib.kHIDUsage_GD_GamePad;
import static vavix.rococoa.iokit.IOKitLib.kHIDUsage_GD_Hatswitch;
import static vavix.rococoa.iokit.IOKitLib.kHIDUsage_GD_Joystick;
import static vavix.rococoa.iokit.IOKitLib.kHIDUsage_GD_MultiAxisController;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDDeviceUsageKey;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDDeviceUsagePageKey;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDElementTypeInput_Axis;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDElementTypeInput_Button;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDElementTypeInput_Misc;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDOptionsTypeNone;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDProductIDKey;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDProductKey;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDVendorIDKey;


/**
 * @author Alex Diener adiener@sacredsoftware.net
 */
public class MacosxGamepad extends BaseGamepad {

    /** a cupcell for passing to callback */
    public static class ObjectContext extends Structure {

        public int id;

        public ObjectContext() {
            id = idGenerator++;
            write();
        }

        public ObjectContext(Pointer p) {
            super(p);
            read();
        }

        /** */
        public static class ByReference extends ObjectContext implements Structure.ByReference {
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("id");
        }

        private static int idGenerator = 1000;
        private static final Map<Integer, Object> map = new HashMap<>();

        public static ByReference create(Object object) {
            ByReference context = new ByReference();
            map.put(context.id, object);
            return context;
        }

        public static Object get(Pointer p) {
            ObjectContext context = new ObjectContext(p);
 logger.finer(context.id + ", " + map + "\n" + p.dump(0, 4));
            return map.get(context.id);
        }
    }

    private static class Axis {

        int /* IOHIDElementCookie */ cookie;
        int logicalMin;
        int logicalMax;
        boolean hasNullState;
        boolean isHatSwitch;
        boolean isHatSwitchSecondAxis;

        @Override public String toString() {
            return "Axis{" +
                    "cookie=" + cookie +
                    ", logicalMin=" + logicalMin +
                    ", logicalMax=" + logicalMax +
                    ", hasNullState=" + hasNullState +
                    ", isHatSwitch=" + isHatSwitch +
                    ", isHatSwitchSecondAxis=" + isHatSwitchSecondAxis +
                    '}';
        }
    }

    private static class Button {

        int /* IOHIDElementCookie */ cookie;

        @Override public String toString() {
            return "Button{" +
                    "cookie=" + cookie +
                    '}';
        }
    }

    private static class MacosDevice extends Device {

        MacosDevice(List<GamepadListener> listeners) {
            super(listeners);
        }

        Pointer /* IOHIDDeviceRef */ deviceRef;
        List<Axis> axisElements;
        List<Button> buttonElements;

        /** avoid garbage collection */
        ObjectContext.ByReference context;
    }

    private Pointer /* IOHIDManagerRef */ hidManager = null;
    private final List<MacosDevice> devices = new ArrayList<>();
    private int nextDeviceID = 0;

    /** */
    private static Hat hatValueToXY(int value, int range) {
        int x, y;
        if (value == range) {
            x = y = 0;

        } else {
            if (value > 0 && value < range / 2) {
                x = 1;

            } else if (value > range / 2) {
                x = -1;

            } else {
                x = 0;
            }

            if (value > range / 4 * 3 || value < range / 4) {
                y = -1;

            } else if (value > range / 4 && value < range / 4 * 3) {
                y = 1;

            } else {
                y = 0;
            }
        }
        return new Hat(x, y);
    }

    /** a callback when a device value changed */
    private static void onDeviceValueChanged(Pointer context, int /* IOReturn */ result, Pointer sender, Pointer /* IOHIDValueRef */ value) {
        MacosDevice device = (MacosDevice) ObjectContext.get(context);
if (device == null) {
 logger.severe("device is null: " + context + ", " + Integer.toHexString(context.getInt(0)) + ", " + result + ", " + sender);
 return;
}
logger.finer("onDeviceValueChanged: " + device.deviceID + ", " + context + ", " + Integer.toHexString(context.getInt(0)) + ", " + result + ", " + sender);
        Pointer /* IOHIDElementRef */ element = IOKitLib.INSTANCE.IOHIDValueGetElement(value);
        int /* IOHIDElementCookie */ cookie = IOKitLib.INSTANCE.IOHIDElementGetCookie(element);

        for (int axisIndex = 0; axisIndex < device.numAxes; axisIndex++) {
            Axis axisElement = device.axisElements.get(axisIndex);
            if (!axisElement.isHatSwitchSecondAxis && axisElement.cookie == cookie) {

                if (IOKitLib.INSTANCE.IOHIDValueGetLength(value).intValue() > 4) {
                    // Workaround for a strange crash that occurs with PS3 controller; was getting lengths of 39 (!)
                    continue;
                }
                int integerValue = IOKitLib.INSTANCE.IOHIDValueGetIntegerValue(value).intValue();

                if (axisElement.isHatSwitch) {
                    // Fix for Saitek X52
                    if (!axisElement.hasNullState) {
                        if (integerValue < axisElement.logicalMin) {
                            integerValue = axisElement.logicalMax - axisElement.logicalMin + 1;
                        } else {
                            integerValue--;
                        }
                    }

                    Hat hat = hatValueToXY(integerValue, axisElement.logicalMax - axisElement.logicalMin + 1);

                    if (hat.x() != device.axisStates[axisIndex]) {
logger.finer("hat x: " + hat.x());
                        device.fireAxisMove(axisIndex, hat.x());

                        device.axisStates[axisIndex] = hat.x();
                    }

                    if (hat.y() != device.axisStates[axisIndex + 1]) {
logger.finer("hat y: " + hat.y());
                        device.fireAxisMove(axisIndex + 1, hat.y());

                        device.axisStates[axisIndex + 1] = hat.y();
                    }

                } else {
                    if (integerValue < axisElement.logicalMin) {
                        axisElement.logicalMin = integerValue;
                    }
                    if (integerValue > axisElement.logicalMax) {
                        axisElement.logicalMax = integerValue;
                    }
                    float floatValue = (integerValue - axisElement.logicalMin) /
                            (float) (axisElement.logicalMax - axisElement.logicalMin) * 2.0f - 1.0f;

logger.finer(String.format("axis %d: %1.3f", axisIndex, floatValue));
                    device.fireAxisMove(axisIndex, floatValue);

                    device.axisStates[axisIndex] = floatValue;
                }

                return;
            }
        }

        for (int buttonIndex = 0; buttonIndex < device.numButtons; buttonIndex++) {
            if (device.buttonElements.get(buttonIndex).cookie == cookie) {
                boolean down = IOKitLib.INSTANCE.IOHIDValueGetIntegerValue(value).intValue() != 0;
                if (down) {
                    device.fireButtonDown(buttonIndex);
                } else {
                    device.fireButtonUp(buttonIndex);
                }

                device.buttonStates[buttonIndex] = down;

                return;
            }
        }
    }

    /** */
    private static int getIntProperty(Pointer /* IOHIDDeviceRef */ deviceRef, CFString key) {
        CFType typeRef = IOKitLib.INSTANCE.IOHIDDeviceGetProperty(deviceRef, key);
        if (typeRef == null || !CFLib.INSTANCE.CFGetTypeID(typeRef).equals(CFLib.INSTANCE.CFNumberGetTypeID())) {
logger.fine("no such key for: " + key.getString());
            return 0;
        }

        IntByReference value = new IntByReference();
        CFLib.INSTANCE.CFNumberGetValue(typeRef.asNumber(), kCFNumberSInt32Type, value);
        return value.getValue();
    }

    /** a callback when a device attached */
    private static void onDeviceMatched(Pointer context, int /* IOReturn */ result, Pointer sender, Pointer /* IOHIDDeviceRef */ deviceRef) {
        MacosxGamepad this_ = (MacosxGamepad) ObjectContext.get(context);
if (this_ == null) {
 logger.severe("this_ is null: " + context);
 return;
}
logger.finer("CHECKPOINT-4.S");
        MacosDevice device = new MacosDevice(this_.listeners);
        device.deviceID = this_.nextDeviceID++;
        device.vendorID = getIntProperty(deviceRef, CFSTR(kIOHIDVendorIDKey));
        device.productID = getIntProperty(deviceRef, CFSTR(kIOHIDProductIDKey));
        device.numAxes = 0;
        device.numButtons = 0;

        device.deviceRef = deviceRef;
        device.axisElements = new ArrayList<>();
        device.buttonElements = new ArrayList<>();

        CFType cfProductName = IOKitLib.INSTANCE.IOHIDDeviceGetProperty(deviceRef, CFSTR(kIOHIDProductKey));
        String description;
        if (cfProductName == null || !CFLib.INSTANCE.CFGetTypeID(cfProductName).equals(CFLib.INSTANCE.CFStringGetTypeID())) {
            description = "[Unknown]";
        } else {
            description = cfProductName.asString().getString();
        }
        device.description = description;
logger.fine("mac device: " + description);

        CFArray elements = IOKitLib.INSTANCE.IOHIDDeviceCopyMatchingElements(deviceRef, null, kIOHIDOptionsTypeNone);
        for (int elementIndex = 0; elementIndex < CFLib.INSTANCE.CFArrayGetCount(elements).intValue(); elementIndex++) {
            Pointer /* IOHIDElementRef */ element = CFLib.INSTANCE.CFArrayGetValueAtIndex(elements, elementIndex).getPointer();
            int /* IOHIDElementType */ type = IOKitLib.INSTANCE.IOHIDElementGetType(element);

            // All of the axis elements I've ever detected have been kIOHIDElementTypeInput_Misc. kIOHIDElementTypeInput_Axis is only included for good faith...
            if (type == kIOHIDElementTypeInput_Misc || type == kIOHIDElementTypeInput_Axis) {

                Axis axis = new Axis();
                axis.cookie = IOKitLib.INSTANCE.IOHIDElementGetCookie(element);
                axis.logicalMin = IOKitLib.INSTANCE.IOHIDElementGetLogicalMin(element).intValue();
                axis.logicalMax = IOKitLib.INSTANCE.IOHIDElementGetLogicalMax(element).intValue();
                axis.hasNullState = IOKitLib.INSTANCE.IOHIDElementHasNullState(element);
                axis.isHatSwitch = IOKitLib.INSTANCE.IOHIDElementGetUsage(element) == kHIDUsage_GD_Hatswitch;
                axis.isHatSwitchSecondAxis = false;
logger.finer("CHECKPOINT-4.2: " + axis);
                device.axisElements.add(axis);
                device.numAxes++;

                if (device.axisElements.get(device.numAxes - 1).isHatSwitch) {
                    axis = new Axis();
                    axis.isHatSwitchSecondAxis = true;
logger.finer("CHECKPOINT-4.3: " + axis);
                    device.axisElements.add(axis);
                    device.numAxes++;
                }

            } else if (type == kIOHIDElementTypeInput_Button) {
                Button button = new Button();
                button.cookie = IOKitLib.INSTANCE.IOHIDElementGetCookie(element);
logger.finer("CHECKPOINT-4.4: " + button);
                device.buttonElements.add(button);
                device.numButtons++;
            }
        }
        CFLib.INSTANCE.CFRelease(elements);

        device.axisStates = new float[device.numAxes];
        device.buttonStates = new boolean[device.numButtons];

        device.context = ObjectContext.create(device);
        IOKitLib.INSTANCE.IOHIDDeviceRegisterInputValueCallback(deviceRef, MacosxGamepad::onDeviceValueChanged, device.context);

        this_.devices.add(device);

        device.fireDeviceAttach();
logger.finer("CHECKPOINT-4.E: axis: " + device.axisStates.length + ", button: " + device.buttonStates.length);
    }

    /** a callback when a device removed */
    private static void onDeviceRemoved(Pointer context, int /* IOReturn */ result, Pointer sender, Pointer /* IOHIDDeviceRef */ deviceRef) {
        MacosxGamepad this_ = (MacosxGamepad) ObjectContext.get(context);
if (this_ == null) {
 logger.severe("this_ is null: " + context);
 return;
}
logger.fine("onDeviceRemoved: " + deviceRef);
        Iterator<MacosDevice> deviceIndex = this_.devices.iterator();
        while (deviceIndex.hasNext()) {
            MacosDevice device = deviceIndex.next();
            if (device.deviceRef == deviceRef) {
                device.fireDeviceRemove();

                device.context = null;

                IOKitLib.INSTANCE.IOHIDDeviceRegisterInputValueCallback(device.deviceRef, null, null);
logger.fine("IOHIDDeviceRegisterInputValueCallback: stop");
                deviceIndex.remove();
                return;
            }
        }
    }

    /** for RunLoopRun */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /** for returning before RunLoopRun */
    private final CountDownLatch cdl = new CountDownLatch(1);

    @Override
    public void open() {
        executorService.submit(this::initInternal);
        try { cdl.await(); } catch (InterruptedException ignore) {}
    }

    /** for RunLoopRun never returning */
    private void initInternal() {
        if (hidManager == null) {
logger.finer("CHECKPOINT-0.S");
            hidManager = IOKitLib.INSTANCE.IOHIDManagerCreate(kCFAllocatorDefault, kIOHIDOptionsTypeNone);
            IOKitLib.INSTANCE.IOHIDManagerOpen(hidManager, kIOHIDOptionsTypeNone);
            IOKitLib.INSTANCE.IOHIDManagerScheduleWithRunLoop(hidManager, CFLib.INSTANCE.CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);

            CFString[] keys = new CFString[2];
            keys[0] = CFSTR(kIOHIDDeviceUsagePageKey);
            keys[1] = CFSTR(kIOHIDDeviceUsageKey);

            CFDictionary[] dictionaries = new CFDictionary[3];

            CFNumber[] values = new CFNumber[2];
            IntByReference value = new IntByReference();
            value.setValue(kHIDPage_GenericDesktop);
            values[0] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            value.setValue(kHIDUsage_GD_Joystick);
            values[1] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            dictionaries[0] = CFLib.INSTANCE.CFDictionaryCreate(kCFAllocatorDefault, keys, values, CFIndex.of(2), kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);
            CFLib.INSTANCE.CFRelease(values[0]);
            CFLib.INSTANCE.CFRelease(values[1]);

            value.setValue(kHIDPage_GenericDesktop);
            values[0] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            value.setValue(kHIDUsage_GD_GamePad);
            values[1] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            dictionaries[1] = CFLib.INSTANCE.CFDictionaryCreate(kCFAllocatorDefault, keys, values, CFIndex.of(2), kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);
            CFLib.INSTANCE.CFRelease(values[0]);
            CFLib.INSTANCE.CFRelease(values[1]);

            value.setValue(kHIDPage_GenericDesktop);
            values[0] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            value.setValue(kHIDUsage_GD_MultiAxisController);
            values[1] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            dictionaries[2] = CFLib.INSTANCE.CFDictionaryCreate(kCFAllocatorDefault, keys, values, CFIndex.of(2), kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);
            CFLib.INSTANCE.CFRelease(values[0]);
            CFLib.INSTANCE.CFRelease(values[1]);

            Pointer[] pp = new Pointer[dictionaries.length];
            for (int i = 0; i < dictionaries.length; i++) {
                pp[i] = dictionaries[i].getPointer();
            }
            CFArray array = CFLib.INSTANCE.CFArrayCreate(kCFAllocatorDefault, pp, CFIndex.of(pp.length), kCFTypeArrayCallBacks);
            for (CFDictionary dictionary : dictionaries) {
                CFLib.INSTANCE.CFRelease(dictionary);
            }

            ObjectContext.ByReference context = ObjectContext.create(this);
            IOKitLib.INSTANCE.IOHIDManagerSetDeviceMatchingMultiple(hidManager, array);
            CFLib.INSTANCE.CFRelease(array);
            IOKitLib.INSTANCE.IOHIDManagerRegisterDeviceMatchingCallback(hidManager, MacosxGamepad::onDeviceMatched, context);
            IOKitLib.INSTANCE.IOHIDManagerRegisterDeviceRemovalCallback(hidManager, MacosxGamepad::onDeviceRemoved, context);

            cdl.countDown();
logger.fine("mac init done.");

            CFLib.INSTANCE.CFRunLoopRun();
logger.finer("CHECKPOINT-0.E");
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
logger.fine("i am a mac, bye");

        if (hidManager != null) {
            IOKitLib.INSTANCE.IOHIDManagerUnscheduleFromRunLoop(hidManager, CFLib.INSTANCE.CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
            IOKitLib.INSTANCE.IOHIDManagerClose(hidManager, 0);
            CFLib.INSTANCE.CFRelease(hidManager);
            hidManager = null;

            for (MacosDevice device : devices) {
                IOKitLib.INSTANCE.IOHIDDeviceRegisterInputValueCallback(device.deviceRef, null, null);
            }
            devices.clear();
        }
    }

    @Override
    public int size() {
        return devices.size();
    }

    @Override
    public Device get(int deviceId) {
        for (MacosDevice device : devices) {
            if (device.deviceID == deviceId) {
                return devices.get(deviceId);
            }
        }
logger.warning("no such deviceId: " + deviceId);
        return null;
    }

    @Override
    public boolean isSupported() {
        return PlatformUtil.isMac();
    }
}