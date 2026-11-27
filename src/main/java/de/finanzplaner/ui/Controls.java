package de.finanzplaner.ui;

import java.time.LocalDate;
import java.util.function.Supplier;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/** Factory methods for consistently configured input controls. */
final class Controls {
    private Controls() {
    }

    static DatePicker datePicker(LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return Formats.date(date);
            }

            @Override
            public LocalDate fromString(String text) {
                try {
                    return Formats.parseDate(text);
                } catch (IllegalArgumentException exception) {
                    return picker.getValue();
                }
            }
        });
        picker.setPromptText("TT.MM.JJJJ");
        picker.setPrefWidth(130);
        return picker;
    }

    static TextField moneyField(String text) {
        TextField field = new TextField(text);
        field.setPromptText("0,00");
        field.setPrefColumnCount(9);
        field.getStyleClass().add("money-field");
        return field;
    }

    /** Commits typed spinner text when the spinner loses focus. */
    static <T> Spinner<T> commitOnFocusLoss(Spinner<T> spinner) {
        spinner.setEditable(true);
        spinner.focusedProperty().addListener((observable, before, focused) -> {
            if (!focused) {
                try {
                    spinner.increment(0);
                } catch (RuntimeException exception) {
                    spinner.getEditor().setText(String.valueOf(spinner.getValue()));
                }
            }
        });
        return spinner;
    }

    static Label errorLabel() {
        Label label = new Label();
        label.getStyleClass().add("form-error");
        label.setWrapText(true);
        label.setManaged(false);
        label.setVisible(false);
        return label;
    }

    static void showError(Label label, String text) {
        boolean show = text != null && !text.isBlank();
        label.setText(show ? text : "");
        label.setManaged(show);
        label.setVisible(show);
    }

    /**
     * Validates the dialog input when OK is pressed. The dialog stays open and shows
     * the validation message if building the result fails.
     */
    static <T> void validateOnOk(Dialog<T> dialog, Label error, Supplier<T> builder) {
        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        Object[] result = new Object[1];
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                result[0] = builder.get();
                showError(error, null);
            } catch (RuntimeException exception) {
                showError(error, Dialogs.message(exception));
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
                event.consume();
            }
        });
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                @SuppressWarnings("unchecked")
                T value = (T) result[0];
                return value;
            }
            return null;
        });
    }
}
