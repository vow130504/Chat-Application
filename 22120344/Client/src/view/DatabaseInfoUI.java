package Client.src.view;

import Client.src.Controller.DatabaseConnection;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.SQLException;
import javax.swing.border.EmptyBorder;

public class DatabaseInfoUI extends JFrame {
    private JTextField dbNameField, hostField, userField;
    private JPasswordField passwordField;

    public DatabaseInfoUI() {
        initUI();
        setupWindow();
    }

    private void initUI() {
        setTitle("Database Connection");
        setSize(450, 350);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 30, 20, 30));
        mainPanel.setBackground(Color.WHITE);

        dbNameField = createTextField("chat_db");
        hostField = createTextField("localhost:3306");
        userField = createTextField("root");
        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(200, 30));

        mainPanel.add(createLabeledField("Database Name:", dbNameField));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createLabeledField("Host (e.g., localhost:3306):", hostField));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createLabeledField("Username:", userField));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createLabeledField("Password:", passwordField));
        mainPanel.add(Box.createVerticalStrut(20));

        JButton connectButton = new JButton("Connect");
        styleButton(connectButton, new Color(70, 130, 180));
        connectButton.addActionListener(this::connectToDatabase);

        mainPanel.add(connectButton);
        add(mainPanel);
    }

    private JTextField createTextField(String defaultValue) {
        JTextField field = new JTextField(defaultValue);
        field.setPreferredSize(new Dimension(200, 30));
        return field;
    }

    private JPanel createLabeledField(String labelText, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBackground(Color.WHITE);

        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(150, 30));

        panel.add(label, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private void styleButton(JButton button, Color bgColor) {
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFocusPainted(false);
        button.setBackground(bgColor);
        button.setPreferredSize(new Dimension(120, 40));
        button.setFont(new Font("Arial", Font.BOLD, 14));
    }

    private void setupWindow() {
        setLocationRelativeTo(null);
    }

    private void connectToDatabase(ActionEvent e) {
        String dbName = dbNameField.getText().trim();
        String host = hostField.getText().trim();
        String user = userField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (dbName.isEmpty() || host.isEmpty() || user.isEmpty()) {
            showError("Vui lòng điền đầy đủ các trường bắt buộc.");
            return;
        }

        String url = buildConnectionUrl(dbName, host);

        try {
            DatabaseConnection.connect(url, user, password);
            if (DatabaseConnection.testConnection()) {
                JOptionPane.showMessageDialog(this, "Kết nối cơ sở dữ liệu thành công!",
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
                openLoginScreen();
                dispose();
            } else {
                showError("Không thể thiết lập kết nối cơ sở dữ liệu.");
            }
        } catch (SQLException ex) {
            showError("Lỗi kết nối cơ sở dữ liệu:\n" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String buildConnectionUrl(String dbName, String host) {
        return "jdbc:mysql://" + host + "/" + dbName +
                "?useUnicode=true&characterEncoding=UTF-8" +
                "&serverTimezone=UTC" +
                "&autoReconnect=true" +
                "&failOverReadOnly=false" +
                "&maxReconnects=10";
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void openLoginScreen() {
        LoginFrame loginFrame = new LoginFrame();
        loginFrame.setVisible(true);
        ChatApplication.centerWindow(loginFrame);
    }
}