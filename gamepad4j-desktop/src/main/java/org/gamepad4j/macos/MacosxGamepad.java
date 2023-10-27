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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import org.gamepad4j.shared.Gamepad;
import vavix.rococoa.corefoundation.CFArray;
import vavix.rococoa.corefoundation.CFDictionary;
import vavix.rococoa.corefoundation.CFIndex;
import vavix.rococoa.corefoundation.CFLib;
import vavix.rococoa.corefoundation.CFNumber;
import vavix.rococoa.corefoundation.CFRange;
import vavix.rococoa.corefoundation.CFString;
import vavix.rococoa.corefoundation.CFType;
import vavix.rococoa.iokit.IOKitLib;

import static org.gamepad4j.shared.Gamepad.EventType.DEVICE_ATTACHED;
import static vavix.rococoa.corefoundation.CFAllocator.kCFAllocatorDefault;
import static vavix.rococoa.corefoundation.CFLib.CFNumberType.kCFNumberSInt32Type;
import static vavix.rococoa.corefoundation.CFLib.kCFRunLoopDefaultMode;
import static vavix.rococoa.corefoundation.CFLib.kCFStringEncodingUTF8;
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
public class MacosxGamepad implements Gamepad {

    private static final Logger logger = Logger.getLogger(MacosxGamepad.class.getName());

    private static class mach_timebase_info {

        public long numer;
        public long denom;
    }

    private static class HIDGamepadAxis {

        public int /* IOHIDElementCookie */ cookie;
        public CFIndex logicalMin;
        public CFIndex logicalMax;
        public boolean hasNullState;
        public boolean isHatSwitch;
        public boolean isHatSwitchSecondAxis;
    }

    private static class HIDGamepadButton {

        public int /* IOHIDElementCookie */ cookie;
    }

    private static class DevicePrivate implements Device.Private {

        public Pointer /* IOHIDDeviceRef */ deviceRef;
        public List<HIDGamepadAxis> axisElements;
        public List<HIDGamepadButton> buttonElements;
    }

    private static class QueuedEvent {

        public int deviceID;
        public EventType eventType;
        public EventData eventData;
    }

    private Pointer /* IOHIDManagerRef */ hidManager = null;
    private Device[] devices = null;
    private int numDevices = 0;
    private int nextDeviceID = 0;

    private QueuedEvent[] inputEventQueue = null;
    private int inputEventQueueSize = 0;
    private int inputEventCount = 0;

    private QueuedEvent[] deviceEventQueue = null;
    private int deviceEventQueueSize = 0;
    private int deviceEventCount = 0;

    private static void hatValueToXY(int value, int range, int[] outX, int[] outY) {
        if (value == range) {
            outX[0] = outY[0] = 0;

        } else {
            if (value > 0 && value < range / 2) {
                outX[0] = 1;

            } else if (value > range / 2) {
                outX[0] = -1;

            } else {
                outX[0] = 0;
            }

            if (value > range / 4 * 3 || value < range / 4) {
                outY[0] = -1;

            } else if (value > range / 4 && value < range / 4 * 3) {
                outY[0] = 1;

            } else {
                outY[0] = 0;
            }
        }
    }

    private void queueInputEvent(int deviceID, EventType eventType, EventData eventData) {
        QueuedEvent queuedEvent = new QueuedEvent();
        queuedEvent.deviceID = deviceID;
        queuedEvent.eventType = eventType;
        queuedEvent.eventData = eventData;

        if (inputEventCount >= inputEventQueueSize) {
            inputEventQueueSize = inputEventQueueSize == 0 ? 1 : inputEventQueueSize * 2;
            inputEventQueue = new QueuedEvent[inputEventQueueSize];
        }
        inputEventQueue[inputEventCount++] = queuedEvent;
    }

    /** a cupcell for passing to callback */
    public static class DeviceContext extends Structure {

        public int id;

        public DeviceContext() {
        }

