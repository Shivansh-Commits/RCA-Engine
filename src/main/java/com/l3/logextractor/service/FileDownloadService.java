package com.l3.logextractor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l3.logextractor.config.AzureConfig;
import com.l3.logextractor.model.ArtifactInfo;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.GZIPInputStream;

/**
 * Service class for downloading and managing build artifacts from Azure DevOps
 */
public class FileDownloadService {

    private final AzureConfig config;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public FileDownloadService(AzureConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Get list of available artifacts for a build
     */
    public List<ArtifactInfo> getArtifacts(String buildId, Consumer<String> logCallback) {
        List<ArtifactInfo> artifacts = new ArrayList<>();

        try {
            String artifactsUrl = String.format("%s/%s/%s/_apis/build/builds/%s/artifacts?api-version=7.0",
                config.getBaseUrl(), config.getOrganization(), config.getProject(), buildId);

            HttpGet getRequest = new HttpGet(artifactsUrl);
            getRequest.setHeader("Authorization", "Basic " + getEncodedAuth());

            ClassicHttpResponse response = httpClient.execute(getRequest);
            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity, StandardCharsets.UTF_8);

            if (response.getCode() >= 200 && response.getCode() < 300) {
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                JsonNode artifactsArray = jsonResponse.get("value");

                if (artifactsArray != null && artifactsArray.isArray()) {
                    for (JsonNode artifactNode : artifactsArray) {
                        ArtifactInfo artifact = new ArtifactInfo();
                        artifact.setName(artifactNode.get("name").asText());
                        artifact.setId(artifactNode.get("id").asText());

                        JsonNode resourceNode = artifactNode.get("resource");
                        if (resourceNode != null) {
                            artifact.setDownloadUrl(resourceNode.get("downloadUrl").asText());
                            if (resourceNode.has("properties")) {
                                JsonNode propsNode = resourceNode.get("properties");
                                if (propsNode.has("artifactsize")) {
                                    artifact.setSize(propsNode.get("artifactsize").asLong());
                                }
                            }
                        }

                        artifacts.add(artifact);
                        logCallback.accept("Found artifact: " + artifact.getName());
                    }
                }
            } else {
                logCallback.accept("Failed to retrieve artifacts. Status: " + response.getCode());
            }

        } catch (Exception e) {
            logCallback.accept("Error retrieving artifacts: " + e.getMessage());
        }

        return artifacts;
    }

    /**
     * Download an artifact to the specified directory
     */
    public boolean downloadArtifact(ArtifactInfo artifact, String outputDir, Consumer<String> logCallback) {
        try {
            logCallback.accept("Starting download of: " + artifact.getName());

            HttpGet downloadRequest = new HttpGet(artifact.getDownloadUrl());
            downloadRequest.setHeader("Authorization", "Basic " + getEncodedAuth());

            ClassicHttpResponse response = httpClient.execute(downloadRequest);

            if (response.getCode() >= 200 && response.getCode() < 300) {
                HttpEntity entity = response.getEntity();

                // Create output directory if it doesn't exist
                Path outputPath = Paths.get(outputDir);
                Files.createDirectories(outputPath);

                // Download and extract zip file
                try (InputStream inputStream = entity.getContent()) {
                    if (artifact.getName().toLowerCase().endsWith(".zip") ||
                        artifact.getDownloadUrl().contains("format=zip")) {
                        extractZipFile(inputStream, outputPath, artifact.getName(), logCallback);
                    } else {
                        // Download as single file
                        Path filePath = outputPath.resolve(artifact.getName());
                        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
                        logCallback.accept("Downloaded: " + artifact.getName());

                        // If the downloaded file is a .gz file, extract it in place
                        if (filePath.toString().toLowerCase().endsWith(".gz")) {
                            extractGzipFile(filePath, logCallback);
                        }
                    }
                }

                logCallback.accept("Successfully downloaded: " + artifact.getName());
                return true;
            } else {
                logCallback.accept("Failed to download artifact. Status: " + response.getCode());
                return false;
            }

        } catch (Exception e) {
            logCallback.accept("Error downloading artifact: " + e.getMessage());
            return false;
        }
    }

