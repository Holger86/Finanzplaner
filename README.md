# Finanzplaner

[![CI](https://github.com/Holger86/Finanzplaner/actions/workflows/ci.yml/badge.svg)](https://github.com/Holger86/Finanzplaner/actions/workflows/ci.yml)

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
  (einmalig, Intervall alle N Tage/Wochen/Monate/Quartale/Jahre, erster oder letzter
  Werktag des Monats) mit dynamischen Feldern und Vorschau der nächsten Termine.
  Die Reihenfolge von Posten am selben Tag ist per ▲/▼ steuerbar.
* **Live-Vorschau:** Tabelle `Datum | Was | Betrag | Neue Gesamtsumme` mit
  Heatmap-Färbung (Grün → Gelb → Rot, tiefrot unterhalb der Untergrenze),
  identisch zu den Exporten. Ein Warnbanner nennt das erste Datum unter der
  Untergrenze sowie den niedrigsten Stand.
* **Trenddiagramm:** Umschaltbar zwischen Tabelle und Diagramm. Das Diagramm
  zeigt den Verlauf der Gesamtsumme über die Monate (horizontal) in Euro
  (vertikal) samt gestrichelter Untergrenze.
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
Desktop-Verknüpfung). Die Installer-Version ist `${app.version}` (Standard
`1.0.0`). Eine andere Version, zum Beispiel für ein Release:

```shell
mvn clean package -Pwindows-package -Dapp.version=1.2.3
```

`jpackage` akzeptiert nur 1–4 numerische Komponenten (`1.2.3`), keine Suffixe
wie `-SNAPSHOT`.

`jlink` erzeugt eine reduzierte Runtime mit den Modulen `javafx.controls`,
`java.sql`, `java.sql.rowset`, `java.xml`, `java.desktop`, `java.naming`,
`java.management`, `java.net.http`, `java.scripting`, `java.instrument`,
`java.security.jgss`, `jdk.unsupported` und `jdk.localedata` (nur Locales
`de` und `en`, nötig für die deutsche Zahlen- und Währungsformatierung).
Die übrigen Bibliotheken einschließlich SQLite-Natives und
Liquibase-Ressourcen liegen auf dem Klassenpfad der Anwendung, da Liquibase
ein automatisches Modul ist und nicht direkt mit `jlink` gelinkt werden kann.
JavaFX wird ausschließlich aus der Runtime geladen.

## GitHub-Pipelines

Jeder Pull Request und jeder Push auf `main` startet den Workflow **CI**.
Der Check heißt `Build and test` und führt `mvn verify` mit JDK 25 aus.
Damit ein Pull Request erst nach grünem Check gemergt werden kann, unter
*Settings → Rules → Rulesets* (oder *Branches*) für `main` den Statuscheck
`CI / Build and test` als erforderlich markieren.

Ein Windows-Installer lässt sich in der GitHub-Oberfläche erzeugen. Die EXE
wird angehängt, sobald der Workflow **Release** durchgelaufen ist. Der
Installer ist nicht codesigniert.

* **Release anlegen:** *Releases → Draft a new release*, Tag `vX.Y.Z`
  (zum Beispiel `v1.2.3`) veröffentlichen. Der Workflow baut genau diesen
  Tag und hängt `Finanzplaner-X.Y.Z.exe` an das Release.
* **Oder über Actions:** *Actions → Release → Run workflow*, Branch wählen
  und die Version `1.2.3` angeben. Es entsteht das Release `v1.2.3`
  inklusive EXE.

Der Workflow **Release** muss auf `main` liegen. Actions-Läufe bauen den
gewählten Branch; veröffentlichte Releases bauen den angegebenen Tag. Der
Commit muss diese Pipeline bereits enthalten.

Die Versionsnummer muss 1–4 numerische Komponenten haben, optional mit
führendem `v`. Ein Entwurf allein startet den Build nicht; das Release muss
veröffentlicht werden.