        public DeviceContext(Pointer p) {
            super(p);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("id");
        }

        static int idGenerator = 0;
        static Map<Integer, Device> map = new HashMap<>();
    }

    /** a callback when a device value changed */
    private void onDeviceValueChanged(Pointer context, int /* IOReturn */ result, Pointer sender, Pointer /* IOHIDValueRef */ value) {
        Device device = DeviceContext.map.get(new DeviceContext(context).id);
if (device == null) {
 logger.fine("device is null");
 return;
}
        DevicePrivate hidDeviceRecord = (DevicePrivate) device.privateData;
        Pointer /* IOHIDElementRef */ element = IOKitLib.INSTANCE.IOHIDValueGetElement(value);
        int /* IOHIDElementCookie */ cookie = IOKitLib.INSTANCE.IOHIDElementGetCookie(element);

        mach_timebase_info timebaseInfo = new mach_timebase_info();

        if (timebaseInfo.denom == 0) {
            long t = System.nanoTime();
            timebaseInfo.numer = TimeUnit.NANOSECONDS.toSeconds(t);
            timebaseInfo.denom = TimeUnit.NANOSECONDS.toMicros(t) - timebaseInfo.numer * 100;
        }

        for (int axisIndex = 0; axisIndex < device.numAxes; axisIndex++) {
            HIDGamepadAxis axisElement = hidDeviceRecord.axisElements.get(axisIndex);
            if (!axisElement.isHatSwitchSecondAxis &&
                    axisElement.cookie == cookie) {
                int integerValue;

                if (IOKitLib.INSTANCE.IOHIDValueGetLength(value).intValue() > 4) {
                    // Workaround for a strange crash that occurs with PS3 controller; was getting lengths of 39 (!)
                    continue;
                }
                integerValue = IOKitLib.INSTANCE.IOHIDValueGetIntegerValue(value).intValue();

                if (axisElement.isHatSwitch) {
                    int[] x = new int[1], y = new int[1];

                    // Fix for Saitek X52
                    if (!axisElement.hasNullState) {
                        if (integerValue < axisElement.logicalMin.intValue()) {
                            integerValue = axisElement.logicalMax.intValue() - axisElement.logicalMin.intValue() + 1;
                        } else {
                            integerValue--;
                        }
                    }

                    hatValueToXY(integerValue, axisElement.logicalMax.intValue() - axisElement.logicalMin.intValue() + 1, x, y);

                    if (x[0] != device.axisStates[axisIndex]) {
                        AxisEventData axisEvent = new AxisEventData(device,
                                (double) (IOKitLib.INSTANCE.IOHIDValueGetTimeStamp(value) * timebaseInfo.numer) / timebaseInfo.denom * 0.000000001,
                                axisIndex,
                                x[0]);
                        queueInputEvent(device.deviceID, EventType.AXIS_MOVED, axisEvent);

                        device.axisStates[axisIndex] = x[0];
                    }

                    if (y[0] != device.axisStates[axisIndex + 1]) {
                        AxisEventData axisEvent = new AxisEventData(device,
                                (double) (IOKitLib.INSTANCE.IOHIDValueGetTimeStamp(value) * timebaseInfo.numer) / timebaseInfo.denom * 0.000000001,
                                axisIndex + 1,
                                y[0]);
                        queueInputEvent(device.deviceID, EventType.AXIS_MOVED, axisEvent);

                        device.axisStates[axisIndex + 1] = y[0];
                    }

                } else {
                    if (integerValue < axisElement.logicalMin.intValue()) {
                        axisElement.logicalMin = CFIndex.of(integerValue);
                    }
                    if (integerValue > axisElement.logicalMax.intValue()) {
                        axisElement.logicalMax = CFIndex.of(integerValue);
                    }
                    float floatValue = (integerValue - axisElement.logicalMin.intValue()) /
                            (float) (axisElement.logicalMax.intValue() -
                                    axisElement.logicalMin.intValue()) * 2.0f - 1.0f;

                    AxisEventData axisEvent = new AxisEventData(device,
                            (double) (IOKitLib.INSTANCE.IOHIDValueGetTimeStamp(value) * timebaseInfo.numer) / timebaseInfo.denom * 0.000000001,
                            axisIndex,
                            floatValue);
                    queueInputEvent(device.deviceID, EventType.AXIS_MOVED, axisEvent);

                    device.axisStates[axisIndex] = floatValue;
                }

                return;
            }
        }

        for (int buttonIndex = 0; buttonIndex < device.numButtons; buttonIndex++) {
            if (hidDeviceRecord.buttonElements.get(buttonIndex).cookie == cookie) {
                boolean down = IOKitLib.INSTANCE.IOHIDValueGetIntegerValue(value).intValue() != 0;
                ButtonEventData buttonEvent = new ButtonEventData(device,
                        (double) (IOKitLib.INSTANCE.IOHIDValueGetTimeStamp(value) * timebaseInfo.numer) / timebaseInfo.denom * 0.000000001,
                        buttonIndex,
                        down);
                queueInputEvent(device.deviceID, down ? EventType.BUTTON_DOWN : EventType.BUTTON_UP, buttonEvent);

                device.buttonStates[buttonIndex] = down;

                return;
            }
        }
    }

