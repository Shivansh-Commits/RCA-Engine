package com.l3.logextractor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.l3.logextractor.model.SearchTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service class for managing search templates
 * Handles CRUD operations and persistence of search templates
 */
public class SearchProfilesService {

    private static final String TEMPLATES_DIR = System.getProperty("user.home") + File.separator + ".l3engine";
    private static final String TEMPLATES_FILE = TEMPLATES_DIR + File.separator + "templates.json";

    private final ObjectMapper objectMapper;
    private List<SearchTemplate> templates;

    public SearchProfilesService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.templates = new ArrayList<>();

        // Create templates directory if it doesn't exist
        createTemplatesDirectory();

        // Load templates from file
        loadTemplates();

        // Create default templates if none exist
        if (templates.isEmpty()) {
            createDefaultTemplates();
        }
    }

    /**
     * Create the templates directory if it doesn't exist
     */
    private void createTemplatesDirectory() {
        try {
            Path dirPath = Paths.get(TEMPLATES_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
        } catch (IOException e) {
            System.err.println("Failed to create templates directory: " + e.getMessage());
        }
    }

    /**
     * Load templates from file
     */
    private void loadTemplates() {
        try {
            File file = new File(TEMPLATES_FILE);
            if (file.exists()) {
                SearchTemplate[] loadedTemplates = objectMapper.readValue(file, SearchTemplate[].class);
                templates = new ArrayList<>(Arrays.asList(loadedTemplates));
            }
        } catch (IOException e) {
            System.err.println("Failed to load templates: " + e.getMessage());
            templates = new ArrayList<>();
        }
    }

    /**
     * Save templates to file
     */
    private void saveTemplates() {
        try {
            File file = new File(TEMPLATES_FILE);
            objectMapper.writeValue(file, templates);
        } catch (IOException e) {
            System.err.println("Failed to save templates: " + e.getMessage());
        }
    }

    /**
     * Create default search templates
     */
    private void createDefaultTemplates() {
        // Default template - All logs
        SearchTemplate allLogs = new SearchTemplate(
            "All Logs",
            "Search in all available log files",
            Arrays.asList(
                "das.log",
                "MessageForwarder.log",
                "MessageMHPNRGOV.log",
                "MessageAPI.log",
                "MessageTypeB.log"
            )
        );
        allLogs.setActive(true);

        // API focused template
        SearchTemplate apiLogs = new SearchTemplate(
            "API Logs Only",
            "Search only in API log files",
            Arrays.asList(
                "das.log",
                "MessageForwarder.log",
                "MessageAPI.log",
                "MessageTypeB.log"
            )
        );

        // DCS focused template
        SearchTemplate dcsLogs = new SearchTemplate(
            "PNR Logs Only",
            "Search only in PNR log files",
            Arrays.asList(
                "das.log",
                "MessageForwarder.log",
                "MessageMHPNRGOV.log"
            )
        );

        templates.add(allLogs);
        templates.add(apiLogs);
        templates.add(dcsLogs);

        saveTemplates();
    }

    /**
     * Get all search templates
     */
    public List<SearchTemplate> getAllTemplates() {
        return new ArrayList<>(templates);
    }

    /**
     * Get the active search template
     */
    public Optional<SearchTemplate> getActiveTemplate() {
        return templates.stream()
            .filter(SearchTemplate::isActive)
            .findFirst();
    }

    /**
     * Get a template by ID
     */
    public Optional<SearchTemplate> getTemplateById(String id) {
        return templates.stream()
            .filter(t -> t.getId().equals(id))
            .findFirst();
    }

    /**
     * Create a new search template
     */
    public SearchTemplate createTemplate(SearchTemplate template) {
        if (template.getId() == null || template.getId().isEmpty()) {
            template.setId(UUID.randomUUID().toString());
        }
        templates.add(template);
        saveTemplates();
        return template;
    }

    /**
     * Update an existing search template
     */
    public boolean updateTemplate(SearchTemplate updatedTemplate) {
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).getId().equals(updatedTemplate.getId())) {
                templates.set(i, updatedTemplate);
                saveTemplates();
                return true;
            }
        }
        return false;
    }

    /**
     * Delete a search template
     */
    public boolean deleteTemplate(String templateId) {
        boolean removed = templates.removeIf(t -> t.getId().equals(templateId));
        if (removed) {
            saveTemplates();

            // If the deleted template was active, activate the first template
            if (getActiveTemplate().isEmpty() && !templates.isEmpty()) {
                templates.get(0).setActive(true);
                saveTemplates();
            }
        }
        return removed;
    }

    /**
     * Activate a search template (deactivates all others)
     */
    public boolean activateTemplate(String templateId) {
        boolean found = false;
        for (SearchTemplate template : templates) {
            if (template.getId().equals(templateId)) {
                template.setActive(true);
                found = true;
            } else {
                template.setActive(false);
            }
        }
        if (found) {
            saveTemplates();
        }
        return found;
    }

    /**
     * Get log files from the active template
     */
    public List<String> getActiveLogFiles() {
        return getActiveTemplate()
            .map(SearchTemplate::getLogFiles)
            .orElse(Collections.emptyList());
    }

    /**
     * Get the templates file location
     */
    public String getTemplatesFileLocation() {
        return TEMPLATES_FILE;
    }
}

