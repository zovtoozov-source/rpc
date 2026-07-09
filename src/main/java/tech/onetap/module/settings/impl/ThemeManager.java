package tech.onetap.module.settings.impl;

public class ThemeManager {
    private static ThemeManager instance;

    private final Theme defaultTheme = new Theme("SpaceVisuals", 0xFFAA44FF, 0xFF0D0D10);

    private ThemeManager() {
        defaultTheme.rebuildExtendedColors();
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public Theme getCurrentTheme() {
        return defaultTheme;
    }

}