    private static int getIntProperty(Pointer /* IOHIDDeviceRef */ deviceRef, CFString key) {
        CFType typeRef = IOKitLib.INSTANCE.IOHIDDeviceGetProperty(deviceRef, key);
        if (typeRef == null || !CFLib.INSTANCE.CFGetTypeID(typeRef).equals(CFLib.INSTANCE.CFNumberGetTypeID())) {
            return 0;
        }

        IntByReference value = new IntByReference();
        CFLib.INSTANCE.CFNumberGetValue(typeRef.asNumber(), kCFNumberSInt32Type, value);
        return value.getValue();
    }

    /** */
    private void onDeviceMatched(Pointer context, int /* IOReturn */ result, Pointer sender, Pointer /* IOHIDDeviceRef */ device) {
logger.finer("CHECKPOINT-4.S");
        Device deviceRecord = new Device();
        deviceRecord.deviceID = nextDeviceID++;
        deviceRecord.vendorID = getIntProperty(device, CFSTR(kIOHIDVendorIDKey));
        deviceRecord.productID = getIntProperty(device, CFSTR(kIOHIDProductIDKey));
        deviceRecord.numAxes = 0;
        deviceRecord.numButtons = 0;

        devices = new Device[numDevices + 1];
        devices[numDevices++] = deviceRecord;

        DevicePrivate hidDeviceRecord = new DevicePrivate();
        hidDeviceRecord.deviceRef = device;
        hidDeviceRecord.axisElements = new ArrayList<>();
        hidDeviceRecord.buttonElements = new ArrayList<>();
        deviceRecord.privateData = hidDeviceRecord;

        CFString cfProductName = IOKitLib.INSTANCE.IOHIDDeviceGetProperty(device, CFSTR(kIOHIDProductKey)).asString();
        String description;
        if (cfProductName == null || !CFLib.INSTANCE.CFGetTypeID(cfProductName).equals(CFLib.INSTANCE.CFStringGetTypeID())) {
            description = "[Unknown]";
        } else {
            NativeLongByReference length = new NativeLongByReference();

            CFLib.INSTANCE.CFStringGetBytes(cfProductName, new CFRange(0, CFLib.INSTANCE.CFStringGetLength(cfProductName).intValue()).newByValue(), kCFStringEncodingUTF8, (byte) '?', false, null, CFIndex.of(100), length);
            byte[] bytes = new byte[length.getValue().intValue() + 1];
            CFLib.INSTANCE.CFStringGetBytes(cfProductName, new CFRange(0, CFLib.INSTANCE.CFStringGetLength(cfProductName).intValue()).newByValue(), kCFStringEncodingUTF8, (byte) '?', false, bytes, CFIndex.of(length.getValue().intValue() + 1), null);
            description = new String(bytes, 0, length.getValue().intValue());
        }
        deviceRecord.description = description;
logger.finer("CHECKPOINT-4.1: " + description);

        CFArray elements = IOKitLib.INSTANCE.IOHIDDeviceCopyMatchingElements(device, null, kIOHIDOptionsTypeNone);
        for (int elementIndex = 0; elementIndex < CFLib.INSTANCE.CFArrayGetCount(elements).intValue(); elementIndex++) {
            Pointer /* IOHIDElementRef */ element = CFLib.INSTANCE.CFArrayGetValueAtIndex(elements, elementIndex).getPointer();
            int /* IOHIDElementType */ type = IOKitLib.INSTANCE.IOHIDElementGetType(element);

            // All of the axis elements I've ever detected have been kIOHIDElementTypeInput_Misc. kIOHIDElementTypeInput_Axis is only included for good faith...
            if (type == kIOHIDElementTypeInput_Misc ||
                    type == kIOHIDElementTypeInput_Axis) {

                HIDGamepadAxis axisElement = new HIDGamepadAxis();
                axisElement.cookie = IOKitLib.INSTANCE.IOHIDElementGetCookie(element);
                axisElement.logicalMin = IOKitLib.INSTANCE.IOHIDElementGetLogicalMin(element);
                axisElement.logicalMax = IOKitLib.INSTANCE.IOHIDElementGetLogicalMax(element);
                axisElement.hasNullState = IOKitLib.INSTANCE.IOHIDElementHasNullState(element);
                axisElement.isHatSwitch = IOKitLib.INSTANCE.IOHIDElementGetUsage(element) == kHIDUsage_GD_Hatswitch;
                axisElement.isHatSwitchSecondAxis = false;
                hidDeviceRecord.axisElements.add(axisElement);
                deviceRecord.numAxes++;

                if (hidDeviceRecord.axisElements.get(deviceRecord.numAxes - 1).isHatSwitch) {
                    axisElement = new HIDGamepadAxis();
                    axisElement.isHatSwitchSecondAxis = true;
                    hidDeviceRecord.axisElements.add(axisElement);
                    deviceRecord.numAxes++;
                }

            } else if (type == kIOHIDElementTypeInput_Button) {
                HIDGamepadButton buttonElement = new HIDGamepadButton();
                buttonElement.cookie = IOKitLib.INSTANCE.IOHIDElementGetCookie(element);
                hidDeviceRecord.buttonElements.add(buttonElement);
                deviceRecord.numButtons++;
            }
        }
        CFLib.INSTANCE.CFRelease(elements);

        deviceRecord.axisStates = new float[deviceRecord.numAxes];
        deviceRecord.buttonStates = new boolean[deviceRecord.numButtons];

        DeviceContext deviceContext = new DeviceContext();
        deviceContext.id = DeviceContext.idGenerator++;
        DeviceContext.map.put(deviceContext.id, deviceRecord);
        IOKitLib.INSTANCE.IOHIDDeviceRegisterInputValueCallback(device, this::onDeviceValueChanged, deviceContext.getPointer());

        QueuedEvent queuedEvent = new QueuedEvent();
        queuedEvent.deviceID = deviceRecord.deviceID;
        queuedEvent.eventType = DEVICE_ATTACHED;
        queuedEvent.eventData = deviceRecord;

        if (deviceEventCount >= deviceEventQueueSize) {
            deviceEventQueueSize = deviceEventQueueSize == 0 ? 1 : deviceEventQueueSize * 2;
            deviceEventQueue = new QueuedEvent[deviceEventQueueSize];
        }
        deviceEventQueue[deviceEventCount++] = queuedEvent;
logger.finer("CHECKPOINT-4.E");
    }

