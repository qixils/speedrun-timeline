package io.github.lexikiq.vistest;

import processing.core.PApplet;
import processing.data.JSONObject;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class VisApplet extends PApplet {
    public final Map<String, Speedrunner> speedrunners = new HashMap<>(); // all speedrunners
    public Speedrunner[] runnerArray;
    public int DATA_LENGTH; // how many dates/data entries there are
    public String[] dates;
    public double[] MAXES;

    public int frames = (int) (FRAMES_PER_DAY*365*7);

    public static final float FRAMES_PER_DAY = 4f;
    public static final int RANK_SMOOTHING = 4;
    public static final int S_WIDTH = 1920; // screen width
    public static final int S_HEIGHT = 1080; // screen height
    public static final int X_MIN = 50;
    public static final int X_MAX = S_WIDTH-50;
    public static final int Y_MIN = 25;
    public static final int Y_MAX = S_HEIGHT-25;
    public static final int WIDTH = X_MAX-X_MIN; // drawing width
    public static final int HEIGHT = Y_MAX-Y_MIN; // drawing height
    public static final int DISPLAY_RANKS = 10; // how many people to display (ie top 10)
    public static final float BAR_PROPORTION = 0.9f; // how much space the bar should fill up as a percentage
    public static final int BAR_HEIGHT = (int) ((rankToY(1)-rankToY(0)) * BAR_PROPORTION);
    public static final int MIN_VALUE = 90*60;
    public static final Random rand = new Random();

    static {
        rand.setSeed(1152003);
    }

    public void settings() { // diet setup
        size(S_WIDTH, S_HEIGHT);
    }

    public void setup() {
        JSONObject players = loadJSONObject("players.json");
        String[] textFile = loadStrings("runs.csv");
        DATA_LENGTH = textFile.length - 1;
        MAXES = new double[DATA_LENGTH];
        dates = new String[DATA_LENGTH];
        runnerArray = new Speedrunner[players.keys().size()];

        // create speedrunner objects
        int c = 0;
        for (Object playerObject : players.keys()) {
            String player = (String) playerObject;
            Speedrunner speedrunner = new Speedrunner(player, players.getJSONArray(player), DATA_LENGTH);
            speedrunners.put(player, speedrunner);
            runnerArray[c] = speedrunner;
            c++;
        }

        // init user data
        initUserData(textFile);
    }

    public void initUserData(String[] textFile) {
        String[] header = textFile[0].split(",");
        for (int i = 1; i < textFile.length; i++) {
            String row = textFile[i];
            String[] cols = row.split(",");
            String date = cols[0];
            dates[i-1] = date;
            Set<Speedrunner> runners = new HashSet<>();

            for (int c = 1; c < cols.length; c++) {
                String col = cols[c];
                // default values are fine so ignore empty data
                if (col.isEmpty()) {
                    continue;
                }

                double[] units = Arrays.stream(col.split(":")).mapToDouble(Double::parseDouble).toArray();
                float time = (float) (units[2] + (units[1] + units[0] * 60) * 60);
                String head = header[c];
                Speedrunner speedrunner = speedrunners.get(head);
                speedrunner.values[i - 1] = time-MIN_VALUE;
                speedrunner.displayValues[i - 1] = col;
                runners.add(speedrunner);
            }

            // the int[] here is a hack to make the variable usable
            final int[] _i = new int[]{i-1};
            List<Speedrunner> sortedRunners = runners.stream().sorted(Comparator.comparingDouble(x -> x.values[_i[0]])).collect(Collectors.toCollection(ArrayList::new));
            // update ranks
            int maxValueAt = Math.min(sortedRunners.size(), DISPLAY_RANKS)-1;
            for (int c = 0; c < sortedRunners.size() && c < DISPLAY_RANKS; c++) {
                Speedrunner runner = sortedRunners.get(c);
                runner.ranks[i - 1] = c;
                if (c <= maxValueAt) {
                    MAXES[i-1] = runner.values[i-1];
                }
            }
        }
    }

    public float stepIndex(float[] values, float index) {
        return values[(int) index];
    }

    public float linIndex(float[] values, float index) {
        int indexInt = (int) index;
        float indexRem = index%1.0f;
        float before = values[indexInt];
        float after = values[min(indexInt+1, values.length-1)];
        return lerp(before, after, indexRem);
    }

    // averagingWindow generally corresponds to how snappy animations are
    // larger values have a larger window of averaging, making it smoother
    public float avgIndex(float[] values, float index, float averagingWindow) {
        int startIndex = max(0, ceil(index-averagingWindow));
        int endIndex = min(values.length-1, floor(index+averagingWindow));
        float sum = 0;
        float count = 0;
        for (int i = startIndex; i <= endIndex; i++){
            float val = values[i];
            float weight = 0.5f + 0.5f*cos((i-index)/averagingWindow * PI);
            count += weight;
            sum += val*weight;
        }
        return sum/count;
    }

    public float avgIndex(int[] values, float index, float averagingWindow) {
        float[] floatValues = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            floatValues[i] = values[i];
        }
        return avgIndex(floatValues, index, averagingWindow);
    }

    public float avgIndex(double[] values, float index, float averagingWindow) {
        float[] floatValues = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            floatValues[i] = (float) values[i];
        }
        return avgIndex(floatValues, index, averagingWindow);
    }

    public float getXScale(float max) {
        return avgIndex(MAXES, max, 14);
    }

    public static float valueToX(float value, float scale) {
        return lerp(X_MIN, X_MAX, value/scale);
    }

    public static float rankToY(float rank) {
        return Y_MIN + rank * ((float) HEIGHT/DISPLAY_RANKS);
    }

    public void draw() {
        float currentDayIndex = frames/FRAMES_PER_DAY;
        float currentScale = getXScale(currentDayIndex);
        drawBackground(currentDayIndex);
        drawBars(currentDayIndex, currentScale);

        fill(255);
        ellipse(mouseX, mouseY, 20, 20);

        frames++;
    }

    public void drawBackground(float currentDay) {
        background(0);
    }

    public void drawBars(float currentDay, float currentScale) {
        noStroke();
        for (int p = 0; p < runnerArray.length; p++) {
            Speedrunner sr = runnerArray[p];
            float val = linIndex(sr.values, currentDay);
            float x = valueToX(val, currentScale);
            float y = rankToY(avgIndex(sr.ranks, currentDay, RANK_SMOOTHING));
            if (y > S_HEIGHT) {continue;}

            Color color = sr.getColor();
            fill(color.getRed(), color.getGreen(), color.getBlue());
            rect(X_MIN, y, x-X_MIN, BAR_HEIGHT);
        }
    }

    public static void main(String[] args) {
        PApplet.main("io.github.lexikiq.vistest.VisApplet");
    }

    /**
     * For a translated object (i.e. one with a <code>names</code> attribute containing an <code>international</code> and <code>japanese</code> name), returns the full displayable text.
     * Specifically, this returns either the International or Japanese name if only one is available.
     * If both are available, it returns a string formatted like "English (Japanese)", with the order being determined by <code>japaneseFirst</code>.
     *
     * @param namedObject a JSONObject from the speedrun.com api containing a "names" attribute
     * @param japaneseFirst whether or not to display Japanese names first
     * @return displayable text
     */
    public static String getFullName(JSONObject namedObject, boolean japaneseFirst) {

        JSONObject userNames = namedObject.getJSONObject("names");
        if (userNames.isNull("japanese")) {
            return userNames.getString("international");
        }
        if (userNames.isNull("international")) {
            return userNames.getString("japanese");
        }

        String[] args = new String[]{userNames.getString("japanese"), userNames.getString("international")};
        // swap if not japanese first
        if (!japaneseFirst) {
            String _temp = args[0];
            args[0] = args[1];
            args[1] = _temp;
        }
        return String.format("%s (%s)", args);
    }
}
