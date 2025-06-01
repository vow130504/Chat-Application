package Client.src.view;

import Client.src.Controller.DatabaseConnection;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class RegisterFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;

    public RegisterFrame() {
        initUI();
        setupWindow();
    }

    private void initUI() {
        setTitle("Đăng ký");
        setSize(400, 300);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 30, 20, 30));
        mainPanel.setBackground(Color.WHITE);

        usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(200, 30));
        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(200, 30));

        mainPanel.add(createLabeledField("Tên người dùng:", usernameField));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createLabeledField("Mật khẩu:", passwordField));
        mainPanel.add(Box.createVerticalStrut(20));

        JButton registerButton = new JButton("Đăng ký");
        styleButton(registerButton, new Color(100, 100, 100));
        registerButton.addActionListener(this::register);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.add(registerButton);

        mainPanel.add(buttonPanel);
        add(mainPanel);
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

    private void register(ActionEvent e) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng điền đầy đủ các trường.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT username FROM users WHERE username = ?")) {
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    JOptionPane.showMessageDialog(this, "Tên người dùng đã tồn tại.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (username, password, is_online) VALUES (?, ?, ?)")) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                stmt.setBoolean(3, false);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(this, "Đăng ký thành công! Vui lòng đăng nhập.",
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
                dispose(); // Đóng hộp thoại đăng ký
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi đăng ký:\n" + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}