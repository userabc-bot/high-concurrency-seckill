CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seckill;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_seckill_voucher
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    title      VARCHAR(128)   NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    stock      INT            NOT NULL,
    begin_time DATETIME       NOT NULL,
    end_time   DATETIME       NOT NULL,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS t_seckill_order
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no    VARCHAR(64)    NOT NULL,
    voucher_id  BIGINT         NOT NULL,
    user_id     BIGINT         NOT NULL,
    amount      DECIMAL(10, 2) NOT NULL,
    status      TINYINT        NOT NULL DEFAULT 1,
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_voucher_user (voucher_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO t_seckill_voucher (id, title, price, stock, begin_time, end_time)
VALUES (1, 'iPhone 16 Pro 秒杀', 4999.00, 100, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 365 DAY))
ON DUPLICATE KEY UPDATE title = VALUES(title);
