package Client.src.view;

import Client.src.Controller.DatabaseConnection;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ChatFrame extends JFrame {
    private String username;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentReceiver;

    private JEditorPane chatArea;
    private JTextField messageField;
    private JList<String> onlineUsersList;
    private DefaultListModel<String> onlineUsersModel;
    private JLabel receiverLabel;

    private static final String HISTORY_DIR = "chat_history";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private List<MessageInfo> messages = new ArrayList<>();
    private int messageCounter = 0;

    private static class MessageInfo {
        String timestamp;
        String sender;
        String message;
        String receiver;
        String messageId;

        MessageInfo(String timestamp, String sender, String message, String receiver, String messageId) {
            this.timestamp = timestamp;
            this.sender = sender;
            this.message = message;
            this.receiver = receiver;
            this.messageId = messageId;
        }
    }

    public ChatFrame(String username) {
        this.username = username;
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            e.printStackTrace();
        }
        initUI();
        connectToServer();
        createHistoryDirectory();
    }

    private void createHistoryDirectory() {
        File dir = new File(HISTORY_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private void initUI() {
        setTitle("Ứng dụng Chat - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(new ImageIcon("image/icon.png").getImage());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 240));

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

        chatArea = new JEditorPane();
        chatArea.setContentType("text/html");
        chatArea.setEditable(false);
        chatArea.setBackground(Color.WHITE);
        chatArea.setBorder(new LineBorder(new Color(200, 200, 200)));
        chatArea.setEditorKit(new HTMLEditorKit());
        chatArea.setText("<html><body style='font-family: Arial; font-size: 14px; padding: 10px;'></body></html>");

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Xóa");
        deleteItem.addActionListener(e -> deleteMessage());
        popupMenu.add(deleteItem);

        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                checkPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                checkPopup(e);
            }
            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int pos = chatArea.viewToModel(e.getPoint());
                    HTMLDocument doc = (HTMLDocument) chatArea.getDocument();
                    Element elem = doc.getCharacterElement(pos);
                    System.out.println("Clicked at position: " + pos + ", element: " + elem.getName());
                    Element targetDiv = findMessageDiv(elem);
                    if (targetDiv != null) {
                        Object styleAttr = targetDiv.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.STYLE);
                        Object idAttr = targetDiv.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.ID);
                        System.out.println("Found div with style: " + (styleAttr != null ? styleAttr : "none") + ", id: " + (idAttr != null ? idAttr : "none"));
                        popupMenu.show(chatArea, e.getX(), e.getY());
                    } else {
                        System.out.println("No valid div found at position: " + pos);
                        printElementTree(doc.getDefaultRootElement(), 0);
                    }
                }
            }
        });

        JScrollPane chatScrollPane = new JScrollPane(chatArea);

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

    private void printElementTree(Element elem, int level) {
        String indent = "  ".repeat(level);
        Object styleAttr = elem.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.STYLE);
        Object idAttr = elem.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.ID);
        System.out.println(indent + "Element: " + elem.getName() + ", Style: " + (styleAttr != null ? styleAttr : "none") + ", ID: " + (idAttr != null ? idAttr : "none"));
        try {
            int start = elem.getStartOffset();
            int end = elem.getEndOffset();
            String text = elem.getDocument().getText(start, end - start).trim();
            if (!text.isEmpty()) {
                System.out.println(indent + "  Text: " + text);
            }
        } catch (Exception e) {
            System.out.println(indent + "  Text: [unreadable]");
        }
        for (int i = 0; i < elem.getElementCount(); i++) {
            printElementTree(elem.getElement(i), level + 1);
        }
    }

    private Element findMessageDiv(Element elem) {
        if (elem == null) return null;

        Stack<Element> stack = new Stack<>();
        Set<Element> visited = new HashSet<>();
        stack.push(elem);

        while (!stack.isEmpty()) {
            Element current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.getName().equals("div")) {
                Object idAttr = current.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.ID);
                Object styleAttr = current.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.STYLE);
                if (idAttr != null && idAttr.toString().startsWith("msg-")) {
                    return current;
                }
                if (styleAttr != null && styleAttr.toString().contains("border-radius")) {
                    return current;
                }
                if (styleAttr != null && styleAttr.toString().contains("text-align")) {
                    for (int i = 0; i < current.getElementCount(); i++) {
                        Element child = current.getElement(i);
                        Object childId = child.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.ID);
                        Object childStyle = child.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.STYLE);
                        if (childId != null && childId.toString().startsWith("msg-")) {
                            return child;
                        }
                        if (childStyle != null && childStyle.toString().contains("border-radius")) {
                            return child;
                        }
                    }
                }
            }

            // Handle p-implied
            if (current.getName().equals("p-implied") && current.getParentElement() != null) {
                stack.push(current.getParentElement());
            }

            for (int i = current.getElementCount() - 1; i >= 0; i--) {
                stack.push(current.getElement(i));
            }
            if (current.getParentElement() != null) {
                stack.push(current.getParentElement());
            }
        }
        return null;
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
            System.out.println("Selected receiver: " + currentReceiver);
            loadConversationHistory();
        } else {
            currentReceiver = "";
            receiverLabel.setText("Chọn người dùng hoặc nhóm để chat");
            clearChatArea();
        }
    }

    private void clearChatArea() {
        chatArea.setText("<html><body style='font-family: Arial; font-size: 14px; padding: 10px;'></body></html>");
        messages.clear();
        messageCounter = 0;
    }

    private void appendMessage(String sender, String message, boolean isSent, boolean isGroup, String timestamp) {
        message = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");

        String bubbleStyle = isSent ?
                "background-color: #0084ff; color: white; border-radius: 10px; padding: 8px 12px; margin: 5px; max-width: 60%; display: inline-block; text-align: left; box-shadow: 1px 1px 3px rgba(0,0,0,0.2);" :
                "background-color: #e9ecef; color: black; border-radius: 10px; padding: 8px 12px; margin: 5px; max-width: 60%; display: inline-block; text-align: left; box-shadow: 1px 1px 3px rgba(0,0,0,0.2);";
        String alignStyle = isSent ? "text-align: right;" : "text-align: left;";
        String senderName = isGroup && !isSent ? sender + ": " : "";
        String messageId = "msg-" + messageCounter++;
        String html = String.format(
                "<div style='%s'><div id='%s' style='%s'>%s%s</div></div>",
                alignStyle, messageId, bubbleStyle, senderName, message.replace("\n", "<br>")
        );

        HTMLDocument doc = (HTMLDocument) chatArea.getDocument();
        HTMLEditorKit kit = (HTMLEditorKit) chatArea.getEditorKit();
        try {
            kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
            System.out.println("Appended message: [" + timestamp + "] " + sender + ": " + message + ", ID: " + messageId);
            printElementTree(doc.getDefaultRootElement(), 0);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to append message: " + message);
        }

        messages.add(new MessageInfo(timestamp, sender, message, currentReceiver, messageId));

        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane) chatArea.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void deleteMessage() {
        int pos = chatArea.getCaretPosition();
        HTMLDocument doc = (HTMLDocument) chatArea.getDocument();
        Element elem = doc.getCharacterElement(pos);
        Element targetDiv = findMessageDiv(elem);
        if (targetDiv == null) {
            JOptionPane.showMessageDialog(this, "Không thể xác định tin nhắn để xóa.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Object idAttr = targetDiv.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.ID);
        String messageId = idAttr != null ? idAttr.toString() : null;
        String elemText = getElementText(targetDiv);

        MessageInfo messageToDelete = null;
        for (MessageInfo msg : messages) {
            String msgText = (msg.receiver.startsWith("GROUP_") && !msg.sender.equals(username) ? msg.sender + ": " : "") + msg.message;
            if (msgText.equals(elemText) && msg.receiver.equals(currentReceiver) && (messageId == null || msg.messageId.equals(messageId))) {
                messageToDelete = msg;
                break;
            }
        }

        if (messageToDelete == null) {
            System.out.println("No MessageInfo found for text: " + elemText + ", ID: " + messageId);
            JOptionPane.showMessageDialog(this, "Không tìm thấy tin nhắn để xóa.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        System.out.println("Attempting to delete: [" + messageToDelete.timestamp + "] " + messageToDelete.sender + ":" + messageToDelete.message);

        String fileName = getChatFileName(messageToDelete.sender, messageToDelete.receiver);
        String lineToDelete = "[" + messageToDelete.timestamp + "] " + messageToDelete.sender + ":" + messageToDelete.message;
        File file = new File(fileName);
        List<String> remainingLines = new ArrayList<>();
        boolean found = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.equals(lineToDelete)) {
                    remainingLines.add(line);
                } else {
                    found = true;
                    System.out.println("Found line in file: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi khi đọc file lịch sử: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (found) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (String line : remainingLines) {
                    writer.write(line);
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Lỗi khi cập nhật file lịch sử: " + e.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            messages.remove(messageToDelete);
            try {
                doc.removeElement(targetDiv.getParentElement());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Lỗi khi xóa tin nhắn khỏi giao diện: " + e.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JOptionPane.showMessageDialog(this, "Đã xóa tin nhắn.",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } else {
            System.out.println("Line not found in file: " + lineToDelete);
            JOptionPane.showMessageDialog(this, "Không tìm thấy tin nhắn trong file lịch sử.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getElementText(Element elem) {
        try {
            int start = elem.getStartOffset();
            int end = elem.getEndOffset();
            return chatArea.getDocument().getText(start, end - start).trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
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
                System.out.println("Received: " + message);
                if (message.startsWith("ONLINE_USERS_AND_GROUPS:")) {
                    updateOnlineUsersAndGroups(message.substring("ONLINE_USERS_AND_GROUPS:".length()));
                } else if (message.startsWith("FILE:")) {
                    receiveFile(message);
                } else if (message.startsWith("GROUP_CREATED:")) {
                    String timestamp = DATE_FORMAT.format(new Date());
                    appendMessage("Hệ thống", "Nhóm được tạo: " + message.substring("GROUP_CREATED:".length()), false, false, timestamp);
                } else if (message.startsWith("PRIVATE:")) {
                    String[] parts = message.split(":", 3);
                    if (parts.length == 3) {
                        String sender = parts[1];
                        String msg = parts[2];
                        if (!sender.equals(username)) {
                            String timestamp = DATE_FORMAT.format(new Date());
                            saveMessageToFile(sender, username, msg, timestamp);
                            if (currentReceiver != null && currentReceiver.equals(sender)) {
                                appendMessage(sender, msg, false, false, timestamp);
                            }
                        }
                    }
                } else if (message.startsWith("GROUP:")) {
                    String[] parts = message.split(":", 4);
                    if (parts.length == 4) {
                        String groupName = parts[1];
                        String sender = parts[2];
                        String msg = parts[3];
                        String timestamp = DATE_FORMAT.format(new Date());
                        saveMessageToFile(sender, groupName, msg, timestamp);
                        if (currentReceiver != null && currentReceiver.equals(groupName)) {
                            appendMessage(sender, msg, sender.equals(username), true, timestamp);
                        }
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
            String timestamp = DATE_FORMAT.format(new Date());
            if (currentReceiver.startsWith("GROUP_")) {
                out.println("GROUP:" + currentReceiver + ":" + message);
                saveMessageToFile(username, currentReceiver, message, timestamp);
            } else {
                out.println("PRIVATE:" + currentReceiver + ":" + message);
                appendMessage(username, message, true, false, timestamp);
                saveMessageToFile(username, currentReceiver, message, timestamp);
            }
            messageField.setText("");
        } else {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người dùng hoặc nhóm để gửi tin nhắn.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveMessageToFile(String sender, String receiver, String message, String timestamp) {
        try {
            String fileName = getChatFileName(sender, receiver);
            File file = new File(fileName);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            String formattedMessage = "[" + timestamp + "] " + sender + ":" + message;
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
                writer.write(formattedMessage);
                writer.newLine();
            }
            System.out.println("Saved to " + fileName + ": " + formattedMessage);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi khi lưu tin nhắn vào lịch sử: " + e.getMessage(),
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
                    out.flush();
                    FileInputStream fis = new FileInputStream(file);
                    OutputStream os = socket.getOutputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    fis.close();
                    os.flush();
                    String timestamp = DATE_FORMAT.format(new Date());
                    appendMessage(username, "Đã gửi file: " + file.getName(), true, false, timestamp);
                    saveMessageToFile(username, currentReceiver, "File: " + file.getName(), timestamp);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi khi gửi file: " + ex.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }).start();
        }
    }

    private void receiveFile(String message) {
        String[] parts = message.split(":");
        if (parts.length == 3) {
            String sender = parts[1];
            String fileName = parts[2];

            if (currentReceiver != null && currentReceiver.equals(sender)) {
                int response = JOptionPane.showConfirmDialog(this,
                        sender + " muốn gửi bạn file: " + fileName, "Nhận File", JOptionPane.YES_NO_OPTION);
                if (response == JOptionPane.YES_OPTION) {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setSelectedFile(new File(fileName));
                    if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File file = fileChooser.getSelectedFile();
                        new Thread(() -> {
                            try {
                                FileOutputStream fos = new FileOutputStream(file);
                                InputStream is = socket.getInputStream();
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = is.read(buffer)) > 0) {
                                    fos.write(buffer, 0, bytesRead);
                                    if (is.available() == 0) break;
                                }
                                fos.close();
                                String timestamp = DATE_FORMAT.format(new Date());
                                appendMessage(sender, "Đã nhận file: " + fileName, false, false, timestamp);
                                saveMessageToFile(sender, username, "File: " + fileName, timestamp);
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
    }

    private void showHistory(ActionEvent e) {
        HistoryDialog historyDialog = new HistoryDialog(this, username);
        historyDialog.setVisible(true);
    }

    private void createGroup(ActionEvent e) {
        JTextField groupNameField = new JTextField(20);
        JList<String> memberList = new JList<>(onlineUsersModel);
        memberList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Tên nhóm:"));
        panel.add(groupNameField);
        panel.add(new JLabel("Chọn thành viên:"));
        panel.add(new JScrollPane(memberList));

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

    private String getChatFileName(String sender, String receiver) {
        if (receiver.startsWith("GROUP_")) {
            return HISTORY_DIR + "/" + receiver + ".txt";
        }
        String[] names = new String[]{sender, receiver};
        Arrays.sort(names);
        return HISTORY_DIR + "/" + names[0] + "_" + names[1] + ".txt";
    }

    private void loadConversationHistory() {
        clearChatArea();
        if (currentReceiver != null && !currentReceiver.isEmpty()) {
            String filePath = getChatFileName(username, currentReceiver);
            System.out.println("Loading history from: " + filePath);
            File file = new File(filePath);
            Set<String> displayedMessages = new HashSet<>();
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty() && !displayedMessages.contains(line)) {
                            try {
                                if (line.startsWith("[")) {
                                    int timestampEnd = line.indexOf(']');
                                    if (timestampEnd != -1) {
                                        String timestamp = line.substring(1, timestampEnd);
                                        int senderEnd = line.indexOf(':', timestampEnd);
                                        if (senderEnd != -1) {
                                            String sender = line.substring(timestampEnd + 2, senderEnd).trim();
                                            String message = line.substring(senderEnd + 1).trim();
                                            boolean isSent = sender.equals(username);
                                            boolean isGroup = currentReceiver.startsWith("GROUP_");
                                            appendMessage(sender, message, isSent, isGroup, timestamp);
                                            displayedMessages.add(line);
                                            System.out.println("Loaded: " + line);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("Error parsing line: " + line);
                            }
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Lỗi khi đọc lịch sử chat: " + ex.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                System.out.println("History file not found: " + filePath);
                JOptionPane.showMessageDialog(this, "Không tìm thấy lịch sử chat cho " + currentReceiver,
                        "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            System.out.println("No valid receiver selected");
        }
    }
}