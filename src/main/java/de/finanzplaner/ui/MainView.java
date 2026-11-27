package de.finanzplaner.ui;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.Entry;
import de.finanzplaner.domain.EntryType;
import de.finanzplaner.domain.FinancialPlan;
import de.finanzplaner.persistence.SqliteFinancialPlanRepository;
import de.finanzplaner.persistence.SqliteFinancialPlanRepository.StoredPlan;
import de.finanzplaner.service.ColorScale;
import de.finanzplaner.service.OdsExportService;
import de.finanzplaner.service.SimulationEngine;
import de.finanzplaner.service.SimulationResult;
import de.finanzplaner.service.SimulationRow;
import de.finanzplaner.service.XlsxExportService;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Main window: plan management, period and limit parameters, accounts, entries,
 * live preview with heatmap colouring, warning banner and export.
 */
final class MainView extends BorderPane {
    private static final String DEFAULT_NAME = "Mein Finanzplan";

    private final Stage stage;
    private final SqliteFinancialPlanRepository repository;

    private final ComboBox<StoredPlan> planChooser = new ComboBox<>();
    private final TextField planName = new TextField(DEFAULT_NAME);
    private final DatePicker startDate = Controls.datePicker(LocalDate.now());
    private final Spinner<Integer> months =
            Controls.commitOnFocusLoss(new Spinner<>(1, 1200, PlanPeriod.DEFAULT_MONTHS));
    private final DatePicker endDate =
            Controls.datePicker(PlanPeriod.end(LocalDate.now(), PlanPeriod.DEFAULT_MONTHS));
    private final TextField limitField = Controls.moneyField(Formats.money(BigDecimal.ZERO));
    private final ObservableList<Account> accounts = FXCollections.observableArrayList();
    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private final TableView<Account> accountTable = new TableView<>(accounts);
    private final TableView<Entry> entryTable = new TableView<>(entries);
    private final TableView<SimulationRow> previewTable = new TableView<>();
    private final Label capital = new Label();
    private final Label banner = new Label();
    private final Label summary = new Label();
    private final Label status = new Label();
    private final Button exportXlsx = new Button("Export Excel (.xlsx)");
    private final Button exportOds = new Button("Export Calc (.ods)");

    private Long currentPlanId;
    private boolean dirty;
    private boolean loading;
    private boolean syncingPeriod;
    private FinancialPlan previewPlan;
    private SimulationResult previewResult;
    private BigDecimal scaleLimit = BigDecimal.ZERO;
    private BigDecimal scaleMaximum = BigDecimal.ONE;

    MainView(Stage stage, SqliteFinancialPlanRepository repository) {
        this.stage = stage;
        this.repository = repository;
        getStyleClass().add("main-view");
        setTop(new VBox(toolBar(), parameterBar()));
        SplitPane split = new SplitPane(editorPane(), previewPane());
        split.setDividerPositions(0.44);
        setCenter(split);
        status.getStyleClass().add("status-bar");
        status.setMaxWidth(Double.MAX_VALUE);
        setBottom(status);
        installListeners();
        reloadPlanChooser(null);
        List<StoredPlan> stored = planChooser.getItems();
        if (stored.isEmpty()) {
            newPlan();
        } else {
            StoredPlan last = stored.getLast();
            planChooser.getSelectionModel().select(last);
            showPlan(last.id(), last.plan(), false);
            setStatus("Plan „" + last.plan().name() + "“ geladen.");
        }
    }

    // ---------------------------------------------------------------- layout

    private ToolBar toolBar() {
        planChooser.setPromptText("Gespeicherten Plan wählen …");
        planChooser.setPrefWidth(300);
        planChooser.setCellFactory(list -> new StoredPlanCell());
        planChooser.setButtonCell(new StoredPlanCell());
        Button open = button("Öffnen", "Ausgewählten Plan in den Editor laden", this::openSelected);
        Button continuePlan = button("Fortschreiben …",
                "Auf Basis des ausgewählten Plans ab heute neu starten", this::continueSelected);
        Button delete = button("Löschen", "Ausgewählten Plan aus der Datenbank löschen", this::deleteSelected);
        for (Button button : List.of(open, continuePlan, delete)) {
            button.disableProperty().bind(planChooser.valueProperty().isNull());
        }
        Button create = button("Neu", "Neuen, leeren Plan beginnen", () -> {
            if (confirmDiscard()) {
                newPlan();
            }
        });
        Button save = button("Speichern", "Plan in der Datenbank speichern", () -> save(false));
        Button saveAsNew = button("Als neuen Plan speichern", "Kopie als eigenen Plan speichern", () -> save(true));
        exportXlsx.setOnAction(event -> export("xlsx", "Excel-Arbeitsmappe", new XlsxExportService()::export));
        exportOds.setOnAction(event -> export("ods", "OpenDocument-Tabelle", new OdsExportService()::export));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new ToolBar(new Label("Pläne:"), planChooser, open, continuePlan, delete, new Separator(),
                create, save, saveAsNew, spacer, exportXlsx, exportOds);
    }

