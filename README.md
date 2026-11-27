# Finanzplaner

Schritt 1 enthält das Domänenmodell, die Simulation, die SQLite-Persistenz
mit Liquibase sowie den XLSX- und ODS-Export. Die JavaFX-Oberfläche und der
Anwendungsstart folgen in Schritt 2.

## Voraussetzungen und Tests

Java 25 und Maven werden benötigt. Mit aktiviertem JDK 25:

```shell
mvn test
```

Unter Windows liegt die Datenbank standardmäßig unter
`%APPDATA%\Finanzplaner\finanzplaner.db`. Das Verzeichnis wird angelegt;
ohne `APPDATA` muss ein expliziter Datenbankpfad angegeben werden.

## Windows-Paketierung

Das Profil `windows-package` ist für ein Windows-JDK 25 und ein installiertes
WiX Toolset vorbereitet. Der geplante Befehl für Schritt 2 lautet:

```shell
mvn clean package -Pwindows-package
```

Der Installer wird unter `target/dist` abgelegt. Das Profil setzt den in
Schritt 2 zu implementierenden Einstiegspunkt
`de.finanzplaner.ui.FinanzplanerLauncher` voraus und ist daher noch kein
fertiger Anwendungs-Installer. Die JDK- und JavaFX-Module werden mit `jlink`
gebündelt; die übrigen Bibliotheken einschließlich SQLite-Natives und
Liquibase-Ressourcen bleiben auf dem Klassenpfad, da Liquibase ein
automatisches Modul ist und nicht direkt mit `jlink` gelinkt werden kann.
