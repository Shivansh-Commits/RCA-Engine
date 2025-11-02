package com.l3.logextractor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.l3.logextractor.config.AzureConfig;
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
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            logCallback.accept("Error extracting zip file: " + e.getMessage());
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

    private String getEncodedAuth() {
        String auth = ":" + config.getPersonalAccessToken();
        return Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    public void close() throws IOException {
        httpClient.close();
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
