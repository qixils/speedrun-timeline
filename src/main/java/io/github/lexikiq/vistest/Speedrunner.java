package io.github.lexikiq.vistest;

import lombok.Getter;
import processing.data.JSONArray;
import processing.data.JSONObject;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Getter
public class Speedrunner {
    protected static final ColorType COLOR_TYPE = ColorType.DARK;
    protected static final String COLOR_TYPE_NAME = COLOR_TYPE.toString();
    private final String uuid;
    private final JSONArray playerInfo;
    public final float[] values;
    public final String[] displayValues;
    public final int[] ranks;
    private final String displayName;
    private final Color color;
    public Speedrunner(String runnerID, JSONArray playerInfo, int dataLength) {
        uuid = runnerID;
        this.playerInfo = playerInfo;

        values = new float[dataLength];
        ranks = new int[dataLength];
        displayValues = new String[dataLength];
        for (int i = 0; i < dataLength; i++) {
            values[i] = 0;
            ranks[i] = VisApplet.DISPLAY_RANKS+1;
            displayValues[i] = "";
        }

        displayName = initDisplayName();
        color = initColor();
    }

    private String initDisplayName() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < playerInfo.size(); i++) {
            JSONObject data = playerInfo.getJSONObject(i);
            boolean isUser = data.getString("rel").equals("user");
            String name;
            if (isUser) {
                name = VisApplet.getFullName(data, true);
            } else {
                name = data.getString("name");
            }
            names.add(name.replace("[jp]", ""));
        }

        return String.join(" & ", names);
    }

    private Color initColor() {
        if (playerInfo.size() == 1 && playerInfo.getJSONObject(0).hasKey("name-style")) {
            JSONObject style = playerInfo.getJSONObject(0).getJSONObject("name-style");
            boolean isSolid = style.getString("style").equals("solid");
            if (isSolid) {
                return new Color(Integer.parseInt(style.getJSONObject("color").getString(COLOR_TYPE_NAME).substring(1), 16));
            } else {
                int color1 = Integer.parseInt(style.getJSONObject("color-from").getString(COLOR_TYPE_NAME).substring(1), 16);
                int color2 = Integer.parseInt(style.getJSONObject("color-to").getString(COLOR_TYPE_NAME).substring(1), 16);
                return new Color((color1+color2)/2);
            }
        } else {
            Random rand = VisApplet.rand;
            return Color.getHSBColor(rand.nextFloat(), 0.6f, 0.6f);
        }
    }
}
