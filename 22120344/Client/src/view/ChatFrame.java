package Client.src.view;

import Client.src.Controller.DatabaseConnection;
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
        onlineUsersList.addListSelectionListener(e -> updateReceiverLabel());
        JScrollPane usersScrollPane = new JScrollPane(onlineUsersList);
        usersPanel.add(usersScrollPane, BorderLayout.CENTER);

        receiverLabel = new JLabel("Đang chat với: Tất cả");
        receiverLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        usersPanel.add(receiverLabel, BorderLayout.NORTH);

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
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(new LineBorder(bgColor.darker(), 1));
    }

    private void styleGroupButton(JButton button, Color bgColor) {
        button.setFocusPainted(false);
        button.setBackground(bgColor);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(new LineBorder(bgColor.darker(), 1));
    }

    private void updateReceiverLabel() {
        String selected = onlineUsersList.getSelectedValue();
        receiverLabel.setText("Đang chat với: " + (selected != null ? selected : "Tất cả"));
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(username);

            new Thread(this::listenForMessages).start();
            loadChatHistory();
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
                    chatArea.append("Nhóm được tạo: " + message.substring("GROUP_CREATED:".length()) + "\n");
                } else {
                    chatArea.append(message + "\n");
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
        if (!message.isEmpty()) {
            String receiver = onlineUsersList.getSelectedValue();
            if (receiver != null && !receiver.equals(username) && !receiver.startsWith("--")) {
                if (receiver.startsWith("GROUP_")) {
                    out.println("GROUP:" + receiver + ":" + message);
                } else {
                    out.println("PRIVATE:" + receiver + ":" + message);
                }
            } else {
                out.println("ALL:" + message);
            }
            messageField.setText("");
        }
    }

    private void sendFile(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String receiver = onlineUsersList.getSelectedValue();

            if (receiver != null && !receiver.startsWith("GROUP_") && !receiver.startsWith("--")) {
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
                        chatArea.append("Đã gửi file " + file.getName() + " đến " + receiver + "\n");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Lỗi khi gửi file: " + ex.getMessage(),
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                        ex.printStackTrace();
                    }
                }).start();
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng (không phải nhóm) để gửi file.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void receiveFile(String message) {
        String[] parts = message.split(":", 3);
        String sender = parts[1];
        String fileName = parts[2];

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
                        chatArea.append("Đã nhận file từ " + sender + ": " + file.getAbsolutePath() + "\n");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Lỗi khi nhận file: " + ex.getMessage(),
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
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

    private void loadChatHistory() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT sender, receiver, message_type, message_content, file_path FROM messages " +
                             "WHERE receiver = ? OR sender = ? OR receiver IN " +
                             "(SELECT group_name FROM chat_groups cg JOIN group_members gm ON cg.group_id = gm.group_id " +
                             "WHERE gm.username = ?) ORDER BY timestamp")) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            stmt.setString(3, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String display = rs.getString("sender") + " -> " + rs.getString("receiver") + ": ";
                if (rs.getString("message_type").equals("FILE")) {
                    display += "File: " + rs.getString("file_path");
                } else {
                    display += rs.getString("message_content");
                }
                chatArea.append(display + "\n");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}