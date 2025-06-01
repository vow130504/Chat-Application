package Client.src.Controller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static Connection connection = null;
    private static String url;
    private static String user;
    private static String password;

    public static void connect(String url, String user, String password) throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            DatabaseConnection.url = url;
            DatabaseConnection.user = user;
            DatabaseConnection.password = password;
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Database connection successful!");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found.");
            throw new SQLException("MySQL JDBC Driver not found.", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            if (url == null || user == null || password == null) {
                throw new SQLException("Database parameters not set. Call connect() first.");
            }
            try {
                connection = DriverManager.getConnection(url, user, password);
                System.out.println("Re-established database connection!");
            } catch (SQLException e) {
                System.err.println("Failed to re-establish connection: " + e.getMessage());
                throw e;
            }
        }
        return connection;
    }

    public static boolean testConnection() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Database connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
            connection = null;
        }
    }
}