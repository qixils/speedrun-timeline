package io.github.lexikiq.vistest;

import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;
import processing.data.JSONObject;

import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.*;

//import com.hamoid.VideoExport;

public class VisApplet extends PApplet {
    public final Map<String, Speedrunner> speedrunners = new HashMap<String, Speedrunner>(); // all speedrunners
    public final Map<String, PImage> flags = new HashMap<String, PImage>();
    public Speedrunner[] runnerArray;
    public int DATA_LENGTH; // how many dates/data entries there are
    public Date[] dates;
    public double[] maxes;
    public double[] unitChoices;
    public int[] recordHolderDays;
    public Speedrunner recordHolder = null;
    public float dateTextWidth = 0;
    public PImage coverImage = null;
    public PImage missingFlag;
//    public VideoExport videoExport;

    public PFont font;
    public int frames = (int) (FRAMES_PER_DAY*365*4);
    public JSONObject metadata;

    public static final String IMAGE_FOLDER = "pfps/";
    public static final String FLAG_FOLDER = "flags";

    public static final float FRAMES_PER_DAY = 3f;
    public static final int RANK_SMOOTHING = 4;
    public static final int MIN_VALUE = 90*60; // minimum speedrun time

    public static final int S_WIDTH = 1920; // screen width
    public static final int S_HEIGHT = 1080; // screen height
    public static final int X_MIN = 80;
    public static final int X_MAX = S_WIDTH-50;
    public static final int Y_MIN = 200;
    public static final int Y_MAX = S_HEIGHT-25;
    public static final int WIDTH = X_MAX-X_MIN; // drawing width
    public static final int HEIGHT = Y_MAX-Y_MIN; // drawing height

    public static final int DISPLAY_RANKS = 10; // how many people to display (ie top 10)
    public static final String[] PLACEMENTS = {"1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th", "9th", "10th", "11th", "12th", "13th", "14th", "15th", "16th", "17th", "18th", "19th", "20th"};
    // public static final String TOP_RANKS_TEXT = "The fastest completions on";

    public static final int TRIANGLE_SIZE = 20;
    public static final float BAR_PROPORTION = 0.7f; // how much space the bar should fill up as a percentage
    public static final int BAR_HEIGHT = (int) ((rankToY(1)-rankToY(0)) * BAR_PROPORTION);
    public static final int BAR_HEIGHT_HALF = BAR_HEIGHT/2;
    public static final int BAR_MAX_X = X_MAX-TRIANGLE_SIZE;

    public static final int NAME_FONT_SIZE = 54;
    public static final int DATE_FONT_SIZE = 96;
    public static final int COMMENT_FONT_SIZE = 24;

    public static final int PLATFORM_MARGIN = 8;
    public static final int TITLE_TOP_MARGIN = 96;
    public static final int TITLE_SIDE_MARGIN = 20;
    public static final int NAME_TEXT_OFFSET = 14;
    public static final int IMAGE_PADDING = 4;
    public static final int FLAG_DIMENSIONS = BAR_HEIGHT-IMAGE_PADDING;

    public static final float GRAY_COLOR = 204f;
    public static final float DARK_GRAY_COLOR = 85f;
    public static final SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy");
    public static final Random rand = new Random();

    public static final int[] SCALE_UNITS = {1, 5, 10, 15, 30, 60, 120, 180, 300, 600, 900, 1800, 3600, 7200, 10800, 18000, 36000, 86400, 172800}; // possible increments for tick marks (in seconds)
    public static final int UNITS_GOAL = 3; // how many units we'd like to fit on screen
    public static final int TICK_FADE_SPEED = 3; // how fast the tick marks fade (not exactly in seconds)


    public static final boolean USE_MILLISECONDS = false;

    static {
        rand.setSeed(1152003);
    }

    public static String dateToString(Date date) {
        return formatter.format(date);
    }

    public void settings() { // diet setup
        size(S_WIDTH, S_HEIGHT);
    }

