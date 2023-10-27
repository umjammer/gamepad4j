/*
 * Copyright (c) 2023 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package org.gamepad4j.shared;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;


/**
 * BaseGamepad.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2023-10-27 nsano initial version <br>
 */
public abstract class BaseGamepad implements Gamepad {

    protected static final Logger logger = Logger.getLogger(BaseGamepad.class.getName());

    /** a cupcell for passing to callback */
    public static class DeviceContext extends Structure {

        public int id;

        public DeviceContext() {
        }

        public DeviceContext(Pointer p) {
            super(p);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("id");
        }

        private static int idGenerator = 0;
        private static Map<Integer, Device> map = new HashMap<>();

        public static DeviceContext create(Device device) {
            DeviceContext context = new DeviceContext();
            context.id = DeviceContext.idGenerator++;
            DeviceContext.map.put(context.id, device);
            return context;
        }

        public static Device get(Pointer p) {
            DeviceContext context = new DeviceContext(p);
            return map.get(context.id);
        }
    }

    public static class QueuedEvent {

        public int deviceID;
        public EventType eventType;
        public EventData eventData;
    }

    /** */
    private final List<GamepadListener> listeners = new ArrayList<>();

    /** */
    protected void fireDeviceAttach(Device device) {
        listeners.forEach(l -> l.deviceAttach(device));
    }

    /** */
    protected void fireDeviceRemove(Device device) {
        listeners.forEach(l -> l.deviceRemove(device));
    }

    /** */
    protected void fireButtonDown(Device device, int buttonID, double timestamp) {
        listeners.forEach(l -> l.buttonDown(device, buttonID, timestamp));
    }

    /** */
    protected void fireBbuttonUp(Device device, int buttonID, double timestamp) {
        listeners.forEach(l -> l.buttonUp(device, buttonID, timestamp));
    }

    /** */
    protected void fireAxisMove(Device device, int axisID, float value, double timestamp) {
        listeners.forEach(l -> l.axisMove(device, axisID, value, timestamp));
    }

    /** */
    protected void processQueuedEvent(QueuedEvent event) {
if (event == null) {
 logger.finer("event is null");
 return;
}
        switch (event.eventType) {
        case DEVICE_ATTACHED: {
            fireDeviceAttach((Device) event.eventData);
            break;
        }
        case DEVICE_REMOVED: {
            fireDeviceRemove((Device) event.eventData);
            break;
        }
        case BUTTON_DOWN: {
            ButtonEventData buttonEvent = (ButtonEventData) event.eventData;
            fireButtonDown(buttonEvent.device, buttonEvent.buttonID, buttonEvent.timestamp);
            break;
        }
        case BUTTON_UP: {
            ButtonEventData buttonEvent = (ButtonEventData) event.eventData;
            fireBbuttonUp(buttonEvent.device, buttonEvent.buttonID, buttonEvent.timestamp);
            break;
        }
        case AXIS_MOVED: {
            AxisEventData axisEvent = (AxisEventData) event.eventData;
            fireAxisMove(axisEvent.device, axisEvent.axisID, axisEvent.value, axisEvent.timestamp);
            break;
        }
        }
    }
}
