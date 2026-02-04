package com.example.ui.model;

public final class EntryRow {
    private final String id;
    private final String entry;
    private final String category;
    private final String language;
    private final String definitions;
    private final boolean hasDetails;

    public EntryRow(String id, String entry, String category, String language, String definitions, boolean hasDetails) {
        this.id = id;
        this.entry = entry;
        this.category = category;
        this.language = language;
        this.definitions = definitions;
        this.hasDetails = hasDetails;
    }

    public String getId() {
        return id;
    }

    public String getEntry() {
        return entry;
    }

    public String getCategory() {
        return category;
    }

    public String getLang() {
        return language;
    }

    public String getDefinition() {
        return definitions;
    }

    public boolean isHasFields() {
        return hasDetails;
    }
}

