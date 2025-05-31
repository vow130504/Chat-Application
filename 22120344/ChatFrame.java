import Controller.DatabaseConnection;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import javax.swing.border.EmptyBorder;
public class ChatFrame extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private JList<String> userList;
    private JList<String> groupList;
    private String username;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ChatFrame(String username) {
        this.username = username;
        initUI();
        connectToServer();
        loadChatHistory();
        loadGroups();
    }

    private void initUI() {
        setTitle("Ứng dụng Chat - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        mainPanel.add(chatScroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());
        JButton sendButton = new JButton("Gửi");
        styleButton(sendButton, new Color(70, 130, 180));
        sendButton.addActionListener(e -> sendMessage());
        JButton sendFileButton = new JButton("Gửi File");
        styleButton(sendFileButton, new Color(100, 100, 100));
        sendFileButton.addActionListener(e -> sendFile());
        JButton deleteHistoryButton = new JButton("Xóa lịch sử");
        styleButton(deleteHistoryButton, new Color(200, 100, 100));
        deleteHistoryButton.addActionListener(e -> deleteChatHistory());
        inputPanel.add(sendFileButton, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.add(deleteHistoryButton, BorderLayout.NORTH);

        JPanel sidePanel = new JPanel(new BorderLayout());
        userList = new JList<>();
        groupList = new JList<>();
        sidePanel.add(new JScrollPane(userList), BorderLayout.NORTH);
        sidePanel.add(new JScrollPane(groupList), BorderLayout.SOUTH);

        JButton createGroupButton = new JButton("Tạo nhóm");
        styleButton(createGroupButton, new Color(100, 100, 100));
        createGroupButton.addActionListener(e -> createGroup());
        sidePanel.add(createGroupButton, BorderLayout.CENTER);

        mainPanel.add(inputPanel, BorderLayout.SOUTH);
        mainPanel.add(sidePanel, BorderLayout.WEST);

        add(mainPanel);
    }

    private void styleButton(JButton button, Color bgColor) {
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 14));
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(username);

            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.startsWith("PRIVATE:")) {
                            String[] parts = message.split(":", 3);
                            chatArea.append(parts[1] + " (riêng tư): " + parts[2] + "\n");
                        } else if (message.startsWith("GROUP:")) {
                            String[] parts = message.split(":", 4);
                            chatArea.append("[" + parts[1] + "] " + parts[2] + ": " + parts[3] + "\n");
                        } else if (message.startsWith("FILE:")) {
                            String[] parts = message.split(":", 3);
                            chatArea.append(parts[1] + " đã gửi file: " + parts[2] + "\n");
                        } else {
                            chatArea.append(message + "\n");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String selectedUser = userList.getSelectedValue();
            String selectedGroup = groupList.getSelectedValue();
            if (selectedUser != null) {
                out.println("PRIVATE:" + selectedUser + ":" + message);
            } else if (selectedGroup != null) {
                out.println("GROUP:" + selectedGroup + ":" + message);
            } else {
                out.println(message);
            }
            messageField.setText("");
        }
    }

    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String selectedUser = userList.getSelectedValue();
            if (selectedUser != null) {
                try {
                    out.println("FILE:" + selectedUser + ":" + file.getName());
                    FileInputStream fis = new FileInputStream(file);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        socket.getOutputStream().write(buffer, 0, bytesRead);
                    }
                    fis.close();
                    socket.getOutputStream().flush();
                    chatArea.append("Đã gửi file: " + file.getName() + " tới " + selectedUser + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận file.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void createGroup() {
        String groupName = JOptionPane.showInputDialog(this, "Nhập tên nhóm:");
        if (groupName != null && !groupName.trim().isEmpty()) {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("INSERT INTO chat_groups (group_name, members) VALUES (?, ?)")) {
                stmt.setString(1, groupName);
                stmt.setString(2, username);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(this, "Tạo nhóm thành công!");
                loadGroups();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Lỗi tạo nhóm:\n" + e.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    private void loadChatHistory() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT sender, receiver, message, timestamp FROM messages WHERE sender = ? OR receiver = ? OR receiver = 'ALL'")) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String receiver = rs.getString("receiver");
                String message = rs.getString("message");
                String timestamp = rs.getString("timestamp");
                chatArea.append("[" + timestamp + "] " + sender + " to " + receiver + ": " + message + "\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadGroups() {
        DefaultListModel<String> groupModel = new DefaultListModel<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT group_name FROM chat_groups WHERE members LIKE ?")) {
            stmt.setString(1, "%" + username + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                groupModel.addElement(rs.getString("group_name"));
            }
            groupList.setModel(groupModel);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deleteChatHistory() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM messages WHERE sender = ? OR receiver = ?")) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            stmt.executeUpdate();
            chatArea.setText("");
            JOptionPane.showMessageDialog(this, "Đã xóa lịch sử chat!");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Lỗi xóa lịch sử:\n" + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
}