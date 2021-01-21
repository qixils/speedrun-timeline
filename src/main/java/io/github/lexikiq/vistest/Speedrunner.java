package io.github.lexikiq.vistest;

import processing.data.JSONObject;

public class Speedrunner {
    private final String uuid;
    private final JSONObject data;
    public Speedrunner(String runnerID, JSONObject players) {
        uuid = runnerID;
        data = players.getJSONObject(runnerID);
    }
}
