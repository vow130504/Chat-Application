package Client.src.view;



import Client.src.view.DatabaseInfoUI;
import javax.swing.*;
import java.awt.*;

public class ChatApplication {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
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