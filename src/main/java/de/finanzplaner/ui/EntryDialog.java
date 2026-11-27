package de.finanzplaner.ui;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.BusinessDay;
import de.finanzplaner.domain.Entry;
import de.finanzplaner.domain.EntryType;
import de.finanzplaner.domain.FinancialPlan;
import de.finanzplaner.domain.IntervalUnit;
import de.finanzplaner.domain.Recurrence;
import de.finanzplaner.service.SimulationEngine;
import de.finanzplaner.service.SimulationRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/** Editor for an income or expense entry with fields that adapt to the chosen recurrence rule. */
final class EntryDialog {
    private static final int PREVIEW_COUNT = 6;

    enum Kind {
        EINMALIG("Einmalig"),
        INTERVALL("Regelmäßig (Intervall)"),
        ERSTER_WERKTAG("Erster Werktag im Monat"),
        LETZTER_WERKTAG("Letzter Werktag im Monat");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        static Kind of(Recurrence recurrence) {
            return switch (recurrence) {
                case Recurrence.OneTime ignored -> EINMALIG;
                case Recurrence.Interval ignored -> INTERVALL;
                case Recurrence.MonthlyBusinessDay monthly ->
                        monthly.type() == BusinessDay.FIRST ? ERSTER_WERKTAG : LETZTER_WERKTAG;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Entry original;
    private final TextField name = new TextField();
    private final RadioButton income = new RadioButton("Einnahme");
    private final RadioButton expense = new RadioButton("Ausgabe");
    private final TextField amount = Controls.moneyField("");
    private final ComboBox<Kind> kind = new ComboBox<>(FXCollections.observableArrayList(Kind.values()));
    private final DatePicker date;
    private final DatePicker start;
    private final DatePicker end = Controls.datePicker(null);
    private final Spinner<Integer> every = Controls.commitOnFocusLoss(new Spinner<>(1, 999, 1));
    private final ComboBox<IntervalUnit> unit = new ComboBox<>(FXCollections.observableArrayList(IntervalUnit.values()));
    private final GridPane ruleGrid = new GridPane();
    private final Label preview = new Label();

    private EntryDialog(Entry original, LocalDate defaultDate) {
        this.original = original;
        date = Controls.datePicker(defaultDate);
        start = Controls.datePicker(defaultDate);
        ToggleGroup types = new ToggleGroup();
        income.setToggleGroup(types);
        expense.setToggleGroup(types);
        expense.setSelected(true);
        unit.getSelectionModel().select(IntervalUnit.MONAT);
        unit.setConverter(new StringConverter<>() {
            @Override
            public String toString(IntervalUnit value) {
                return value == null ? "" : switch (value) {
                    case TAG -> "Tag(e)";
                    case WOCHE -> "Woche(n)";
                    case MONAT -> "Monat(e)";
                    case QUARTAL -> "Quartal(e)";
                };
            }

            @Override
            public IntervalUnit fromString(String text) {
                return unit.getValue();
            }
        });
        kind.getSelectionModel().select(Kind.INTERVALL);
        if (original != null) {
            name.setText(original.name());
            (original.type() == EntryType.EINNAHME ? income : expense).setSelected(true);
            amount.setText(Formats.money(original.amount()));
            kind.getSelectionModel().select(Kind.of(original.recurrence()));
            switch (original.recurrence()) {
                case Recurrence.OneTime oneTime -> date.setValue(oneTime.date());
                case Recurrence.Interval interval -> {
                    start.setValue(interval.startDate());
                    end.setValue(interval.endDate());
                    every.getValueFactory().setValue(interval.every());
                    unit.getSelectionModel().select(interval.unit());
                }
                case Recurrence.MonthlyBusinessDay monthly -> {
                    start.setValue(monthly.startDate());
                    end.setValue(monthly.endDate());
                }
            }
        }
        name.setPromptText("z. B. Gehalt, Miete, Versicherung");
        preview.getStyleClass().add("hint");
        preview.setWrapText(true);
        ruleGrid.getStyleClass().add("form-grid");
        ruleGrid.setMinHeight(150);
        every.setPrefWidth(80);
        kind.valueProperty().addListener((observable, before, after) -> layoutRule());
        for (Observable observable : List.of(name.textProperty(), amount.textProperty(), kind.valueProperty(),
                date.valueProperty(), start.valueProperty(), end.valueProperty(), every.valueProperty(),
                unit.valueProperty(), income.selectedProperty())) {
            observable.addListener(ignored -> updatePreview());
        }
        layoutRule();
    }

    static Optional<Entry> show(Window owner, Entry initial, LocalDate defaultDate) {
        EntryDialog editor = new EntryDialog(initial, defaultDate == null ? LocalDate.now() : defaultDate);
        Dialog<Entry> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(initial == null ? "Posten anlegen" : "Posten bearbeiten");
        dialog.setHeaderText("Einnahme oder Ausgabe mit Wiederholungsregel");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.addRow(0, new Label("Bezeichnung:"), editor.name);
        grid.addRow(1, new Label("Typ:"), new HBox(16, editor.income, editor.expense));
        grid.addRow(2, new Label("Betrag (€):"), editor.amount);
        grid.addRow(3, new Label("Wiederholung:"), editor.kind);
        Label error = Controls.errorLabel();
        VBox content = new VBox(12, grid, editor.ruleGrid, editor.preview, error);
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);
        Controls.validateOnOk(dialog, error, editor::build);
        Platform.runLater(editor.name::requestFocus);
        return dialog.showAndWait();
    }

    private void layoutRule() {
        ruleGrid.getChildren().clear();
        Kind selected = kind.getValue();
        if (selected == null) {
            return;
        }
        switch (selected) {
            case EINMALIG -> ruleGrid.addRow(0, new Label("Fälligkeitsdatum:"), date);
            case INTERVALL -> {
                ruleGrid.addRow(0, new Label("Alle:"), new HBox(8, every, unit));
                ruleGrid.addRow(1, new Label("Ab (Startdatum):"), start);
                ruleGrid.addRow(2, new Label("Bis (optional):"), withClear(end));
                Label hint = new Label("Bei Monaten/Quartalen wird auf Monatsenden abgerundet,"
                        + " z. B. 31.01. → 28./29.02. → 31.03.");
                hint.getStyleClass().add("hint");
                hint.setWrapText(true);
                ruleGrid.add(hint, 0, 3, 2, 1);
            }
            case ERSTER_WERKTAG, LETZTER_WERKTAG -> {
                ruleGrid.addRow(0, new Label("Ab (Startdatum):"), start);
                ruleGrid.addRow(1, new Label("Bis (optional):"), withClear(end));
                Label hint = new Label("Werktage sind Montag bis Freitag; gesetzliche Feiertage werden nicht berücksichtigt.");
                hint.getStyleClass().add("hint");
                hint.setWrapText(true);
                ruleGrid.add(hint, 0, 2, 2, 1);
            }
        }
        updatePreview();
    }

    private static HBox withClear(DatePicker picker) {
        Button clear = new Button("Kein Enddatum");
        clear.setOnAction(event -> picker.setValue(null));
        return new HBox(8, picker, clear);
    }

    private Entry build() {
        if (name.getText().isBlank()) {
            throw new IllegalArgumentException("Bitte eine Bezeichnung eingeben.");
        }
        BigDecimal value = Formats.parseMoney(amount.getText());
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Der Betrag muss größer als 0 sein; das Vorzeichen ergibt sich aus dem Typ.");
        }
        Kind selected = kind.getValue();
        if (selected == null) {
            throw new IllegalArgumentException("Bitte eine Wiederholungsregel wählen.");
        }
        Recurrence recurrence = switch (selected) {
            case EINMALIG -> {
                LocalDate due = require(date.getValue(), "Bitte ein Fälligkeitsdatum wählen.");
                boolean settled = original != null && original.recurrence() instanceof Recurrence.OneTime oneTime
                        && oneTime.settled() && oneTime.date().equals(due);
                yield new Recurrence.OneTime(due, settled);
            }
            case INTERVALL -> {
                LocalDate from = startDate();
                if (unit.getValue() == null) {
                    throw new IllegalArgumentException("Bitte eine Intervall-Einheit wählen.");
                }
                LocalDate excluded = original != null && original.recurrence() instanceof Recurrence.Interval interval
                        ? interval.excludedThrough() : null;
                yield new Recurrence.Interval(from, end.getValue(), every.getValue(), unit.getValue(), excluded);
            }
            case ERSTER_WERKTAG, LETZTER_WERKTAG -> {
                LocalDate from = startDate();
                LocalDate excluded = original != null
                        && original.recurrence() instanceof Recurrence.MonthlyBusinessDay monthly
                        ? monthly.excludedThrough() : null;
                yield new Recurrence.MonthlyBusinessDay(from, end.getValue(),
                        selected == Kind.ERSTER_WERKTAG ? BusinessDay.FIRST : BusinessDay.LAST, excluded);
            }
        };
        return new Entry(name.getText().strip(), income.isSelected() ? EntryType.EINNAHME : EntryType.AUSGABE,
                value, recurrence);
    }

