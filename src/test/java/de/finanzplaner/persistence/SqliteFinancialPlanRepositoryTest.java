package de.finanzplaner.persistence;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.BusinessDay;
import de.finanzplaner.domain.Entry;
import de.finanzplaner.domain.EntryType;
import de.finanzplaner.domain.FinancialPlan;
import de.finanzplaner.domain.IntervalUnit;
import de.finanzplaner.domain.Recurrence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SqliteFinancialPlanRepositoryTest {
    private Path testDirectory;
    private Path database;

    @BeforeEach
    void setUp() {
        testDirectory = Path.of("target", "persistence-tests", UUID.randomUUID().toString()).toAbsolutePath();
        database = testDirectory.resolve("nested").resolve("plan.db");
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (Files.exists(testDirectory)) {
            try (var paths = Files.walk(testDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    @Test
    void migratesNewDatabaseAndReopensWithoutRepeatingChangesets() throws Exception {
        SqliteFinancialPlanRepository first = new SqliteFinancialPlanRepository(database);
        assertTrue(Files.isRegularFile(database));
        long id = first.save(plan());
        SqliteFinancialPlanRepository second = new SqliteFinancialPlanRepository(database);
        assertEquals(plan(), second.findById(id).orElseThrow());
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    @Test
    void roundTripsAllDetailsAndUpdatesAndDeletes() throws Exception {
        SqliteFinancialPlanRepository repository = new SqliteFinancialPlanRepository(database);
        FinancialPlan original = plan();
        long id = repository.save(original);
        long secondId = repository.save(original);
        assertNotEquals(id, secondId);
        assertEquals(original, repository.findById(id).orElseThrow());
        assertEquals(List.of(new SqliteFinancialPlanRepository.StoredPlan(id, original),
                new SqliteFinancialPlanRepository.StoredPlan(secondId, original)), repository.findAll());

        FinancialPlan replacement = new FinancialPlan("Ersetzt", LocalDate.of(2028, 1, 1),
                LocalDate.of(2029, 1, 1), new BigDecimal("0.00000"),
                List.of(new Account("Ein Konto", BigDecimal.ZERO)), List.of());
        assertTrue(repository.update(id, replacement));
        assertEquals(replacement, repository.findById(id).orElseThrow());
        assertEquals(original, repository.findById(secondId).orElseThrow());
        assertFalse(repository.update(-1, replacement));
        assertFalse(repository.delete(-1));
        assertTrue(repository.delete(secondId));
        assertTrue(repository.findById(secondId).isEmpty());
        assertEquals(List.of(new SqliteFinancialPlanRepository.StoredPlan(id, replacement)), repository.findAll());
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT (SELECT COUNT(*) FROM account), (SELECT COUNT(*) FROM entry)")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
            assertEquals(0, result.getInt(2));
        }
        assertTrue(repository.save(replacement) > secondId);
    }

    @Test
    void rollsBackWholeSaveAndUpdateWhenChildInsertFails() throws Exception {
        SqliteFinancialPlanRepository repository = new SqliteFinancialPlanRepository(database);
        FinancialPlan original = plan();
        long id = repository.save(original);
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_entry BEFORE INSERT ON entry "
                    + "BEGIN SELECT RAISE(ABORT, 'rejected'); END");
        }
        assertThrows(PersistenceException.class, () -> repository.save(original));
        FinancialPlan changed = new FinancialPlan("Neuer Name", original.startDate(), original.endDate(),
                original.overdraftLimit(), original.accounts(), original.entries());
        assertThrows(PersistenceException.class, () -> repository.update(id, changed));
        assertEquals(List.of(new SqliteFinancialPlanRepository.StoredPlan(id, original)), repository.findAll());
    }

    @Test
    void defaultPathUsesAppDataAndReportsMissingOrBlankAppData() throws Exception {
        Files.createDirectories(testDirectory);
        runDefaultPathProbe(null);
        runDefaultPathProbe(" ");
        Path appData = testDirectory.resolve("appdata");
        runDefaultPathProbe(appData.toString());
        assertTrue(Files.isRegularFile(appData.resolve("Finanzplaner").resolve("finanzplaner.db")));
    }

    private void runDefaultPathProbe(String appData) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.io.tmpdir=" + testDirectory,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                SqliteFinancialPlanRepositoryTest.class.getName(), appData == null || appData.isBlank() ? "missing" : "present");
        if (appData == null) {
            builder.environment().remove("APPDATA");
        } else {
            builder.environment().put("APPDATA", appData);
        }
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    public static void main(String[] args) {
        try {
            new SqliteFinancialPlanRepository();
            if (args[0].equals("missing")) {
                throw new AssertionError("Fehlendes APPDATA wurde akzeptiert.");
            }
        } catch (IllegalStateException e) {
            if (!args[0].equals("missing") || !e.getMessage().contains("APPDATA")) {
                throw e;
            }
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    private static FinancialPlan plan() {
        LocalDate start = LocalDate.of(2024, 2, 29);
        LocalDate end = LocalDate.of(2034, 12, 31);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("Einmal '); DROP TABLE entry; --", EntryType.EINNAHME,
                new BigDecimal("1.234567890123456789000"), new Recurrence.OneTime(start)));
        entries.add(new Entry("Gebucht", EntryType.AUSGABE, new BigDecimal("12.00"),
                new Recurrence.OneTime(start, true)));
        int every = 1;
        for (IntervalUnit unit : IntervalUnit.values()) {
            entries.add(new Entry("Intervall " + unit, EntryType.AUSGABE, new BigDecimal("1E+15"),
                    new Recurrence.Interval(start, unit == IntervalUnit.TAG ? null : end, every++, unit,
                            unit == IntervalUnit.TAG ? null : start.plusDays(20))));
        }
        for (BusinessDay day : BusinessDay.values()) {
            entries.add(new Entry("Geschäftstag " + day, EntryType.EINNAHME, new BigDecimal("0.00001"),
                    new Recurrence.MonthlyBusinessDay(start, day == BusinessDay.LAST ? null : end, day,
                            day == BusinessDay.FIRST ? null : start.plusDays(20))));
        }
        return new FinancialPlan("Plan ä € '", start, end, new BigDecimal("-123456789123456789.000000"),
                List.of(new Account("Konto A", new BigDecimal("-0.00001")),
                        new Account("Konto A", new BigDecimal("1E+20"))), entries);
    }
}