    /** */
    private void disposeDevice(Device deviceRecord) {
        int inputEventIndex, deviceEventIndex;

        IOKitLib.INSTANCE.IOHIDDeviceRegisterInputValueCallback(((DevicePrivate) deviceRecord.privateData).deviceRef, null, null);

        for (inputEventIndex = 0; inputEventIndex < inputEventCount; inputEventIndex++) {
            if (inputEventQueue[inputEventIndex].deviceID == deviceRecord.deviceID) {
                int inputEventIndex2;

                inputEventCount--;
                for (inputEventIndex2 = inputEventIndex; inputEventIndex2 < inputEventCount; inputEventIndex2++) {
                    inputEventQueue[inputEventIndex2] = inputEventQueue[inputEventIndex2 + 1];
                }
                inputEventIndex--;
            }
        }

        for (deviceEventIndex = 0; deviceEventIndex < deviceEventCount; deviceEventIndex++) {
            if (deviceEventQueue[deviceEventIndex].deviceID == deviceRecord.deviceID) {
                int deviceEventIndex2;

                deviceEventCount--;
                for (deviceEventIndex2 = deviceEventIndex; deviceEventIndex2 < deviceEventCount; deviceEventIndex2++) {
                    deviceEventQueue[deviceEventIndex2] = deviceEventQueue[deviceEventIndex2 + 1];
                }
                deviceEventIndex--;
            }
        }
    }

