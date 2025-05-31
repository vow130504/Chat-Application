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
    private String username;

    public HistoryDialog(JFrame parent, String username) {
        super(parent, "Lịch sử chat - " + username, true);
        this.username = username;
        setSize(600, 400);
        setLocationRelativeTo(parent);
        setIconImage(new ImageIcon("image/icon.png").getImage());

        initUI();
        loadHistory();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] columns = {"ID", "Thời gian", "Người gửi", "Người nhận", "Loại", "Nội dung", "File"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = new JTable(tableModel);
        historyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(historyTable);

        deleteButton = new JButton("Xóa tin nhắn đã chọn");
        deleteButton.addActionListener(e -> deleteSelectedMessages());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(deleteButton);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void loadHistory() {
        tableModel.setRowCount(0);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, sender, receiver, message_type, message_content, file_path, timestamp " +
                             "FROM messages WHERE receiver = ? OR sender = ? OR receiver IN " +
                             "(SELECT group_name FROM chat_groups cg JOIN group_members gm ON cg.group_id = gm.group_id " +
                             "WHERE gm.username = ?) ORDER BY timestamp DESC")) {

            stmt.setString(1, username);
            stmt.setString(2, username);
            stmt.setString(3, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getTimestamp("timestamp"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("message_type"),
                        rs.getString("message_content"),
                        rs.getString("file_path") != null ? rs.getString("file_path") : ""
                });
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi khi tải lịch sử: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedMessages() {
        int[] selectedRows = historyTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn ít nhất một tin nhắn để xóa.",
                    "Thông tin", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM messages WHERE id = ?")) {
            for (int row : selectedRows) {
                int messageId = (int) tableModel.getValueAt(row, 0);
                stmt.setInt(1, messageId);
                stmt.executeUpdate();
            }
            JOptionPane.showMessageDialog(this, "Đã xóa các tin nhắn đã chọn.",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
            loadHistory();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi xóa tin nhắn: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}