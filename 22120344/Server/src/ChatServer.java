package Server.src;

import Server.src.Controller.DatabaseConnection;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 8080;
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static ServerSocket serverSocket;
    private static final String HISTORY_DIR = "server_files/chat_history";
    private static final String UPLOAD_DIR = "server_files/uploads";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        try {
            File historyDir = new File(HISTORY_DIR);
            if (!historyDir.exists()) {
                historyDir.mkdirs();
            }
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            DatabaseConnection.connect(
                    "jdbc:mysql://localhost:3306/chat_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC",
                    "root", "Nmt2004.");
            System.out.println("Server started with database connection");
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            return;
        }

        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Chat Server is running on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getChatFileName(String sender, String receiver) {
        if (receiver.startsWith("GROUP_")) {
            return HISTORY_DIR + "/" + receiver + ".txt";
        }
        String[] names = new String[]{sender, receiver};
        Arrays.sort(names);
        return HISTORY_DIR + "/" + names[0] + "_" + names[1] + ".txt";
    }

    private static void saveMessageToFile(String sender, String receiver, String messageContent, String fileName) {
        try {
            String filePath = getChatFileName(sender, receiver);
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            String message = "[" + DATE_FORMAT.format(new Date()) + "] " + sender + ":" +
                    (fileName != null ? "File: " + fileName : messageContent);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
                writer.write(message);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        private void updateOnlineStatus(String username, boolean status) throws SQLException {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE users SET is_online = ? WHERE username = ?")) {
                stmt.setBoolean(1, status);
                stmt.setString(2, username);
                stmt.executeUpdate();
            }
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                username = in.readLine();
                if (username == null) return;

                if (!isUsernameValid(username)) {
                    out.println("ERROR: Tên người dùng không hợp lệ");
                    return;
                }

                clients.put(username, this);
                try {
                    updateOnlineStatus(username, true);
                } catch (SQLException e) {
                    System.err.println("Error updating online status: " + e.getMessage());
                    return;
                }
                broadcast("SERVER: " + username + " đã tham gia chat", username);
                sendOnlineUsersAndGroups();

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received from " + username + ": " + message);
                    if (message.startsWith("PRIVATE:")) {
                        handlePrivateMessage(message);
                    } else if (message.startsWith("GROUP:")) {
                        handleGroupMessage(message);
                    } else if (message.startsWith("FILE:")) {
                        handleFileTransfer(message);
                    } else if (message.startsWith("CREATE_GROUP:")) {
                        handleCreateGroup(message);
                    } else if (message.startsWith("DOWNLOAD:")) {
                        handleFileDownload(message);
                    } else {
                        sendMessage("ERROR: Vui lòng chọn người nhận hoặc nhóm để gửi tin nhắn.");
                    }
                }
            } catch (IOException e) {
                System.err.println("Lỗi với client " + username + ": " + e.getMessage());
            } finally {
                if (username != null) {
                    clients.remove(username);
                    try {
                        updateOnlineStatus(username, false);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    broadcast("SERVER: " + username + " đã rời chat", username);
                    sendOnlineUsersAndGroups();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean isUsernameValid(String username) throws IOException {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT username FROM users WHERE username = ?")) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                out.println("ERROR: Lỗi cơ sở dữ liệu");
                e.printStackTrace();
                return false;
            }
        }

        private void handlePrivateMessage(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String receiver = parts[1];
                String msg = parts[2];

                ClientHandler receiverHandler = clients.get(receiver);
                if (receiverHandler != null) {
                    receiverHandler.sendMessage("PRIVATE:" + username + ":" + msg);
                    saveMessageToFile(username, receiver, msg, null);
                } else {
                    sendMessage("ERROR: Người dùng " + receiver + " đang offline");
                }
            }
        }

        private void handleGroupMessage(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String groupName = parts[1];
                String msg = parts[2];

                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                             "SELECT username FROM group_members gm JOIN chat_groups cg ON gm.group_id = cg.group_id " +
                                     "WHERE cg.group_name = ?")) {
                    stmt.setString(1, groupName);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        String member = rs.getString("username").trim();
                        ClientHandler memberHandler = clients.get(member);
                        if (memberHandler != null) {
                            memberHandler.sendMessage("GROUP:" + groupName + ":" + username + ":" + msg);
                        }
                    }
                    saveMessageToFile(username, groupName, msg, null);
                } catch (SQLException e) {
                    sendMessage("ERROR: Nhóm " + groupName + " không tồn tại");
                    e.printStackTrace();
                }
            }
        }

        private void handleFileTransfer(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String receiver = parts[1];
                String fileName = parts[2];

                try {
                    // Save file to server
                    File file = new File(UPLOAD_DIR + "/" + fileName);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        InputStream is = socket.getInputStream();
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, bytesRead);
                            if (is.available() == 0) break;
                        }
                    }

                    ClientHandler receiverHandler = clients.get(receiver);
                    if (receiverHandler != null) {
                        receiverHandler.sendMessage("FILE:" + username + ":" + fileName);
                        saveMessageToFile(username, receiver, null, fileName);
                    } else {
                        sendMessage("ERROR: Người dùng " + receiver + " đang offline");
                    }
                } catch (IOException e) {
                    sendMessage("ERROR: Lỗi khi lưu file");
                    e.printStackTrace();
                }
            }
        }

        private void handleFileDownload(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length == 2) {
                String fileName = parts[1];
                File file = new File(UPLOAD_DIR + "/" + fileName);
                if (file.exists()) {
                    try {
                        sendMessage("DOWNLOAD:" + fileName);
                        try (FileInputStream fis = new FileInputStream(file)) {
                            OutputStream os = socket.getOutputStream();
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                            os.flush();
                        }
                    } catch (IOException e) {
                        sendMessage("ERROR: Lỗi khi gửi file");
                        e.printStackTrace();
                    }
                } else {
                    sendMessage("ERROR: File không tồn tại trên server");
                }
            }
        }

        private void handleCreateGroup(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String groupName = parts[1];
                String[] members = parts[2].split(",");

                try (Connection conn = DatabaseConnection.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO chat_groups (group_name, creator) VALUES (?, ?)",
                            Statement.RETURN_GENERATED_KEYS);
                    stmt.setString(1, groupName);
                    stmt.setString(2, username);
                    stmt.executeUpdate();

                    ResultSet rs = stmt.getGeneratedKeys();
                    int groupId = 0;
                    if (rs.next()) {
                        groupId = rs.getInt(1);
                    }

                    PreparedStatement memberStmt = conn.prepareStatement(
                            "INSERT INTO group_members (group_id, username) VALUES (?, ?)");
                    for (String member : members) {
                        memberStmt.setInt(1, groupId);
                        memberStmt.setString(2, member.trim());
                        memberStmt.executeUpdate();
                    }

                    broadcast("GROUP_CREATED:" + groupName, null);
                    sendOnlineUsersAndGroups();
                } catch (SQLException e) {
                    sendMessage("ERROR: Lỗi khi tạo nhóm");
                    e.printStackTrace();
                }
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }

    public static void broadcast(String message, String excludeUser) {
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(excludeUser)) {
                entry.getValue().sendMessage(message);
            }
        }
    }

    public static void sendOnlineUsersAndGroups() {
        StringBuilder onlineUsers = new StringBuilder();
        for (String user : clients.keySet()) {
            onlineUsers.append(user).append(",");
        }

        StringBuilder groups = new StringBuilder();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT group_name FROM chat_groups")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                groups.append(rs.getString("group_name")).append(",");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String message = "ONLINE_USERS_AND_GROUPS:" + onlineUsers + ";" + groups;
        broadcast(message, null);
    }
}