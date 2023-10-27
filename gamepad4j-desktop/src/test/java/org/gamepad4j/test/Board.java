package org.gamepad4j.test;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.gamepad4j.Controllers;
import org.gamepad4j.DpadDirection;
import org.gamepad4j.IController;
import org.gamepad4j.IStick;
import org.gamepad4j.StickID;
import org.gamepad4j.StickPosition;
import vavi.util.Debug;


/**
 * The code of this class was taken from this Java game programming tutorial:
 * <p>
 * http://zetcode.com/tutorials/javagamestutorial/movingsprites/
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class Board extends JPanel implements ActionListener, Runnable {

    static final Logger logger = Logger.getLogger(Board.class.getName());

    private Timer timer;
    private Craft craft;

    public Board() {

        // Initialize the API
        logger.fine("Initialize controllers...");
        Controllers.initialize();

        setFocusable(true);
        setBackground(Color.BLACK);
        setDoubleBuffered(true);

        craft = new Craft();

        timer = new Timer(5, this);
        timer.start();

        Thread runner = new Thread(this);
        runner.setDaemon(true);
        runner.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.fine("Shutdown Gamepad API");
            Controllers.shutdown();
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

    @Override
    public void actionPerformed(ActionEvent e) {
        craft.move();
        repaint();
    }

    int c = 0;

    /**
     * Gamepad state checking loop (equates typical game main loop)
     */
    @Override
    public void run() {
        while (true) {
            try {
                // Poll the state of the controllers
                Controllers.checkControllers();
                IController[] gamepads = Controllers.getControllers();
                if (gamepads.length > 0) {
                    boolean moving = false;

                    IController mainController = gamepads[0];
                    DpadDirection dpadDirection = mainController.getDpadDirection();
                    if (dpadDirection == DpadDirection.UP) {
                        craft.goUp();
                        moving = true;
                    } else if (dpadDirection == DpadDirection.DOWN) {
                        craft.goDown();
                        moving = true;
                    } else if (dpadDirection == DpadDirection.LEFT) {
                        craft.goLeft();
                        moving = true;
                    } else if (dpadDirection == DpadDirection.RIGHT) {
                        craft.goRight();
                        moving = true;
                    }

                    Debug.println(Level.FINER, "controller: " + mainController + ", " + Arrays.toString(mainController.getSticks()));
                    IStick leftStick = mainController.getStick(StickID.LEFT);
                    if (leftStick != null) {
                        StickPosition leftStickPosition = leftStick.getPosition();
                        if (!leftStickPosition.isStickCentered()) {
                            Debug.println("Stick degree: " + leftStickPosition.getDegree());
                        }
                        // Get direction from left analog stick as if it were a digital pad
                        // (no degree / speed calculations here, because I'm too lazy)
                        DpadDirection mainDirection = leftStickPosition.getDirection();
                        if (mainDirection == DpadDirection.UP) {
                            craft.goUp();
                            moving = true;
                        } else if (mainDirection == DpadDirection.DOWN) {
                            craft.goDown();
                            moving = true;
                        } else if (mainDirection == DpadDirection.LEFT) {
                            craft.goLeft();
                            moving = true;
                        } else if (mainDirection == DpadDirection.RIGHT) {
                            craft.goRight();
                            moving = true;
                        }
                    }
                    if (!moving) {
                        craft.stopMoving();
                    }
                }
            } catch (Exception e) {
                Debug.printStackTrace(e);
                c++;
                assert c < 10 : "too many errors";
            }
        }
    }
}