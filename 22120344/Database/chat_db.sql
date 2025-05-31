-- Xóa cơ sở dữ liệu cũ
DROP DATABASE IF EXISTS chat_db;
CREATE DATABASE chat_db;
USE chat_db;

-- Bảng users: Lưu thông tin người dùng
CREATE TABLE users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    is_online BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_online (is_online)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Bảng chat_groups: Lưu thông tin nhóm
CREATE TABLE chat_groups (
    group_id INT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(50) UNIQUE NOT NULL,
    creator VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (creator) REFERENCES users(username),
    INDEX idx_group_name (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Bảng group_members: Lưu thành viên của nhóm
CREATE TABLE group_members (
    group_id INT,
    username VARCHAR(50),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, username),
    FOREIGN KEY (group_id) REFERENCES chat_groups(group_id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Bảng messages: Lưu tin nhắn
CREATE TABLE messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50) NOT NULL,
    message_type ENUM('TEXT', 'FILE') DEFAULT 'TEXT' NOT NULL,
    message_content TEXT NOT NULL,
    file_path VARCHAR(255),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sender) REFERENCES users(username),
    INDEX idx_sender_receiver (sender, receiver),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Chèn dữ liệu mẫu
INSERT INTO users (username, password, is_online) VALUES
('user1', '123', FALSE),
('user2', '123', FALSE),
('user3', '123', FALSE);

INSERT INTO messages (sender, receiver, message_type, message_content, timestamp) VALUES
('user1', 'user2', 'TEXT', 'Hello user2!', '2025-05-30 19:00:00'),
('user2', 'user1', 'TEXT', 'Hi user1!', '2025-05-30 19:01:00'),
('user1', 'myGroup', 'TEXT', 'Hello group!', '2025-05-30 19:02:00');