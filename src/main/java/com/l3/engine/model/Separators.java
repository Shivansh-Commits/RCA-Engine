package com.l3.engine.model;

public class Separators {
    public final char subElement;
    public final char element;
    public final char decimal;
    public final char release;
    public final char segment;
    public final char terminator;

    public Separators(char subElement, char element, char decimal, char release, char segment, char terminator) {
        this.subElement = subElement;
        this.element = element;
        this.decimal = decimal;
        this.release = release;
        this.segment = segment;
        this.terminator = terminator;
    }
}
