package io.github.lexikiq.vistest;

import lombok.Getter;
import processing.data.JSONArray;
import processing.data.JSONObject;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Speedrunner {
    private final String uuid;
    private final JSONArray playerInfo;
    public final double[] values;
    public final String[] displayValues;
    public final int[] ranks;
    private final String displayName;
    public Speedrunner(String runnerID, JSONArray playerInfo, int dataLength) {
        uuid = runnerID;
        this.playerInfo = playerInfo;

        values = new double[dataLength];
        ranks = new int[dataLength];
        displayValues = new String[dataLength];
        for (int i = 0; i < dataLength; i++) {
            values[i] = 0;
            ranks[i] = -1;
            displayValues[i] = "";
        }

        displayName = initDisplayName(playerInfo);
    }

    private String initDisplayName(JSONArray playerInfo) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < playerInfo.size(); i++) {
            JSONObject data = playerInfo.getJSONObject(i);
            boolean isUser = data.getString("rel").equals("user");
            String name;
            if (isUser) {
                JSONObject userNames = data.getJSONObject("names");
                if (userNames.isNull("japanese")) {
                    name = userNames.getString("international");
                } else if (userNames.isNull("international")) {
                    name = userNames.getString("japanese");
                } else {
                    name = String.format("%s (%s)", userNames.getString("japanese"), userNames.getString("international"));
                }
            } else {
                name = data.getString("name");
            }
            names.add(name.replace("[jp]", ""));
        }

        return String.join(" & ", names);
    }


}
