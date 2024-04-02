/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.input.gamepad4j.spi;

import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;

import net.java.games.input.Component;
import net.java.games.input.Event;
import net.java.games.input.InputEvent;


/**
 * Gamepad4jInputEvent.
 * <p>
 * <h4>system property</h4>
 * <li>"net.java.games.input.InputEvent.fillAll" ... determine to fill all events (true) or events which value is changed (false)</li>
 * </p>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-01-23 nsano initial version <br>
 */
public class Gamepad4jInputEvent extends InputEvent {

    /** which value is changed only */
    private final Deque<Gamepad4jComponent> deque = new LinkedList<>();

    /** the time when got an event */
    private final long time;

    /** */
    public Gamepad4jInputEvent(Object source, Component[] components, float data) {
        super(source);
        this.time = System.nanoTime();

        boolean fillAll = Boolean.parseBoolean(System.getProperty("net.java.games.input.InputEvent.fillAll", "false"));

        deque.clear();
        for (Gamepad4jComponent component : Arrays.stream(components).map(Gamepad4jComponent.class::cast).toArray(Gamepad4jComponent[]::new)) {
            if (fillAll || component.isValueChanged(data)) {
                component.setValue(data);
                deque.offer(component);
            }
        }
    }

    @Override
    public boolean getNextEvent(Event event) {
        if (!deque.isEmpty()) {
            Gamepad4jComponent component = deque.poll();
            event.set(component, component.getValue(), time);
            return true;
        } else {
            return false;
        }
    }
}
