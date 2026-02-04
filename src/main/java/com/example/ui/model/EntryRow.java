package com.example.ui.model;

public final class EntryRow {
    private final String id;
    private final String entry;
    private final String code;
    private final String pronunciation;
    private final String language;
    private final String definitions;
    private final String content;
    private final String metaLanguage;

    public EntryRow(
            String id,
            String entry,
            String code,
            String pronunciation,
            String language,
            String definitions,
            String content,
            String metaLanguage
    ) {
        this.id = id;
        this.entry = entry;
        this.code = code;
        this.pronunciation = pronunciation;
        this.language = language;
        this.definitions = definitions;
        this.content = content;
        this.metaLanguage = metaLanguage;
    }

    public String getId() {
        return id;
    }

    public String getEntry() {
        return entry;
    }

    public String getCode() {
        return code;
    }

    public String getPron() {
        return pronunciation;
    }

    public String getLang() {
        return language;
    }

    public String getDef() {
        return definitions;
    }

    public String getContent() {
        return content;
    }

    public String getMetaLang() {
        return metaLanguage;
    }
}

