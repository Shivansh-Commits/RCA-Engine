package com.l3.engine.model;

import java.util.*;

public class Passenger {
    private String name; // normalized name as displayed
    private String docNum;  // doc number

    public String getDocType() {
        return "("+docType+")";
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    private String docType; // doc type
    private String dtm;  // date
    private String recordedKey; // name|doc|dtm (normalized name)
    private String sources; // CSV of sources
    private int count;
    private List<String> invalidNads = new ArrayList<>();
    private List<String> invalidDocs = new ArrayList<>();
    private List<String> missingSegments = new ArrayList<>();
    private List<String> miscFlags = new ArrayList<>();

    public Passenger(String name, String docNum, String dtm, String source,String docType) {
        this.name = name == null ? "" : name;
        this.docNum = docNum == null ? "" : docNum;
        this.dtm = dtm == null ? "" : dtm;
        this.sources = source == null ? "" : source;
        this.count = 1;
        this.docType = docType;
        this.recordedKey = name + "|" + (docNum == null ? "" : docNum) + "|" + (dtm == null ? "" : dtm);
    }

    // copy constructor
    public Passenger(Passenger other) {
        this.name = other.name;
        this.docNum = other.docNum;
        this.docType = other.docType;
        this.dtm = other.dtm;
        this.recordedKey = other.recordedKey;
        this.sources = other.sources;
        this.count = other.count;
        this.invalidNads = new ArrayList<>(other.invalidNads);
        this.invalidDocs = new ArrayList<>(other.invalidDocs);
        this.missingSegments = new ArrayList<>(other.missingSegments);
        this.miscFlags = new ArrayList<>(other.miscFlags);
    }

    public String getName() { return name; }
    public String getDocNum() { return docNum; }
    // DTM shown as DOB in PS
    public String getDtm() { return dtm; }
    public String getRecordedKey() { return recordedKey; }
    public int getCount() { return count; }
    public String getSources() { return sources; }

    public String getDocTypeWithParens() {
        if (docNum == null || docNum.isEmpty()) return "";
        // PS used doc type separately; in many places Document was printed w/o parentheses. We keep doc as standalone.
        // If you need type like (P)XXX, that would require storing docType; current implementation stores only number.
        return "("+docType+")" + docNum; // to mimic earlier PS output like (P)NUMBER. If docType was not P, caller's parse will not set to this.
    }

    public void setRecordedKey(String rk) { this.recordedKey = rk; }

    public void incrementCount() { this.count++; }
    public void incrementCountBy(int n) { this.count += n; }

    public void addSource(String s) {
        if (s == null || s.isEmpty()) return;
        if (this.sources == null || this.sources.isEmpty()) this.sources = s;
        else {
            // ensure unique
            Set<String> parts = new LinkedHashSet<>(Arrays.asList(this.sources.split("\\s*,\\s*")));
            parts.addAll(Arrays.asList(s.split("\\s*,\\s*")));
            this.sources = String.join(", ", parts);
        }
    }

    public void addInvalidMarker(String m) { this.miscFlags.add(m); }

    public List<String> getInvalidNads() { return invalidNads; }
    public List<String> getInvalidDocs() { return invalidDocs; }
    public List<String> getMissingSegments() { return missingSegments; }

    public String keyForLookup() {
        return this.name + "|" + (this.docNum == null ? "" : this.docNum) + "|" + (this.dtm == null ? "" : this.dtm);
    }
}
