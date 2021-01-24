import processing.core.PImage;
import processing.data.JSONArray;
import processing.data.JSONObject;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class Speedrunner implements Comparable<Speedrunner> {
    protected static final String COLOR_TYPE_NAME = "dark"; // can be "light"
    private final String uuid;
    private final JSONArray playerInfo;
    public final float[] values;
    public final String[] displayValues;
    public final int[] ranks;
    private final String displayName;
    private final Color clr;
    public final List<String> runs = new ArrayList<String>();
    public final int[] runIndex;
    private final PImage pImage;
    private int sortValue = -1;
    private PImage flag = null;
    public Speedrunner(String runnerID, JSONArray playerInfo, int dataLength, PImage pImage, Map<String, PImage> flags) {
        uuid = runnerID;
        this.playerInfo = playerInfo;
        this.pImage = pImage;

        values = new float[dataLength];
        ranks = new int[dataLength];
        displayValues = new String[dataLength];
        runIndex = new int[dataLength];
        for (int i = 0; i < dataLength; i++) {
            values[i] = 0;
            ranks[i] = VisApplet.DISPLAY_RANKS+1;
            displayValues[i] = "";
            runIndex[i] = -1;
        }

        displayName = initDisplayName();
        clr = initColor();
        flag = initFlag(flags);
    }

    private PImage initFlag(Map<String, PImage> flags) {
        String flagCode = null;
        for (int i = 0; i < playerInfo.size(); i++) {
            JSONObject playerObject = playerInfo.getJSONObject(i);
            if (!playerObject.hasKey("location") || playerObject.isNull("location")) return null;
            String playerCode = playerObject.getJSONObject("location").getJSONObject("country").getString("code");
            if (flagCode != null && !playerCode.equals(flagCode)) return null;
            flagCode = playerCode;
        }
        return flags.getOrDefault(flagCode, null);
    }

    public PImage getFlag() {
        return flag;
    }

    public void setFlag(PImage flag) {
        this.flag = flag;
    }

    public Float getValueForSort() {
        return values[getSortValue()];
    }

    public int compareTo(Speedrunner s) {
        return getValueForSort().compareTo(s.getValueForSort());
    }

    private String initDisplayName() {
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < playerInfo.size(); i++) {
            JSONObject data = playerInfo.getJSONObject(i);
            boolean isUser = data.getString("rel").equals("user");
            String name;
            if (isUser) {
                JSONObject uNames = data.getJSONObject("names");
                name = uNames.getString("international");
            } else {
                name = data.getString("name");
                // get international name (pensive emoji)
                if (name.startsWith("[jp]")) {
                    String[] jpNames = name.split(" ");
                    if (jpNames.length == 2) {
                        String newName = jpNames[1];
                        if (newName.startsWith("(") && newName.endsWith(")")) {
                            name = newName.substring(1, newName.length()-1);
                        }
                    }
                }
            }
            names.add(name.replaceAll("\\[\\w{2}]", ""));
        }

        // IDE said String.join() wasn't compatible in 5 and while I'm pretty sure processing supports it,
        // I'm doing this anyway just so IntelliJ stops yelling at me
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                out.append(" & ");
            }
            out.append(names.get(i));
        }
        return out.toString();
    }

    private Color initColor() {
        if (playerInfo.size() == 1 && playerInfo.getJSONObject(0).hasKey("name-style")) {
            Color userColor = getUserColor();
            float[] hsb = Color.RGBtoHSB(userColor.getRed(), userColor.getGreen(), userColor.getBlue(), null);
            hsb[1] = Math.max(hsb[1], 0.2f); // stops gray colors (i.e. pure white)
            return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
        } else {
            Random rand = VisApplet.rand;
            float s = 0.6f + rand.nextFloat()*.2f;
            float b = 0.5f + rand.nextFloat()*.2f;
            return Color.getHSBColor(rand.nextFloat(), s, b);
        }
    }

    private Color getUserColor() {
        JSONObject style = playerInfo.getJSONObject(0).getJSONObject("name-style");
        boolean isSolid = style.getString("style").equals("solid");
        if (isSolid) {
            return new Color(Integer.parseInt(style.getJSONObject("color").getString(COLOR_TYPE_NAME).substring(1), 16));
        } else {
            int color1 = Integer.parseInt(style.getJSONObject("color-from").getString(COLOR_TYPE_NAME).substring(1), 16);
            int color2 = Integer.parseInt(style.getJSONObject("color-to").getString(COLOR_TYPE_NAME).substring(1), 16);
            return new Color((color1+color2)/2);
        }
    }

    public String getUuid() {
        return this.uuid;
    }

    public JSONArray getPlayerInfo() {
        return this.playerInfo;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public Color getClr() {
        return this.clr;
    }

    public PImage getpImage() {
        return this.pImage;
    }

    public int getSortValue() {
        return this.sortValue;
    }

    public void setSortValue(int sortValue) {
        this.sortValue = sortValue;
    }
}
