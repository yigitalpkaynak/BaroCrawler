package org.kanunum.crawlers;

import org.kanunum.crawlers.bean.Article;

import java.sql.*;

public class MySqlConnection implements AutoCloseable {
    private Connection connection;

    public MySqlConnection(String url, String user, String password) throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        this.connection = DriverManager.getConnection(url, user, password);
    }

    // Veritabanında makale mevcut mu kontrol eder
    public boolean isArticleExists(Article article) throws SQLException {
        String query = "SELECT COUNT(*) FROM article WHERE title = ? AND author = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, article.getTitle());
            stmt.setString(2, article.getAuthor());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0; // Eğer sonuç 0'dan büyükse, makale mevcut demektir
                }
            }
        }
        return false;
    }

    // Makaleyi veritabanına ekler
    public void insertArticle(Article article) throws SQLException {
        if (article.getText() == null) {
            throw new IllegalArgumentException("Article text cannot be null");
        }

        String query = "INSERT INTO article (title, text, filename, url, author, start_page, page_count) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, article.getTitle());
            statement.setString(2, article.getText());
            statement.setString(3, article.getFilename());
            statement.setString(4, article.getUrl());
            statement.setString(5, article.getAuthor());
            statement.setInt(6, article.getStartPage());
            statement.setInt(7, article.getPageCount());

            statement.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
