/*
 * @Copyright: Marcel Schoen, Switzerland, 2013, All Rights Reserved.
 */

package org.gamepad4j.desktop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.gamepad4j.base.BaseAxis;
import org.gamepad4j.base.BaseButton;
import org.gamepad4j.util.PlatformUtil;


/**
 * JNI wrapper for Gamepad library by Alex Diener.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class GamepadJniWrapper {

    static final Logger logger = Logger.getLogger(GamepadJniWrapper.class.getName());

    /**
     * Array which temporarily holds controller ID information.
     */
    private static int[] idArray = new int[3];

    /**
     * Array which temporarily holds controller button state information.
     */
    private static boolean[] buttonArray = new boolean[64];

    /**
     * Array which temporarily holds controller axis state information.
     */
    private static float[] axisArray = new float[64];

    /**
     * Prepares the native library for usage. This method
     * must be invoked before anything else is used.
     */
    public GamepadJniWrapper() {
        // Since there is no really portable way of detecting if a Java
        // program is currently running on a 32 or 64 bit system, we try
        // to load the 64 bit library first, and if that fails, fall back
        // to the 32 bit library.
        File libraryFile = unpackLibrary(true);
        if (!loadLibrary(libraryFile)) {
            libraryFile = unpackLibrary(false);
            if (!loadLibrary(libraryFile)) {
                logger.fine("GamepadJniWrapper.<init>: *** FAILED TO LOAD EITHER 32 OR 64 BIT LIBRARY ***");
            }
        }
    }

    /**
     * Unpacks the JNI library to a temporary directory and sets the
     * "java.library.path" to that directory.
     *
     * @param use64bit True, if the 64 bit version of the library should be used.
     */
    private static File unpackLibrary(boolean use64bit) {
        try {
            File temp = File.createTempFile("nativelib", "");
            temp.delete();
            temp.mkdir();
            logger.fine("GamepadJniWrapper.unpackLibrary(): Using temporary directory: " + temp.getAbsolutePath());
            logger.fine("GamepadJniWrapper.unpackLibrary(): exists: " + temp.exists());
            logger.fine("GamepadJniWrapper.unpackLibrary(): Is directory: " + temp.isDirectory());
            if (temp.exists() && temp.isDirectory()) {
                String subPath = "/32bit/";
                if (use64bit) {
                    subPath = "/64bit/";
                }
                String library = PlatformUtil.getPlatform().getLibraryName();
                String path = "/native/" + PlatformUtil.getPlatform().name()
                        + subPath;
                String resourceName = path + library;
                File libraryFile = new File(temp, library);
                logger.fine("GamepadJniWrapper.unpackLibrary(): Extracting library resource: " + resourceName);
                logger.fine("GamepadJniWrapper.unpackLibrary(): Extracting to local file: " + libraryFile.getCanonicalPath());
                InputStream in = GamepadJniWrapper.class.getResourceAsStream(resourceName);
                if (in != null) {
                    FileOutputStream out = new FileOutputStream(libraryFile);
                    byte[] buffer = new byte[64000];
                    int num = -1;
                    while ((num = in.read(buffer)) > 0) {
                        out.write(buffer, 0, num);
                    }
                    out.flush();
                    out.close();
                    in.close();
                    logger.fine("GamepadJniWrapper.unpackLibrary(): Library successfully extracted.");
                } else {
                    throw new IOException("Resource not found in classpath: " + resourceName);
                }
                return libraryFile;
            }
        } catch (IOException e) {
            logger.severe("GamepadJniWrapper.unpackLibrary(): *** FAILED TO EXTRACT LIBRARY ***");
            // TODO Auto-generated catch block
//			e.printStackTrace();
        }
        return null;
    }

    /**
     * Tries to load the currently unpacked library.
     *
     * @return True if the library could be loaded without errors.
     */
    private static boolean loadLibrary(File libraryFile) {
        try {
            if (libraryFile == null) {
                throw new IllegalArgumentException("There must be a native library.");
            }
            logger.fine("GamepadJniWrapper.loadLibrary(): Trying to load library: " + libraryFile.getCanonicalPath());
            System.load(libraryFile.getAbsolutePath());
            logger.fine("GamepadJniWrapper.loadLibrary(): Gamepad4j native library successfully loaded.");
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Updates the status information on the given controller holder object.
     *
     * @param controller
     */
    public void updateControllerStatus(DesktopController controller) {
        natGetControllerButtonStates(controller.getIndex(), buttonArray);
        int numberOfButtons = natGetNumberOfButtons(controller.getIndex());
        for (int i = 0; i < numberOfButtons; i++) {
            BaseButton button = (BaseButton) controller.getButton(i);
            if (button != null) {
                boolean isPressed = natGetControllerButtonState(controller.getIndex(), button.getCode()) == 1;
                button.setPressed(isPressed);
            }
        }

        natGetControllerAxesStates(controller.getIndex(), axisArray);
        BaseAxis[] axes = (BaseAxis[]) controller.getAxes();
        for (int i = 0; i < axes.length; i++) {
            if (axes[i] != null) {
                float axisState = natGetControllerAxisState(controller.getIndex(), i);
                axes[i].setValue(axisState);
            }
        }
    }

    /**
     * Updates the ID information on the given controller holder object.
     *
     * @param controller
     */
    public void updateControllerInfo(DesktopController controller) {
        String description = natGetControllerDescription(controller.getIndex());
        controller.setDescription(description);
        natGetControllerIDs(controller.getIndex(), idArray);
        controller.setDeviceID(idArray[0]);
        controller.setVendorID(idArray[1]);
        controller.setProductID(idArray[2]);
        controller.initializeMapping();

        int numberOfButtons = natGetNumberOfButtons(controller.getIndex());
        controller.createButtons(numberOfButtons);

        int numberOfAxes = natGetNumberOfAxes(controller.getIndex());
        controller.createAxes(numberOfAxes);
    }

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Initializes the JNI wrapper.
     */
    public void initialize() {
        logger.fine("GamepadJniWrapper.initialize(): initialize JNI wrapper...");
        if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
            // cause natInit uses CFRunLoopRun() and never returns
            executorService.submit(this::natInit);
        } else {
            natInit();
        }
        logger.fine("GamepadJniWrapper.initialize(): detect pads...");
        natDetectPads();
        logger.fine("GamepadJniWrapper.initialize(): done.");
    }

    /** */
    public void close() {
        if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
            executorService.shutdown();
        }
    }

    /**
     * Initializes the native gamepad library.
     */
    public native void natInit();

    /**
     * Releases all resources held by the native gamepad library.
     */
    public native void natRelease();

    /**
     * Returns the device ID of the given pad.
     *
     * @param index The code of the pad.
     * @return The device ID (or -1 if the code was invalid).
     */
    public native int natGetDeviceID(int index);

    /**
     * Returns the number of gamepads currently connected.
     *
     * @return The number of pads.
     */
    public native int natGetNumberOfPads();

    /**
     * Returns the number of analog axes on the given pad.
     *
     * @param index The code of the pad.
     * @return The number of axes.
     */
    public native int natGetNumberOfAxes(int index);

    /**
     * Returns the number of digital buttons on the given pad.
     * NOTE: Analog trigger buttons are handled like axes, not
     * like buttons.
     *
     * @param index The code of the pad.
     * @return The number of buttons.
     */
    public native int natGetNumberOfButtons(int index);

    /**
     * Returns the ID of the pad with the given code.
     *
     * @param index The code of the pad.
     * @return The ID (if such a pad exists).
     */
    public native int natGetIdOfPad(int index);

    /**
     * Forces detection of connected gamepads.
     */
    public native void natDetectPads();

    /**
     * Returns the description text for a given pad.
     *
     * @param index The code of the pad.
     * @return The description text (or null).
     */
    public native String natGetControllerDescription(int index);

    /**
     * Updates the various IDs for a given pad in the given array.
     *
     * @param index   The code of the pad.
     * @param idArray The array which gets the updated values, where [0] is
     *                the device ID, [1] the vendor ID and [2] the product ID.
     */
    public native void natGetControllerIDs(int index, int[] idArray);

    /**
     * Updates the state of all digital buttons on the given controller.
     *
     * @param index      The code of the pad.
     * @param stateArray An array where every entry represents a button, and if it's
     *                   TRUE, the button is pressed.
     */
    public native void natGetControllerButtonStates(int index, boolean[] stateArray);

    /**
     * Updates the state of all analog axes on the given controller.
     *
     * @param index     The code of the pad.
     * @param axesArray An array where every entry represents an axis.
     */
    public native void natGetControllerAxesStates(int index, float[] axesArray);

    /**
     * Polls the state of a certain button of a certain controller.
     *
     * @param index       The code of the controller.
     * @param buttonIndex The code of the button.
     */
    public native int natGetControllerButtonState(int index, int buttonIndex);

    /**
     * Polls the state of a certain axis of a certain controller.
     *
     * @param index     The code of the controller.
     * @param axisIndex The code of the axis.
     */
    public native float natGetControllerAxisState(int index, int axisIndex);
}
