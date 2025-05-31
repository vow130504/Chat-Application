package Client.src.view;

import Client.src.Controller.DatabaseConnection;
import Client.src.view.ChatApplication;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;

    public LoginFrame() {
        initUI();
        setupWindow();
    }

    private void initUI() {
        setTitle("Login");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 30, 20, 30));
        mainPanel.setBackground(Color.WHITE);

        usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(200, 30));
        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(200, 30));

        mainPanel.add(createLabeledField("Username:", usernameField));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createLabeledField("Password:", passwordField));
        mainPanel.add(Box.createVerticalStrut(20));

        JButton loginButton = new JButton("Login");
        styleButton(loginButton, new Color(70, 130, 180));
        loginButton.addActionListener(this::login);

        JButton registerButton = new JButton("Register");
        styleButton(registerButton, new Color(100, 100, 100));
        registerButton.addActionListener(this::register);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.add(loginButton);
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

    private void login(ActionEvent e) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        System.out.println("Attempting login with username: " + username); // Thêm dòng này

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println("Database connection established"); // Thêm dòng này

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM users WHERE username = ? AND password = ?")) {

                stmt.setString(1, username);
                stmt.setString(2, password);
                System.out.println("Executing query: " + stmt.toString()); // Thêm dòng này

                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    System.out.println("User found in database"); // Thêm dòng này
                    updateOnlineStatus(username, true);
                    JOptionPane.showMessageDialog(this, "Login successful!",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    openChatFrame(username);
                    dispose();
                } else {
                    System.out.println("No matching user found"); // Thêm dòng này
                    JOptionPane.showMessageDialog(this, "Invalid username or password.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (SQLException ex) {
            System.err.println("SQL Error during login: " + ex.getMessage()); // Thêm dòng này
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database connection error:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void register(ActionEvent e) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if username exists
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT username FROM users WHERE username = ?")) {
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    JOptionPane.showMessageDialog(this, "Username already exists.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            // Add new user
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (username, password, is_online) VALUES (?, ?, ?)")) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                stmt.setBoolean(3, false);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(this, "Registration successful! Please login.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Registration error:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
    private void updateOnlineStatus(String username, boolean status) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE users SET is_online = ? WHERE username = ?")) {
            stmt.setBoolean(1, status);
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("Error updating online status: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void openChatFrame(String username) {
        ChatFrame chatFrame = new ChatFrame(username);
        chatFrame.setVisible(true);
        ChatApplication.centerWindow(chatFrame);
    }
}