package org.kanunum.crawlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.kanunum.crawlers.bean.Article;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URL;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    private static final String DOWNLOADED_PDFS_DIR = "downloaded_pdfs";
    private static final String EXTRACTED_TXTS_DIR = "extracted_txts";

    public static void main(String[] args) {


        String url = "https://istanbulbarosu.org.tr/Yayinlar.aspx/GetList";
        String payload = "{\"type\": 5}";
        String outputPath = "response.json";
        String outputTxtPath = "links.txt";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(url);
            postRequest.setHeader("Content-Type", "application/json; charset=UTF-8");
            postRequest.setHeader("Accept", "application/json, text/javascript, */*; q=0.01");

            postRequest.setEntity(new StringEntity(payload));

            CloseableHttpResponse response = httpClient.execute(postRequest);
            InputStream inputStream = response.getEntity().getContent();

            Path getOutputPath = Paths.get(outputPath);

            // Yanıtı doğrudan dosyaya yaz
            Files.copy(inputStream, getOutputPath, StandardCopyOption.REPLACE_EXISTING);

            // JSON yanıtını oku
            String jsonResponse = new String(Files.readAllBytes(getOutputPath), StandardCharsets.UTF_8);
            //System.out.println("JSON Response: " + jsonResponse);

            // JSON verisini ayrıştır
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            String xmlContent = rootNode.path("d").asText();

            // YayinUrl etiketlerindeki linkleri bul
            Pattern pattern = Pattern.compile("<YayinUrl>(.*?)</YayinUrl>");
            Matcher matcher = pattern.matcher(xmlContent);

            // linkleri link.txt dosyasına yaz
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputTxtPath))) {
                while (matcher.find()) {
                    String link = matcher.group(1);
                    writer.write(link);
                    writer.newLine();
                }
            }

            //System.out.println("Linkler link.txt dosyasına yazıldı: " + outputTxtPath);
        } catch (Exception e) {
            logger.error("Error occurred while processing HTTP request or handling JSON response.", e);
        }

        String href = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(outputTxtPath))) {
            // İlk satırı oku
            href = reader.readLine();
        } catch (Exception e) {
            logger.error("Error occurred while reading link.txt file.", e);
        }

        //System.out.println("First link: " + href);

        try {
            //Bu kısım href i elde etmek içindi, yukarıda elde ettik.
            // HTML dosyasını oku

            if (href == null) {
                File input = new File("index.html");
                Document doc = Jsoup.parse(input, "UTF-8");

                // For one time downloading.
                Element link = doc.select("li a").first();
                assert link != null;
                //href = link.attr("href");
            } else {
                String pdfFileName;
                    pdfFileName = getFileName(href);

                // Dosya dizinini kontrol et ve gerekirse oluştur
                Path pdfDirPath = Paths.get(DOWNLOADED_PDFS_DIR); // path yol oluşturur
                Files.createDirectories(pdfDirPath); // directory oluşturur

                // Dosyanın mevcut olup olmadığını kontrol et
                File pdfFile = pdfDirPath.resolve(pdfFileName).toFile(); // resolve name i path e çevirir.
                if (!pdfFile.exists()) {
                    // PDF dosyasını indir
                    downloadFile(href, pdfFile); // link ve boş dosya yeterli
                } else {
                    System.out.println("File already exists. Using existing file.");
                }

                // try-with-resources ile veritabanı bağlantısı oluştur ve kullan
                try (MySqlConnection mySqlConnection = new MySqlConnection("jdbc:mysql://localhost:3306/mavendb", "root", "123456")) {
                    // PDF dosyasını oku ve metni çıkar
                    List<Article> articles = extractTextFromPdf(pdfFile, href, pdfFileName, mySqlConnection);

                    // Metni .txt dosyasına yaz
                    saveTextToTxtFile(articles, pdfFileName);

                    // Article bilgilerini ekrana yazdır
                    printArticleDetails(articles);
                } catch (SQLException | ClassNotFoundException e) {
                    logger.error("Error occurred while connecting to or querying MySQL database.", e);
                }
            }
        } catch (IOException e) {
            logger.error("Error occurred while processing PDF file.", e);
        }
    }

    private static void downloadFile(String href, File pdfFile) throws IOException {
        // PDF dosyasını indir
        URL url = new URL(href); // url işlemleri buradan yapılır.
        try (InputStream in = url.openStream(); // download için input stream açar
             FileOutputStream out = new FileOutputStream(pdfFile)) { // dosyaya write için output stream açar.

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) { // input streamden okuyacak karakter olduğu sürece okur.
                out.write(buffer, 0, bytesRead); // ve yazar.
            }
            System.out.println("File downloaded successfully.");
        } catch (IOException e) {
            logger.error("Error occurred while downloading PDF file from URL: {}", href, e);
        }
    }

    private static List<Article> extractTextFromPdf(File pdfFile, String href, String pdfFileName, MySqlConnection mySqlConnection) throws IOException, SQLException {
        List<Article> articles = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            AccessPermission ap = document.getCurrentAccessPermission();
            if (!ap.canExtractContent()) {
                throw new IOException("You do not have permission to extract text");
            }

            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setSortByPosition(true);

            for (int p = 1; p <= document.getNumberOfPages(); ++p) {
                pdfStripper.setStartPage(p);
                pdfStripper.setEndPage(p);

                String text = pdfStripper.getText(document);
                Article article = extractArticleInfo(text, p, href, pdfFileName);

                if (article != null) {
                    // Veritabanında mevcut mu kontrol et
                    if (mySqlConnection.isArticleExists(article)) {
                        System.out.println("This article is already in the database: " + article.getTitle() + " by " + article.getAuthor());
                        continue; // Mevcutsa, eklemeyi atla
                    }

                    articles.add(article);

                    // Künye dosyasını yaz
                    writeArticleToFile(article, pdfFileName.replace(".pdf", "_" + p + "_Kunye.txt"));

                    // Veritabanına article ekle
                    mySqlConnection.insertArticle(article);
                }
            }
            System.out.println("Text extracted successfully from PDF.");
        } catch (IOException | SQLException e) {
            logger.error("Error occurred while extracting text from PDF file: {}", pdfFile.getAbsolutePath(), e);
        }
        return articles;
    }


    private static void saveTextToTxtFile(List<Article> articles, String pdfFileName) {
        try {
            Path txtDirPath = Paths.get(EXTRACTED_TXTS_DIR);
            Files.createDirectories(txtDirPath);

            // Metni .txt dosyasına yaz
            StringBuilder builder = new StringBuilder();
            for (Article article : articles) {
                builder.append(article.getText()).append("\n");
            }
            try (FileWriter writer = new FileWriter(txtDirPath.resolve(pdfFileName.replace(".pdf", ".txt")).toFile())) {
                writer.write(builder.toString());
            }
            System.out.println("Text extracted and saved to .txt file successfully.");
        } catch (IOException e) {
            logger.error("Error occurred while saving text to .txt file for PDF: {}", pdfFileName, e);
        }
    }

    private static void printArticleDetails(List<Article> articles) {
        for (Article article : articles) {
            System.out.println("Article Details:");
            System.out.println("Title: " + article.getTitle());
            System.out.println("Author: " + article.getAuthor());
            System.out.println("Start Page: " + article.getStartPage());
            System.out.println("Page Count: " + article.getPageCount());
            System.out.println("Filename: " + article.getFilename());
            System.out.println("URL: " + article.getUrl());
            System.out.println("Text: " + article.getText().substring(0, Math.min(article.getText().length(), 100)) + "...");
        }
    }

    private static String getFileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static Article extractArticleInfo(String text, int startPage, String url, String filename) {
        String title = extractTitle(text);
        String author = extractAuthor(text);

        // If the title or author is missing, skip this article
        if (title.isEmpty() || author.isEmpty()) {
            return null;
        }

        Article article = new Article();
        article.setTitle(title);
        article.setAuthor(author);
        article.setStartPage(startPage);
        article.setUrl(url);
        article.setFilename(filename);

        // Clean up the text to remove unwanted numbers at the start
        String cleanedText = text.replaceAll("(?m)^\\d+\\s+", "").trim();
        article.setText(cleanedText);

        // Dummy implementation for page count (modify as needed)
        int pageCount = 1;
        article.setPageCount(pageCount);

        return article;
    }

    private static String extractTitle(String text) {
        // Split the text by lines
        String[] lines = text.split("\n");

        for (String line : lines) {
            // Skip lines that are mostly numeric or too short
            if (line.trim().isEmpty() || line.matches("^\\d+.*$") || line.length() < 5) {
                continue;
            }

            // If line starts with a number, but has other content, remove the number
            if (line.matches("^\\d+\\s+.*$")) {
                line = line.replaceFirst("^\\d+\\s+", "");
            }

            // Remove any numbers at the end of the line
            line = line.replaceAll("\\s+\\d+$", "");

            // Return the first line that looks like a title
            return line.trim();
        }

        return "";
    }


    private static String extractAuthor(String text) {
        // Implement author extraction logic here
        // Example:
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("Av.")) {
                return line.trim();
            }
        }
        return "";
    }

    private static void writeArticleToFile(Article article, String fileName) {
        try {
            // "Kunye" klasör yolunu belirle
            Path kunyeDirPath = Paths.get("Kunye");

            // Klasör mevcut değilse oluştur
            Files.createDirectories(kunyeDirPath);

            // Tam dosya yolunu oluştur
            Path fullPath = kunyeDirPath.resolve(fileName);

            // İçerik oluştur
            StringBuilder content = new StringBuilder();
            content.append("Title: ").append(article.getTitle()).append("\n");
            content.append("Author: ").append(article.getAuthor()).append("\n");
            content.append("Start Page: ").append(article.getStartPage()).append("\n");
            content.append("Page Count: ").append(article.getPageCount()).append("\n");
            content.append("Filename: ").append(article.getFilename()).append("\n");
            content.append("URL: ").append(article.getUrl()).append("\n");
            content.append("Text:\n").append(article.getText()).append("\n");

            // Dosyaya yaz
            try (FileWriter writer = new FileWriter(fullPath.toFile())) {
                writer.write(content.toString());
                System.out.println("Article details written to: " + fullPath.toString());
            }
        } catch (IOException e) {
            logger.error("Error occurred while writing article details to file: {}", fileName, e);
        }
    }

}
