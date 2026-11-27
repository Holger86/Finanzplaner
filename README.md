# Finanzplaner

Windows-Desktop-Anwendung (Java 25, JavaFX, SQLite/Liquibase) zur Erstellung
und Simulation tabellarischer Liquiditätspläne.

## Funktionen der Oberfläche

* **Plan-Auswahl:** Gespeicherte Pläne öffnen, löschen, neu anlegen, speichern
  oder als neuen Plan speichern. Ungespeicherte Änderungen werden im Titel mit
  `*` markiert und vor dem Verwerfen abgefragt.
* **Parameterleiste:** Planname, Startdatum, Zeitraum in Monaten (Standard 12)
  oder per Enddatum, Untergrenze/Dispo (`0,00 €` oder negativ).
* **Kontenverwaltung:** Beliebig viele Konten mit Startsaldo; das Startkapital
  ist die Summe aller Konten.
* **Posten-Editor:** Einnahme/Ausgabe, Betrag und Wiederholungsregel
  (einmalig, Intervall alle N Tage/Wochen/Monate/Quartale, erster oder letzter
  Werktag des Monats) mit dynamischen Feldern und Vorschau der nächsten Termine.
  Die Reihenfolge von Posten am selben Tag ist per ▲/▼ steuerbar.
* **Live-Vorschau:** Tabelle `Datum | Was | Betrag | Neue Gesamtsumme` mit
  Heatmap-Färbung (Grün → Gelb → Rot, tiefrot unterhalb der Untergrenze),
  identisch zu den Exporten. Ein Warnbanner nennt das erste Datum unter der
  Untergrenze sowie den niedrigsten Stand.
* **Fortschreiben:** Schätzt den heutigen Kontostand aus dem gewählten Plan,
  verlangt die Eingabe/Bestätigung der realen Kontostände, übernimmt
  regelmäßige Zahlungen ohne Doppelbuchung am Stichtag und entfernt
  vergangene einmalige Zahlungen.
* **Export:** Excel (`.xlsx`) und LibreOffice/OpenOffice Calc (`.ods`).

## Voraussetzungen, Tests und Start

Java 25 und Maven werden benötigt. Mit aktiviertem JDK 25:

```shell
mvn test
```

Start aus der Entwicklungsumgebung:

```shell
mvn javafx:run
```

Unter Windows liegt die Datenbank standardmäßig unter
`%APPDATA%\Finanzplaner\finanzplaner.db`. Das Verzeichnis wird angelegt.
Mit der Systemeigenschaft `-Dfinanzplaner.db=<Pfad>` kann eine andere
Datenbankdatei verwendet werden (z. B. ohne `APPDATA`).

## Windows-Paketierung (`.exe`)

Voraussetzungen: Windows, JDK 25 (`JAVA_HOME`) und WiX Toolset im `PATH`.
Exakter Befehl zum Bauen des Installers:

```shell
mvn clean package -Pwindows-package
```

Ergebnis: `target/dist/Finanzplaner-1.0.0.exe` (mit Startmenüeintrag und
Desktop-Verknüpfung).

`jlink` erzeugt eine reduzierte Runtime mit den Modulen `javafx.controls`,
`java.sql`, `java.sql.rowset`, `java.xml`, `java.desktop`, `java.naming`,
`java.management`, `java.net.http`, `java.scripting`, `java.instrument`,
`java.security.jgss`, `jdk.unsupported` und `jdk.localedata` (nur Locales
`de` und `en`, nötig für die deutsche Zahlen- und Währungsformatierung).
Die übrigen Bibliotheken einschließlich SQLite-Natives und
Liquibase-Ressourcen liegen auf dem Klassenpfad der Anwendung, da Liquibase
ein automatisches Modul ist und nicht direkt mit `jlink` gelinkt werden kann.
JavaFX wird ausschließlich aus der Runtime geladen.
