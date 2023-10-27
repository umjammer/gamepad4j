/*
 * $Header:  $
 *
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j.util;

import java.lang.reflect.Field;
import java.util.logging.Logger;


/**
 * OS-/Platform-related utility functionality.
 *
 * @author Marcel Schoen
 */
public class PlatformUtil {

    static final Logger logger = Logger.getLogger(PlatformUtil.class.getName());

    /** Runtime OS detection. */
    private static String OS = System.getProperty("os.name").toLowerCase();

    /** Holds value of OUYA runtime detection. */
    private static Boolean isOuya = null;

    /**
     * Returns the type of platform the JVM is
     * currently running on.
     *
     * @return The platform type.
     */
    public static Platform getPlatform() {
        if (isOuya()) {
            return Platform.ouya;
        }
        if (isMac()) {
            return Platform.macos;
        }
        if (isWindows()) {
            return Platform.windows;
        }
        if (isLinux()) {
            return Platform.linux;
        }
        return Platform.unknown;
    }

    /**
     * Check if game runs on OUYA.
     *
     * @return True if it's running on OUYA.
     */
    public static boolean isOuya() {
        if (isOuya == null) {
            try {
                logger.finer("checking ouya...");
                Class<?> buildClass = Class.forName("android.os.Build");
                logger.fine("Create class 'android.os.Build'");
                Field deviceField = buildClass.getDeclaredField("DEVICE");
                Object device = deviceField.get(null);
                logger.fine("Device Type: '" + device + "'");
                String deviceName = device.toString().trim().toLowerCase();
                isOuya = deviceName.contains("ouya");
                logger.fine("is OUYA: " + isOuya);
            } catch (Exception e) {
                logger.finer("checking ouya: NO");
                return false;
            }
        }
        return isOuya;
    }

    /**
     * Check if game runs on Win32.
     *
     * @return True if it's running on Windows.
     */
    public static boolean isWindows() {
        return (OS.contains("win"));
    }

    /**
     * Check if game runs on MacOS.
     *
     * @return True if it runs on a Mac.
     */
    public static boolean isMac() {
        return (OS.contains("mac"));
    }

    /**
     * Check if game runs on Unix / Linux.
     *
     * @return True if it runs on a Unix system.
     */
    public static boolean isLinux() {
        return (OS.contains("nix") || OS.contains("nux") || OS.indexOf("aix") > 0);
    }
}
