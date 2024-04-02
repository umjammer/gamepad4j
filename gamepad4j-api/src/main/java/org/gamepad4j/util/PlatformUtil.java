/*
 * $Header:  $
 *
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j.util;


/**
 * OS-/Platform-related utility functionality.
 *
 * @author Marcel Schoen
 */
public class PlatformUtil {

    /** Runtime OS detection. */
    private static final String OS = System.getProperty("os.name").toLowerCase();

    /** Holds value of OUYA runtime detection. */
    @Deprecated
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
