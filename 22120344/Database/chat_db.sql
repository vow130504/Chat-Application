-- Tạo cơ sở dữ liệu
DROP DATABASE IF EXISTS chat_db;
CREATE DATABASE IF NOT EXISTS chat_db;
USE chat_db;

-- Tạo bảng users
CREATE TABLE users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tạo bảng chat_groups
CREATE TABLE chat_groups (
    group_name VARCHAR(50) PRIMARY KEY,
    members TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tạo bảng messages
CREATE TABLE messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50) NOT NULL,
    message TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tạo chỉ mục để tối ưu hóa truy vấn
CREATE INDEX idx_sender_receiver ON messages (sender, receiver);

-- Chèn dữ liệu mẫu cho bảng users
INSERT INTO users (username, password) VALUES
('user1', 'pass1'),
('user2', 'pass2'),
('user3', 'pass3');

-- Chèn dữ liệu mẫu cho bảng chat_groups
INSERT INTO chat_groups (group_name, members) VALUES
('GROUP_myGroup', 'user1,user2,user3');

-- Chèn dữ liệu mẫu cho bảng messages
INSERT INTO messages (sender, receiver, message, timestamp) VALUES
('user1', 'user2', 'Hello user2!', '2025-05-30 19:00:00'),
('user2', 'user1', 'Hi user1!', '2025-05-30 19:01:00'),
('user1', 'GROUP_myGroup', 'Hello group!', '2025-05-30 19:02:00');