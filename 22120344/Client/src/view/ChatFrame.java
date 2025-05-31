package Client.src.view;

import Client.src.Controller.DatabaseConnection;
import Client.src.view.ChatApplication;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.List;

public class ChatFrame extends JFrame {
    private String username;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private JTextArea chatArea;
    private JTextField messageField;
    private JList<String> onlineUsersList;
    private DefaultListModel<String> onlineUsersModel;

    public ChatFrame(String username) {
        this.username = username;
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf"); // Sử dụng FlatLaf
        } catch (Exception e) {
            e.printStackTrace();
        }
        initUI();
        connectToServer();
    }

    private void initUI() {
        setTitle("Chat Application - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(new ImageIcon("image/icon.png").getImage()); // Thêm icon

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 240)); // Nền sáng

        // Online users panel
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBorder(BorderFactory.createTitledBorder("Online Users"));
        usersPanel.setPreferredSize(new Dimension(200, 0));
        usersPanel.setBackground(Color.WHITE);

        onlineUsersModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(onlineUsersModel);
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.setBackground(new Color(250, 250, 250));
        JScrollPane usersScrollPane = new JScrollPane(onlineUsersList);
        usersPanel.add(usersScrollPane, BorderLayout.CENTER);

        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));
        chatArea.setBackground(Color.WHITE);
        chatArea.setBorder(new LineBorder(new Color(200, 200, 200)));
        JScrollPane chatScrollPane = new JScrollPane(chatArea);

        // Message input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));
        messageField.addActionListener(this::sendMessage);

        JButton sendButton = new JButton("Send");
        styleButton(sendButton, new Color(70, 130, 180));
        sendButton.addActionListener(this::sendMessage);

        JButton fileButton = new JButton("Send File");
        styleButton(fileButton, new Color(100, 100, 100));
        fileButton.addActionListener(this::sendFile);

        JButton historyButton = new JButton("History");
        styleButton(historyButton, new Color(100, 100, 100));
        historyButton.addActionListener(this::showHistory);

        JButton createGroupButton = new JButton("Create Group");
        styleGroupButton(createGroupButton, new Color(60, 179, 113));
        createGroupButton.addActionListener(this::createGroup);

        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        buttonPanel.setBackground(new Color(240, 240, 240));
        buttonPanel.add(sendButton);
        buttonPanel.add(fileButton);
        buttonPanel.add(historyButton);
        buttonPanel.add(createGroupButton);

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        // Add components to main panel
        mainPanel.add(usersPanel, BorderLayout.WEST);
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void styleButton(JButton button, Color bgColor) {
        button.setFocusPainted(false);
        button.setBackground(bgColor);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(new LineBorder(bgColor.darker(), 1));
    }

    private void styleGroupButton(JButton button, Color bgColor) {
        button.setFocusPainted(false);
        button.setBackground(bgColor);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(new LineBorder(bgColor.darker(), 1));
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send username to server
            out.println(username);

            // Start thread to listen for messages
            new Thread(this::listenForMessages).start();

            // Load chat history
            loadChatHistory();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("ONLINE_USERS:")) {
                    updateOnlineUsers(message.substring("ONLINE_USERS:".length()));
                } else if (message.startsWith("FILE:")) {
                    receiveFile(message);
                } else if (message.startsWith("GROUP_CREATED:")) {
                    chatArea.append("Group created: " + message.substring("GROUP_CREATED:".length()) + "\n");
                } else {
                    chatArea.append(message + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateOnlineUsers(String users) {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.clear();
            for (String user : users.split(",")) {
                if (!user.isEmpty()) {
                    onlineUsersModel.addElement(user);
                }
            }
        });
    }

    private void sendMessage(ActionEvent e) {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String receiver = onlineUsersList.getSelectedValue();
            if (receiver != null && !receiver.equals(username)) {
                if (receiver.startsWith("GROUP_")) {
                    out.println("GROUP:" + receiver + ":" + message);
                } else {
                    out.println("PRIVATE:" + receiver + ":" + message);
                }
            } else {
                out.println(message);
            }
            messageField.setText("");
        }
    }

    private void sendFile(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String receiver = onlineUsersList.getSelectedValue();

            if (receiver != null && !receiver.startsWith("GROUP_")) {
                new Thread(() -> {
                    try {
                        out.println("FILE:" + receiver + ":" + file.getName());

                        FileInputStream fis = new FileInputStream(file);
                        OutputStream os = socket.getOutputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        fis.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }).start();
            } else {
                JOptionPane.showMessageDialog(this, "Please select a user (not a group) to send the file.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void receiveFile(String message) {
        String[] parts = message.split(":");
        String sender = parts[1];
        String fileName = parts[2];

        int response = JOptionPane.showConfirmDialog(this,
                sender + " wants to send you a file: " + fileName + ". Accept?",
                "File Transfer", JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName));
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                new Thread(() -> {
                    try {
                        FileOutputStream fos = new FileOutputStream(file);
                        InputStream is = socket.getInputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            if (is.available() == 0) break;
                        }
                        fos.close();
                        chatArea.append("File received from " + sender + ": " + file.getAbsolutePath() + "\n");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }
        }
    }

    private void showHistory(ActionEvent e) {
        HistoryDialog historyDialog = new HistoryDialog(this, username);
        historyDialog.setVisible(true);
    }

    private void createGroup(ActionEvent e) {
        JTextField groupNameField = new JTextField(20);
        JList<String> memberList = new JList<>(onlineUsersModel);
        memberList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Group Name:"), BorderLayout.NORTH);
        panel.add(groupNameField, BorderLayout.CENTER);
        panel.add(new JScrollPane(memberList), BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create Group", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String groupName = "GROUP_" + groupNameField.getText().trim();
            List<String> selectedMembers = memberList.getSelectedValuesList();
            if (!groupName.isEmpty() && !selectedMembers.isEmpty()) {
                selectedMembers.add(username); // Thêm người tạo vào nhóm
                String members = String.join(",", selectedMembers);
                out.println("CREATE_GROUP:" + groupName + ":" + members);
            } else {
                JOptionPane.showMessageDialog(this, "Please enter a group name and select at least one member.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadChatHistory() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT sender, message FROM messages WHERE receiver = ? OR sender = ? OR receiver = 'ALL' ORDER BY timestamp")) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                chatArea.append(rs.getString("sender") + ": " + rs.getString("message") + "\n");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}