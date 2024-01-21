/*
 * Copyright (c) 2023 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vavi.util.Debug;
import vavix.rococoa.corefoundation.CFArray;
import vavix.rococoa.corefoundation.CFDictionary;
import vavix.rococoa.corefoundation.CFIndex;
import vavix.rococoa.corefoundation.CFLib;
import vavix.rococoa.corefoundation.CFNumber;
import vavix.rococoa.corefoundation.CFString;
import vavix.rococoa.iokit.IOKitLib;

import static vavix.rococoa.corefoundation.CFAllocator.kCFAllocatorDefault;
import static vavix.rococoa.corefoundation.CFLib.CFNumberType.kCFNumberSInt32Type;
import static vavix.rococoa.corefoundation.CFLib.kCFCopyStringDictionaryKeyCallBacks;
import static vavix.rococoa.corefoundation.CFLib.kCFRunLoopDefaultMode;
import static vavix.rococoa.corefoundation.CFLib.kCFTypeArrayCallBacks;
import static vavix.rococoa.corefoundation.CFLib.kCFTypeDictionaryKeyCallBacks;
import static vavix.rococoa.corefoundation.CFLib.kCFTypeDictionaryValueCallBacks;
import static vavix.rococoa.corefoundation.CFString.CFSTR;
import static vavix.rococoa.iokit.IOKitLib.kHIDPage_GenericDesktop;
import static vavix.rococoa.iokit.IOKitLib.kHIDUsage_GD_GamePad;
import static vavix.rococoa.iokit.IOKitLib.kHIDUsage_GD_Joystick;
import static vavix.rococoa.iokit.IOKitLib.kHIDUsage_GD_MultiAxisController;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDDeviceUsageKey;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDDeviceUsagePageKey;
import static vavix.rococoa.iokit.IOKitLib.kIOHIDOptionsTypeNone;


/**
 * TestJna.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2023-10-28 nsano initial version <br>
 * @see "https://github.com/born2snipe/gamepad4j/blob/master/gamepad4j-desktop/src/main/c/macos/Gamepad_macosx.c"
 */
public class TestJna {

    @Test
    void test1() throws Exception {
        Pointer hidManager = IOKitLib.INSTANCE.IOHIDManagerCreate(kCFAllocatorDefault, kIOHIDOptionsTypeNone);
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

Debug.println(dictionaries[0].toMap());

        value.setValue(kHIDPage_GenericDesktop);
        values[0] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        value.setValue(kHIDUsage_GD_GamePad);
        values[1] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        dictionaries[1] = CFLib.INSTANCE.CFDictionaryCreate(kCFAllocatorDefault, keys, values, CFIndex.of(2), kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);
        CFLib.INSTANCE.CFRelease(values[0]);
        CFLib.INSTANCE.CFRelease(values[1]);

Debug.println(dictionaries[1].toMap());

        value.setValue(kHIDPage_GenericDesktop);
        values[0] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        value.setValue(kHIDUsage_GD_MultiAxisController);
        values[1] = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        dictionaries[2] = CFLib.INSTANCE.CFDictionaryCreate(kCFAllocatorDefault, keys, values, CFIndex.of(2), kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);
        CFLib.INSTANCE.CFRelease(values[0]);
        CFLib.INSTANCE.CFRelease(values[1]);

Debug.println(dictionaries[2].toMap());

        Pointer[] pp = new Pointer[dictionaries.length];
        for (int i = 0; i < dictionaries.length; i++) {
            pp[i] = dictionaries[i].getPointer();
        }
        CFArray array = CFLib.INSTANCE.CFArrayCreate(kCFAllocatorDefault, pp, CFIndex.of(pp.length), kCFTypeArrayCallBacks);
        for (CFDictionary dictionary : dictionaries) {
            CFLib.INSTANCE.CFRelease(dictionary);
        }

for (int i = 0; i < array.size(); i++) {
 Debug.println("array[" + i + "]: " + array.getValue(i).asDict().toMap());
}

        IOKitLib.INSTANCE.IOHIDManagerSetDeviceMatchingMultiple(hidManager, array);
        CFLib.INSTANCE.CFRelease(array);
    }

    @Test
    @DisplayName("doesn't work as expected")
    void test2() throws Exception {
        Pointer hidManager = IOKitLib.INSTANCE.IOHIDManagerCreate(kCFAllocatorDefault, kIOHIDOptionsTypeNone);
        IOKitLib.INSTANCE.IOHIDManagerOpen(hidManager, kIOHIDOptionsTypeNone);
        IOKitLib.INSTANCE.IOHIDManagerScheduleWithRunLoop(hidManager, CFLib.INSTANCE.CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);

        CFDictionary dictionary = CFLib.INSTANCE.CFDictionaryCreateMutable(kCFAllocatorDefault, CFIndex.of(kIOHIDOptionsTypeNone), kCFCopyStringDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks);

        CFString keys0 = CFSTR(kIOHIDDeviceUsagePageKey);
        CFString keys1 = CFSTR(kIOHIDDeviceUsageKey);

        IntByReference value = new IntByReference();
        value.setValue(kHIDPage_GenericDesktop);
        CFNumber values0 = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        value.setValue(kHIDUsage_GD_Joystick);
        CFNumber values1 = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        CFLib.INSTANCE.CFDictionaryAddValue(dictionary, keys0, values0);
        CFLib.INSTANCE.CFDictionaryAddValue(dictionary, keys1, values1);
        CFLib.INSTANCE.CFRelease(values0);
        CFLib.INSTANCE.CFRelease(values1);

Debug.println(dictionary.toMap());

        value.setValue(kHIDPage_GenericDesktop);
        values0 = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        value.setValue(kHIDUsage_GD_GamePad);
        values1 = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        CFLib.INSTANCE.CFDictionaryAddValue(dictionary, keys0, values0);
        CFLib.INSTANCE.CFDictionaryAddValue(dictionary, keys1, values1);
        CFLib.INSTANCE.CFRelease(values0);
        CFLib.INSTANCE.CFRelease(values1);

Debug.println(dictionary.toMap());

        value.setValue(kHIDPage_GenericDesktop);
        values0 = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        value.setValue(kHIDUsage_GD_MultiAxisController);
        values1 = CFLib.INSTANCE.CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, value);
        CFLib.INSTANCE.CFDictionaryAddValue(dictionary, keys0, values0);
        CFLib.INSTANCE.CFDictionaryAddValue(dictionary, keys1, values1);
        CFLib.INSTANCE.CFRelease(values0);
        CFLib.INSTANCE.CFRelease(values1);

Debug.println(dictionary.toMap());

        Pointer[] pp = new Pointer[] { dictionary.getPointer() };
        CFArray array = CFLib.INSTANCE.CFArrayCreate(kCFAllocatorDefault, pp, CFIndex.of(pp.length), kCFTypeArrayCallBacks);
        CFLib.INSTANCE.CFRelease(dictionary);

for (int i = 0; i < array.size(); i++) {
 Debug.println("array[" + i + "]: " + array.getValue(i).asDict().toMap());
}

        IOKitLib.INSTANCE.IOHIDManagerSetDeviceMatchingMultiple(hidManager, array);
        CFLib.INSTANCE.CFRelease(array);
    }
}
