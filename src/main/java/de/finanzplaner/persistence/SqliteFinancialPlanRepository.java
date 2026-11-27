package de.finanzplaner.persistence;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.BusinessDay;
import de.finanzplaner.domain.Entry;
import de.finanzplaner.domain.EntryType;
import de.finanzplaner.domain.FinancialPlan;
import de.finanzplaner.domain.IntervalUnit;
import de.finanzplaner.domain.Recurrence;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SqliteFinancialPlanRepository {
    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private final Path databasePath;

    public record StoredPlan(long id, FinancialPlan plan) {}

    public SqliteFinancialPlanRepository() {
        this(defaultDatabasePath());
    }

    public SqliteFinancialPlanRepository(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.databasePath.getParent());
            try (Connection connection = openConnection()) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            }
        } catch (Exception e) {
            throw new PersistenceException("SQLite-Datenbank konnte nicht initialisiert werden: " + this.databasePath, e);
        }
    }

    private static Path defaultDatabasePath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            throw new IllegalStateException("APPDATA ist nicht gesetzt; bitte einen expliziten Datenbankpfad angeben.");
        }
        return Path.of(appData).resolve("Finanzplaner").resolve("finanzplaner.db");
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            return connection;
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    public long save(FinancialPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return transaction(connection -> {
            long id;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO financial_plan (name, start_date, end_date, overdraft_limit) VALUES (?, ?, ?, ?)")) {
                bindPlan(statement, plan);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Keine ID für den gespeicherten Finanzplan zurückgegeben.");
                }
                id = result.getLong(1);
            }
            insertChildren(connection, id, plan);
            return id;
        });
    }

    public boolean update(long id, FinancialPlan plan) {
        Objects.requireNonNull(plan, "plan");
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE financial_plan SET name = ?, start_date = ?, end_date = ?, overdraft_limit = ? WHERE id = ?")) {
                bindPlan(statement, plan);
                statement.setLong(5, id);
                if (statement.executeUpdate() == 0) {
                    return false;
                }
            }
            deleteChildren(connection, "account", id);
            deleteChildren(connection, "entry", id);
            insertChildren(connection, id, plan);
            return true;
        });
    }

    public boolean delete(long id) {
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM financial_plan WHERE id = ?")) {
                statement.setLong(1, id);
                return statement.executeUpdate() != 0;
            }
        });
    }

    public Optional<FinancialPlan> findById(long id) {
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT name, start_date, end_date, overdraft_limit FROM financial_plan WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readPlan(connection, id, result)) : Optional.empty();
                }
            }
        });
    }

    public List<StoredPlan> findAll() {
        return transaction(connection -> {
            List<StoredPlan> plans = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, name, start_date, end_date, overdraft_limit FROM financial_plan ORDER BY id");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long id = result.getLong("id");
                    plans.add(new StoredPlan(id, readPlan(connection, id, result)));
                }
            }
            return List.copyOf(plans);
        });
    }

    private static void bindPlan(PreparedStatement statement, FinancialPlan plan) throws SQLException {
        statement.setString(1, plan.name());
        statement.setString(2, plan.startDate().toString());
        statement.setString(3, plan.endDate().toString());
        statement.setString(4, plan.overdraftLimit().toString());
    }

    private static void deleteChildren(Connection connection, String table, long id) throws SQLException {
        // The table name comes only from the two fixed calls in update().
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE plan_id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static void insertChildren(Connection connection, long id, FinancialPlan plan) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO account (plan_id, position, name, opening_balance) VALUES (?, ?, ?, ?)")) {
            int position = 0;
            for (Account account : plan.accounts()) {
                statement.setLong(1, id);
                statement.setInt(2, position++);
                statement.setString(3, account.name());
                statement.setString(4, account.openingBalance().toString());
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO entry (plan_id, position, name, type, amount, recurrence_kind, start_date, end_date, "
                        + "interval_every, interval_unit, business_day, settled, excluded_through) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int position = 0;
            for (Entry entry : plan.entries()) {
                statement.setLong(1, id);
                statement.setInt(2, position++);
                statement.setString(3, entry.name());
                statement.setString(4, entry.type().name());
                statement.setString(5, entry.amount().toString());
                statement.setString(8, null);
                statement.setNull(9, java.sql.Types.INTEGER);
                statement.setString(10, null);
                statement.setString(11, null);
                statement.setNull(12, java.sql.Types.INTEGER);
                statement.setString(13, null);
                switch (entry.recurrence()) {
                    case Recurrence.OneTime oneTime -> {
                        statement.setString(6, "ONE_TIME");
                        statement.setString(7, oneTime.date().toString());
                        statement.setBoolean(12, oneTime.settled());
                    }
                    case Recurrence.Interval interval -> {
                        statement.setString(6, "INTERVAL");
                        statement.setString(7, interval.startDate().toString());
                        statement.setString(8, dateText(interval.endDate()));
                        statement.setInt(9, interval.every());
                        statement.setString(10, interval.unit().name());
                        statement.setString(13, dateText(interval.excludedThrough()));
                    }
                    case Recurrence.MonthlyBusinessDay monthly -> {
                        statement.setString(6, "MONTHLY_BUSINESS_DAY");
                        statement.setString(7, monthly.startDate().toString());
                        statement.setString(8, dateText(monthly.endDate()));
                        statement.setString(11, monthly.type().name());
                        statement.setString(13, dateText(monthly.excludedThrough()));
                    }
                }
                statement.executeUpdate();
            }
        }
    }

    private static String dateText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static FinancialPlan readPlan(Connection connection, long id, ResultSet plan) throws SQLException {
        List<Account> accounts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name, opening_balance FROM account WHERE plan_id = ? ORDER BY position")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    accounts.add(new Account(result.getString("name"), new BigDecimal(result.getString("opening_balance"))));
                }
            }
        }
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name, type, amount, recurrence_kind, start_date, end_date, interval_every, interval_unit, "
                        + "business_day, settled, excluded_through "
                        + "FROM entry WHERE plan_id = ? ORDER BY position")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    LocalDate start = LocalDate.parse(result.getString("start_date"));
                    String endText = result.getString("end_date");
                    LocalDate end = endText == null ? null : LocalDate.parse(endText);
                    String excludedText = result.getString("excluded_through");
                    LocalDate excludedThrough = excludedText == null ? null : LocalDate.parse(excludedText);
                    Recurrence recurrence = switch (result.getString("recurrence_kind")) {
                        case "ONE_TIME" -> new Recurrence.OneTime(start, result.getBoolean("settled"));
                        case "INTERVAL" -> new Recurrence.Interval(start, end, result.getInt("interval_every"),
                                IntervalUnit.valueOf(result.getString("interval_unit")), excludedThrough);
                        case "MONTHLY_BUSINESS_DAY" -> new Recurrence.MonthlyBusinessDay(start, end,
                                BusinessDay.valueOf(result.getString("business_day")), excludedThrough);
                        default -> throw new SQLException("Unbekannter Wiederholungstyp in Eintrag.");
                    };
                    entries.add(new Entry(result.getString("name"), EntryType.valueOf(result.getString("type")),
                            new BigDecimal(result.getString("amount")), recurrence));
                }
            }
        }
        return new FinancialPlan(plan.getString("name"), LocalDate.parse(plan.getString("start_date")),
                LocalDate.parse(plan.getString("end_date")), new BigDecimal(plan.getString("overdraft_limit")),
                accounts, entries);
    }

    @FunctionalInterface
    private interface Transaction<T> {
        T execute(Connection connection) throws SQLException;
    }

    private <T> T transaction(Transaction<T> action) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = action.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException e) {
            throw new PersistenceException("SQLite-Datenbankzugriff fehlgeschlagen: " + databasePath, e);
        }
    }
}
