package com.bankmanagement.common.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * Builds RFC 4180 CSV. Cells that start with = + - @ tab or CR are prefixed with a quote so that
 * spreadsheet software does not execute them as formulas (CSV injection).
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static <T> byte[] write(List<String> headers, List<T> rows, List<Function<T, Object>> columns) {
        StringBuilder sb = new StringBuilder("﻿"); // BOM so Excel detects UTF-8
        sb.append(String.join(",", headers.stream().map(CsvWriter::cell).toList())).append("\r\n");
        for (T row : rows) {
            sb.append(String.join(",", columns.stream().map(c -> cell(c.apply(row))).toList())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
