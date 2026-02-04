package com.example.ui.model;

public final class SenseValueRow {
    private final String lang;
    private final String value;

    public SenseValueRow(String lang, String value) {
        this.lang = lang;
        this.value = value;
    }

    public String getLang() {
        return lang;
    }

    public String getValue() {
        return value;
    }
}

