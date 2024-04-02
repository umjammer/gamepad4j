/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.input.gamepad4j.spi;

import net.java.games.input.AbstractComponent;
import org.gamepad4j.IComponent;


/**
 * Gamepad4jComponent.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-01-23 nsano initial version <br>
 */
public class Gamepad4jComponent extends AbstractComponent {

    IComponent component;

    /**
     * @param name must be org.gamepad4j.Identifier#name
     * @param id must be the one normalized by {@link IdConvertible#normalize(org.gamepad4j.Identifier)}
     */
    public Gamepad4jComponent(String name, Identifier id, IComponent component) {
        super(name, id);
        this.component = component;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    /** */
    public boolean isValueChanged(float value) {
        return getEventValue() != value;
    }

    /** */
    public float getValue() {
        return getEventValue();
    }

    /** */
    public void setValue(float value) {
        setEventValue(value);
    }
}
