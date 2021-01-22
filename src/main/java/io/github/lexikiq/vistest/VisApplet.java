package io.github.lexikiq.vistest;

import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;
import processing.data.JSONObject;
//import com.hamoid.VideoExport;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class VisApplet extends PApplet {
    public final Map<String, Speedrunner> speedrunners = new HashMap<>(); // all speedrunners
    public Speedrunner[] runnerArray;
    public int DATA_LENGTH; // how many dates/data entries there are
    public Date[] dates;
    public double[] maxes;
    public double[] unitChoices;
//    public final VideoExport videoExport = new VideoExport(this, "output.mp4");

    public PFont font;
    public int frames = 0;//(int) (FRAMES_PER_DAY*365*7);
    public JSONObject metadata;

    public static final float FRAMES_PER_DAY = 2f;
    public static final int RANK_SMOOTHING = 4;
    public static final int S_WIDTH = 1920; // screen width
    public static final int S_HEIGHT = 1080; // screen height
    public static final int X_MIN = 50;
    public static final int X_MAX = S_WIDTH-200;
    public static final int Y_MIN = 200;
    public static final int Y_MAX = S_HEIGHT-25;
    public static final int WIDTH = X_MAX-X_MIN; // drawing width
    public static final int HEIGHT = Y_MAX-Y_MIN; // drawing height
    public static final int DISPLAY_RANKS = 10; // how many people to display (ie top 10)
    public static final float BAR_PROPORTION = 0.7f; // how much space the bar should fill up as a percentage
    public static final int BAR_HEIGHT = (int) ((rankToY(1)-rankToY(0)) * BAR_PROPORTION);
    public static final int MIN_VALUE = 90*60;
    public static final int NAME_FONT_SIZE = 54;
    public static final int DATE_FONT_SIZE = 96;
    public static final int COMMENT_FONT_SIZE = 24;
    public static final int TITLE_TOP_MARGIN = 96+16;
    public static final int IMAGE_PADDING = 4;
    public static final SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy");
    public static final Random rand = new Random();
    public static final int[] SCALE_UNITS = {1, 5, 10, 15, 30, 60, 120, 180, 300, 600, 900, 1800, 3600, 7200, 10800, 18000, 36000, 86400, 172800};
    public static final int UNITS_GOAL = 3; // how many units we'd like to fit on screen
    public static final int BAR_FADE_SPEED = 3; // how fast the tick marks fade (not exactly in seconds)

    public static final boolean USE_MILLISECONDS = false;

    static {
        rand.setSeed(1152003);
    }

    public void settings() { // diet setup
        size(S_WIDTH, S_HEIGHT);
    }

    public void setup() {
        frameRate(60);
        font = loadFont("Jygquif1-96.vlw");
        metadata = loadJSONObject("metadata.json");
        JSONObject pfps = metadata.getJSONObject("pfps");
        JSONObject players = loadJSONObject("players.json");
        String[] textFile = loadStrings("runs.csv");
        DATA_LENGTH = textFile.length - 1;
        maxes = new double[DATA_LENGTH];
        unitChoices = new double[DATA_LENGTH];
        dates = new Date[DATA_LENGTH];
        runnerArray = new Speedrunner[players.keys().size()];

        // create speedrunner objects
        int c = 0;
        for (Object playerObject : players.keys()) {
            String player = (String) playerObject;
            PImage img = null;
            if (pfps.hasKey(player)) img = loadImage("pfps/"+pfps.getString(player)+".png");
            Speedrunner speedrunner = new Speedrunner(player, players.getJSONArray(player), DATA_LENGTH, img);
            speedrunners.put(player, speedrunner);
            runnerArray[c] = speedrunner;
            c++;
        }

        initUserData(textFile);
        initUnits();

//        videoExport.startMovie();
    }

    public void initUserData(String[] textFile) {
        JSONObject runData = metadata.getJSONObject("runs");
        String[] header = textFile[0].split(",");
        for (int i = 1; i < textFile.length; i++) {
            String row = textFile[i];
            String[] cols = row.split(",");
            String date = cols[0];
            int[] dateSplit = Arrays.stream(date.split("-")).mapToInt(Integer::parseInt).toArray();
            LocalDate localDate = LocalDate.of(dateSplit[0], dateSplit[1], dateSplit[2]);
            dates[i-1] = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Set<Speedrunner> runners = new HashSet<>();
            for (int c = 1; c < cols.length; c++) {
                String runID = cols[c];
                // default values are fine so ignore empty data
                if (runID.isEmpty()) {
                    continue;
                }

                JSONObject run = runData.getJSONObject(runID);
                float time = run.getFloat("time_t");


                String head = header[c];
                Speedrunner speedrunner = speedrunners.get(head);
                speedrunner.values[i - 1] = time-MIN_VALUE;
                speedrunner.displayValues[i - 1] = displayTime(time, USE_MILLISECONDS, true, true);

                if (!run.isNull("comment")) {
                    String[] mComment = run.getString("comment").split("\n");
                    String comment = mComment[0];
                    if (mComment.length > 1) comment += " [...]";

                    int commentIndex = -1;
                    for (int m = 0; m < speedrunner.comments.size(); m++) {
                        if (speedrunner.comments.get(m).equals(comment)) {
                            commentIndex = m;
                            break;
                        }
                    }
                    if (commentIndex == -1) {
                        commentIndex = speedrunner.comments.size();
                        speedrunner.comments.add(comment);
                    }
                    speedrunner.commentIndex[i - 1] = commentIndex;
                }

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
                    maxes[i-1] = runner.values[i-1];
                }
            }
        }
    }

    public void initUnits() {
        for (int d = 0; d < DATA_LENGTH; d++) {
            float scale = getXScale(d);
            for (int u = 0; u < SCALE_UNITS.length; u++) {
                if (SCALE_UNITS[u] >= scale/UNITS_GOAL) {
                    unitChoices[d] = u-1;
                    break;
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

    public float getXScale(float at) {
        return avgIndex(maxes, at, 14);
    }

    public static float valueToX(float value, float scale) {
        return lerp(X_MIN, X_MAX, value/scale);
    }

    public static float rankToY(float rank) {
        return Y_MIN + rank * ((float) HEIGHT/DISPLAY_RANKS);
    }

    public static float getDayFromFrames(int frames) {
        return frames/FRAMES_PER_DAY;
    }

    public void draw() {
        float currentDayIndex = getDayFromFrames(frames);
        float currentScale = getXScale(currentDayIndex);
        drawBackground(currentDayIndex);
        drawHorizTickMarks(currentDayIndex, currentScale);
        drawBars(currentDayIndex, currentScale);

//        videoExport.saveFrame();
        if(getDayFromFrames(frames+1) >= DATA_LENGTH) {
//            videoExport.endMovie();
            exit();
        }
        frames++;
    }

    public void drawBackground(float currentDay) {
        background(0);
        fill(255);
        textFont(font, DATE_FONT_SIZE);

        // date
        textAlign(RIGHT, BOTTOM);
        text(formatter.format(dates[floor(currentDay)]), S_WIDTH-20, TITLE_TOP_MARGIN);

        // game + category
        String game = metadata.getString("game");
        String category = metadata.getString("category");

        textAlign(LEFT, BOTTOM);
        text(game, X_MIN, TITLE_TOP_MARGIN);

        int categoryX = (int) (textWidth(game)+8+X_MIN);
        textSize(DATE_FONT_SIZE * (2f/3f));
        fill(204f); // 0.8f * 255
        text(category, categoryX, TITLE_TOP_MARGIN-4);
    }

    public void drawHorizTickMarks(float currentDay, float currentScale) {
        float preferredUnit = avgIndex(unitChoices, currentDay, BAR_FADE_SPEED);
        int unitIndex = jitterFix(preferredUnit);
        int thisUnit = SCALE_UNITS[unitIndex];
        int nextUnit = SCALE_UNITS[unitIndex+1];
        float unitRem = preferredUnit % 1.0f;
        if (unitRem > 0.99) {unitRem = 0;}
        drawTickMarksOfUnit(thisUnit, currentScale, 255-unitRem*255);
        if (unitRem >= 0.01) {
            drawTickMarksOfUnit(nextUnit, currentScale, unitRem*255);
        }
    }

    public void drawTickMarksOfUnit(int thisUnit, float currentScale, float opacity) {
        for (int v = 0; v < currentScale * 1.4; v+=thisUnit) {
            float x = valueToX(v, currentScale);

            fill(100, 100, 100, opacity);
            float W = 4; // width of the bar
            float Wh = W/2f; // half of the width of the bar
            float yOffset = 20; // how far above the top of the screen to render
            rect(x-Wh, Y_MIN-yOffset, W, HEIGHT+yOffset);

            boolean firstMark = v == 0;
            int align = firstMark ? LEFT : CENTER;
            if (firstMark) x -= 10;

            textAlign(align);
            textFont(font, 50);
            String display = displayTime(v+MIN_VALUE, false, true, true);
            text(display, x, Y_MIN-yOffset-10);
        }
    }

    public static String displayTime(float seconds, boolean useMilliseconds, boolean useSeconds, boolean useHours) {
        int h = (int) ((seconds/60)/60);
        String out = String.format("%d:%02d:%06.3f", h, (int) ((seconds/60) % 60), seconds%60);
        if (!useMilliseconds && useSeconds) out = out.substring(0, out.length()-4);
        if (!useSeconds) out = out.substring(0, out.length()-7);
        if (!useHours || h == 0) out = out.substring(2);
        return out;
    }

    public static int jitterFix(float f) {
        if (abs(f - floor(f)) > 0.99) return ceil(f);
        return floor(f);
    }

    public void drawBars(float currentDay, float currentScale) {
        noStroke();
        textFont(font, NAME_FONT_SIZE);
        for (Speedrunner sr : runnerArray) {
            float val = linIndex(sr.values, currentDay);
            float fx = valueToX(val, currentScale);
            float rank = avgIndex(sr.ranks, currentDay, RANK_SMOOTHING);
            float fy = rankToY(rank);
            int x = jitterFix(fx);
            int y = jitterFix(fy);
            if (y > S_HEIGHT) {
                continue;
            }

            Color color = sr.getColor();
            fill(color.getRed(), color.getGreen(), color.getBlue());
            rect(X_MIN, y, x - X_MIN, BAR_HEIGHT);

            int textX = X_MIN + 6;
            int origTextX = textX;
            int textY = y + BAR_HEIGHT - 12;

            PImage image = sr.getImage();
            if (image != null){
                // scale aspect ratios correctly (this might not be perfectly efficient)
                int maxDim = BAR_HEIGHT - IMAGE_PADDING * 2;

                int imgH;
                float ratio;
                int imgW;
                int wOffset = 0;
                int hOffset = 0;

                if (image.pixelHeight >= image.pixelWidth) {
                    imgH = maxDim;
                    ratio = (float) imgH / image.pixelHeight;
                    imgW = (int) (image.pixelWidth * ratio);
                    wOffset = (maxDim - imgW) / 2;
                } else {
                    imgW = maxDim;
                    ratio = (float) imgW / image.pixelWidth;
                    imgH = (int) (image.pixelHeight * ratio);
                    hOffset = (maxDim - imgH) / 2;
                }

                image(image, textX+wOffset, y+IMAGE_PADDING+hOffset, imgW, imgH);
                textX += maxDim + 6;
            }

            textAlign(LEFT, TOP);
            int commentIndex = sr.commentIndex[round(currentDay)];
            if (commentIndex != -1) {
                String comment = sr.comments.get(commentIndex);
                textSize(COMMENT_FONT_SIZE);
                text(comment, origTextX, textY+16);
            }

            textSize(NAME_FONT_SIZE);

            textAlign(LEFT);
            fill(255);
            text(sr.getDisplayName(), textX, textY);

            textAlign(RIGHT);
            text(sr.displayValues[round(currentDay)], x - 4, textY);
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
        return userNames.getString("international");

        // my CJK JP font doesn't work so oh well
//        if (userNames.isNull("japanese")) {
//            return userNames.getString("international");
//        }
//        if (userNames.isNull("international")) {
//            return userNames.getString("japanese");
//        }
//
//        String[] args = new String[]{userNames.getString("japanese"), userNames.getString("international")};
//        // swap if not japanese first
//        if (!japaneseFirst) {
//            String _temp = args[0];
//            args[0] = args[1];
//            args[1] = _temp;
//        }
//        return String.format("%s (%s)", args);
    }
}
