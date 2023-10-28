/*
 * $Header:  $
 *
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j.util;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import com.jogamp.common.os.Platform;


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
    public static String getPlatform() {
        if (isMac()) {
            return "macos";
        } else if (isWindows()) {
            return "windows";
        } else  if (isLinux()) {
            return "linux";
        } else {
            return "unknown";
        }
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
