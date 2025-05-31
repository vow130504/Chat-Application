import Controller.DatabaseConnection;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.println("SUBMIT_USERNAME");
                username = in.readLine();
                if (username == null) return;
                clients.put(username, out);
                broadcast(username + " đã tham gia chat!");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("PRIVATE:")) {
                        String[] parts = message.split(":", 3);
                        sendPrivateMessage(parts[1], parts[2], username);
                    } else if (message.startsWith("GROUP:")) {
                        String[] parts = message.split(":", 3);
                        sendGroupMessage(parts[1], parts[2], username);
                    } else if (message.startsWith("FILE:")) {
                        String[] parts = message.split(":", 3);
                        sendFile(parts[1], parts[2], username);
                    } else {
                        broadcast(username + ": " + message);
                    }
                    saveMessage(username, message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (username != null) {
                    clients.remove(username);
                    broadcast(username + " đã rời chat!");
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void broadcast(String message) {
            for (PrintWriter client : clients.values()) {
                client.println(message);
            }
        }

        private void sendPrivateMessage(String receiver, String message, String sender) {
            PrintWriter receiverOut = clients.get(receiver);
            if (receiverOut != null) {
                receiverOut.println("PRIVATE:" + sender + ":" + message);
                saveMessage(sender, "PRIVATE:" + receiver + ":" + message);
            }
        }

        private void sendGroupMessage(String groupName, String message, String sender) {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT members FROM chat_groups WHERE group_name = ?")) {
                stmt.setString(1, groupName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String[] members = rs.getString("members").split(",");
                    for (String member : members) {
                        PrintWriter memberOut = clients.get(member.trim());
                        if (memberOut != null) {
                            memberOut.println("GROUP:" + groupName + ":" + sender + ":" + message);
                        }
                    }
                    saveMessage(sender, "GROUP:" + groupName + ":" + message);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void sendFile(String receiver, String fileName, String sender) {
            try {
                File file = new File("Uploads/" + fileName);
                file.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                InputStream is = socket.getInputStream();
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    if (is.available() == 0) break; // Kết thúc file
                }
                fos.close();
                PrintWriter receiverOut = clients.get(receiver);
                if (receiverOut != null) {
                    receiverOut.println("FILE:" + sender + ":" + fileName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void saveMessage(String sender, String message) {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("INSERT INTO messages (sender, receiver, message) VALUES (?, ?, ?)")) {
                String receiver = message.startsWith("PRIVATE:") ? message.split(":")[1] :
                        message.startsWith("GROUP:") ? message.split(":")[1] : "ALL";
                String msgContent = message.contains(":") ? message.substring(message.indexOf(":", message.indexOf(":") + 1) + 1) : message;
                stmt.setString(1, sender);
                stmt.setString(2, receiver);
                stmt.setString(3, msgContent);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}