    private LocalDate startDate() {
        LocalDate from = require(start.getValue(), "Bitte ein Startdatum wählen.");
        if (end.getValue() != null && end.getValue().isBefore(from)) {
            throw new IllegalArgumentException("Das Enddatum darf nicht vor dem Startdatum liegen.");
        }
        return from;
    }

    private static LocalDate require(LocalDate value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private void updatePreview() {
        Entry entry;
        try {
            entry = new Entry(name.getText().isBlank() ? "Vorschau" : name.getText(),
                    income.isSelected() ? EntryType.EINNAHME : EntryType.AUSGABE, BigDecimal.ONE, build().recurrence());
        } catch (RuntimeException exception) {
            preview.setText("");
            return;
        }
        LocalDate from = switch (entry.recurrence()) {
            case Recurrence.OneTime oneTime -> oneTime.date();
            case Recurrence.Interval interval -> interval.startDate();
            case Recurrence.MonthlyBusinessDay monthly -> monthly.startDate();
        };
        try {
            FinancialPlan probe = new FinancialPlan("Vorschau", from, from.plusYears(3), BigDecimal.ZERO,
                    List.of(new Account("Vorschau", BigDecimal.ZERO)), List.of(entry));
            List<SimulationRow> rows = SimulationEngine.simulate(probe).rows().stream()
                    .filter(row -> !row.opening()).limit(PREVIEW_COUNT + 1L).toList();
            if (rows.isEmpty()) {
                preview.setText("Keine Buchungstermine.");
                return;
            }
            String dates = rows.stream().limit(PREVIEW_COUNT).map(row -> Formats.date(row.date()))
                    .collect(Collectors.joining(", "));
            preview.setText("Nächste Termine: " + dates + (rows.size() > PREVIEW_COUNT ? ", …" : ""));
        } catch (RuntimeException exception) {
            preview.setText("");
        }
    }
}
