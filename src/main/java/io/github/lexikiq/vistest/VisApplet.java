package io.github.lexikiq.vistest;

import processing.core.PApplet;
import processing.data.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class VisApplet extends PApplet {
    private final Map<String, Speedrunner> speedrunners = new HashMap<>(); // all speedrunners
    public int DATA_LENGTH; // how many dates/data entries there are
    public final int DISPLAY_RANKS = 10; // how many people to display (ie top 10)
    public final Map<String, Set<Speedrunner>> dailyRanks = new LinkedHashMap<>(); // date, runner
    public double[] MAXES;
    public double[] SCALES;

    public static final int S_WIDTH = 1920; // screen width
    public static final int S_HEIGHT = 1080; // screen height
    public static final int X_MIN = 50;
    public static final int X_MAX = S_WIDTH-50;
    public static final int Y_MIN = 25;
    public static final int Y_MAX = S_HEIGHT-25;
    public static final int WIDTH = X_MAX-X_MIN; // drawing width
    public static final int HEIGHT = Y_MAX-Y_MIN; // drawing height

    public void settings() { // diet setup
        size(S_WIDTH, S_HEIGHT);
    }

    public void setup() {
        JSONObject players = loadJSONObject("players.json");
        String[] textFile = loadStrings("runs.csv");
        DATA_LENGTH = textFile.length - 1;
        MAXES = new double[DATA_LENGTH];

        // create speedrunner objects
        for (Object playerObject : players.keys()) {
            String player = (String) playerObject;
            speedrunners.put(player, new Speedrunner(player, players.getJSONArray(player), DATA_LENGTH));
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
            Set<Speedrunner> runners = new HashSet<>();

            for (int c = 1; c < cols.length; c++) {
                String col = cols[c];
                // default values are fine so ignore empty data
                if (col.isEmpty()) {
                    continue;
                }

                double[] units = Arrays.stream(col.split(":")).mapToDouble(Double::parseDouble).toArray();
                double time = units[2] + (units[1] + units[0] * 60) * 60;
                String head = header[c];
                Speedrunner speedrunner = speedrunners.get(head);
                speedrunner.values[i - 1] = time;
                speedrunner.displayValues[i - 1] = col;
                runners.add(speedrunner);
            }

            // the int[] here is a hack to make the variable usable
            final int[] _i = new int[]{i-1};
            Set<Speedrunner> sortedRunners = runners.stream().sorted(Comparator.comparingDouble(x -> x.values[_i[0]])).collect(Collectors.toCollection(LinkedHashSet::new));
            double lastValue = Double.MAX_VALUE;
            // update ranks
            int c = 0;
            int rank = c;
            int maxValueAt = Math.min(sortedRunners.size(), DISPLAY_RANKS)-1;
            for (Speedrunner runner : sortedRunners) {
                double currentValue = runner.values[i-1];
                if (currentValue != lastValue) {
                    rank = c;
                }
                runner.ranks[i-1] = rank;
                lastValue = currentValue;

                if (c <= maxValueAt) {
                    MAXES[i-1] = runner.values[i-1];
                }

                c++;
            }

            // save data
            dailyRanks.put(date, sortedRunners);
        }
    }

    public int valueToX(double value) {
        return 0;
    }

    public int rankToY(float rank) {
        return (int) (Y_MIN + rank * (HEIGHT/DISPLAY_RANKS));
    }

    public void draw() {
        background(0);
        ellipse(mouseX, mouseY, 20, 20);
    }

    public static void main(String[] args) {
        PApplet.main("io.github.lexikiq.vistest.VisApplet");
    }

    /**
     * For a translated object (i.e. one with a <code>names</code> attribute containing an <code>international</code> and <code>japanese</code> name), returns the full displayable text.
     *
     * @param namedObject a JSONObject from the speedrun.com api containing a "names" attribute
     * @param japaneseFirst whether or not to display Japanese names first
     * @return the first available string if only one translation is available or a string formatted like "English (Japanese)"
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
