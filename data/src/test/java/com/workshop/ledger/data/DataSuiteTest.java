package com.workshop.ledger.data;

import org.junit.Test;
import com.workshop.ledger.data.backup.LegacyBackupReaderTestKt;
import com.workshop.ledger.data.export.LegacyCsvTestKt;
import com.workshop.ledger.data.export.FinancialCsvReportTestKt;
import com.workshop.ledger.data.export.AuditCsvReportTestKt;
import com.workshop.ledger.data.migration.LegacyRowConversionTestKt;
import com.workshop.ledger.data.journal.JournalRepositoryTestKt;

public class DataSuiteTest {
    private static String path(String relative) { return System.getProperty("stayar.root") + "/" + relative; }
    @Test public void backupReader() { LegacyBackupReaderTestKt.main(new String[]{path("parity/golden/backup-full.json"), path("parity/golden/backup-project.json")}); }
    @Test public void csv() { LegacyCsvTestKt.main(new String[]{path("parity/golden/legacy-sample.json"), path("parity/golden/reports-sample.json")}); }
    @Test public void financialCsv() { FinancialCsvReportTestKt.main(new String[]{path("parity/golden/reports-sample.json")}); }
    @Test public void auditCsv() { AuditCsvReportTestKt.main(new String[]{path("parity/golden/reports-sample.json")}); }
    @Test public void journalRepository() { JournalRepositoryTestKt.main(); }
    @Test public void conversion() { LegacyRowConversionTestKt.main(new String[]{path("parity/golden/calendar-1200-1700.jsonl")}); }
}
