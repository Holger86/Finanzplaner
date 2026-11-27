package de.finanzplaner.ui;

import de.finanzplaner.domain.Account;
import java.util.Optional;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Dialog for creating or editing an account with its opening balance. */
final class AccountDialog {
    private AccountDialog() {
    }

    static Optional<Account> show(Window owner, Account initial) {
        Dialog<Account> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(initial == null ? "Konto anlegen" : "Konto bearbeiten");
        dialog.setHeaderText("Konto und Kontostand zum Startdatum");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField name = new TextField(initial == null ? "" : initial.name());
        name.setPromptText("z. B. Girokonto, Tagesgeld, Bargeld");
        TextField balance = Controls.moneyField(initial == null ? "" : Formats.money(initial.openingBalance()));
        GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.addRow(0, new Label("Bezeichnung:"), name);
        grid.addRow(1, new Label("Startsaldo (€):"), balance);
        Label error = Controls.errorLabel();
        dialog.getDialogPane().setContent(new VBox(10, grid, error));

        Controls.validateOnOk(dialog, error, () -> {
            if (name.getText().isBlank()) {
                throw new IllegalArgumentException("Bitte eine Bezeichnung eingeben.");
            }
            return new Account(name.getText().strip(), Formats.parseMoney(balance.getText()));
        });
        Platform.runLater(name::requestFocus);
        return dialog.showAndWait();
    }
}
