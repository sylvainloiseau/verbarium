package com.example.ui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Singleton managing the current locale and ResourceBundle for UI strings.
 * Persists the user's language choice via java.util.prefs.
 */
public final class I18n {

    private static final String BUNDLE_BASE = "com.example.ui.messages";
    private static final String PREF_KEY = "ui.language";
    private static final Preferences PREFS = Preferences.userNodeForPackage(I18n.class);

    private static Locale currentLocale;
    private static ResourceBundle bundle;

    static {
        String saved = PREFS.get(PREF_KEY, "fr");
        currentLocale = Locale.of(saved);
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, currentLocale);
    }

    private I18n() {}

    public static String get(String key) {
        try { return bundle.getString(key); }
        catch (java.util.MissingResourceException e) { return "!" + key + "!"; }
    }

    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }

    public static Locale getLocale() { return currentLocale; }

    public static void setLocale(Locale locale) {
        currentLocale = locale;
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, currentLocale);
        PREFS.put(PREF_KEY, locale.getLanguage());
    }

    public static ResourceBundle getBundle() { return bundle; }
}
