package Client.src.view;

import Client.src.Controller.DatabaseConnection;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.html.HTMLEditorKit;
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
    private String currentReceiver; // Người nhận hiện tại (người dùng hoặc nhóm)

    private JEditorPane chatArea; // Thay JTextArea bằng JEditorPane để hỗ trợ HTML
    private JTextField messageField;
    private JList<String> onlineUsersList;
    private DefaultListModel<String> onlineUsersModel;
    private JLabel receiverLabel;

    public ChatFrame(String username) {
        this.username = username;
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            e.printStackTrace();
        }
        initUI();
        connectToServer();
    }

    private void initUI() {
        setTitle("Ứng dụng Chat - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(new ImageIcon("image/icon.png").getImage());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 240));

        // Sidebar: Online users and groups
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBorder(BorderFactory.createTitledBorder("Người dùng và Nhóm"));
        usersPanel.setPreferredSize(new Dimension(200, 0));
        usersPanel.setBackground(Color.WHITE);

        onlineUsersModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(onlineUsersModel);
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.setBackground(new Color(250, 250, 250));
        onlineUsersList.addListSelectionListener(e -> updateReceiver());
        JScrollPane usersScrollPane = new JScrollPane(onlineUsersList);
        usersPanel.add(usersScrollPane, BorderLayout.CENTER);

        receiverLabel = new JLabel("Chọn người dùng hoặc nhóm để chat");
        receiverLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        usersPanel.add(receiverLabel, BorderLayout.NORTH);

        // Chat area
        chatArea = new JEditorPane();
        chatArea.setContentType("text/html");
        chatArea.setEditable(false);
        chatArea.setBackground(Color.WHITE);
        chatArea.setBorder(new LineBorder(new Color(200, 200, 200)));
        chatArea.setEditorKit(new HTMLEditorKit());
        chatArea.setText("<html><body style='font-family: Arial; font-size: 14px; padding: 10px;'></body></html>");
        JScrollPane chatScrollPane = new JScrollPane(chatArea);

        // Message input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));
        messageField.addActionListener(this::sendMessage);

        JButton sendButton = new JButton("Gửi");
        styleButton(sendButton, new Color(70, 130, 180));
        sendButton.addActionListener(this::sendMessage);

        JButton fileButton = new JButton("Gửi File");
        styleButton(fileButton, new Color(100, 100, 100));
        fileButton.addActionListener(this::sendFile);

        JButton historyButton = new JButton("Lịch sử");
        styleButton(historyButton, new Color(100, 100, 100));
        historyButton.addActionListener(this::showHistory);

        JButton createGroupButton = new JButton("Tạo nhóm");
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

        mainPanel.add(usersPanel, BorderLayout.WEST);
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void styleButton(JButton button, Color bgColor) {
        button.setFocusPainted(false);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(new LineBorder(bgColor.darker(), 1));
    }

    private void styleGroupButton(JButton button, Color bgColor) {
        button.setFocusPainted(false);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(new LineBorder(bgColor.darker(), 1));
    }

    private void updateReceiver() {
        String selected = onlineUsersList.getSelectedValue();
        if (selected != null && !selected.startsWith("--")) {
            currentReceiver = selected;
            receiverLabel.setText("Đang chat với: " + selected);
            loadConversationHistory();
        } else {
            currentReceiver = null;
            receiverLabel.setText("Chọn người dùng hoặc nhóm để chat");
            clearChatArea();
        }
    }

    private void clearChatArea() {
        chatArea.setText("<html><body style='font-family: Arial; font-size: 14px; padding: 10px;'></body></html>");
    }

    private void appendMessage(String sender, String message, boolean isSent, boolean isGroup) {
        String bubbleStyle = isSent ?
                "background-color: #0084ff; color: white; border-radius: 10px; padding: 8px 12px; margin: 5px; max-width: 60%; display: inline-block; text-align: left; box-shadow: 1px 1px 3px rgba(0,0,0,0.2);" :
                "background-color: #e9ecef; color: black; border-radius: 10px; padding: 8px 12px; margin: 5px; max-width: 60%; display: inline-block; text-align: left; box-shadow: 1px 1px 3px rgba(0,0,0,0.2);";
        String alignStyle = isSent ? "text-align: right;" : "text-align: left;";
        String senderName = isGroup && !isSent ? sender + ": " : "";
        String html = String.format(
                "<div style='%s'><div style='%s'>%s%s</div></div>",
                alignStyle, bubbleStyle, senderName, message.replace("\n", "<br>")
        );

        String currentContent = chatArea.getText();
        String newContent = currentContent.replace("</body>", html + "</body>");
        chatArea.setText(newContent);

        // Cuộn xuống cuối
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) chatArea.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(username);

            new Thread(this::listenForMessages).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối đến server: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("ONLINE_USERS_AND_GROUPS:")) {
                    updateOnlineUsersAndGroups(message.substring("ONLINE_USERS_AND_GROUPS:".length()));
                } else if (message.startsWith("FILE:")) {
                    receiveFile(message);
                } else if (message.startsWith("GROUP_CREATED:")) {
                    appendMessage("Hệ thống", "Nhóm được tạo: " + message.substring("GROUP_CREATED:".length()), false, false);
                } else if (message.startsWith("PRIVATE:")) {
                    String[] parts = message.split(":", 3);
                    String sender = parts[1];
                    String msg = parts[2];
                    if (currentReceiver != null && currentReceiver.equals(sender)) {
                        appendMessage(sender, msg, false, false);
                    }
                } else if (message.startsWith("GROUP:")) {
                    String[] parts = message.split(":", 4);
                    String groupName = parts[1];
                    String sender = parts[2];
                    String msg = parts[3];
                    if (currentReceiver != null && currentReceiver.equals(groupName)) {
                        appendMessage(sender, msg, sender.equals(username), true);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateOnlineUsersAndGroups(String data) {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.clear();
            String[] parts = data.split(";");
            String[] users = parts[0].split(",");
            String[] groups = parts.length > 1 ? parts[1].split(",") : new String[0];

            onlineUsersModel.addElement("-- Người dùng --");
            for (String user : users) {
                if (!user.isEmpty() && !user.equals(username)) {
                    onlineUsersModel.addElement(user);
                }
            }
            onlineUsersModel.addElement("-- Nhóm --");
            for (String group : groups) {
                if (!group.isEmpty()) {
                    onlineUsersModel.addElement(group);
                }
            }
        });
    }

    private void sendMessage(ActionEvent e) {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && currentReceiver != null && !currentReceiver.startsWith("--")) {
            if (currentReceiver.startsWith("GROUP_")) {
                out.println("GROUP:" + currentReceiver + ":" + message);
                appendMessage(username, message, true, true);
            } else {
                out.println("PRIVATE:" + currentReceiver + ":" + message);
                appendMessage(username, message, true, false);
            }
            messageField.setText("");
        } else {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người dùng hoặc nhóm để gửi tin nhắn.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendFile(ActionEvent e) {
        if (currentReceiver == null || currentReceiver.startsWith("GROUP_") || currentReceiver.startsWith("--")) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng (không phải nhóm) để gửi file.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            new Thread(() -> {
                try {
                    out.println("FILE:" + currentReceiver + ":" + file.getName());
                    FileInputStream fis = new FileInputStream(file);
                    OutputStream os = socket.getOutputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    fis.close();
                    appendMessage(username, "Đã gửi file: " + file.getName(), true, false);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi khi gửi file: " + ex.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }).start();
        }
    }

    private void receiveFile(String message) {
        String[] parts = message.split(":", 3);
        String sender = parts[1];
        String fileName = parts[2];

        if (currentReceiver != null && currentReceiver.equals(sender)) {
            int response = JOptionPane.showConfirmDialog(this,
                    sender + " muốn gửi bạn file: " + fileName + ". Chấp nhận?",
                    "Nhận File", JOptionPane.YES_NO_OPTION);

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
                            appendMessage(sender, "Đã nhận file: " + fileName, false, false);
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(this, "Lỗi khi nhận file: " + ex.getMessage(),
                                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                            ex.printStackTrace();
                        }
                    }).start();
                }
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
        panel.add(new JLabel("Tên nhóm:"), BorderLayout.NORTH);
        panel.add(groupNameField, BorderLayout.CENTER);
        panel.add(new JScrollPane(memberList), BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, panel, "Tạo nhóm", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String groupName = "GROUP_" + groupNameField.getText().trim();
            List<String> selectedMembers = memberList.getSelectedValuesList();
            if (!groupName.isEmpty() && !selectedMembers.isEmpty()) {
                selectedMembers.removeIf(member -> member.startsWith("--"));
                selectedMembers.add(username);
                String members = String.join(",", selectedMembers);
                out.println("CREATE_GROUP:" + groupName + ":" + members);
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên nhóm và chọn ít nhất một thành viên.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadConversationHistory() {
        clearChatArea();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT sender, receiver, message_type, message_content, file_path FROM messages " +
                             "WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) OR " +
                             "(receiver = ? AND ? IN (SELECT group_name FROM chat_groups cg JOIN group_members gm " +
                             "ON cg.group_id = gm.group_id WHERE gm.username = ?)) ORDER BY timestamp")) {
            stmt.setString(1, username);
            stmt.setString(2, currentReceiver);
            stmt.setString(3, currentReceiver);
            stmt.setString(4, username);
            stmt.setString(5, currentReceiver);
            stmt.setString(6, currentReceiver);
            stmt.setString(7, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender");
                String message = rs.getString("message_type").equals("FILE") ?
                        "File: " + rs.getString("file_path") : rs.getString("message_content");
                boolean isSent = sender.equals(username);
                boolean isGroup = currentReceiver.startsWith("GROUP_");
                appendMessage(sender, message, isSent, isGroup);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}