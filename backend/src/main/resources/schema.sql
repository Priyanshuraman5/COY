CREATE TABLE IF NOT EXISTS profiles (
  id INT PRIMARY KEY, name VARCHAR(80), age INT, photo VARCHAR(255),
  bio VARCHAR(255), city VARCHAR(80), interests VARCHAR(255));
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, phone VARCHAR(20) UNIQUE NOT NULL,
  email VARCHAR(120), email_verified BOOLEAN DEFAULT FALSE NOT NULL,
  invite_code VARCHAR(12) UNIQUE, referred_by BIGINT, bonus_slots INT DEFAULT 0 NOT NULL);
CREATE TABLE IF NOT EXISTS email_otps (
  user_id BIGINT PRIMARY KEY, email VARCHAR(120) NOT NULL, code VARCHAR(6) NOT NULL, expires_at BIGINT NOT NULL);
-- A private "crush list". Nobody can read another user's list.
CREATE TABLE IF NOT EXISTS crushes (
  user_id BIGINT NOT NULL, phone VARCHAR(20) NOT NULL, added_at BIGINT DEFAULT 0 NOT NULL,
  PRIMARY KEY (user_id, phone), FOREIGN KEY (user_id) REFERENCES users(id));
-- Ghost chat between two users who are a mutual crush. user_a < user_b.
CREATE TABLE IF NOT EXISTS chats (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, user_a BIGINT NOT NULL, user_b BIGINT NOT NULL,
  alias_a VARCHAR(40) NOT NULL, alias_b VARCHAR(40) NOT NULL, expires_at BIGINT NOT NULL,
  reveal_a BOOLEAN DEFAULT FALSE NOT NULL, reveal_b BOOLEAN DEFAULT FALSE NOT NULL,
  UNIQUE (user_a, user_b), FOREIGN KEY (user_a) REFERENCES users(id), FOREIGN KEY (user_b) REFERENCES users(id));
CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, chat_id BIGINT NOT NULL, sender_id BIGINT NOT NULL,
  body VARCHAR(500) NOT NULL, sent_at BIGINT NOT NULL, FOREIGN KEY (chat_id) REFERENCES chats(id));
CREATE TABLE IF NOT EXISTS otps (phone VARCHAR(20) PRIMARY KEY, code VARCHAR(6) NOT NULL, expires_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS sessions (token VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(id));
CREATE TABLE IF NOT EXISTS swipes (
  user_id BIGINT NOT NULL, target_id INT NOT NULL, direction VARCHAR(4) NOT NULL,
  PRIMARY KEY (user_id, target_id),
  FOREIGN KEY (user_id) REFERENCES users(id), FOREIGN KEY (target_id) REFERENCES profiles(id));
-- Seeded profiles that already liked every new user. Never returned by any endpoint.
CREATE TABLE IF NOT EXISTS incoming_likes (
  profile_id INT PRIMARY KEY, FOREIGN KEY (profile_id) REFERENCES profiles(id));
CREATE TABLE IF NOT EXISTS matches (
  user_id BIGINT NOT NULL, profile_id INT NOT NULL, PRIMARY KEY (user_id, profile_id),
  FOREIGN KEY (user_id) REFERENCES users(id), FOREIGN KEY (profile_id) REFERENCES profiles(id));
