package de.finanzplaner.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Simple modal message dialogs. */
final class Dialogs {
    private Dialogs() {
    }

    static void error(Window owner, String header, Throwable error) {
        Alert alert = alert(Alert.AlertType.ERROR, owner, "Fehler", header, message(error));
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        TextArea details = new TextArea(trace.toString());
        details.setEditable(false);
        details.setWrapText(false);
        alert.getDialogPane().setExpandableContent(details);
        alert.showAndWait();
    }

    static void error(Window owner, String header, String text) {
        alert(Alert.AlertType.ERROR, owner, "Fehler", header, text).showAndWait();
    }

    static void info(Window owner, String header, String text) {
        alert(Alert.AlertType.INFORMATION, owner, "Hinweis", header, text).showAndWait();
    }

    static boolean confirm(Window owner, String header, String text, String confirmLabel) {
        Alert alert = alert(Alert.AlertType.CONFIRMATION, owner, "Bestätigung", header, text);
        ButtonType yes = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, no);
        return alert.showAndWait().filter(yes::equals).isPresent();
    }

    static String message(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return error.getClass().getSimpleName();
    }

    private static Alert alert(Alert.AlertType type, Window owner, String title, String header, String text) {
        Alert alert = new Alert(type);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(header);
        Label content = new Label(text);
        content.setWrapText(true);
        content.setMaxWidth(520);
        alert.getDialogPane().setContent(content);
        return alert;
    }
}
