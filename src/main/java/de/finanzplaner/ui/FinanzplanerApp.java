package de.finanzplaner.ui;

import de.finanzplaner.persistence.SqliteFinancialPlanRepository;
import java.nio.file.Path;
import java.util.Objects;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX application. The database is stored in {@code %APPDATA%\Finanzplaner\finanzplaner.db};
 * the system property {@code finanzplaner.db} may point to a different file.
 */
public final class FinanzplanerApp extends Application {
    public static final String DATABASE_PROPERTY = "finanzplaner.db";

    @Override
    public void start(Stage stage) {
        SqliteFinancialPlanRepository repository;
        try {
            String path = System.getProperty(DATABASE_PROPERTY);
            repository = path == null || path.isBlank()
                    ? new SqliteFinancialPlanRepository()
                    : new SqliteFinancialPlanRepository(Path.of(path));
        } catch (RuntimeException exception) {
            Dialogs.error(null, "Die Datenbank konnte nicht geöffnet werden.", exception);
            Platform.exit();
            return;
        }
        MainView view = new MainView(stage, repository);
        Scene scene = new Scene(view, 1400, 860);
        scene.getStylesheets().add(Objects.requireNonNull(
                FinanzplanerApp.class.getResource("finanzplaner.css"), "finanzplaner.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(event -> {
            if (!view.confirmDiscard()) {
                event.consume();
            }
        });
        stage.show();
    }
}
