/**
 * @see "https://bard.google.com/chat/9665fdb38439eac5"
 */

package vavi.games.input.gamepad4j.spi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import net.java.games.input.Event;
import net.java.games.input.InputEvent;
import net.java.games.input.WrappedComponent;
import net.java.games.input.usb.HidController;
import net.java.games.input.usb.HidInputEvent;
import net.java.games.input.usb.parser.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;


@PropsEntity(url = "file:local.properties")
public class GamepadTest {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "mid")
    String mid;
    @Property(name = "pid")
    String pid;

    int vendorId;
    int productId;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);

            vendorId = Integer.decode(mid);
            productId = Integer.decode(pid);
        }
    }

    static {
        // for fixing table rows count
        System.setProperty("net.java.games.input.InputEvent.fillAll", "true");
    }

    static class GamepadConsole {

        /** */
        private final HidController controller;

        public GamepadConsole(HidController controller) throws IOException {
            this.controller = controller;
        }

        /** */
        public void start() throws IOException {
            controller.addInputEventListener(GamepadConsole::print);
            controller.open();
        }

        /** analyze data by jinput */
        static void print(InputEvent e) {
            Event event = new Event();
            System.out.println("\033[2J");
            while (e.getNextEvent(event)) {
                System.out.printf("%30s:  % 10.3f  %s%s%n",
                        event.getComponent().getName(),
                        event.getValue(),
                        ((WrappedComponent<Field>) event.getComponent()).getWrappedObject().getDump(((HidInputEvent) e).getData()),
                        " ".repeat(50));
            }
            System.out.flush();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test1() throws Exception {
        Gamepad4jEnvironmentPlugin environment = new Gamepad4jEnvironmentPlugin();
        HidController controller = environment.getController(vendorId, productId);

        GamepadConsole app = new GamepadConsole(controller);
        app.start();

        CountDownLatch cdl = new CountDownLatch(1);
        cdl.await();
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        GamepadTest app = new GamepadTest();
        PropsEntity.Util.bind(app);
        app.vendorId = Integer.decode(app.mid);
        app.productId = Integer.decode(app.pid);
        app.test1();
    }
}