    private Node parameterBar() {
        planName.setPrefColumnCount(18);
        months.setPrefWidth(80);
        limitField.setTooltip(new Tooltip("Untergrenze ≤ 0,00 €, z. B. -500,00 bei eingeräumtem Dispositionskredit"));
        HBox bar = new HBox(8,
                new Label("Planname:"), planName,
                new Separator(Orientation.VERTICAL),
                new Label("Startdatum:"), startDate,
                new Label("Zeitraum:"), months, new Label("Monate, bis"), endDate,
                new Separator(Orientation.VERTICAL),
                new Label("Untergrenze / Dispo:"), limitField);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("parameter-bar");
        return bar;
    }

    private Node editorPane() {
        TableColumn<Account, String> accountName = column("Konto", account -> account.name());
        TableColumn<Account, String> accountBalance = column("Startsaldo", account -> Formats.money(account.openingBalance()));
        accountBalance.getStyleClass().add("money-column");
        accountName.setPrefWidth(220);
        accountBalance.setPrefWidth(140);
        accountTable.getColumns().setAll(List.of(accountName, accountBalance));
        accountTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        accountTable.setPlaceholder(new Label("Noch kein Konto – bitte mindestens ein Konto anlegen."));
        accountTable.setPrefHeight(150);
        accountTable.setRowFactory(table -> doubleClickRow(this::editAccount));
        HBox accountButtons = new HBox(6,
                button("+ Konto", "Konto mit Startsaldo hinzufügen", this::addAccount),
                selectionButton(accountTable, "Bearbeiten", this::editAccount),
                selectionButton(accountTable, "Entfernen", this::removeAccount),
                spacer(), capital);
        accountButtons.setAlignment(Pos.CENTER_LEFT);
        capital.getStyleClass().add("capital");

        TableColumn<Entry, String> entryName = column("Bezeichnung", Entry::name);
        TableColumn<Entry, String> entryType = column("Typ",
                entry -> entry.type() == EntryType.EINNAHME ? "Einnahme" : "Ausgabe");
        TableColumn<Entry, String> entryAmount = column("Betrag", entry -> Formats.signedMoney(signed(entry)));
        TableColumn<Entry, String> entryRule = column("Wiederholung", entry -> RecurrenceText.describe(entry.recurrence()));
        entryAmount.getStyleClass().add("money-column");
        entryName.setPrefWidth(150);
        entryType.setPrefWidth(80);
        entryAmount.setPrefWidth(110);
        entryRule.setPrefWidth(300);
        entryTable.getColumns().setAll(List.of(entryName, entryType, entryAmount, entryRule));
        entryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        entryTable.setPlaceholder(new Label("Noch keine Einnahmen oder Ausgaben erfasst."));
        entryTable.setRowFactory(table -> {
            TableRow<Entry> row = doubleClickRow(this::editEntry);
            row.itemProperty().addListener((observable, before, entry) -> {
                row.getStyleClass().removeAll("income-row", "expense-row");
                if (entry != null) {
                    row.getStyleClass().add(entry.type() == EntryType.EINNAHME ? "income-row" : "expense-row");
                }
            });
            return row;
        });
        VBox.setVgrow(entryTable, Priority.ALWAYS);
        HBox entryButtons = new HBox(6,
                button("+ Posten", "Einnahme oder Ausgabe hinzufügen", this::addEntry),
                selectionButton(entryTable, "Bearbeiten", this::editEntry),
                selectionButton(entryTable, "Duplizieren", this::duplicateEntry),
                selectionButton(entryTable, "Entfernen", this::removeEntry),
                selectionButton(entryTable, "▲", () -> moveEntry(-1)),
                selectionButton(entryTable, "▼", () -> moveEntry(1)));
        entryButtons.setAlignment(Pos.CENTER_LEFT);

        VBox pane = new VBox(8,
                heading("Konten zum Startdatum"), accountTable, accountButtons,
                heading("Einnahmen & Ausgaben"), entryTable, entryButtons);
        pane.setPadding(new Insets(10));
        return pane;
    }

