package com.l3.logextractor.model;

/**
 * Model class representing an Azure DevOps build artifact
 */
public class ArtifactInfo {
    private String id;
    private String name;
    private String downloadUrl;
    private long size;
    private String type;

    public ArtifactInfo() {}

    public ArtifactInfo(String id, String name, String downloadUrl, long size) {
        this.id = id;
        this.name = name;
        this.downloadUrl = downloadUrl;
        this.size = size;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "ArtifactInfo{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                ", size=" + size +
                ", type='" + type + '\'' +
                '}';
    }
}
