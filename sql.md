```sql
-- 在 db0 执行
CREATE TABLE test (id INT, name VARCHAR(50));
INSERT INTO test VALUES (1, 'from-db0');

-- 在 db1 执行
CREATE TABLE test (id INT, name VARCHAR(50));
INSERT INTO test VALUES (2, 'from-db1');
```