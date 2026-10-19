CREATE DATABASE IF NOT EXISTS `ticket` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ticket`;

DROP TABLE IF EXISTS `city`;
CREATE TABLE `city` (
                        `id` bigint NOT NULL AUTO_INCREMENT,
                        `name` varchar(50) NOT NULL,
                        `code` varchar(20) NOT NULL,
                        `province` varchar(50) NOT NULL,
                        `hot_level` tinyint DEFAULT '0',
                        `status` tinyint DEFAULT '1',
                        `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                        `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `email_verification`;
CREATE TABLE `email_verification` (
                                      `id` bigint NOT NULL AUTO_INCREMENT,
                                      `email` varchar(100) NOT NULL,
                                      `code` varchar(10) NOT NULL,
                                      `type` tinyint NOT NULL,
                                      `is_used` tinyint DEFAULT '0',
                                      `expire_time` datetime NOT NULL,
                                      `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY (`id`),
                                      KEY `idx_email` (`email`),
                                      KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `event_category`;
CREATE TABLE `event_category` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `name` varchar(50) NOT NULL,
                                  `sort_order` int DEFAULT '0',
                                  `status` tinyint DEFAULT '1',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `event`;
CREATE TABLE `event` (
                         `id` bigint NOT NULL AUTO_INCREMENT,
                         `name` varchar(200) NOT NULL,
                         `artist_name` varchar(100) DEFAULT NULL,
                         `category_id` bigint NOT NULL,
                         `city_id` bigint NOT NULL,
                         `venue` varchar(200) NOT NULL,
                         `cover_image` varchar(500) DEFAULT NULL,
                         `description` text,
                         `status` tinyint NOT NULL DEFAULT '1',
                         `sale_start_time` datetime NOT NULL,
                         `sale_end_time` datetime NOT NULL,
                         `event_start_time` datetime NOT NULL,
                         `event_end_time` datetime NOT NULL,
                         `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                         `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         PRIMARY KEY (`id`),
                         KEY `idx_category_city` (`category_id`,`city_id`),
                         KEY `idx_artist` (`artist_name`),
                         KEY `idx_status_time` (`status`,`event_start_time`),
                         KEY `fk_event_city` (`city_id`),
                         CONSTRAINT `fk_event_category` FOREIGN KEY (`category_id`) REFERENCES `event_category` (`id`),
                         CONSTRAINT `fk_event_city` FOREIGN KEY (`city_id`) REFERENCES `city` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `event_session`;
CREATE TABLE `event_session` (
                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                 `event_id` bigint NOT NULL,
                                 `session_name` varchar(100) NOT NULL,
                                 `session_time` datetime NOT NULL,
                                 `status` tinyint DEFAULT '1',
                                 `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`),
                                 KEY `idx_event` (`event_id`),
                                 CONSTRAINT `fk_session_event` FOREIGN KEY (`event_id`) REFERENCES `event` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `ticket_tier`;
CREATE TABLE `ticket_tier` (
                               `id` bigint NOT NULL AUTO_INCREMENT,
                               `event_id` bigint NOT NULL,
                               `session_id` bigint NOT NULL,
                               `tier_name` varchar(100) NOT NULL,
                               `original_price` decimal(10,2) NOT NULL,
                               `current_price` decimal(10,2) NOT NULL,
                               `total_stock` int NOT NULL,
                               `available_stock` int NOT NULL,
                               `status` tinyint DEFAULT '1',
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                               `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               PRIMARY KEY (`id`),
                               KEY `idx_event_session` (`event_id`,`session_id`),
                               KEY `fk_tier_session` (`session_id`),
                               CONSTRAINT `fk_tier_event` FOREIGN KEY (`event_id`) REFERENCES `event` (`id`) ON DELETE CASCADE,
                               CONSTRAINT `fk_tier_session` FOREIGN KEY (`session_id`) REFERENCES `event_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
                        `id` bigint NOT NULL AUTO_INCREMENT,
                        `username` varchar(50) NOT NULL,
                        `password` varchar(100) NOT NULL,
                        `email` varchar(100) DEFAULT NULL,
                        `phone` varchar(20) DEFAULT NULL,
                        `nickname` varchar(50) DEFAULT NULL,
                        `avatar` varchar(255) DEFAULT NULL,
                        `real_name` varchar(50) DEFAULT NULL,
                        `id_card` varchar(20) DEFAULT NULL,
                        `gender` tinyint DEFAULT '0',
                        `birthday` date DEFAULT NULL,
                        `status` tinyint DEFAULT '1',
                        `last_login_time` datetime DEFAULT NULL,
                        `last_login_ip` varchar(50) DEFAULT NULL,
                        `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                        `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        `open_id` varchar(100) DEFAULT NULL,
                        `union_id` varchar(100) DEFAULT NULL,
                        `wx_nickname` varchar(100) DEFAULT NULL,
                        `wx_avatar` varchar(500) DEFAULT NULL,
                        `role` varchar(10) DEFAULT 'USER',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_username` (`username`),
                        UNIQUE KEY `uk_email` (`email`),
                        UNIQUE KEY `uk_phone` (`phone`),
                        UNIQUE KEY `uk_open_id` (`open_id`),
                        KEY `idx_create_time` (`create_time`),
                        KEY `idx_status` (`status`),
                        KEY `idx_user_role` (`role`),
                        KEY `idx_user_status` (`status`),
                        KEY `idx_user_email` (`email`),
                        KEY `idx_user_phone` (`phone`),
                        KEY `idx_user_username` (`username`),
                        KEY `idx_user_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_address`;
CREATE TABLE `user_address` (
                                `id` bigint NOT NULL AUTO_INCREMENT,
                                `user_id` bigint NOT NULL,
                                `receiver_name` varchar(50) NOT NULL,
                                `receiver_phone` varchar(20) NOT NULL,
                                `province` varchar(50) NOT NULL,
                                `city` varchar(50) NOT NULL,
                                `district` varchar(50) NOT NULL,
                                `detail_address` varchar(255) NOT NULL,
                                `postal_code` varchar(10) DEFAULT NULL,
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                `is_default` tinyint DEFAULT '0',
                                PRIMARY KEY (`id`),
                                KEY `idx_user_id` (`user_id`),
                                KEY `idx_user_default` (`user_id`),
                                KEY `idx_user_address_user_id` (`user_id`),
                                KEY `idx_user_address_is_default` (`is_default`),
                                KEY `idx_user_address_create_time` (`create_time`),
                                CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `order_no` varchar(32) NOT NULL,
                              `user_id` bigint NOT NULL,
                              `event_id` bigint NOT NULL,
                              `session_id` bigint NOT NULL,
                              `tier_id` bigint NOT NULL,
                              `quantity` int NOT NULL DEFAULT '1',
                              `total_amount` decimal(10,2) NOT NULL,
                              `order_status` tinyint NOT NULL DEFAULT '0',
                              `pay_time` datetime DEFAULT NULL,
                              `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                              `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_order_no` (`order_no`),
                              KEY `idx_user_id` (`user_id`),
                              KEY `idx_event_session` (`event_id`,`session_id`),
                              KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `payment_record`;
CREATE TABLE `payment_record` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `order_id` bigint NOT NULL,
                                  `order_no` varchar(32) NOT NULL,
                                  `user_id` bigint NOT NULL,
                                  `payment_no` varchar(64) NOT NULL,
                                  `trade_no` varchar(64) DEFAULT NULL,
                                  `total_amount` decimal(10,2) NOT NULL,
                                  `pay_status` tinyint NOT NULL DEFAULT '0',
                                  `pay_type` tinyint NOT NULL DEFAULT '1',
                                  `subject` varchar(256) DEFAULT NULL,
                                  `body` varchar(512) DEFAULT NULL,
                                  `notify_data` text,
                                  `notify_time` datetime DEFAULT NULL,
                                  `pay_time` datetime DEFAULT NULL,
                                  `expire_time` datetime NOT NULL,
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_payment_no` (`payment_no`),
                                  UNIQUE KEY `uk_trade_no` (`trade_no`),
                                  KEY `idx_order_id` (`order_id`),
                                  KEY `idx_order_no` (`order_no`),
                                  KEY `idx_user_id` (`user_id`),
                                  KEY `idx_pay_status` (`pay_status`),
                                  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `payment_notify_log`;
CREATE TABLE `payment_notify_log` (
                                      `id` bigint NOT NULL AUTO_INCREMENT,
                                      `payment_no` varchar(64) NOT NULL,
                                      `trade_no` varchar(64) DEFAULT NULL,
                                      `notify_data` text NOT NULL,
                                      `notify_status` varchar(20) DEFAULT NULL,
                                      `process_status` tinyint DEFAULT '0',
                                      `error_msg` varchar(500) DEFAULT NULL,
                                      `ip_address` varchar(50) DEFAULT NULL,
                                      `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY (`id`),
                                      KEY `idx_payment_no` (`payment_no`),
                                      KEY `idx_trade_no` (`trade_no`),
                                      KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `payment_compensation`;
CREATE TABLE `payment_compensation` (
                                        `id` bigint NOT NULL AUTO_INCREMENT,
                                        `payment_no` varchar(64) NOT NULL,
                                        `order_id` bigint NOT NULL,
                                        `order_no` varchar(32) NOT NULL,
                                        `user_id` bigint NOT NULL,
                                        `task_status` tinyint NOT NULL DEFAULT '0',
                                        `retry_count` int NOT NULL DEFAULT '0',
                                        `max_retry` int NOT NULL DEFAULT '3',
                                        `last_execute_time` datetime DEFAULT NULL,
                                        `next_execute_time` datetime DEFAULT NULL,
                                        `error_msg` varchar(500) DEFAULT NULL,
                                        `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                        `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `uk_payment_no` (`payment_no`),
                                        KEY `idx_order_id` (`order_id`),
                                        KEY `idx_task_status` (`task_status`),
                                        KEY `idx_next_execute_time` (`next_execute_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
