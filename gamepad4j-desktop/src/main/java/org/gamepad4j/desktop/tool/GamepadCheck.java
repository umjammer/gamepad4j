/*
 * @Copyright: Marcel Schoen, Switzerland, 2014, All Rights Reserved.
 */

package org.gamepad4j.desktop.tool;

import java.util.logging.Logger;

import org.gamepad4j.AxisID;
import org.gamepad4j.ButtonID;
import org.gamepad4j.Controllers;
import org.gamepad4j.DpadDirection;
import org.gamepad4j.IButton;
import org.gamepad4j.IController;
import org.gamepad4j.IStick;
import org.gamepad4j.ITrigger;
import org.gamepad4j.StickID;
import org.gamepad4j.TriggerID;


/**
 * Simulates game loop. Since there is no "main loop" in a Swing
 * GUI application like in a typical game, it has to be simulated
 * with a separate thread, polling the controller states.
 *
 * @author Marcel Schoen
 * @version $Revision: $
 */
public class GamepadCheck implements Runnable {

    private static final Logger logger = Logger.getLogger(GamepadCheck.class.getName());
    
    public static boolean running = true;

    @Override
    public void run() {
        Controllers environment = Controllers.instance();

        while (running) {
            // This is basically what you would do in your game's main loop
            IController[] controllers = environment.getControllers();
            if (controllers != null && controllers.length > 0) {
                IButton acceptButton = controllers[0].getButton(ButtonID.FACE_DOWN);
                if (acceptButton != null && acceptButton.isPressedOnce()) {
                    logger.fine("*** FACE DOWN ***");
                }
                IButton cancelButton = controllers[0].getButton(ButtonID.CANCEL);
                if (cancelButton != null && cancelButton.isPressedOnce()) {
                    logger.fine("*** CANCEL / DENY ***");
                }

                ITrigger triggerLeft = controllers[0].getTrigger(TriggerID.LEFT_DOWN);
                if (triggerLeft != null) {
                    float trigger = triggerLeft.analogValue();
					logger.finer("> left trigger button: " + trigger);
                    if (trigger < -0.2 || trigger > 0.2) {
						logger.finer("> left trigger button: " + trigger);
                    }
                }

                DpadDirection dpad = controllers[0].getDpadDirection();
                if (dpad != DpadDirection.NONE) {
					logger.finer("D-Pad: " + dpad);
                }

                IStick leftStick = controllers[0].getStick(StickID.LEFT);
                if (leftStick == null) {
                    logger.fine("no left stick found");
                } else {
//                    DpadDirection stickDpad = leftStick.getPosition().getDirection();
//                    if (stickDpad != DpadDirection.NONE) {
//                        logger.fine(">> left stick as d-pad: " + stickDpad);
//                    }

                    float xAxis = leftStick.getAxis(AxisID.X).getValue();

                    // TODO: Y-AXIS NOT WORKING / DEGREE NOT CORRECT

                    float yAxis = leftStick.getAxis(AxisID.Y).getValue();
                    float degree = leftStick.getPosition().getDegree();
                    float distance = leftStick.getPosition().getDistanceToCenter();
					logger.finer("> Left stick: X=" + xAxis + ",Y=" + yAxis + ",rotation=" + degree + " / distance: " + distance);
                }
            } else {
                logger.warning("No controllers available.");
            }
            try { Thread.sleep(150); } catch (InterruptedException ignore) {}
        }
    }
}
