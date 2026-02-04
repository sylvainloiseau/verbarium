package com.example.ui.model;

public final class ExampleRow {
    private final String type;
    private final String fr;
    private final String gloss;
    private final String em;

    public ExampleRow(String type, String fr, String gloss) {
        this(type, fr, gloss, "");
    }

    public ExampleRow(String type, String fr, String gloss, String em) {
        this.type = type;
        this.fr = fr;
        this.gloss = gloss;
        this.em = em;
    }

    public String getType() {
        return type;
    }

    public String getFr() {
        return fr;
    }

    public String getGloss() {
        return gloss;
    }

    public String getEm() {
        return em;
    }
}

