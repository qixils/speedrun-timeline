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

    public void settings() { // diet setup
        size(1280, 720);
    }

    public void setup() {
        JSONObject players = loadJSONObject("players.json");
        String[] textFile = loadStrings("runs.csv");
        DATA_LENGTH = textFile.length - 1;

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
            for (Speedrunner runner : sortedRunners) {
                double currentValue = runner.values[i-1];
                if (currentValue != lastValue) {
                    rank = c;
                }
                runner.ranks[i-1] = rank;
                lastValue = currentValue;
                c++;
            }
            // save data
            dailyRanks.put(date, sortedRunners);
        }
    }

    public void draw() {
        background(0);
        ellipse(mouseX, mouseY, 20, 20);
    }

    public static void main(String[] args) {
        PApplet.main("io.github.lexikiq.vistest.VisApplet");
    }
}
