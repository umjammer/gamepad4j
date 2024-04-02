package org.gamepad4j.test;

import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.JFrame;

import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;


@PropsEntity(url = "file:local.properties")
public class Demo extends JFrame {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "mid")
    String mid;
    @Property(name = "pid")
    String pid;

    int vendorId;
    int productId;

    void exec() {

        add(new Board(vendorId, productId));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 300);
        setLocationRelativeTo(null);
        setTitle("GamepadAPI DemoTest");
        setResizable(false);
        setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        Demo app = new Demo();
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(app);

            app.vendorId = Integer.decode(app.mid);
            app.productId = Integer.decode(app.pid);
        } else {
            throw new IllegalStateException("prepare local.properties");
        }
        app.exec();
    }
}