    /** */
    private void onDeviceRemoved(Pointer context, int /* IOReturn */ result, Pointer sender, Pointer /* IOHIDDeviceRef */ device) {
logger.fine("onDeviceRemoved: " + device);
        for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
            if (((DevicePrivate) devices[deviceIndex].privateData).deviceRef == device) {
                fireDeviceRemove(devices[deviceIndex]);

                disposeDevice(devices[deviceIndex]);
                numDevices--;
                for (; deviceIndex < numDevices; deviceIndex++) {
                    devices[deviceIndex] = devices[deviceIndex + 1];
                }
                return;
            }
        }
    }

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    private CountDownLatch cdl = new CountDownLatch(1);

    @Override
    public void init() {
        executorService.submit(this::initInternal);
        try { cdl.await(); } catch (InterruptedException ignore) {}
    }

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
            dictionaries[0] = CFLib.INSTANCE.CFDictionaryCreate(kCFAllocatorDefault, keys, values, new NativeLong(2), kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);
            CFLib.INSTANCE.CFRelease(values[0]);
            CFLib.INSTANCE.CFRelease(values[1]);

            value.setValue(kHIDPage_GenericDesktop);
            values[0] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            value.setValue(kHIDUsage_GD_GamePad);
            values[1] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            dictionaries[1] = CFLib.INSTANCE.CFDictionaryCreate(kCFAllocatorDefault, keys, values, new NativeLong(2), kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);
            CFLib.INSTANCE.CFRelease(values[0]);
            CFLib.INSTANCE.CFRelease(values[1]);

            value.setValue(kHIDPage_GenericDesktop);
            values[0] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            value.setValue(kHIDUsage_GD_MultiAxisController);
            values[1] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
            dictionaries[2] = CFLib.INSTANCE.CFDictionaryCreate(kCFAllocatorDefault, keys, values, new NativeLong(2), kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);
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

            IOKitLib.INSTANCE.IOHIDManagerSetDeviceMatchingMultiple(hidManager, array);
            CFLib.INSTANCE.CFRelease(array);
            IOKitLib.INSTANCE.IOHIDManagerRegisterDeviceMatchingCallback(hidManager, this::onDeviceMatched, null);
            IOKitLib.INSTANCE.IOHIDManagerRegisterDeviceRemovalCallback(hidManager, this::onDeviceRemoved, null);

            cdl.countDown();
logger.fine("CHECKPOINT-0.8: bye...");

            CFLib.INSTANCE.CFRunLoopRun();
