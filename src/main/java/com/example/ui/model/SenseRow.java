package com.example.ui.model;

public final class SenseRow {
    private final String fr;
    private final String en;
    private final String es;

    public SenseRow(String fr, String en, String es) {
        this.fr = fr;
        this.en = en;
        this.es = es;
    }

    public String getFr() {
        return fr;
    }

    public String getEn() {
        return en;
    }

    public String getEs() {
        return es;
    }
}