    private Node previewPane() {
        TableColumn<SimulationRow, String> date = column("Datum", row -> Formats.date(row.date()));
        TableColumn<SimulationRow, String> what = column("Was", SimulationRow::description);
        TableColumn<SimulationRow, SimulationRow> amount = new TableColumn<>("Betrag");
        amount.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        amount.setCellFactory(column -> new AmountCell());
        TableColumn<SimulationRow, BigDecimal> balance = new TableColumn<>("Neue Gesamtsumme");
        balance.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().balance()));
        balance.setCellFactory(column -> new HeatmapCell());
        date.setPrefWidth(100);
        what.setPrefWidth(260);
        amount.setPrefWidth(140);
        balance.setPrefWidth(160);
        for (TableColumn<SimulationRow, ?> column : List.of(date, what, amount, balance)) {
            column.setSortable(false);
        }
        previewTable.getColumns().setAll(List.of(date, what, amount, balance));
        previewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        previewTable.getStyleClass().add("preview-table");
        VBox.setVgrow(previewTable, Priority.ALWAYS);
        banner.getStyleClass().add("banner");
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setWrapText(true);
        summary.getStyleClass().add("summary");
        summary.setWrapText(true);
        VBox pane = new VBox(8, heading("Vorschau (nur Buchungstage)"), banner, summary, previewTable);
        pane.setPadding(new Insets(10));
        return pane;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-heading");
        return label;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static Button button(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Button selectionButton(TableView<?> table, String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        button.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        return button;
    }

    private static <T> TableColumn<T, String> column(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static <T> TableRow<T> doubleClickRow(Runnable action) {
        TableRow<T> row = new TableRow<>();
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !row.isEmpty()) {
                action.run();
            }
        });
        return row;
    }

    // ------------------------------------------------------------- listeners

    private void installListeners() {
        planName.textProperty().addListener(ignored -> changed());
        limitField.textProperty().addListener(ignored -> changed());
        accounts.addListener((ListChangeListener<Account>) change -> changed());
        entries.addListener((ListChangeListener<Entry>) change -> changed());
        startDate.valueProperty().addListener((observable, before, start) -> {
            if (!loading && start != null) {
                syncPeriod(() -> endDate.setValue(PlanPeriod.end(start, months.getValue())));
            }
            changed();
        });
        months.valueProperty().addListener((observable, before, count) -> {
            if (!loading && startDate.getValue() != null && count != null) {
                syncPeriod(() -> endDate.setValue(PlanPeriod.end(startDate.getValue(), count)));
            }
            changed();
        });
        endDate.valueProperty().addListener((observable, before, end) -> {
            if (!loading && startDate.getValue() != null && end != null) {
                syncPeriod(() -> PlanPeriod.months(startDate.getValue(), end)
                        .ifPresent(count -> months.getValueFactory().setValue(count)));
            }
            changed();
        });
    }

    private void syncPeriod(Runnable update) {
        if (syncingPeriod) {
            return;
        }
        syncingPeriod = true;
        try {
            update.run();
        } finally {
            syncingPeriod = false;
        }
    }

    private void changed() {
        if (loading) {
            return;
        }
        dirty = true;
        recalculate();
    }

    // ---------------------------------------------------------- calculation

    private FinancialPlan buildPlan(boolean requireName) {
        String name = planName.getText().strip();
        if (name.isEmpty()) {
            if (requireName) {
                throw new IllegalArgumentException("Bitte einen Plannamen eingeben.");
            }
            name = DEFAULT_NAME;
        }
        LocalDate start = startDate.getValue();
        LocalDate end = endDate.getValue();
        if (start == null) {
            throw new IllegalArgumentException("Bitte ein Startdatum wählen.");
        }
        if (end == null) {
            throw new IllegalArgumentException("Bitte ein Enddatum oder eine Monatsanzahl wählen.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Das Enddatum darf nicht vor dem Startdatum liegen.");
        }
        BigDecimal limit;
        try {
            limit = Formats.parseMoney(limitField.getText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Untergrenze: " + exception.getMessage(), exception);
        }
        if (limit.signum() > 0) {
            throw new IllegalArgumentException("Die Untergrenze muss kleiner oder gleich 0,00 € sein.");
        }
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens ein Konto mit Startsaldo anlegen.");
        }
        return new FinancialPlan(name, start, end, limit, accounts, entries);
    }

    private void recalculate() {
        updateTitle();
        BigDecimal total = accounts.stream().map(Account::openingBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        capital.setText("Startkapital: " + Formats.money(total));
        limitField.getStyleClass().remove("invalid");
        if (!limitValid()) {
            limitField.getStyleClass().add("invalid");
        }
        try {
            previewPlan = buildPlan(false);
            previewResult = SimulationEngine.simulate(previewPlan);
        } catch (RuntimeException exception) {
            previewPlan = null;
            previewResult = null;
            previewTable.getItems().clear();
            previewTable.setPlaceholder(new Label(Dialogs.message(exception)));
            showBanner("banner-info", "ℹ " + Dialogs.message(exception));
            summary.setText("");
            exportXlsx.setDisable(true);
            exportOds.setDisable(true);
            return;
        }
        scaleLimit = previewPlan.overdraftLimit();
        scaleMaximum = ColorScale.maximum(previewResult, scaleLimit);
        previewTable.getItems().setAll(previewResult.rows());
        previewTable.refresh();
        exportXlsx.setDisable(false);
        exportOds.setDisable(false);

        List<SimulationRow> rows = previewResult.rows();
        SimulationRow last = rows.getLast();
        long bookings = rows.stream().filter(row -> !row.opening()).count();
        summary.setText("Zeitraum " + Formats.date(previewPlan.startDate()) + " – " + Formats.date(previewPlan.endDate())
                + "  ·  Startkapital " + Formats.money(total)
                + "  ·  Endstand " + Formats.money(last.balance())
                + "  ·  Höchststand " + Formats.money(rows.stream().map(SimulationRow::balance)
                        .reduce(BigDecimal::max).orElse(total))
                + "  ·  " + bookings + (bookings == 1 ? " Buchung" : " Buchungen"));
        Optional<SimulationRow> lowest = previewResult.lowestBelowLimit();
        if (lowest.isPresent()) {
            SimulationRow first = rows.stream().filter(row -> row.balance().compareTo(scaleLimit) < 0)
                    .findFirst().orElse(lowest.get());
            showBanner("banner-warning", "⚠ Achtung: Der Kontostand unterschreitet die Untergrenze von "
                    + Formats.money(scaleLimit) + " erstmals am " + Formats.date(first.date())
                    + " (" + Formats.money(first.balance()) + "). Niedrigster Stand: "
                    + Formats.money(lowest.get().balance()) + " am " + Formats.date(lowest.get().date())
                    + " (" + lowest.get().description() + ").");
        } else {
            showBanner("banner-ok", "✔ Der Kontostand bleibt im gesamten Zeitraum oberhalb der Untergrenze von "
                    + Formats.money(scaleLimit) + ".");
        }
    }

    private boolean limitValid() {
        try {
            return Formats.parseMoney(limitField.getText()).signum() <= 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void showBanner(String styleClass, String text) {
        banner.getStyleClass().removeAll("banner-info", "banner-warning", "banner-ok");
        banner.getStyleClass().add(styleClass);
        banner.setText(text);
    }

    private static BigDecimal signed(Entry entry) {
        return entry.type() == EntryType.AUSGABE ? entry.amount().negate() : entry.amount();
    }

    private void updateTitle() {
        String name = planName.getText().isBlank() ? DEFAULT_NAME : planName.getText().strip();
        stage.setTitle("Finanzplaner – " + name + (currentPlanId == null ? " (nicht gespeichert)" : "")
                + (dirty ? " *" : ""));
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    // ------------------------------------------------------------ plan state

    private void newPlan() {
        LocalDate today = LocalDate.now();
        showPlan(null, null, false);
        loading = true;
        try {
            planName.setText(DEFAULT_NAME);
            startDate.setValue(today);
            months.getValueFactory().setValue(PlanPeriod.DEFAULT_MONTHS);
            endDate.setValue(PlanPeriod.end(today, PlanPeriod.DEFAULT_MONTHS));
            limitField.setText(Formats.money(BigDecimal.ZERO));
        } finally {
            loading = false;
        }
        planChooser.getSelectionModel().clearSelection();
        recalculate();
        setStatus("Neuer Plan – bitte Konten sowie Einnahmen und Ausgaben erfassen.");
    }

    /** Shows the given plan in the editor; {@code plan == null} clears all lists. */
    private void showPlan(Long id, FinancialPlan plan, boolean markDirty) {
        loading = true;
        try {
            currentPlanId = id;
            if (plan == null) {
                accounts.clear();
                entries.clear();
            } else {
                planName.setText(plan.name());
                startDate.setValue(plan.startDate());
                endDate.setValue(plan.endDate());
                months.getValueFactory().setValue(PlanPeriod.months(plan.startDate(), plan.endDate())
                        .orElse(months.getValue()));
                limitField.setText(Formats.money(plan.overdraftLimit()));
                accounts.setAll(plan.accounts());
                entries.setAll(plan.entries());
            }
        } finally {
            loading = false;
        }
        dirty = markDirty;
        recalculate();
    }

    boolean confirmDiscard() {
        return !dirty || Dialogs.confirm(stage, "Ungespeicherte Änderungen verwerfen?",
                "Der aktuelle Plan enthält Änderungen, die noch nicht gespeichert wurden.", "Verwerfen");
    }

    private void reloadPlanChooser(Long selectId) {
        try {
            List<StoredPlan> plans = repository.findAll();
            planChooser.getItems().setAll(plans);
            if (selectId != null) {
                plans.stream().filter(plan -> plan.id() == selectId).findFirst()
                        .ifPresent(plan -> planChooser.getSelectionModel().select(plan));
            }
        } catch (RuntimeException exception) {
            Dialogs.error(stage, "Gespeicherte Pläne konnten nicht gelesen werden.", exception);
        }
    }

    private void openSelected() {
        StoredPlan selected = planChooser.getValue();
        if (selected == null || !confirmDiscard()) {
            return;
        }
        try {
            Optional<FinancialPlan> plan = repository.findById(selected.id());
            if (plan.isEmpty()) {
                Dialogs.info(stage, "Plan nicht gefunden", "Der Plan wurde inzwischen gelöscht.");
                reloadPlanChooser(null);
                return;
            }
            showPlan(selected.id(), plan.get(), false);
            setStatus("Plan „" + plan.get().name() + "“ geladen.");
        } catch (RuntimeException exception) {
            Dialogs.error(stage, "Der Plan konnte nicht geladen werden.", exception);
        }
    }

    private void save(boolean asNew) {
        FinancialPlan plan;
        try {
            plan = buildPlan(true);
        } catch (RuntimeException exception) {
            Dialogs.error(stage, "Der Plan kann nicht gespeichert werden.", Dialogs.message(exception));
            return;
        }
        try {
            long id;
            if (!asNew && currentPlanId != null && repository.update(currentPlanId, plan)) {
                id = currentPlanId;
            } else {
                id = repository.save(plan);
            }
            currentPlanId = id;
            dirty = false;
            reloadPlanChooser(id);
            updateTitle();
            setStatus("Plan „" + plan.name() + "“ gespeichert.");
        } catch (RuntimeException exception) {
            Dialogs.error(stage, "Der Plan konnte nicht gespeichert werden.", exception);
        }
    }

    private void deleteSelected() {
        StoredPlan selected = planChooser.getValue();
        if (selected == null || !Dialogs.confirm(stage, "Plan löschen?",
                "Soll der Plan „" + selected.plan().name() + "“ endgültig gelöscht werden?", "Löschen")) {
            return;
        }
        try {
            repository.delete(selected.id());
            if (currentPlanId != null && currentPlanId == selected.id()) {
                currentPlanId = null;
                dirty = true;
                updateTitle();
            }
            reloadPlanChooser(null);
            setStatus("Plan „" + selected.plan().name() + "“ gelöscht.");
        } catch (RuntimeException exception) {
            Dialogs.error(stage, "Der Plan konnte nicht gelöscht werden.", exception);
        }
    }

    private void continueSelected() {
        StoredPlan selected = planChooser.getValue();
        if (selected == null || !confirmDiscard()) {
            return;
        }
        ContinuationProposal proposal;
        try {
            FinancialPlan source = repository.findById(selected.id()).orElse(selected.plan());
            proposal = ContinuationProposal.of(source, LocalDate.now());
        } catch (RuntimeException exception) {
            Dialogs.error(stage, "Der Plan kann nicht fortgeschrieben werden.", Dialogs.message(exception));
            return;
        }
        ContinuePlanDialog.show(stage, proposal).ifPresent(plan -> {
            showPlan(null, plan, true);
            planChooser.getSelectionModel().clearSelection();
            setStatus("Fortgeschriebener Plan ab " + Formats.date(plan.startDate())
                    + " erstellt – bitte prüfen und speichern.");
        });
    }

    // -------------------------------------------------------- accounts/entries

    private void addAccount() {
        AccountDialog.show(stage, null).ifPresent(account -> {
            accounts.add(account);
            accountTable.getSelectionModel().select(account);
        });
    }

    private void editAccount() {
        int index = accountTable.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            AccountDialog.show(stage, accounts.get(index)).ifPresent(account -> {
                accounts.set(index, account);
                accountTable.getSelectionModel().select(index);
            });
        }
    }

    private void removeAccount() {
        int index = accountTable.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            accounts.remove(index);
        }
    }

    private void addEntry() {
        EntryDialog.show(stage, null, startDate.getValue()).ifPresent(entry -> {
            entries.add(entry);
            entryTable.getSelectionModel().select(entries.size() - 1);
        });
    }

    private void editEntry() {
        int index = entryTable.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            EntryDialog.show(stage, entries.get(index), startDate.getValue()).ifPresent(entry -> {
                entries.set(index, entry);
                entryTable.getSelectionModel().select(index);
            });
        }
    }

    private void duplicateEntry() {
        int index = entryTable.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            entries.add(index + 1, entries.get(index));
            entryTable.getSelectionModel().select(index + 1);
        }
    }

    private void removeEntry() {
        int index = entryTable.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            entries.remove(index);
        }
    }

    /** Same-day entries are posted in list order, so the order can be adjusted. */
    private void moveEntry(int delta) {
        int index = entryTable.getSelectionModel().getSelectedIndex();
        int target = index + delta;
        if (index >= 0 && target >= 0 && target < entries.size()) {
            Entry entry = entries.remove(index);
            entries.add(target, entry);
            entryTable.getSelectionModel().select(target);
        }
    }

    // ----------------------------------------------------------------- export

    @FunctionalInterface
    private interface Exporter {
        void export(FinancialPlan plan, SimulationResult result, Path destination) throws IOException;
    }

    private void export(String extension, String description, Exporter exporter) {
        if (previewPlan == null || previewResult == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export als " + description);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                description + " (*." + extension + ")", "*." + extension));
        chooser.setInitialFileName(previewPlan.name().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_") + "." + extension);
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path destination = file.toPath();
        if (!destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            destination = destination.resolveSibling(destination.getFileName() + "." + extension);
        }
        try {
            exporter.export(previewPlan, previewResult, destination);
            setStatus("Exportiert nach " + destination);
        } catch (IOException | RuntimeException exception) {
            Dialogs.error(stage, "Der Export ist fehlgeschlagen.", exception);
        }
    }

    // ------------------------------------------------------------------ cells

    private static final class StoredPlanCell extends ListCell<StoredPlan> {
        @Override
        protected void updateItem(StoredPlan item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.plan().name() + "  (" + Formats.date(item.plan().startDate())
                    + " – " + Formats.date(item.plan().endDate()) + ")");
        }
    }

    private static final class AmountCell extends TableCell<SimulationRow, SimulationRow> {
        @Override
        protected void updateItem(SimulationRow row, boolean empty) {
            super.updateItem(row, empty);
            getStyleClass().removeAll("money-cell", "negative", "positive");
            if (empty || row == null) {
                setText(null);
                return;
            }
            getStyleClass().add("money-cell");
            if (row.opening()) {
                setText(Formats.money(row.amount()));
            } else {
                setText(Formats.signedMoney(row.amount()));
                getStyleClass().add(row.amount().signum() < 0 ? "negative" : "positive");
            }
        }
    }

    /** Colours the running balance exactly like the XLSX and ODS exports. */
    private final class HeatmapCell extends TableCell<SimulationRow, BigDecimal> {
        @Override
        protected void updateItem(BigDecimal balance, boolean empty) {
            super.updateItem(balance, empty);
            getStyleClass().remove("money-cell");
            if (empty || balance == null) {
                setText(null);
                setStyle(null);
                return;
            }
            getStyleClass().add("money-cell");
            setText(Formats.money(balance));
            setStyle(HeatmapStyle.css(ColorScale.color(balance, scaleLimit, scaleMaximum)));
        }
    }
}
