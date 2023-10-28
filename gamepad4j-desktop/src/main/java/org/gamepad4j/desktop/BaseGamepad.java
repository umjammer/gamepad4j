/*
 * Copyright (c) 2023 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package org.gamepad4j.desktop;


import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


/**
 * BaseGamepad.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2023-10-27 nsano initial version <br>
 */
public abstract class BaseGamepad implements Gamepad {

    protected static final Logger logger = Logger.getLogger(BaseGamepad.class.getName());

    /** */
    public record Hat(int x, int y) {}

    /** */
    protected final List<GamepadListener> listeners = new ArrayList<>();

    @Override
    public void addGamepadListener(GamepadListener l) {
        listeners.add(l);
    }

    @Override
    public void removeGamepadListener(GamepadListener l) {
        listeners.remove(l);
    }
}
