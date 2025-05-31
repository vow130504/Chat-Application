package Client.src.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class HistoryDialog extends JDialog {
    private JTable historyTable;
    private JButton deleteButton;
    private DefaultTableModel tableModel;
    private String username;
    private static final String HISTORY_DIR = "chat_history";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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

        String[] columns = {"Thời gian", "Người gửi", "Người nhận", "Nội dung"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = new JTable(tableModel);
        historyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Thêm menu ngữ cảnh khi nhấp chuột phải
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Xóa");
        deleteItem.addActionListener(e -> deleteSingleMessage());
        popupMenu.add(deleteItem);

        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() && historyTable.getSelectedRowCount() == 1) {
                    int row = historyTable.rowAtPoint(e.getPoint());
                    historyTable.setRowSelectionInterval(row, row);
                    popupMenu.show(historyTable, e.getX(), e.getY());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(historyTable);

        deleteButton = new JButton("Xóa tin nhắn đã chọn");
        deleteButton.addActionListener(e -> deleteSelectedMessages());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(deleteButton);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private String getChatFileName(String sender, String receiver) {
        if (receiver.startsWith("GROUP_")) {
            return HISTORY_DIR + "/" + receiver + ".txt";
        }
        String[] names = new String[]{sender, receiver};
        Arrays.sort(names);
        return HISTORY_DIR + "/" + names[0] + "_" + names[1] + ".txt";
    }

    private void loadHistory() {
        tableModel.setRowCount(0);
        File historyDir = new File(HISTORY_DIR);
        if (historyDir.exists()) {
            File[] files = historyDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName().replace(".txt", "");
                    String receiver = null;
                    boolean isGroup = fileName.startsWith("GROUP_");
                    if (isGroup) {
                        receiver = fileName;
                    } else {
                        String[] users = fileName.split("_");
                        if (users.length == 2 && (users[0].equals(username) || users[1].equals(username))) {
                            receiver = users[0].equals(username) ? users[1] : users[0];
                        }
                    }
                    if (receiver != null) {
                        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String[] parts = line.split(" ", 3);
                                if (parts.length >= 3) {
                                    String timestamp = parts[0].substring(1) + " " + parts[1];
                                    String sender = parts[2].split(":")[0];
                                    String message = parts[2].substring(parts[2].indexOf(":") + 1);
                                    tableModel.addRow(new Object[]{timestamp, sender, receiver, message});
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void deleteSingleMessage() {
        int selectedRow = historyTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tin nhắn để xóa.",
                    "Thông tin", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String sender = (String) tableModel.getValueAt(selectedRow, 1);
        String receiver = (String) tableModel.getValueAt(selectedRow, 2);
        String timestamp = (String) tableModel.getValueAt(selectedRow, 0);
        String message = (String) tableModel.getValueAt(selectedRow, 3);
        String fileName = getChatFileName(sender, receiver);
        String lineToDelete = "[" + timestamp + "] " + sender + ":" + message;

        // Xóa dòng khỏi file lịch sử
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

            // Xóa dòng khỏi bảng
            tableModel.removeRow(selectedRow);
            JOptionPane.showMessageDialog(this, "Đã xóa tin nhắn.",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Không tìm thấy tin nhắn trong file lịch sử.",
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

        Map<String, List<String>> messagesByFile = new HashMap<>();
        for (int row : selectedRows) {
            String sender = (String) tableModel.getValueAt(row, 1);
            String receiver = (String) tableModel.getValueAt(row, 2);
            String timestamp = (String) tableModel.getValueAt(row, 0);
            String message = (String) tableModel.getValueAt(row, 3);
            String fileName = getChatFileName(sender, receiver);
            String line = "[" + timestamp + "] " + sender + ":" + message;
            messagesByFile.computeIfAbsent(fileName, k -> new ArrayList<>()).add(line);
        }

        for (Map.Entry<String, List<String>> entry : messagesByFile.entrySet()) {
            String fileName = entry.getKey();
            List<String> messagesToDelete = entry.getValue();
            File file = new File(fileName);
            List<String> remainingLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!messagesToDelete.contains(line)) {
                        remainingLines.add(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (String line : remainingLines) {
                    writer.write(line);
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        JOptionPane.showMessageDialog(this, "Đã xóa các tin nhắn đã chọn.",
                "Thành công", JOptionPane.INFORMATION_MESSAGE);
        loadHistory();
    }
}