/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.input.gamepad4j.spi;

import java.util.logging.Logger;

import net.java.games.input.Component;
import net.java.games.input.usb.HidRumbler;


/**
 * Gamepad4jRumbler.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-01-23 nsano initial version <br>
 */
public class Gamepad4jRumbler implements HidRumbler {

    private static final Logger logger = Logger.getLogger(Gamepad4jRumbler.class.getName());

    private final int reportId;
    private final Component.Identifier identifier;
    private float value;

    private final int offset;

    public Gamepad4jRumbler(int reportId, Component.Identifier identifier, int offset) {
        this.reportId = reportId;
        this.identifier = identifier;
        this.offset = offset;
    }

    @Override
    public void setValue(float value) {
        this.value = value;
    }

    public int getValue() {
        return (int) value;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public String getOutputName() {
        return identifier.getName();
    }

    @Override
    public Component.Identifier getOutputIdentifier() {
        return identifier;
    }

    @Override
    public int getReportId() {
        return reportId;
    }

    @Override
    public void fill(byte[] data) {
        data[offset] = (byte) value;
logger.finer(String.format("data[%02d] = 0x%2$02x (%2$d)", offset, data[offset] & 0xff));
    }

    @Override
    public String toString() {
        return getOutputName();
    }
}
