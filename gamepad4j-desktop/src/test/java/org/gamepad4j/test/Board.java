package org.gamepad4j.test;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.logging.Logger;
import javax.swing.JPanel;

import org.gamepad4j.ButtonID;
import org.gamepad4j.Controllers;
import org.gamepad4j.DpadDirection;
import org.gamepad4j.IAxis;
import org.gamepad4j.IButton;
import org.gamepad4j.IController;
import org.gamepad4j.IControllerListener;
import org.gamepad4j.IStick;
import org.gamepad4j.StickID;
import org.gamepad4j.StickPosition;
import vavi.util.Debug;


/**
 * The code of this class was taken from this Java game programming tutorial:
 * <p>
 * <a href="http://zetcode.com/tutorials/javagamestutorial/movingsprites/">...</a>
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class Board extends JPanel {

    static final Logger logger = Logger.getLogger(Board.class.getName());

    private final Craft craft;

    public Board(int vendorId, int productId) {

        // Initialize the API
        logger.fine("Initialize controllers...");
        Controllers environment = Controllers.instance();

        setFocusable(true);
        setBackground(Color.BLACK);
        setDoubleBuffered(true);

        craft = new Craft();

        while (environment.getControllers().length == 0) {
            Thread.yield();
        }

        IController controller = environment.getController(vendorId, productId);
logger.info("controller: " + controller);

        controller.addListener(new IControllerListener() {

            @Override
            public void buttonDown(IButton button, ButtonID buttonID) {
logger.finest("buttonDown: " + controller + ", " + button + ", " + buttonID);
            }

            @Override
            public void buttonUp(IButton button, ButtonID buttonID) {
logger.finest("buttonUp: " + controller + ", " + button + ", " + buttonID);
            }

            @Override
            public void moveStick(IAxis axis, StickID stick) {
logger.finest("moveStick: " + controller + ", " + stick);
                IStick leftStick = controller.getStick(StickID.LEFT);
                if (leftStick != null) {
                    StickPosition leftStickPosition = leftStick.getPosition();
                    if (!leftStickPosition.isStickCentered()) {
                        logger.fine("Stick degree: " + leftStickPosition.getDegree());
                    }
                    // Get direction from left analog stick as if it were a digital pad
                    // (no degree / speed calculations here, because I'm too lazy)
                    DpadDirection mainDirection = leftStickPosition.getDirection();
                    if (mainDirection == DpadDirection.UP) {
                        craft.goUp();
                        move();
                    } else if (mainDirection == DpadDirection.DOWN) {
                        craft.goDown();
                        move();
                    } else if (mainDirection == DpadDirection.LEFT) {
                        craft.goLeft();
                        move();
                    } else if (mainDirection == DpadDirection.RIGHT) {
                        craft.goRight();
                        move();
                    }
                }
                DpadDirection dpadDirection = controller.getDpadDirection();
                if (dpadDirection == DpadDirection.UP) {
                    craft.goUp();
                    move();
                } else if (dpadDirection == DpadDirection.DOWN) {
                    craft.goDown();
                    move();
                } else if (dpadDirection == DpadDirection.LEFT) {
                    craft.goLeft();
                    move();
                } else if (dpadDirection == DpadDirection.RIGHT) {
                    craft.goRight();
                    move();
                }
            }
        });
        controller.open();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.fine("Shutdown Gamepad API");
            try {
                controller.close();
            } catch (IOException e) {
                Debug.printStackTrace(e);
            }
        }));
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.drawImage(craft.getImage(), craft.getX(), craft.getY(), this);

        Toolkit.getDefaultToolkit().sync();
        g.dispose();
    }

    void move() {
        craft.move();
        repaint();
    }

//                if (!moving) {
//                    craft.stopMoving();
//                }
}