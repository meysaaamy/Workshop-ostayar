package com.workshop.ledger.domain;

import org.junit.Test;

/** Adapts the same standalone parity checks to the Gradle test runner. */
public class DomainSuiteTest {
    private static String path(String relative) { return System.getProperty("stayar.root") + "/" + relative; }
    @Test public void isolatedParity() { DomainParityKt.main(new String[]{path("parity/golden/legacy-sample.json"), path("parity/golden/calendar-1200-1700.jsonl"), path("Stayar-v1_16.apk")}); }
    @Test public void computeParity() { FinancialParityKt.main(new String[]{path("parity/golden/legacy-sample.json")}); }
    @Test public void constraints() { InputConstraintsTestKt.main(); }
    @Test public void numberFormat() { LegacyReportNumberTestKt.main(new String[]{path("parity/golden/number-format.jsonl")}); }
    @Test public void journal() { AppendOnlyJournalTestKt.main(); }
}
