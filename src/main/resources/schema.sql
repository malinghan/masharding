-- 创建用户表结构

CREATE TABLE IF NOT EXISTS user (
                                     id BIGINT PRIMARY KEY,
                                     name VARCHAR(50),
    age INTEGER
    );
CREATE TABLE IF NOT EXISTS user0 (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50),
    age INTEGER
);

CREATE TABLE IF NOT EXISTS user1 (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50),
    age INTEGER
);

CREATE TABLE IF NOT EXISTS user2 (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50),
    age INTEGER
);


INSERT INTO user(id, name, age) VALUES (3, 'user3', 23);
INSERT INTO user (id, name, age) VALUES (6, 'user6', 26);
INSERT INTO user (id, name, age) VALUES (9, 'user9', 29);

-- 创建测试数据
INSERT INTO user0 (id, name, age) VALUES (3, 'user3', 23);
INSERT INTO user0 (id, name, age) VALUES (6, 'user6', 26);
INSERT INTO user0 (id, name, age) VALUES (9, 'user9', 29);

INSERT INTO user1 (id, name, age) VALUES (1, 'user1', 21);
INSERT INTO user1 (id, name, age) VALUES (4, 'user4', 24);
INSERT INTO user1 (id, name, age) VALUES (7, 'user7', 27);

INSERT INTO user2 (id, name, age) VALUES (2, 'user2', 22);
INSERT INTO user2 (id, name, age) VALUES (5, 'user5', 25);
INSERT INTO user2 (id, name, age) VALUES (8, 'user8', 28);