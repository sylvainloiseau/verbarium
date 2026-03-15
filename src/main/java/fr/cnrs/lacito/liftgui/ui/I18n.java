package fr.cnrs.lacito.liftgui.ui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Singleton managing the current locale and ResourceBundle for UI strings.
 * Persists the user's language choice via java.util.prefs.
 */
public final class I18n {

    private static final String BUNDLE_BASE = "fr.cnrs.lacito.liftgui.ui.messages";
    private static final String PREF_KEY = "ui.language";
    private static final Preferences PREFS = Preferences.userNodeForPackage(I18n.class);

    private static Locale currentLocale;
    private static ResourceBundle bundle;

    static {
        String saved = PREFS.get(PREF_KEY, null);
        if (saved == null) {
            saved = "fr";
            PREFS.put(PREF_KEY, saved);
        }
        currentLocale = Locale.of(saved);
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, currentLocale);
    }

    private I18n() {}

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (java.util.MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }

    public static Locale getLocale() {
        return currentLocale;
    }

    public static void setLocale(Locale locale) {
        currentLocale = locale;
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, currentLocale);
        PREFS.put(PREF_KEY, locale.getLanguage());
    }

    public static ResourceBundle getBundle() {
        return bundle;
    }

    public static String formatErrorMessage(String errorKey, Exception exception) {
        String description = get(errorKey);
        Throwable rootCause = rootCause(exception);
        StringBuilder details = new StringBuilder(throwableDetails(exception));

        if (rootCause != null && rootCause != exception) {
            String rootDetails = throwableDetails(rootCause);
            if (!rootDetails.equals(details.toString())) {
                details.append("\n")
                    .append(get("error.rootCause"))
                    .append(" ")
                    .append(rootDetails);
            }
        }

        return description + "\n\n" + get("error.technicalDetails") + " " + details;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String throwableDetails(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }
}
