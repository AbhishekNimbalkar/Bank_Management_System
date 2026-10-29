package com.bankmanagement.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void neutralizesFormulaInjection() {
        assertThat(CsvWriter.cell("=HYPERLINK(\"http://evil\")")).isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(CsvWriter.cell("+1")).isEqualTo("\"'+1\"");
        assertThat(CsvWriter.cell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
    }

    @Test
    void quotesPlainValues() {
        assertThat(CsvWriter.cell("Pune, HQ")).isEqualTo("\"Pune, HQ\"");
        assertThat(CsvWriter.cell(null)).isEqualTo("\"\"");
    }
}
