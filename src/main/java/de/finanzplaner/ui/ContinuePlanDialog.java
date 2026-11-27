package de.finanzplaner.ui;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.Entry;
import de.finanzplaner.domain.FinancialPlan;
import de.finanzplaner.domain.Recurrence;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Continues a stored plan at today's date. The balance estimated from the old plan is
 * shown as a suggestion only; the user must enter and explicitly confirm the actual
 * current balances.
 */
final class ContinuePlanDialog {
    private final ContinuationProposal proposal;
    private final List<TextField[]> rows = new ArrayList<>();
    private final GridPane accountGrid = new GridPane();
    private final Label sum = new Label();

    private ContinuePlanDialog(ContinuationProposal proposal) {
        this.proposal = proposal;
    }

    static Optional<FinancialPlan> show(Window owner, ContinuationProposal proposal) {
        return new ContinuePlanDialog(proposal).open(owner);
    }

    private Optional<FinancialPlan> open(Window owner) {
        FinancialPlan source = proposal.source();
        String cutOff = Formats.date(proposal.cutOff());
        Dialog<FinancialPlan> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Plan fortschreiben");
        dialog.setHeaderText("„" + source.name() + "“ ab " + cutOff + " neu starten");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);

        Label estimate = new Label("Geschätzter Gesamtkontostand laut bisheriger Planung am " + cutOff + ": "
                + Formats.money(proposal.estimatedBalance()));
        estimate.getStyleClass().add("estimate");
        estimate.setWrapText(true);
        Label request = new Label("Bitte geben Sie die realen aktuellen Kontostände ein – einschließlich aller "
                + "Buchungen bis einschließlich " + cutOff + ". Der Schätzwert ist nur ein Vorschlag und "
                + "kann von den tatsächlichen Beständen abweichen.");
        request.getStyleClass().add("request");
        request.setWrapText(true);

        accountGrid.getStyleClass().add("form-grid");
        accountGrid.addRow(0, new Label("Konto"), new Label("Realer Kontostand (€)"));
        for (Account account : source.accounts()) {
            addRow(account.name());
        }
        Button addAccount = new Button("+ Konto");
        addAccount.setOnAction(event -> {
            addRow("");
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        });
        Button useEstimate = new Button("Schätzwert übernehmen");
        useEstimate.setOnAction(event -> {
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i)[1].setText(Formats.money(i == 0 ? proposal.estimatedBalance() : BigDecimal.ZERO));
            }
        });
        useEstimate.setTooltip(new Tooltip(
                "Setzt den Schätzwert beim ersten Konto und 0,00 € bei allen weiteren Konten ein."));

        long repeating = source.entries().stream()
                .filter(entry -> !(entry.recurrence() instanceof Recurrence.OneTime)).count();
        long keptOneTime = source.entries().size() - repeating - proposal.removedEntries().size();
        Label changes = new Label(changesText(repeating, keptOneTime, cutOff));
        changes.setWrapText(true);
        changes.getStyleClass().add("hint");

        CheckBox confirmed = new CheckBox("Ich bestätige, dass die eingegebenen Kontostände den realen, "
                + "aktuellen Beständen entsprechen.");
        confirmed.setWrapText(true);
        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(confirmed.selectedProperty().not());
        ((Button) ok).setText("Fortgeschriebenen Plan erstellen");

        Label error = Controls.errorLabel();
        VBox content = new VBox(12, estimate, request, accountGrid, new HBox(8, addAccount, useEstimate), sum,
                changes, confirmed, error);
        content.setPrefWidth(600);
        dialog.getDialogPane().setContent(content);
        sum.setWrapText(true);
        updateSum();
        Controls.validateOnOk(dialog, error, () -> proposal.apply(accounts()));
        return dialog.showAndWait();
    }

    private String changesText(long repeating, long keptOneTime, String cutOff) {
        StringBuilder text = new StringBuilder();
        text.append(repeating).append(" regelmäßige Posten werden übernommen; ihre Buchungen bis einschließlich ")
                .append(cutOff).append(" gelten als bereits im Kontostand enthalten. ");
        text.append(keptOneTime).append(" zukünftige einmalige Posten bleiben erhalten.");
        List<Entry> removed = proposal.removedEntries();
        if (removed.isEmpty()) {
            text.append(" Es werden keine einmaligen Posten entfernt.");
        } else {
            text.append(" Entfernt werden ").append(removed.size()).append(" vergangene einmalige Posten: ")
                    .append(removed.stream().map(entry -> entry.name() + " ("
                                    + RecurrenceText.describe(entry.recurrence()) + ")")
                            .collect(Collectors.joining(", ")))
                    .append('.');
        }
        text.append(" Zeitraum und Untergrenze werden übernommen und können anschließend angepasst werden.");
        return text.toString();
    }

    private void addRow(String accountName) {
        TextField name = new TextField(accountName);
        name.setPromptText("Bezeichnung");
        TextField balance = Controls.moneyField("");
        balance.setPromptText("z. B. 1.234,56");
        balance.setPrefColumnCount(12);
        balance.textProperty().addListener(ignored -> updateSum());
        rows.add(new TextField[] {name, balance});
        accountGrid.addRow(rows.size(), name, balance);
    }

    private void updateSum() {
        try {
            BigDecimal total = BigDecimal.ZERO;
            for (TextField[] row : rows) {
                if (!row[1].getText().isBlank()) {
                    total = total.add(Formats.parseMoney(row[1].getText()));
                }
            }
            BigDecimal difference = total.subtract(proposal.estimatedBalance());
            sum.setText("Summe der eingegebenen Kontostände: " + Formats.money(total)
                    + " (Abweichung zum Schätzwert: " + Formats.signedMoney(difference) + ")");
        } catch (IllegalArgumentException exception) {
            sum.setText("Summe der eingegebenen Kontostände: – (" + exception.getMessage() + ")");
        }
    }

    private List<Account> accounts() {
        List<Account> accounts = new ArrayList<>();
        for (TextField[] row : rows) {
            String name = row[0].getText().strip();
            String balance = row[1].getText();
            if (name.isEmpty() && balance.isBlank()) {
                continue;
            }
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Bitte für jedes Konto eine Bezeichnung eingeben.");
            }
            if (balance.isBlank()) {
                throw new IllegalArgumentException("Bitte den realen Kontostand für „" + name + "“ eingeben.");
            }
            accounts.add(new Account(name, Formats.parseMoney(balance)));
        }
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens ein Konto mit realem Kontostand eingeben.");
        }
        return accounts;
    }
}