    public void setup() {
        frameRate(60);
        missingFlag = loadImage("missing_flag.png");
        font = loadFont("UbuntuCondensed-Regular-96.vlw");//"Jygquif1-96.vlw");
        String[] textFile = loadStrings("runs.csv");

        for (File file : listFiles(sketchPath("data")+"\\"+FLAG_FOLDER)) {
            String filename = file.getName();
            String country = filename.substring(0, filename.lastIndexOf('.'));
            flags.put(country, loadImage(file.getAbsolutePath()));
        }

        metadata = loadJSONObject("metadata.json");
//        videoExport = new VideoExport(this, metadata.getString("game")+"-"+metadata.getString("category")+".mp4");
        if (metadata.getBoolean("cover")) coverImage = loadImage(IMAGE_FOLDER+"_cover.png");
        List<String> pfps = Arrays.asList(metadata.getJSONArray("pfps").getStringArray());
        JSONObject players = metadata.getJSONObject("players");

        DATA_LENGTH = textFile.length - 1;
        maxes = new double[DATA_LENGTH];
        unitChoices = new double[DATA_LENGTH];
        dates = new Date[DATA_LENGTH];
        recordHolderDays = new int[DATA_LENGTH];
        runnerArray = new Speedrunner[players.keys().size()];

        // create speedrunner objects
        int c = 0;
        for (Object playerObject : players.keys()) {
            String player = (String) playerObject;
            PImage img = null;
            if (pfps.contains(player)) img = loadImage(IMAGE_FOLDER+player+".png");
            Speedrunner speedrunner = new Speedrunner(player, players.getJSONArray(player), DATA_LENGTH, img, flags);
            if (speedrunner.getFlag() == null) {
                speedrunner.setFlag(missingFlag);
            }
            speedrunners.put(player, speedrunner);
            runnerArray[c] = speedrunner;
            c++;
        }

        initUserData(textFile);
        initUnits();

        // find size of date text to get the offset for the "the fastest speedruns on..." text
        textFont(font, DATE_FONT_SIZE);
        for (Date date : dates) {
            dateTextWidth = max(textWidth(dateToString(date)), dateTextWidth);
        }

        // pre-process platform shorthands
        JSONObject runs = metadata.getJSONObject("runs");
        for (Object runObject : runs.keys()) {
            String runID = (String) runObject;
            JSONObject runData = runs.getJSONObject(runID);
            if (!runData.isNull("platform")) {
                runData.setString("platform", getShortPlatform(runData.getString("platform")));
            }
        }

//        videoExport.startMovie();
    }

