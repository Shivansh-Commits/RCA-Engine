package com.l3.dqengine.common.reporting;

import com.l3.dqengine.controller.MainController;
import javafx.scene.control.TableView;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Simple CSV export utility that can be easily extended to Excel later
 * This avoids module system complications with Apache POI
 */
public class ReportExporter {

    public static void exportToCSV(String filePath,
                                  TableView<?> inputTable,
                                  TableView<?> outputTable,
                                  TableView<?> droppedTable,
                                  TableView<?> duplicateTable,
                                  String flightNumber,
                                  String departureDate,
                                  String departurePort,
                                  String arrivalPort,
                                  int totalInput,
                                  int totalOutput,
                                  int dropped,
                                  int duplicates) throws IOException {

        try (FileWriter writer = new FileWriter(filePath)) {
            // Write header
            writer.write("API/PNR Data Quality Engine - Report Summary\n");
            writer.write("Generated on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n\n");

            // Flight Information
            writer.write("FLIGHT INFORMATION\n");
            writer.write("Flight Number," + flightNumber + "\n");
            writer.write("Departure Date," + departureDate + "\n");
            writer.write("Departure Port," + departurePort + "\n");
            writer.write("Arrival Port," + arrivalPort + "\n\n");

            // Statistics
            writer.write("PROCESSING STATISTICS\n");
            writer.write("Total Input Passengers," + totalInput + "\n");
            writer.write("Total Output Passengers," + totalOutput + "\n");
            writer.write("Dropped Passengers," + dropped + "\n");
            writer.write("Duplicate Passengers," + duplicates + "\n\n");

            // Export each table
            if (!inputTable.getItems().isEmpty()) {
                writer.write("INPUT PASSENGERS\n");
                exportTableToCSV(writer, inputTable);
                writer.write("\n");
            }

            if (!outputTable.getItems().isEmpty()) {
                writer.write("OUTPUT PASSENGERS\n");
                exportTableToCSV(writer, outputTable);
                writer.write("\n");
            }

            if (!droppedTable.getItems().isEmpty()) {
                writer.write("DROPPED PASSENGERS\n");
                exportTableToCSV(writer, droppedTable);
                writer.write("\n");
            }

            if (!duplicateTable.getItems().isEmpty()) {
                writer.write("DUPLICATE PASSENGERS\n");
                exportTableToCSV(writer, duplicateTable);
                writer.write("\n");
            }
        }
    }

    private static void exportTableToCSV(FileWriter writer, TableView<?> table) throws IOException {
        // Write headers
        writer.write("No,Name,DTM,DOC,Recorded Key,Source,Count\n");

        // Write data
        for (Object item : table.getItems()) {
            if (item instanceof MainController.TableRow) {
                MainController.TableRow row =
                    (MainController.TableRow) item;

                writer.write(String.format("%d,%s,%s,%s,%s,%s,%d\n",
                    row.getNo(),
                    escapeCsv(row.getName()),
                    escapeCsv(row.getDtm()),
                    escapeCsv(row.getDoc()),
                    escapeCsv(row.getRecordedKey()),
                    escapeCsv(row.getSource()),
                    row.getCount()));
            }
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
