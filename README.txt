My MySQL Connections Info:

Local instance MySQL80
username: root
password: 123456
localhost:3306

My db name: mavendb
Table name: article

Script to create table "article"

CREATE TABLE article (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    text TEXT,
    filename VARCHAR(255),
    url VARCHAR(255),
    author VARCHAR(255),
    start_page INT,
    page_count INT
);

To delete and select scripts
SELECT * FROM article;
TRUNCATE TABLE article;