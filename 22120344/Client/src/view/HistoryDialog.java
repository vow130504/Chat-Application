package Client.src.view;

import Client.src.Controller.DatabaseConnection;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class HistoryDialog extends JDialog {
    private JTable historyTable;
    private JButton deleteButton;
    private DefaultTableModel tableModel;

    public HistoryDialog(JFrame parent, String username) {
        super(parent, "Chat History - " + username, true);
        setSize(600, 400);
        setLocationRelativeTo(parent);
        setIconImage(new ImageIcon("image/icon.png").getImage()); // Thêm icon

        initUI(username);
        loadHistory(username);
    }

    private void initUI(String username) {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Tạo JTable để hiển thị lịch sử
        String[] columns = {"ID", "Timestamp", "Sender", "Receiver", "Message"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Không cho phép chỉnh sửa
            }
        };
        historyTable = new JTable(tableModel);
        historyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(historyTable);

        deleteButton = new JButton("Delete Selected");
        deleteButton.addActionListener(e -> deleteSelectedMessages(username));

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(deleteButton);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void loadHistory(String username) {
        tableModel.setRowCount(0); // Xóa dữ liệu cũ
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, sender, receiver, message, timestamp FROM messages " +
                             "WHERE receiver = ? OR sender = ? OR receiver = 'ALL' ORDER BY timestamp DESC")) {

            stmt.setString(1, username);
            stmt.setString(2, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getTimestamp("timestamp"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("message")
                });
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading history: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedMessages(String username) {
        int[] selectedRows = historyTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select at least one message to delete.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM messages WHERE id = ?")) {
            for (int row : selectedRows) {
                int messageId = (int) tableModel.getValueAt(row, 0); // Lấy ID từ cột đầu tiên
                stmt.setInt(1, messageId);
                stmt.executeUpdate();
            }
            JOptionPane.showMessageDialog(this, "Selected messages deleted.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            loadHistory(username); // Tải lại lịch sử
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error deleting messages: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}