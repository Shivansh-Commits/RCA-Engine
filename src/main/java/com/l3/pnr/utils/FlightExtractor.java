package com.l3.pnr.utils;

import com.l3.pnr.model.FlightDetails;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flight details extractor from EDIFACT files
 */
public class FlightExtractor {
    
    /**
     * Extract flight details from EDIFACT file
     */
    public static FlightDetails extractFlightDetails(String filePath) throws Exception {
        return extractFlightDetails(new File(filePath));
    }
    
    /**
     * Extract flight details from EDIFACT file
     */
    public static FlightDetails extractFlightDetails(File file) throws Exception {
        if (!file.exists()) {
            return null;
        }
        
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        if (content.trim().isEmpty()) {
            return null;
        }
        
        EdifactSeparators separators = EdifactSeparators.parse(content);
        
        // Find the first TVL segment using dynamic separators
        String tvlPattern = "TVL" + Pattern.quote(String.valueOf(separators.getElement())) + 
                           "[^" + Pattern.quote(String.valueOf(separators.getSegment())) + "]*" + 
                           Pattern.quote(String.valueOf(separators.getSegment()));
        
        Pattern pattern = Pattern.compile(tvlPattern);
        Matcher matcher = pattern.matcher(content);
        
        if (!matcher.find()) {
            return null;
        }
        
        // Get the first TVL segment (remove trailing segment terminator)
        String firstTvl = matcher.group().replaceAll(Pattern.quote(String.valueOf(separators.getSegment())) + "$", "");
        
        // Parse TVL segment using dynamic separators
        EdifactParser.TvlData tvlData = EdifactParser.parseTvl(firstTvl, separators);
        
        if (tvlData == null) {
            return null;
        }
        
        String datetime = tvlData.getDateTime();
        String origin = tvlData.getOrigin();
        String destination = tvlData.getDestination();
        String airline = tvlData.getAirline();
        String flightNum = tvlData.getFlightNumber();
        
        // Parse departure and arrival date/time
        String depDate = "", depTime = "", arrDate = "", arrTime = "";
        String depDateFormatted = "", arrDateFormatted = "";
        String depTimeFormatted = "", arrTimeFormatted = "";
        
        // Split datetime by component separator
        String[] dateTimeComponents = datetime.split(Pattern.quote(String.valueOf(separators.getSubElement())));
        
        if (dateTimeComponents.length >= 4) {
            depDate = dateTimeComponents[0];  // 310825
            depTime = dateTimeComponents[1];  // 1435
            arrDate = dateTimeComponents[2];  // 310825
            arrTime = dateTimeComponents[3];  // 2325
            
            // Format dates for display (convert DDMMYY to DD/MM/YY)
            if (depDate.length() == 6) {
                depDateFormatted = depDate.substring(0, 2) + "/" + 
                                 depDate.substring(2, 4) + "/" + 
                                 depDate.substring(4, 6);
            } else {
                depDateFormatted = depDate;
            }
            
            if (arrDate.length() == 6) {
                arrDateFormatted = arrDate.substring(0, 2) + "/" + 
                                 arrDate.substring(2, 4) + "/" + 
                                 arrDate.substring(4, 6);
            } else {
                arrDateFormatted = arrDate;
            }
            
            // Format times for display (convert HHMM to HH:MM)
            if (depTime.length() == 4) {
                depTimeFormatted = depTime.substring(0, 2) + ":" + depTime.substring(2, 4);
            } else {
                depTimeFormatted = depTime;
            }
            
            if (arrTime.length() == 4) {
                arrTimeFormatted = arrTime.substring(0, 2) + ":" + arrTime.substring(2, 4);
            } else {
                arrTimeFormatted = arrTime;
            }
        }
        
        return new FlightDetails(airline, flightNum, origin, destination,
                               depDateFormatted, depTimeFormatted, 
                               arrDateFormatted, arrTimeFormatted);
    }
    
    /**
     * Format date from YY/MM/DD to DD/MM/YYYY
     */
    public static String formatDate(String input) throws Exception {
        SimpleDateFormat inputFormat = new SimpleDateFormat("yy/MM/dd");
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy");
        Date date = inputFormat.parse(input);
        return outputFormat.format(date);
    }
}