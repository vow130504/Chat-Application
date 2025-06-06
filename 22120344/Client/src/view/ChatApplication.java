package Client.src.view;

import javax.swing.*;
import java.awt.*;

public class ChatApplication {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            DatabaseInfoUI dbUI = new DatabaseInfoUI();
            dbUI.setVisible(true);
            centerWindow(dbUI);
        });
    }

    public static void centerWindow(Window window) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        window.setLocation((screenSize.width - window.getWidth()) / 2,
                (screenSize.height - window.getHeight()) / 2);
    }
}