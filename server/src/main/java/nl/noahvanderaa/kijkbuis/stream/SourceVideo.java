package nl.noahvanderaa.kijkbuis.stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A channel's video, as ffmpeg found it.
 *
 * @param codec      the video codec as ffmpeg names it
 * @param interlaced whether the video is interlaced, as TV broadcasts often are
 * @param width      the width the picture is shown at, or 0 if unknown. Channels with non-square pixels (such as SD
 *                   channels sending 720x576 shown as 16:9) are shown wider than the width they send.
 * @param height     the height, or 0 if unknown
 */
record SourceVideo(@NotNull String codec, boolean interlaced, int width, int height) {

    /**
     * Matches the lines in which ffmpeg describes the input's video, such as
     * {@code Stream #0:0[0x100]: Video: h264 (High) ([27][0][0][0] / 0x001B), yuv420p(tv, bt709, top first), 1920x1080 [SAR 1:1 DAR 16:9], 25 fps}
     */
    private static final Pattern INPUT_VIDEO = Pattern.compile("^\\s*Stream #0:\\d+\\S*: Video: (\\w+)");
    /**
     * Matches the field order of interlaced video in those lines. Progressive video says "progressive" instead, and
     * there's nothing if ffmpeg doesn't know.
     */
    private static final Pattern INTERLACED = Pattern.compile("\\b(?:top|bottom) (?:coded )?first\\b");
    /**
     * Matches the size in those lines, with the shape of the pixels if they aren't square, such as
     * {@code , 720x576 [SAR 64:45 DAR 16:9]}. It always follows a comma, unlike the codec tag (e.g. 0x001B).
     */
    private static final Pattern SIZE = Pattern.compile(", (\\d{1,5})x(\\d{1,5})(?: \\[SAR (\\d{1,9}):(\\d{1,9}))?");

    /**
     * @param line a line of ffmpeg's log
     * @return the video the line describes, or null if the line doesn't describe the input's video
     */
    @Nullable
    static SourceVideo parse(@NotNull String line) {
        Matcher matcher = INPUT_VIDEO.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        String codec = matcher.group(1);
        boolean interlaced = INTERLACED.matcher(line).find();

        Matcher size = SIZE.matcher(line);
        if (!size.find()) {
            return new SourceVideo(codec, interlaced, 0, 0);
        }
        int width = Integer.parseInt(size.group(1));
        int height = Integer.parseInt(size.group(2));
        if (size.group(3) != null) {
            // An unknown aspect ratio is reported as 0:1
            long sarNum = Long.parseLong(size.group(3));
            long sarDen = Long.parseLong(size.group(4));
            if (sarNum > 0 && sarDen > 0) {
                width = (int) Math.round((double) width * sarNum / sarDen);
            }
        }
        return new SourceVideo(codec, interlaced, width, height);
    }

    /**
     * @return a short description for logs
     */
    @NotNull
    String describe() {
        return this.interlaced ? "interlaced " + this.codec : this.codec;
    }

}
