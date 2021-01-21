package io.github.lexikiq.vistest;

public enum ColorType {
    LIGHT,
    DARK;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
