package com.l3.rcaengine.api.utils;

import com.l3.rcaengine.api.model.Separators;

import java.util.*;

public class NormalizeName {

    public static String normalize(String name, Separators separators) {
        if (name == null) return "";

        // build separator char set
        Set<Character> sepSet = new HashSet<>();
        sepSet.add(separators.element);
        sepSet.add(separators.subElement);
        sepSet.add(separators.segment);
        sepSet.add(separators.decimal);
        sepSet.add(separators.release);

        StringBuilder sb = new StringBuilder();
        boolean inSepRun = false;

        for (char c : name.toCharArray()) {
            if (sepSet.contains(c)) {
                if (!inSepRun) {
                    sb.append(' ');
                    inSepRun = true;
                }
            } else {
                sb.append(c);
                inSepRun = false;
            }
        }

        String normalized = sb.toString().replaceAll("\\s{2,}", " ").trim();
        // keep cleaned string (we don't truncate here; truncation was already applied in parser)
        return normalized;
    }
}
