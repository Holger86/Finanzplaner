package de.finanzplaner.service;

import java.util.Locale;

public record RgbColor(int red, int green, int blue) {
    public RgbColor {
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException("RGB channels must be between 0 and 255");
        }
    }

    public String hex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }
}
