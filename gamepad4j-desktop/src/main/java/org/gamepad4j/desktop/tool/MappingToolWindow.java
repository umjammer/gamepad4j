/*
 * @Copyright: Marcel Schoen, Switzerland, 2014, All Rights Reserved.
 */

package org.gamepad4j.desktop.tool;

import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import javax.swing.JFrame;

import org.gamepad4j.Controllers;
import org.gamepad4j.IController;
import org.gamepad4j.IControllersListener;


/**
 * Shows the mapping tool window.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class MappingToolWindow extends JFrame {

    private static final Logger logger = Logger.getLogger(MappingToolWindow.class.getName());

    /** Stores ImageIcon instances for various pads. */
    public static Map<Long, ImageIcon> padImageMap = new HashMap<>();

    private final Controllers environment;

    public MappingToolWindow() {

        // Initial gamepad detection
        environment = Controllers.instance();

        setTitle("Gamepad4J Test Program");
        setSize(400, 500);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                GamepadCheck.running = false;
                e.getWindow().dispose();
            }
        });
        getContentPane().setLayout(new FlowLayout());
        try {
            InputStream in = MappingToolWindow.class.getResourceAsStream("/image-filenames.properties");
            Properties filenameProps = new Properties();
            filenameProps.load(in);

            // Store controller-specific images
            Enumeration<Object> keys = filenameProps.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                String imageFilename = filenameProps.getProperty(key);
                String id = key.substring(key.indexOf("-") + 3, key.indexOf("_"));
                int vendorID = Integer.parseInt(id, 16);
                id = key.substring(key.indexOf("_") + 3);
                int productID = Integer.parseInt(id, 16);
                long deviceTypeIdentifier = ((long) vendorID << 16) + productID;
                logger.fine(">> load image: " + imageFilename);
                InputStream input = MappingToolWindow.class.getResourceAsStream(imageFilename);
                ImageInputStream imageIn = ImageIO.createImageInputStream(input);
                BufferedImage image = ImageIO.read(imageIn);
                ImageIcon padImage = new ImageIcon(image);
                padImageMap.put(deviceTypeIdentifier, padImage);
            }

            // Store default image
            InputStream input = MappingToolWindow.class.getResourceAsStream("/controller_xbox_360.png");
            ImageInputStream imageIn = ImageIO.createImageInputStream(input);
            BufferedImage image = ImageIO.read(imageIn);
            ImageIcon padImage = new ImageIcon(image);
            padImageMap.put(0L, padImage);

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        environment.addListener(new IControllersListener() {
            @Override public void connected(IController controller) {
                System.out.println(">> Gamepad connected.");
                updateWindow();
            }

            @Override public void disConnected(IController controller) {
                System.out.println(">> Gamepad disconnected.");
                updateWindow();
            }
        });
    }

    private void updateWindow() {
        System.out.println("--- controller change, update window ---");
        getContentPane().removeAll();
        IController[] controllers = environment.getControllers();
        for (IController controller : controllers) {
            getContentPane().add(new ControllerPanel(controller));
        }
        pack();
    }
}