    /**
     * Extract zip file contents to the specified directory
     */
    private void extractZipFile(InputStream zipInputStream, Path outputDir, String artifactName, Consumer<String> logCallback) {
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry zipEntry;

            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    Path filePath = outputDir.resolve(zipEntry.getName());

                    // Create parent directories if they don't exist
                    Files.createDirectories(filePath.getParent());

                    try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) >= 0) {
                            fos.write(buffer, 0, length);
                        }
                    }

                    logCallback.accept("Extracted: " + zipEntry.getName());

                    // If the extracted file is a .gz file, extract it in place
                    if (filePath.toString().toLowerCase().endsWith(".gz")) {
                        extractGzipFile(filePath, logCallback);
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            logCallback.accept("Error extracting zip file: " + e.getMessage());
        }
    }

    /**
     * Extract a .gz file in place and remove the .gz file after extraction
     */
    private void extractGzipFile(Path gzipFile, Consumer<String> logCallback) {
        try {
            String originalFileName = gzipFile.getFileName().toString();
            String extractedFileName = originalFileName.substring(0, originalFileName.length() - 3); // Remove .gz extension
            Path extractedFile = gzipFile.getParent().resolve(extractedFileName);

            logCallback.accept("Extracting gzip file: " + originalFileName);

            // Extract the gzip file
            try (FileInputStream fis = new FileInputStream(gzipFile.toFile());
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 FileOutputStream fos = new FileOutputStream(extractedFile.toFile())) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = gis.read(buffer)) >= 0) {
                    fos.write(buffer, 0, length);
                }
            }

            // Delete the original .gz file after successful extraction
            Files.delete(gzipFile);
            logCallback.accept("Successfully extracted and removed .gz file: " + originalFileName + " -> " + extractedFileName);

        } catch (Exception e) {
            logCallback.accept("Error extracting gzip file " + gzipFile.getFileName() + ": " + e.getMessage());
        }
    }

    /**
     * Get file preview content (first few lines)
     */
    public String getFilePreview(String filePath, int maxLines) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            StringBuilder preview = new StringBuilder();

            int linesToRead = Math.min(maxLines, lines.size());
            for (int i = 0; i < linesToRead; i++) {
                preview.append(lines.get(i)).append("\n");
            }

            if (lines.size() > maxLines) {
                preview.append("... (").append(lines.size() - maxLines).append(" more lines)");
            }

            return preview.toString();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    /**
     * Download all pipeline artifacts after successful completion
     * This is the main orchestration method that should be called after pipeline success
     */
    public List<Path> downloadPipelineArtifacts(String buildId, String flightNumber, Consumer<String> logCallback) {
        List<Path> downloadedFiles = new ArrayList<>();

        try {
            logCallback.accept("Starting artifact discovery and download for build: " + buildId);

            // Step 1: Get list of available artifacts from Azure DevOps
            List<ArtifactInfo> artifacts = getArtifacts(buildId, logCallback);

            if (artifacts.isEmpty()) {
                logCallback.accept("No artifacts found for build " + buildId);
                return downloadedFiles;
            }

            logCallback.accept("Found " + artifacts.size() + " artifacts to download");

            // Step 2: Create download directory
            String downloadDir = createDownloadDirectory(flightNumber);
            logCallback.accept("Download directory: " + downloadDir);

            // Step 3: Download each artifact
            for (ArtifactInfo artifact : artifacts) {
                logCallback.accept("Downloading artifact: " + artifact.getName());

                boolean success = downloadArtifact(artifact, downloadDir, logCallback);

                if (success) {
                    // Step 4: Find and collect downloaded files
                    List<Path> extractedFiles = findExtractedFiles(downloadDir, artifact.getName(), logCallback);
                    downloadedFiles.addAll(extractedFiles);
                } else {
                    logCallback.accept("Failed to download artifact: " + artifact.getName());
                }
            }

            logCallback.accept("Download completed. Total files: " + downloadedFiles.size());

            // Step 5: Log summary of downloaded files
            for (Path file : downloadedFiles) {
                logCallback.accept("Downloaded file: " + file.getFileName() + " (" + getFileSize(file) + ")");
            }

        } catch (Exception e) {
            logCallback.accept("Error downloading pipeline artifacts: " + e.getMessage());
        }

        return downloadedFiles;
    }

    /**
     * Create a download directory for the flight
     */
    private String createDownloadDirectory(String flightNumber) throws IOException {
        String userHome = System.getProperty("user.home");
        String downloadDir = String.format("%s/Downloads/L3Engine_Logs/%s_%s",
            userHome,
            flightNumber,
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );

        Path downloadPath = Paths.get(downloadDir);
        Files.createDirectories(downloadPath);

        return downloadDir;
    }

    /**
     * Find all extracted files from an artifact download
     */
    private List<Path> findExtractedFiles(String downloadDir, String artifactName, Consumer<String> logCallback) {
        List<Path> files = new ArrayList<>();

        try {
            Path dirPath = Paths.get(downloadDir);

            Files.walk(dirPath)
                 .filter(Files::isRegularFile)
                 .filter(path -> isLogFile(path))
                 .forEach(files::add);

            logCallback.accept("Found " + files.size() + " log files in " + artifactName);

        } catch (Exception e) {
            logCallback.accept("Error finding extracted files: " + e.getMessage());
        }

        return files;
    }

    /**
     * Check if a file is a log file based on extension or name patterns
     */
    private boolean isLogFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".log") ||
               fileName.endsWith(".txt") ||
               fileName.contains("log") ||
               fileName.endsWith(".csv") ||
               fileName.endsWith(".json") ||
               fileName.endsWith(".gz"); // Include .gz files as they often contain compressed logs
    }

    /**
     * Get formatted file size
     */
    private String getFileSize(Path file) {
        try {
            long bytes = Files.size(file);
            return formatFileSize(bytes);
        } catch (Exception e) {
            return "Unknown size";
        }
    }

    /**
     * Format file size in human readable format
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String getEncodedAuth() {
        String auth = ":" + config.getPersonalAccessToken();
        return Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    public void close() throws IOException {
        httpClient.close();
    }

    /**
     * Get file names from a zip artifact without downloading the entire artifact
     */
    public List<String> getFileNamesFromArtifact(ArtifactInfo artifact) {
        List<String> fileNames = new ArrayList<>();

        try {
            HttpGet downloadRequest = new HttpGet(artifact.getDownloadUrl());
            downloadRequest.setHeader("Authorization", "Basic " + getEncodedAuth());

            ClassicHttpResponse response = httpClient.execute(downloadRequest);

            if (response.getCode() >= 200 && response.getCode() < 300) {
                HttpEntity entity = response.getEntity();

                // Read the zip file and extract file names
                try (InputStream inputStream = entity.getContent();
                     ZipInputStream zis = new ZipInputStream(inputStream)) {

                    ZipEntry zipEntry;
                    while ((zipEntry = zis.getNextEntry()) != null) {
                        if (!zipEntry.isDirectory()) {
                            String fileName = zipEntry.getName();
                            // Extract just the filename without directory path
                            if (fileName.contains("/")) {
                                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                            }
                            if (fileName.contains("\\")) {
                                fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
                            }
                            if (!fileName.isEmpty() && isLogFile(Paths.get(fileName))) {
                                fileNames.add(fileName);
                            }
                        }
                        zis.closeEntry();
                    }
                }
            }

        } catch (Exception e) {
            // If we can't read the zip, return empty list
        }

        return fileNames;
    }

    /**
     * Download a specific file from an artifact
     */
    public boolean downloadSpecificFile(ArtifactInfo artifact, String specificFileName, String outputDir, Consumer<String> logCallback) {
        if (specificFileName == null) {
            // If no specific file requested, download the entire artifact
            return downloadArtifact(artifact, outputDir, logCallback);
        }

        try {
            logCallback.accept("Downloading specific file: " + specificFileName + " from artifact: " + artifact.getName());

            HttpGet downloadRequest = new HttpGet(artifact.getDownloadUrl());
            downloadRequest.setHeader("Authorization", "Basic " + getEncodedAuth());

            ClassicHttpResponse response = httpClient.execute(downloadRequest);

            if (response.getCode() >= 200 && response.getCode() < 300) {
                HttpEntity entity = response.getEntity();

                // Create output directory if it doesn't exist
                Path outputPath = Paths.get(outputDir);
                Files.createDirectories(outputPath);

                // Extract only the specific file from the zip
                try (InputStream inputStream = entity.getContent();
                     ZipInputStream zis = new ZipInputStream(inputStream)) {

                    ZipEntry zipEntry;
                    while ((zipEntry = zis.getNextEntry()) != null) {
                        if (!zipEntry.isDirectory()) {
                            String fileName = zipEntry.getName();
                            // Extract just the filename without directory path
                            String baseFileName = fileName;
                            if (fileName.contains("/")) {
                                baseFileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                            }
                            if (fileName.contains("\\")) {
                                baseFileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
                            }

                            // Check if this is the file we're looking for
                            if (baseFileName.equals(specificFileName)) {
                                Path filePath = outputPath.resolve(baseFileName);

                                try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                                    byte[] buffer = new byte[1024];
                                    int length;
                                    while ((length = zis.read(buffer)) >= 0) {
                                        fos.write(buffer, 0, length);
                                    }
                                }

                                logCallback.accept("Successfully extracted: " + baseFileName);

                                // If the extracted file is a .gz file, extract it in place
                                if (filePath.toString().toLowerCase().endsWith(".gz")) {
                                    extractGzipFile(filePath, logCallback);
                                }

                                zis.closeEntry();
                                return true;
                            }
                        }
                        zis.closeEntry();
                    }
                }

                logCallback.accept("File not found in artifact: " + specificFileName);
                return false;
            } else {
                logCallback.accept("Failed to download artifact. Status: " + response.getCode());
                return false;
            }

        } catch (Exception e) {
            logCallback.accept("Error downloading specific file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Class representing artifact information
     */
    public static class ArtifactInfo {
        private String name;
        private String id;
        private String downloadUrl;
        private long size;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }

        @Override
        public String toString() {
            return String.format("ArtifactInfo{name='%s', id='%s', size=%d}", name, id, size);
        }
    }
}
