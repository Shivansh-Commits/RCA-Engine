package com.l3.logextractor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l3.logextractor.config.AzureConfig;
import com.l3.logextractor.controller.LogExtractionController;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
    private final LogExtractionController logExtractionController= new LogExtractionController();

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
     * Download an artifact to the specified directory (with auto-unzipping - legacy method)
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
     * Download an artifact to the specified directory WITHOUT auto-unzipping
     */
    public boolean downloadArtifactOnly(ArtifactInfo artifact, String outputDir, Consumer<String> logCallback) {
        try {
            logCallback.accept("Starting download of artifact: " + artifact.getName() + " (" + formatFileSize(artifact.getSize()) + ")");

            HttpGet downloadRequest = new HttpGet(artifact.getDownloadUrl());
            downloadRequest.setHeader("Authorization", "Basic " + getEncodedAuth());

            ClassicHttpResponse response = httpClient.execute(downloadRequest);

            if (response.getCode() >= 200 && response.getCode() < 300) {
                HttpEntity entity = response.getEntity();

                // Create output directory if it doesn't exist
                Path outputPath = Paths.get(outputDir);
                Files.createDirectories(outputPath);

                // Download and extract zip file (but don't auto-unzip .gz files)
                try (InputStream inputStream = entity.getContent()) {
                    if (artifact.getName().toLowerCase().endsWith(".zip") ||
                        artifact.getDownloadUrl().contains("format=zip")) {
                        extractZipFileOnly(inputStream, outputPath, artifact.getName(), logCallback);
                    } else {
                        // Download as single file
                        Path filePath = outputPath.resolve(artifact.getName());
                        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
                        logCallback.accept("Downloaded file: " + artifact.getName() + " to " + filePath.toString());
                    }
                }

                logCallback.accept(" Successfully downloaded artifact: " + artifact.getName());
                return true;
            } else {
                logCallback.accept(" Failed to download artifact " + artifact.getName() + ". HTTP Status: " + response.getCode());
                return false;
            }

        } catch (Exception e) {
            logCallback.accept(" Error downloading artifact " + artifact.getName() + ": " + e.getMessage());
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
     * Extract zip file contents to the specified directory WITHOUT auto-unzipping .gz files
     * Files are extracted flat (no subdirectories preserved)
     */
    private void extractZipFileOnly(InputStream zipInputStream, Path outputDir, String artifactName, Consumer<String> logCallback) {
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry zipEntry;
            int fileCount = 0;

            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    // Extract just the filename without any directory structure
                    String fileName = zipEntry.getName();
                    if (fileName.contains("/")) {
                        fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                    }
                    if (fileName.contains("\\")) {
                        fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
                    }
                    
                    // Skip hidden/system files
                    if (fileName.startsWith(".") || fileName.startsWith("__") || fileName.isEmpty()) {
                        logCallback.accept(" Skipping system file: " + fileName);
                        continue;
                    }
                    
                    // Create file directly in output directory (flat structure)
                    Path filePath = outputDir.resolve(fileName);
                    
                    // Handle file name conflicts by adding number suffix
                    if (Files.exists(filePath)) {
                        String baseName = fileName;
                        String extension = "";
                        int lastDot = fileName.lastIndexOf('.');
                        if (lastDot > 0) {
                            baseName = fileName.substring(0, lastDot);
                            extension = fileName.substring(lastDot);
                        }
                        
                        int counter = 1;
                        do {
                            fileName = baseName + "_" + counter + extension;
                            filePath = outputDir.resolve(fileName);
                            counter++;
                        } while (Files.exists(filePath));
                        
                        logCallback.accept(" File conflict resolved: " + zipEntry.getName() + " -> " + fileName);
                    }

                    try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                        byte[] buffer = new byte[8192]; // Increased buffer size for better performance
                        int length;
                        while ((length = zis.read(buffer)) >= 0) {
                            fos.write(buffer, 0, length);
                        }
                    }

                    fileCount++;
                    long actualSize = Files.size(filePath);
                    logCallback.accept(" Downloading from " + artifactName + ": " + fileName + " (" + formatFileSize(actualSize) + ")");
                    // Note: NOT auto-unzipping .gz files here
                }
                zis.closeEntry();
            }
            
            logCallback.accept(" Completed extraction of " + fileCount + " files from " + artifactName + " (flat structure)");
        } catch (Exception e) {
            logCallback.accept(" Error extracting zip file " + artifactName + ": " + e.getMessage());
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

            long originalSize = Files.size(gzipFile);
            logCallback.accept(" Extracting: " + originalFileName + " (" + formatFileSize(originalSize) + ")");

            // Extract the gzip file with larger buffer for better performance
            try (FileInputStream fis = new FileInputStream(gzipFile.toFile());
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 FileOutputStream fos = new FileOutputStream(extractedFile.toFile())) {

                byte[] buffer = new byte[8192]; // Increased buffer size
                int length;
                long totalBytes = 0;
                while ((length = gis.read(buffer)) >= 0) {
                    fos.write(buffer, 0, length);
                    totalBytes += length;
                }
                
                // Log the extracted file size
                logCallback.accept(" Extracted to: " + extractedFileName + " (" + formatFileSize(totalBytes) + ")");
            }

            // Delete the original .gz file after successful extraction
            Files.delete(gzipFile);
            logCallback.accept("Successfully extracted and removed .gz file: " + originalFileName + " -> " + extractedFileName);

        } catch (Exception e) {
            logCallback.accept(" Error extracting gzip file " + gzipFile.getFileName() + ": " + e.getMessage());
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
     * Download all artifacts in parallel using ExecutorService with 3 threads
     */
    public boolean downloadAllArtifactsParallel(List<ArtifactInfo> artifacts, String outputDir, Consumer<String> logCallback) {
        if (artifacts.isEmpty()) {
            logCallback.accept("No artifacts to download");
            return true;
        }

        logCallback.accept("Starting parallel download of " + artifacts.size() + " artifacts using 3 threads...");

        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (ArtifactInfo artifact : artifacts) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                return downloadArtifactOnly(artifact, outputDir, logCallback);
            }, executor);
            futures.add(future);
        }

        boolean allSuccessful = true;
        try {
            // Wait for all downloads to complete
            for (CompletableFuture<Boolean> future : futures) {
                Boolean result = future.get();
                if (!result) {
                    allSuccessful = false;
                }
            }
        } catch (Exception e) {
            logCallback.accept("Error during parallel download: " + e.getMessage());
            allSuccessful = false;
        } finally {
            executor.shutdown();
        }

        if (allSuccessful) {
            logCallback.accept("All artifacts downloaded successfully!");
        } else {
            logCallback.accept("Some artifacts failed to download. Check logs for details.");
        }

        return allSuccessful;
    }

    /**
     * Unzip all .gz files in the specified directory and delete the .gz files after extraction
     */
    public boolean unzipAllGzFiles(String directory, Consumer<String> logCallback) {
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                logCallback.accept(" Directory does not exist: " + directory);
                return false;
            }

            logCallback.accept(" Searching for .gz files in: " + directory);

            List<Path> gzFiles;
            try (Stream<Path> files = Files.walk(dirPath)) {
                gzFiles = files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase().endsWith(".gz"))
                        .toList();
            }

            if (gzFiles.isEmpty()) {
                logCallback.accept("ℹ No .gz files found in directory");
                return true;
            }

            logCallback.accept(" Found " + gzFiles.size() + " .gz files to extract");

            // ---- PARALLEL UNZIPPING (max 4 threads) ----
            int threadCount = Math.min(4, gzFiles.size());
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            List<CompletableFuture<Boolean>> futures = new ArrayList<>();

            for (Path gzFile : gzFiles) {
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        long fileSize = Files.size(gzFile);
                        logCallback.accept(" Extracting: " + gzFile.getFileName() +
                                " (" + formatFileSize(fileSize) + ")");

                        extractGzipFile(gzFile, logCallback);

                        return true;
                    } catch (Exception e) {
                        logCallback.accept(" Failed to extract " +
                                gzFile.getFileName() + ": " + e.getMessage());
                        return false;
                    }
                }, executor);

                futures.add(future);
            }

            // Wait for all to finish
            boolean allOk = futures.stream()
                    .map(CompletableFuture::join)
                    .reduce(true, (a, b) -> a & b);

            executor.shutdown();

            if (allOk) {
                logCallback.accept(" All .gz files extracted successfully (parallel)!");
            } else {
                logCallback.accept(" Some .gz files failed. Check logs.");
            }

            return allOk;

        } catch (Exception e) {
            logCallback.accept(" Error during parallel unzip: " + e.getMessage());
            return false;
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
     * Get file information (name and size) from a zip artifact without downloading the entire artifact
     */
    public List<FileInfo> getFileInfoFromArtifact(ArtifactInfo artifact) {
        List<FileInfo> fileInfos = new ArrayList<>();
        
        try {
            HttpGet downloadRequest = new HttpGet(artifact.getDownloadUrl());
            downloadRequest.setHeader("Authorization", "Basic " + getEncodedAuth());

            ClassicHttpResponse response = httpClient.execute(downloadRequest);

            if (response.getCode() >= 200 && response.getCode() < 300) {
                HttpEntity entity = response.getEntity();

                // Read the zip file and extract file information
                try (InputStream inputStream = entity.getContent();
                     ZipInputStream zis = new ZipInputStream(inputStream)) {

                    ZipEntry zipEntry;
                    int totalEntries = 0;
                    int validFiles = 0;
                    
                    while ((zipEntry = zis.getNextEntry()) != null) {
                        totalEntries++;
                        if (!zipEntry.isDirectory()) {
                            String fileName = zipEntry.getName();
                            
                            // Extract just the filename without directory path
                            if (fileName.contains("/")) {
                                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                            }
                            if (fileName.contains("\\")) {
                                fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
                            }
                            
                            if (!fileName.isEmpty() && !fileName.startsWith(".") && !fileName.startsWith("__")) {
                                // Get actual file size from ZIP entry
                                long fileSize = zipEntry.getSize();
                                if (fileSize < 0) {
                                    // If compressed size is not available, use a reasonable estimate
                                    fileSize = zipEntry.getCompressedSize() > 0 ? zipEntry.getCompressedSize() * 2 : 1024;
                                }
                                
                                fileInfos.add(new FileInfo(fileName, fileSize));
                                validFiles++;
                            }
                        }
                        zis.closeEntry();
                    }
                    
                    System.out.println(" Artifact " + artifact.getName() + ": Found " + totalEntries + " total entries, " + validFiles + " valid files");
                }
            } else {
                System.out.println(" Failed to read artifact " + artifact.getName() + ": HTTP " + response.getCode());
            }

        } catch (Exception e) {
            System.out.println(" Error reading artifact " + artifact.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        // Always ensure we return at least one entry
        if (fileInfos.isEmpty()) {
            System.out.println(" No files found in artifact, using artifact name as fallback: " + artifact.getName());
            fileInfos.add(new FileInfo(artifact.getName(), artifact.getSize()));
        }

        return fileInfos;
    }
    
    /**
     * Helper class to hold file information
     */
    public static class FileInfo {
        private final String name;
        private final long size;
        
        public FileInfo(String name, long size) {
            this.name = name;
            this.size = size;
        }
        
        public String getName() {
            return name;
        }
        
        public long getSize() {
            return size;
        }
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
