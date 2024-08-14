# IBaroCrawler

## Project Overview
IBaroCrawler is a Java application designed to automatically download PDF documents from the Istanbul Bar Association website, extract text from these files, and save the content locally or in a MySQL database. It can also process and store recently published Bar Association journals.
## Key Details

•	Project Name: IBaroCrawler

•	Programming Language: Java


•	Libraries Used:

-	Jsoup: For HTML parsing.

-	PDFBox: For extracting text from PDF files.

-	MySQL Connector: For database interactions.

-	Jackson Databind: For working with JSON data.

-	Log4j: For logging purposes.

-	HttpClient: For managing HTTP requests.

•	Project Structure: Maven-based project.

## How It Works
1.	Fetch Links: The application retrieves PDF links from the Istanbul Bar Association's website and saves them in a links.txt file.
2.	Download PDFs: It downloads each PDF file to your local system.
3.	Extract Text: Text is extracted from each PDF file and converted into Article objects.
4.	Save Files: The extracted text is saved as .txt files.
5.	Database Integration: If a database is configured, the extracted articles are stored in a MySQL database.
##  Installation
1.	Download the Project:
-	Clone the repository from GitHub or download the ZIP file.
2.	Install Dependencies:
o	Use the pom.xml file to install Maven dependencies.
o	Run mvn clean install to compile the project and download necessary dependencies.

## 3.	Set Up the Database:
-	Create a MySQL database named mavendb.
  
-	Use the following SQL command to create the Article table:
  
CREATE TABLE Article (

  id INT AUTO_INCREMENT PRIMARY KEY,
 	
  title VARCHAR(255) NOT NULL,
 	
  author VARCHAR(255),
 	
  startPage INT,
 	
  pageCount INT,
 	
  fileName VARCHAR(255),
 	
  url VARCHAR(255),
 	
  text TEXT
  
);

## 4.	Configure the Database Connection:

-	Add your database connection details in the config.properties file:


db.url=jdbc:mysql://localhost:3306/mavendb

db.user=root

db.password=[YourPassword]

## 5.	Run the Project:
Execute the App class to start the process.

## Project Status
The project is functional, but there are some areas that need further development and testing. Due to an early end to my internship, I couldn't fully complete the project. I encourage the next developer to continue improving and refining the data processing and validation steps.

## Contact

If you have any questions or need support, feel free to contact me:

•	Name: Yiğit Alp Kaynak

•	Email: yigitalpkaynak1@gmail.com

•	GitHub: IBaroCrawler

