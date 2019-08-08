package com.sk7software.mileageroutetracker.model;

import java.io.Serializable;

public class DevMessage implements Serializable {
    private int id;
    private String text;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return id + ": " + text;
    }
}