    public void initUserData(String[] textFile) {
        JSONObject runData = metadata.getJSONObject("runs");
        String[] header = textFile[0].split(",");
        for (int i = 1; i < textFile.length; i++) {
            String row = textFile[i];
            String[] cols = row.split(",");
            String date = cols[0];
            String[] dateSplitStr = date.split("-");
            int[] dateSplit = new int[3];
            for (int d = 0; d < dateSplitStr.length; d++) {
                dateSplit[d] = Integer.parseInt(dateSplitStr[d]);
            }

            LocalDate localDate = LocalDate.of(dateSplit[0], dateSplit[1], dateSplit[2]);
            dates[i-1] = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            List<Speedrunner> runners = new ArrayList<Speedrunner>();
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

                // save runs
                int runIndex = -1;
                for (int r = 0; r < speedrunner.runs.size(); r++) {
                    if (speedrunner.runs.get(r).equals(runID)) {
                        runIndex = r;
                        break;
                    }
                }
                if (runIndex == -1) {
                    runIndex = speedrunner.runs.size();
                    speedrunner.runs.add(runID);
                }
                speedrunner.runIndex[i-1] = runIndex;

                speedrunner.setSortValue(i-1);
                runners.add(speedrunner);
            }

            // sort :)
            Collections.sort(runners);
            // update ranks
            int maxValueAt = Math.min(runners.size(), DISPLAY_RANKS)-1;
            double previousMax = -1;
            if (i > 1) previousMax = maxes[i-2];
            for (int c = 0; c < runners.size() && c < DISPLAY_RANKS; c++) {
                Speedrunner runner = runners.get(c);
                runner.ranks[i-1] = c;
                double val = runner.values[i-1];
                if (c <= maxValueAt) {
                    if (previousMax != -1) val = Math.min(val, previousMax);
                    maxes[i-1] = val;
                }
                if (c == 0) {
                    if (recordHolder == null || recordHolder != runner) {
                        recordHolder = runner;
                    } else {
                        recordHolderDays[i-1] = recordHolderDays[i-2]+1;
                    }
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

    public float linIndex(int[] values, float index) {
        float[] floatValues = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            floatValues[i] = values[i];
        }
        return linIndex(floatValues, index);
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
        return lerp(X_MIN, X_MAX*0.95f, value/scale);
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
        background(0);
        try {
            drawHorizTickMarks(currentDayIndex, currentScale);
            drawBackground(currentDayIndex);
            drawBars(currentDayIndex, currentScale);
//            videoExport.saveFrame();
        } catch (ArrayIndexOutOfBoundsException e) {
//            videoExport.endMovie();
            exit();
        }

        frames++;
    }

    public void drawBackground(float currentDay) {
        fill(255f);
        textFont(font, DATE_FONT_SIZE);

        // date
        textAlign(RIGHT, BASELINE);
        int dateX = S_WIDTH - TITLE_SIDE_MARGIN;
        text(dateToString(dates[floor(currentDay)]), dateX, TITLE_TOP_MARGIN);

        // "top X speedruns on..."
        // fill(GRAY_COLOR);
        // textSize(DATE_FONT_SIZE * (1f/3f));
        // text(TOP_RANKS_TEXT, dateX-dateTextWidth-20, TITLE_TOP_MARGIN);
        // fill(255f);

        // cover img
        int textX = TITLE_SIDE_MARGIN;
        if (coverImage != null) {
            float ratio = (float) DATE_FONT_SIZE/coverImage.pixelHeight;
            int imgW = (int) (coverImage.pixelWidth * ratio);
            image(coverImage, textX, TITLE_TOP_MARGIN-DATE_FONT_SIZE+20, imgW, DATE_FONT_SIZE);
            textX += imgW+8;
        }

        // game + category
        String game = metadata.getString("game");
        String category = metadata.getString("category");

        textAlign(LEFT, BASELINE);
        textSize(DATE_FONT_SIZE);
        text(game, textX, TITLE_TOP_MARGIN);

        int categoryX = (int) (textWidth(game)+16+textX);
        textSize(DATE_FONT_SIZE * (2f/3f));
        fill(GRAY_COLOR); // 0.8f * 255
        text(category, categoryX, TITLE_TOP_MARGIN);

        // 1st 2nd etc
        int pX = X_MIN-6;
        fill(DARK_GRAY_COLOR);
        textSize(NAME_FONT_SIZE*(2f/3f));
        textAlign(RIGHT, CENTER);
        for (int p = 0; p < DISPLAY_RANKS; p++) {
            String pText = PLACEMENTS[p];
            int pY = jitterFix(rankToY(p)) + BAR_HEIGHT_HALF;
            text(pText, pX, pY);
        }

        // WR for ...
        fill(0xFFFFE200);
        textAlign(LEFT, BOTTOM);
        int fontSize = (int) (NAME_FONT_SIZE * (3f/4f));
        textSize(fontSize);
        text("WR holder for", TITLE_SIDE_MARGIN, Y_MIN-fontSize);
        text(displayDays(recordHolderDays[round(currentDay)]), TITLE_SIDE_MARGIN, Y_MIN);
    }

    public void drawHorizTickMarks(float currentDay, float currentScale) {
        float preferredUnit = avgIndex(unitChoices, currentDay, TICK_FADE_SPEED);
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
        fill(100, 100, 100, opacity);
        textFont(font, 50);
        for (int v = 0; v < currentScale * 1.4; v+=thisUnit) {
            boolean firstMark = v == 0;
            if (firstMark) continue;

            float x = valueToX(v, currentScale);

            float W = 4; // width of the bar
            float Wh = W/2f; // half of the width of the bar
            float yOffset = 20; // how far above the top of the screen to render
            rect(x-Wh, Y_MIN-yOffset, W, HEIGHT+yOffset);

            int align = CENTER;
            //int align = firstMark ? LEFT : CENTER;
            //if (firstMark) x -= 10;

            textAlign(align);
            String display = displayTime(v+MIN_VALUE, false, false, true);
            text(display, x, Y_MIN-yOffset-10);
        }
    }

    public static String displayTime(float seconds, boolean useMilliseconds, boolean useSeconds, boolean useHours) {
        int h = (int) ((seconds/60)/60);
        String out = String.format("%dh %02dm %06.3fs", h, (int) ((seconds/60) % 60), seconds%60);
        if (!useMilliseconds && useSeconds) out = out.substring(0, out.length()-5)+"s";
        if (!useSeconds) out = out.substring(0, out.length()-8);
        if (!useHours || h == 0) out = out.substring(out.indexOf(" "));
        return out;
    }

    public static String displayDays(int days) {
        int trueDays = days % 31;
        int months = floor(days/31f) % 12;
        int years = floor((days/31f)/12f);
        StringBuilder output = new StringBuilder();
        if (years > 0) output.append(years).append("y ");
        if (months > 0 || years > 0) output.append(months).append("m ");
        output.append(trueDays).append("d");
        return output.toString();
    }

    public static int jitterFix(float f) {
        if (abs(f - floor(f)) > 0.99) return ceil(f);
        return floor(f);
    }

    public void drawBars(float currentDay, float currentScale) {
        noStroke();
        textFont(font, NAME_FONT_SIZE);
        JSONObject runs = metadata.getJSONObject("runs");
        for (Speedrunner sr : runnerArray) {
            // get base values
            float val = linIndex(sr.values, currentDay);
            float fx = valueToX(val, currentScale);
            float rank = avgIndex(sr.ranks, currentDay, RANK_SMOOTHING);
            float fy = rankToY(rank);
            int x = jitterFix(fx);
            int y = jitterFix(fy);
            int dIndex = round(currentDay); // get index for fixed data (comments, display time)
            // skip if off screen
            if (y > S_HEIGHT) {
                continue;
            }
            int runIndex = sr.runIndex[dIndex];
            if (runIndex == -1) continue;
            JSONObject run = runs.getJSONObject(sr.runs.get(runIndex));

            String timeText = sr.displayValues[dIndex];
            if (timeText.isEmpty()) {
                continue;
            }

            int platX = x+PLATFORM_MARGIN; // platform value, added here so the tri code can increment it

            // bar fill color
            Color srClr = sr.getClr();
            fill(srClr.getRed(), srClr.getGreen(), srClr.getBlue());

            // draw triangle for runs that would go off the screen
            if (x > X_MAX) {
                x = BAR_MAX_X;
                platX = BAR_MAX_X+TRIANGLE_SIZE+PLATFORM_MARGIN;
                triangle(BAR_MAX_X, y, BAR_MAX_X, y+BAR_HEIGHT, BAR_MAX_X+TRIANGLE_SIZE, y+BAR_HEIGHT_HALF);
            }

            // draw bar
            rect(X_MIN, y, x - X_MIN, BAR_HEIGHT);

            // set text position variables
            int textX = X_MIN + 6;
            int origTextX = textX;
            int textY = y+BAR_HEIGHT-NAME_TEXT_OFFSET;

            // render profile picture
            PImage pImage = sr.getpImage();
            if (pImage != null){
                // scale aspect ratios correctly (this might not be perfectly efficient)
                int maxDim = BAR_HEIGHT - IMAGE_PADDING * 2;

                int imgH;
                float ratio;
                int imgW;
                int wOffset = 0;
                int hOffset = 0;

                if (pImage.pixelHeight >= pImage.pixelWidth) {
                    imgH = maxDim;
                    ratio = (float) imgH / pImage.pixelHeight;
                    imgW = (int) (pImage.pixelWidth * ratio);
                    wOffset = (maxDim - imgW) / 2;
                } else {
                    imgW = maxDim;
                    ratio = (float) imgW / pImage.pixelWidth;
                    imgH = (int) (pImage.pixelHeight * ratio);
                    hOffset = (maxDim - imgH) / 2;
                }

                // finally render img
                image(pImage, textX+wOffset, y+IMAGE_PADDING+hOffset, imgW, imgH);
                textX += maxDim + 6; // offset username text
            }

            textAlign(LEFT, TOP);
            if (!run.isNull("comment")) {
                String[] mComment = run.getString("comment").split("\r?\n");
                String comment = mComment[0];
                if (mComment.length > 1) comment += " [...]";
                textSize(COMMENT_FONT_SIZE);
                text(comment, origTextX, y + BAR_HEIGHT + 2);
            }

            textSize(NAME_FONT_SIZE);

            textAlign(LEFT);
            fill(255);
            String displayName = sr.getDisplayName();
            text(displayName, textX, textY);
            int nameWidth = (int) textWidth(displayName);

            int flagX = textX+nameWidth+4;
            image(sr.getFlag(), flagX, y+IMAGE_PADDING-2, FLAG_DIMENSIONS, FLAG_DIMENSIONS);

            textAlign(RIGHT);

            int timeX = x-4;
            int timeY = textY+3;
            if (!USE_MILLISECONDS) {
                text(timeText, timeX, timeY);
            } else {
                int offset = timeText.length() - 4;
                String others = timeText.substring(0, offset);
                String millis = timeText.substring(offset);
                textSize(NAME_FONT_SIZE * (1f / 2f));
                text(millis, timeX, timeY);
                int mOffset = (int) textWidth(millis);

                textSize(NAME_FONT_SIZE);
                text(others, timeX - mOffset, timeY);
            }

            textAlign(LEFT, CENTER);
            textSize(NAME_FONT_SIZE * (2f/3f));
            fill(DARK_GRAY_COLOR);

            text(getPlatformDisplay(run), platX, y+BAR_HEIGHT_HALF);
        }
    }

    public static String getShortPlatform(String platform) {
        switch (platform) {
            case "Nintendo 64":
                platform = "N64";
                break;
            case "Wii Virtual Console":
                platform = "Wii VC";
                break;
            case "Wii U Virtual Console":
                platform = "Wii U VC";
                break;
            case "3DO Interactive Multiplayer":
                platform = "3DO";
                break;
            case "Amazon Fire TV":
                platform = "FireTV";
                break;
            case "New Nintendo 3DS":
                platform = "New 3DS";
                break;
            case "New Nintendo 3DS Virtual Console":
                platform = "New 3DS VC";
                break;
            case "Nintendo 3DS":
                platform = "3DS";
                break;
            case "Nintendo 3DS Virtual Console":
                platform = "3DS VC";
                break;
            case "Nintendo DS":
                platform = "DS";
                break;
            case "Nintendo Entertainment System":
                platform = "NES";
                break;
            case "Super Nintendo":
                platform = "SNES";
                break;
            case "Switch Virtual Console":
                platform = "Switch VC";
                break;
            case "GameCube":
                platform = "GC";
                break;
            case "PlayStation":
                platform = "PSX";
                break;
            case "PlayStation 2":
                platform = "PS2";
                break;
            case "PlayStation 3":
                platform = "PS3";
                break;
            case "PlayStation 4":
                platform = "PS4";
                break;
            case "PlayStation 4 Pro":
                platform = "PS4 Pro";
                break;
            case "PlayStation 5":
                platform = "PS5";
                break;
            case "Playstation Now":
                platform = "PSNow";
                break;
            case "Playstation TV":
                platform = "PSTV";
                break;
            case "PlayStation Vita":
                platform = "PSVita";
                break;
            case "PlayStation Portable":
                platform = "PSP";
                break;
            case "Xbox 360":
                platform = "X360";
                break;
            case "Xbox 360 Arcade":
                platform = "X360 Arcade";
                break;
            case "Xbox One":
                platform = "XBO";
                break;
            case "Xbox One S":
                platform = "XBOS";
                break;
            case "Xbox One X":
                platform = "XBOX";
                break;
            case "Xbox Series S":
                platform = "XBSS";
                break;
            case "Xbox Series X":
                platform = "XBSX";
                break;
        }
        return platform;
    }

    public static String getPlatformDisplay(JSONObject run) {
        StringBuilder stringBuilder = new StringBuilder();
        if (!run.isNull("region")) stringBuilder.append(run.getString("region").split(" / ")[0]);
        if (!run.isNull("platform")) {
            if (!stringBuilder.toString().isEmpty()) stringBuilder.append(' ');
            stringBuilder.append(run.getString("platform"));
        }
        if (run.getBoolean("emulated")) stringBuilder.append(" emu");
        return stringBuilder.toString();
    }

    public static void main(String[] args) {
        PApplet.main("io.github.lexikiq.vistest.VisApplet");
    }
}
