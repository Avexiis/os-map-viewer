package com.xeon.plugins.shortestpath.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

final class TsvParser {
    List<TransportRecord> parse(String contents) {
        List<TransportRecord> records = new ArrayList<>();
        try (Scanner scanner = new Scanner(contents)) {
            if (!scanner.hasNextLine()) {
                return records;
            }
            String[] headers = parseHeaderLine(scanner.nextLine());
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                records.add(parseLine(line, headers));
            }
        }
        return records;
    }

    private String[] parseHeaderLine(String headerLine) {
        String normalized = headerLine;
        if (normalized.startsWith("# ")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized.split("\t");
    }

    private TransportRecord parseLine(String line, String[] headers) {
        String[] fields = line.split("\t", -1);
        Map<String, String> fieldMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            if (i < fields.length) {
                fieldMap.put(headers[i], fields[i]);
            }
        }
        return new TransportRecord(fieldMap);
    }
}
