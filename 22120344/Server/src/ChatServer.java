package Server.src;

import Server.src.Controller.DatabaseConnection;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 8080;
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        // Initialize database connection
        try {
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

                // Get username
                username = in.readLine();
                if (username == null) return;

                // Validate username
                if (!isUsernameValid(username)) {
                    out.println("ERROR: Invalid username");
                    return;
                }

                // Add to clients map
                clients.put(username, this);
                try {
                    updateOnlineStatus(username, true);
                } catch (SQLException e) {
                    System.err.println("Error updating online status: " + e.getMessage());
                    return;
                }
                broadcast("SERVER: " + username + " has joined the chat", username);
                sendOnlineUsers();


                // Handle messages
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("PRIVATE:")) {
                        handlePrivateMessage(message);
                    } else if (message.startsWith("GROUP:")) {
                        handleGroupMessage(message);
                    } else if (message.startsWith("FILE:")) {
                        handleFileTransfer(message);
                    } else {
                        broadcast(username + ": " + message, username);
                        saveMessage(username, "ALL", message);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error with client " + username + ": " + e.getMessage());
            } finally {
                if (username != null) {
                    clients.remove(username);
                    try {
                        updateOnlineStatus(username, false);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    broadcast("SERVER: " + username + " has left the chat", username);
                    sendOnlineUsers();
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
                out.println("ERROR: Database error");
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
                    saveMessage(username, receiver, msg);
                } else {
                    sendMessage("ERROR: User " + receiver + " is offline");
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
                             "SELECT members FROM chat_groups WHERE group_name = ?")) {

                    stmt.setString(1, groupName);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        String[] members = rs.getString("members").split(",");
                        for (String member : members) {
                            member = member.trim();
                            if (!member.equals(username)) {
                                ClientHandler memberHandler = clients.get(member);
                                if (memberHandler != null) {
                                    memberHandler.sendMessage("GROUP:" + groupName + ":" + username + ":" + msg);
                                }
                            }
                        }
                        saveMessage(username, groupName, msg);
                    } else {
                        sendMessage("ERROR: Group " + groupName + " doesn't exist");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleFileTransfer(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String receiver = parts[1];
                String fileName = parts[2];

                ClientHandler receiverHandler = clients.get(receiver);
                if (receiverHandler != null) {
                    receiverHandler.sendMessage("FILE:" + username + ":" + fileName);
                    // File content will be sent directly through the socket
                } else {
                    sendMessage("ERROR: User " + receiver + " is offline");
                }
            }
        }

        private void saveMessage(String sender, String receiver, String message) {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO messages (sender, receiver, message) VALUES (?, ?, ?)")) {

                stmt.setString(1, sender);
                stmt.setString(2, receiver);
                stmt.setString(3, message);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
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

    public static void sendOnlineUsers() {
        StringBuilder onlineUsers = new StringBuilder("ONLINE_USERS:");
        for (String user : clients.keySet()) {
            onlineUsers.append(user).append(",");
        }
        broadcast(onlineUsers.toString(), null);
    }
}
