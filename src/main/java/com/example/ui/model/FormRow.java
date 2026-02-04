package com.example.ui.model;

public final class FormRow {
    private final String lang;
    private final String value;

    public FormRow(String lang, String value) {
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

