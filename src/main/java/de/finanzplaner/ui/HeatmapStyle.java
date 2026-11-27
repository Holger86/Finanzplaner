package de.finanzplaner.ui;

import de.finanzplaner.service.RgbColor;
import java.util.Objects;

/** JavaFX inline style for a heatmap cell with readable text on the computed background. */
public final class HeatmapStyle {
    private HeatmapStyle() {
    }

    public static String css(RgbColor background) {
        Objects.requireNonNull(background, "background");
        return "-fx-background-color: " + background.hex() + "; -fx-text-fill: " + textColor(background) + ";";
    }

    public static String textColor(RgbColor background) {
        int luminance = (299 * background.red() + 587 * background.green() + 114 * background.blue()) / 1000;
        return luminance < 140 ? "#FFFFFF" : "#000000";
    }
}
