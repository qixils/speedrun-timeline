package io.github.lexikiq.vistest;

import processing.core.PApplet;
import processing.data.JSONObject;

public class VisTest extends PApplet {
    public void settings() { // diet setup
        size(1280, 720);
    }

    public void setup() {
        JSONObject players = loadJSONObject("players.json");
    }

    public void draw() {
        background(0);
        ellipse(mouseX, mouseY, 20, 20);
    }

    public static void main(String[] args) {
        PApplet.main("io.github.lexikiq.vistest.VisTest");
    }
}
