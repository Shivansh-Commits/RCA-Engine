package com.l3.common.reporting;

import com.l3.controller.MainController;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ExcelReportGenerator {

    public static void generateReport(String filePath,
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
                                    int duplicates,
                                    List<String> warnings) throws IOException {

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle summaryStyle = createSummaryStyle(workbook);
            CellStyle warningStyle = createWarningStyle(workbook);

            // Create Summary Sheet
            Sheet summarySheet = workbook.createSheet("Summary");
            createSummarySheet(summarySheet, headerStyle, summaryStyle,
                             flightNumber, departureDate, departurePort, arrivalPort,
                             totalInput, totalOutput, dropped, duplicates, warnings != null ? warnings.size() : 0);

            // Create sheets for each table
            if (inputTable.getItems().size() > 0) {
                Sheet inputSheet = workbook.createSheet("Input Passengers");
                createTableSheet(inputSheet, inputTable, "Input Passengers", headerStyle, dataStyle);
            }

            if (outputTable.getItems().size() > 0) {
                Sheet outputSheet = workbook.createSheet("Output Passengers");
                createTableSheet(outputSheet, outputTable, "Output Passengers", headerStyle, dataStyle);
            }

            if (droppedTable.getItems().size() > 0) {
                Sheet droppedSheet = workbook.createSheet("Dropped Passengers");
                createTableSheet(droppedSheet, droppedTable, "Dropped Passengers", headerStyle, dataStyle);
            }

            if (duplicateTable.getItems().size() > 0) {
                Sheet duplicateSheet = workbook.createSheet("Duplicate Passengers");
                createTableSheet(duplicateSheet, duplicateTable, "Duplicate Passengers", headerStyle, dataStyle);
            }

            // Create Warnings sheet if there are warnings
            if (warnings != null && !warnings.isEmpty()) {
                Sheet warningsSheet = workbook.createSheet("Warnings");
                createWarningsSheet(warningsSheet, warnings, headerStyle, warningStyle);
            }

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    private static void createSummarySheet(Sheet sheet, CellStyle headerStyle, CellStyle summaryStyle,
                                         String flightNumber, String departureDate,
                                         String departurePort, String arrivalPort,
                                         int totalInput, int totalOutput, int dropped, int duplicates, int warningsCount) {

        // Title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("API/PNR Data Quality Engine - Report Summary");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        // Generated date
        Row dateRow = sheet.createRow(1);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Generated on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        dateCell.setCellStyle(summaryStyle);

        // Flight Information
        int rowIndex = 3;
        createSummaryRow(sheet, rowIndex++, "Flight Information", "", headerStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Flight Number", flightNumber, summaryStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Departure Date", departureDate, summaryStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Departure Port", departurePort, summaryStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Arrival Port", arrivalPort, summaryStyle, summaryStyle);

        // Statistics
        rowIndex++;
        createSummaryRow(sheet, rowIndex++, "Processing Statistics", "", headerStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Total Input Passengers", String.valueOf(totalInput), summaryStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Total Output Passengers", String.valueOf(totalOutput), summaryStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Dropped Passengers", String.valueOf(dropped), summaryStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Duplicate Passengers", String.valueOf(duplicates), summaryStyle, summaryStyle);
        createSummaryRow(sheet, rowIndex++, "Warnings", String.valueOf(warningsCount), summaryStyle, summaryStyle);

        // Auto-size columns
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void createSummaryRow(Sheet sheet, int rowIndex, String label, String value,
                                       CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(valueStyle);
    }

    private static void createTableSheet(Sheet sheet, TableView<?> table, String sheetTitle,
                                       CellStyle headerStyle, CellStyle dataStyle) {

        // Get only visible columns
        List<TableColumn<?, ?>> visibleColumns = table.getColumns().stream()
            .filter(col -> col.isVisible())
            .collect(java.util.stream.Collectors.toList());

        // Title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(sheetTitle);
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(0, visibleColumns.size() - 1)));

        // Header row - use actual column header text from visible columns
        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < visibleColumns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            String columnHeaderText = visibleColumns.get(i).getText();
            cell.setCellValue(columnHeaderText);
            cell.setCellStyle(headerStyle);
        }

        // Data rows - dynamic based on visible columns and their actual values
        for (int i = 0; i < table.getItems().size(); i++) {
            Row row = sheet.createRow(i + 3);
            Object item = table.getItems().get(i);

            if (item instanceof MainController.TableRow) {
                MainController.TableRow tableRow =
                    (MainController.TableRow) item;

                // Create cells for each visible column using their cell value factories
                for (int colIndex = 0; colIndex < visibleColumns.size(); colIndex++) {
                    Cell cell = row.createCell(colIndex);
                    TableColumn<?, ?> column = visibleColumns.get(colIndex);

                    // Get the actual cell value from the column's cell value factory
                    String value = "";
                    try {
                        // Use the cell value factory to get the observable value
                        if (column.getCellValueFactory() != null) {
                            @SuppressWarnings("unchecked")
                            TableColumn<Object, Object> typedColumn = (TableColumn<Object, Object>) column;
                            @SuppressWarnings("unchecked")
                            TableView<Object> typedTable = (TableView<Object>) table;

                            TableColumn.CellDataFeatures<Object, Object> cellDataFeatures =
                                new TableColumn.CellDataFeatures<>(typedTable, typedColumn, item);
                            javafx.beans.value.ObservableValue<?> cellValue = typedColumn.getCellValueFactory().call(cellDataFeatures);

                            if (cellValue != null) {
                                // Handle different property types
                                if (cellValue instanceof javafx.beans.property.StringProperty) {
                                    value = ((javafx.beans.property.StringProperty) cellValue).get();
                                } else if (cellValue instanceof javafx.beans.property.IntegerProperty) {
                                    value = String.valueOf(((javafx.beans.property.IntegerProperty) cellValue).get());
                                } else {
                                    Object val = cellValue.getValue();
                                    value = val != null ? val.toString() : "";
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Fallback to the original mapping logic if reflection fails
                        String columnText = column.getText();
                        if (columnText.equals("No.") || columnText.equals("#")) {
                            value = String.valueOf(tableRow.getNo());
                        } else if (columnText.contains("Name") || columnText.contains("name")) {
                            value = tableRow.getName() != null ? tableRow.getName() : "";
                        } else if (columnText.equals("DTM") || columnText.equals("Locator")) {
                            value = tableRow.getDtm() != null ? tableRow.getDtm() : "";
                        } else if (columnText.equals("DOC")) {
                            value = tableRow.getDoc() != null ? tableRow.getDoc() : "";
                        } else if (columnText.equals("RecordedKey") || columnText.equals("Recorded Key")) {
                            value = tableRow.getRecordedKey() != null ? tableRow.getRecordedKey() : "";
                        } else if (columnText.equals("Source")) {
                            value = tableRow.getSource() != null ? tableRow.getSource() : "";
                        } else if (columnText.equals("Count")) {
                            value = String.valueOf(tableRow.getCount());
                        }
                    }

                    // Handle null values
                    if (value == null) {
                        value = "";
                    }

                    cell.setCellValue(value);
                    cell.setCellStyle(dataStyle);
                }
            }
        }

        // Auto-size columns based on visible columns only
        for (int i = 0; i < visibleColumns.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void createWarningsSheet(Sheet sheet, List<String> warnings, CellStyle headerStyle, CellStyle warningStyle) {
        // Title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Warnings");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        // Header row
        Row headerRow = sheet.createRow(1);
        Cell headerCell1 = headerRow.createCell(0);
        headerCell1.setCellValue("Warning Message");
        headerCell1.setCellStyle(headerStyle);

        // Data rows
        for (int i = 0; i < warnings.size(); i++) {
            Row row = sheet.createRow(i + 2);
            Cell cell = row.createCell(0);
            cell.setCellValue(warnings.get(i));
            cell.setCellStyle(warningStyle);
        }

        // Auto-size columns
        sheet.autoSizeColumn(0);
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        style.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private static CellStyle createSummaryStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);

        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private static CellStyle createWarningStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);

        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }
}
