package com.l3.logextractor.model;

/**
 * Model class for displaying log file entries in the UI table
 */
public class LogFileEntry {
    private String fileName;
    private String fileSize;
    private String extractedTime;
    private String status;
    private String filePath;

    public LogFileEntry() {}

    public LogFileEntry(String fileName, String fileSize, String extractedTime, String status) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.extractedTime = extractedTime;
        this.status = status;
    }

    public LogFileEntry(String fileName, String fileSize, String extractedTime, String status, String filePath) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.extractedTime = extractedTime;
        this.status = status;
        this.filePath = filePath;
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getExtractedTime() {
        return extractedTime;
    }

    public void setExtractedTime(String extractedTime) {
        this.extractedTime = extractedTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public String toString() {
        return "LogFileEntry{" +
                "fileName='" + fileName + '\'' +
                ", fileSize='" + fileSize + '\'' +
                ", extractedTime='" + extractedTime + '\'' +
                ", status='" + status + '\'' +
                ", filePath='" + filePath + '\'' +
                '}';
    }
}
