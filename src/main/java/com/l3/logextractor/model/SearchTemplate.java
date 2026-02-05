package com.l3.logextractor.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Model class representing a search template
 * A search template defines which log files should be searched for a flight number
 */
public class SearchTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;
    private List<String> logFiles;
    private boolean active;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

    public SearchTemplate() {
        this.id = UUID.randomUUID().toString();
        this.logFiles = new ArrayList<>();
        this.active = false;
        this.createdDate = LocalDateTime.now();
        this.modifiedDate = LocalDateTime.now();
    }

    public SearchTemplate(String name, String description, List<String> logFiles) {
        this();
        this.name = name;
        this.description = description;
        this.logFiles = new ArrayList<>(logFiles);
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
        this.modifiedDate = LocalDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.modifiedDate = LocalDateTime.now();
    }

    public List<String> getLogFiles() {
        return logFiles;
    }

    public void setLogFiles(List<String> logFiles) {
        this.logFiles = new ArrayList<>(logFiles);
        this.modifiedDate = LocalDateTime.now();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.modifiedDate = LocalDateTime.now();
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchTemplate that = (SearchTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("SearchTemplate{name='%s', active=%s, logFiles=%d}",
            name, active, logFiles.size());
    }

    /**
     * Creates a deep copy of this search template
     */
    public SearchTemplate copy() {
        SearchTemplate copy = new SearchTemplate();
        copy.setId(UUID.randomUUID().toString()); // New ID for copy
        copy.setName(this.name + " (Copy)");
        copy.setDescription(this.description);
        copy.setLogFiles(new ArrayList<>(this.logFiles));
        copy.setActive(false); // Copies are not active by default
        return copy;
    }
}

