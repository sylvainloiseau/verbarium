package com.example.ui.model;

public final class ExampleRow {
    private final String fr;
    private final String gloss;
    private final String em;

    public ExampleRow(String fr, String gloss, String em) {
        this.fr = fr;
        this.gloss = gloss;
        this.em = em;
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

