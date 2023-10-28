package org.gamepad4j.test;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.util.logging.Logger;
import javax.swing.JPanel;

import org.gamepad4j.ButtonID;
import org.gamepad4j.Controllers;
import org.gamepad4j.DpadDirection;
import org.gamepad4j.IButton;
import org.gamepad4j.IController;
import org.gamepad4j.IControllerListener.IControllerAdapter;
import org.gamepad4j.IStick;
import org.gamepad4j.StickID;
import org.gamepad4j.StickPosition;


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

    private Craft craft;

    private Controllers environment;

    public Board() {

        // Initialize the API
        logger.fine("Initialize controllers...");
        environment = Controllers.instance();

        setFocusable(true);
        setBackground(Color.BLACK);
        setDoubleBuffered(true);

        craft = new Craft();

        if (environment.getControllers().length == 0) {
            throw new IllegalStateException("no controller");
        }
        environment.addListener(new IControllerAdapter() {

            @Override
            public void buttonDown(IController controller, IButton button, ButtonID buttonID) {
logger.fine("buttonDown: " + controller + ", " + button + ", " + buttonID);
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

            @Override
            public void buttonUp(IController controller, IButton button, ButtonID buttonID) {
logger.fine("buttonUp: " + controller + ", " + button + ", " + buttonID);
            }

            @Override
            public void moveStick(IController controller, StickID stick) {
logger.finer("moveStick: " + controller + ", " + stick);
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
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.fine("Shutdown Gamepad API");
            environment.shutdown();
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