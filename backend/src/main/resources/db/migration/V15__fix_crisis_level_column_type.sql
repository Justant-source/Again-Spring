-- V15: messages.crisis_level TINYINT → INT (Hibernate Integer 매핑 맞춤)
ALTER TABLE messages MODIFY COLUMN crisis_level INT DEFAULT NULL COMMENT '1=immediate, 2=warning, NULL=none';
