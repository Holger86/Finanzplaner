package de.finanzplaner.ui;

import javafx.application.Application;

/**
 * Entry point for the packaged application. It does not extend {@link Application},
 * so the application jar can be started from the class path while JavaFX is
 * provided as a module of the bundled runtime.
 */
public final class FinanzplanerLauncher {
    private FinanzplanerLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(FinanzplanerApp.class, args);
    }
}