logger.fine("CHECKPOINT-0.E");
        }
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
logger.fine("i am a mac, bye");

        if (hidManager != null) {
            IOKitLib.INSTANCE.IOHIDManagerUnscheduleFromRunLoop(hidManager, CFLib.INSTANCE.CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
            IOKitLib.INSTANCE.IOHIDManagerClose(hidManager, 0);
            CFLib.INSTANCE.CFRelease(hidManager);
            hidManager = null;

            for (int deviceIndex = 0; deviceIndex < numDevices; deviceIndex++) {
                disposeDevice(devices[deviceIndex]);
            }
            devices = null;
            numDevices = 0;
        }
    }

    @Override
    public int numDevices() {
logger.finer("CHECKPOINT-3.0");
        return numDevices;
    }

    @Override
    public Device deviceAtIndex(int deviceIndex) {
        if (deviceIndex >= numDevices) {
logger.warning(deviceIndex + " >= " + numDevices);
            return null;
        }
        return devices[deviceIndex];
    }

    /**  */
    private final List<GamepadListener> listeners = new ArrayList<>();

    /**  */
    protected void fireDeviceAttach(Device device) {
        listeners.forEach(l -> l.deviceAttach(device));
    }

    /**  */
    protected void fireDeviceRemove(Device device) {
        listeners.forEach(l -> l.deviceRemove(device));
    }

    /**  */
    protected void fireButtonDown(Device device, int buttonID, double timestamp) {
        listeners.forEach(l -> l.buttonDown(device, buttonID, timestamp));
    }

    /**  */
    protected void fireBbuttonUp(Device device, int buttonID, double timestamp) {
        listeners.forEach(l -> l.buttonUp(device, buttonID, timestamp));
    }

    /**  */
    protected void fireAxisMove(Device device, int axisID, float value, double timestamp) {
        listeners.forEach(l -> l.axisMove(device, axisID, value, timestamp));
    }

    /**  */
    private void processQueuedEvent(QueuedEvent event) {
if (event == null) {
 logger.finer("event is null");
 return;
}
        switch (event.eventType) {
        case DEVICE_ATTACHED: {
            fireDeviceAttach((Device) event.eventData);
            break;
        }
        case DEVICE_REMOVED: {
            fireDeviceRemove((Device) event.eventData);
            break;
        }
        case BUTTON_DOWN: {
            ButtonEventData buttonEvent = (ButtonEventData) event.eventData;
            fireButtonDown(buttonEvent.device, buttonEvent.buttonID, buttonEvent.timestamp);
            break;
        }
        case BUTTON_UP: {
            ButtonEventData buttonEvent = (ButtonEventData) event.eventData;
            fireBbuttonUp(buttonEvent.device, buttonEvent.buttonID, buttonEvent.timestamp);
            break;
        }
        case AXIS_MOVED: {
            AxisEventData axisEvent = (AxisEventData) event.eventData;
            fireAxisMove(axisEvent.device, axisEvent.axisID, axisEvent.value, axisEvent.timestamp);
            break;
        }
        }
    }

    @Override
    public void detectDevices() {
        if (hidManager == null) {
logger.fine("detectDevices: hidManager is null");
            return;
        }
logger.finer("detectDevices: deviceEventCount: " + deviceEventCount);
        for (int eventIndex = 0; eventIndex < deviceEventCount; eventIndex++) {
            processQueuedEvent(deviceEventQueue[eventIndex]);
        }
        deviceEventCount = 0;
    }

    private volatile boolean inProcessEvents;

    @Override
    public void processEvents() {
        int eventIndex;

logger.finer("CHECKPOINT-7.0");
        if (hidManager == null || inProcessEvents) {
            return;
        }

logger.finer("CHECKPOINT-7.1");
        inProcessEvents = true;
        for (eventIndex = 0; eventIndex < inputEventCount; eventIndex++) {
            processQueuedEvent(inputEventQueue[eventIndex]);
        }
        inputEventCount = 0;
        inProcessEvents = false;
logger.finer("CHECKPOINT-7.2");
    }
}