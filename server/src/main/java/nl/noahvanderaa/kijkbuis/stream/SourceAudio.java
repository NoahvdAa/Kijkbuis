package nl.noahvanderaa.kijkbuis.stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One of a channel's audio tracks, as ffmpeg found it.
 *
 * @param index            which of the channel's audio tracks it is, counting from 0, as ffmpeg's 0:a:N picks it
 * @param codec            the audio codec as ffmpeg names it, which is none if ffmpeg can't tell
 * @param language         the language the channel labels the track with, such as dut or eng, or null if it doesn't
 * @param audioDescription whether the track describes what's on screen, for people who can't see it
 */
public record SourceAudio(int index, @NotNull String codec, @Nullable String language, boolean audioDescription) {

    /**
     * Matches the lines in which ffmpeg describes the input's audio tracks, with their language if the channel labels
     * them, such as
     * {@code Stream #0:2[0x102](eng): Audio: mp2 ([3][0][0][0] / 0x0003), 48000 Hz, stereo, fltp, 192 kb/s (visual impaired) (descriptions)}
     */
    private static final Pattern INPUT_AUDIO = Pattern.compile("^\\s*Stream #0:\\d+(?:\\[\\w+])?(?:\\(([A-Za-z]{2,8})\\))?: Audio: (\\w+)");

    /**
     * @param line  a line of ffmpeg's log
     * @param index how many of the input's audio tracks came before this line
     * @return the audio track the line describes, or null if the line doesn't describe one of the input's audio tracks
     */
    @Nullable
    static SourceAudio parse(@NotNull String line, int index) {
        Matcher matcher = INPUT_AUDIO.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        String language = matcher.group(1) != null ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
        // und is the code for not saying
        return new SourceAudio(index, matcher.group(2), "und".equals(language) ? null : language, line.contains("(visual impaired)"));
    }

    /**
     * @return whether ffmpeg can decode the track, which it needs to convert it for browsers
     */
    boolean playable() {
        return !"none".equals(this.codec);
    }

}
