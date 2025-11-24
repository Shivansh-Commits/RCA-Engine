package com.l3.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to retrieve application version information
 */
public class VersionUtil {

    private static String version = null;
    private static final String UNKNOWN_VERSION = "Unknown";

    /**
     * Get the application version from Maven properties
     * @return version string or "Unknown" if unable to read
     */
    public static String getVersion() {
        if (version == null) {
            version = loadVersionFromProperties();
        }
        return version;
    }

    /**
     * Get formatted version string for display
     * @return formatted version string like "v3.2.0.0"
     */
    public static String getFormattedVersion() {
        return "v" + getVersion();
    }

    private static String loadVersionFromProperties() {
        try {
            // Method 1: Try to load from Maven-generated properties file (works in packaged JAR)
            InputStream is = VersionUtil.class.getResourceAsStream("/META-INF/maven/com.l3/engine/pom.properties");
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String ver = props.getProperty("version");
                if (ver != null && !ver.trim().isEmpty() && !ver.contains("${")) {
                    return ver.trim();
                }
            }

            // Method 2: Try to read from application.properties (if Maven filtering worked)
            is = VersionUtil.class.getResourceAsStream("/application.properties");
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String ver = props.getProperty("application.version");
                // Check if Maven variables were resolved
                if (ver != null && !ver.trim().isEmpty() && !ver.contains("${")) {
                    return ver.trim();
                }
            }

            // Method 3: Try to read directly from POM file (development mode)
            String pomVersion = readVersionFromPomFile();
            if (pomVersion != null) {
                return pomVersion;
            }

            // Method 4: Try to read from MANIFEST.MF
            is = VersionUtil.class.getResourceAsStream("/META-INF/MANIFEST.MF");
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String ver = props.getProperty("Implementation-Version");
                if (ver != null && !ver.trim().isEmpty()) {
                    return ver.trim();
                }
            }

        } catch (Exception e) {
            System.err.println("Warning: Could not read version information: " + e.getMessage());
        }

        // Final fallback - hardcoded version
        return "3.2.0.0";
    }

    /**
     * Read version directly from POM file using text parsing (for development mode)
     */
    private static String readVersionFromPomFile() {
        try {
            // Try to locate pom.xml file in various possible locations
            String[] possiblePomPaths = {
                "pom.xml",
                "../pom.xml",
                "../../pom.xml",
                "../../../pom.xml"
            };

            for (String pomPath : possiblePomPaths) {
                Path pomFile = Paths.get(pomPath);
                if (Files.exists(pomFile)) {
                    List<String> lines = Files.readAllLines(pomFile);

                    // Simple regex to find <version>X.X.X.X</version> in the project section
                    Pattern versionPattern = Pattern.compile("<version>([^<]+)</version>");
                    boolean inProjectSection = false;

                    for (String line : lines) {
                        // Look for the main project version (not dependency versions)
                        if (line.contains("<groupId>com.l3</groupId>")) {
                            inProjectSection = true;
                            continue;
                        }

                        if (inProjectSection && line.contains("</project>")) {
                            break;
                        }

                        if (inProjectSection) {
                            Matcher matcher = versionPattern.matcher(line.trim());
                            if (matcher.find()) {
                                String version = matcher.group(1).trim();
                                if (!version.isEmpty() && !version.contains("$")) {
                                    return version;
                                }
                            }
                        }
                    }
                    break;
                }
            }

        } catch (Exception e) {
            // Silently ignore errors when reading POM file
            // This is expected in packaged environments
        }

        return null;
    }
}

