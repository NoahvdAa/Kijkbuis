package nl.noahvanderaa.kijkbuis.stream;

import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How a stream's video is encoded. Audio is always converted to AAC, see {@link FfmpegCommand}.
 *
 * @param transcode  whether to transcode the video to H.264, rather than passing it through as is
 * @param profile    the quality to transcode to, or null to keep the channel's quality
 * @param interlaced whether the transcoded video is interlaced, so it's deinterlaced at the configured rate
 * @param hvc1Tag    whether to tag passed-through HEVC as hvc1 rather than hev1, which Safari needs
 */
public record EncodingPlan(boolean transcode, @Nullable KijkbuisConfig.TranscodeProfile profile, boolean interlaced, boolean hvc1Tag) {

    /**
     * The video codecs that can be passed through in fMP4 HLS, if the browser can decode them.
     */
    private static final Set<String> PASSTHROUGH_CODECS = Set.of("h264", "hevc", "av1");
    private static final Map<String, String> CODEC_ALIASES = Map.of(
            "avc", "h264",
            "avc1", "h264",
            "h.264", "h264",
            "h265", "hevc",
            "h.265", "hevc",
            "hvc1", "hevc",
            "hev1", "hevc",
            "av01", "av1"
    );

    /**
     * Decides how to encode a stream.
     *
     * @param profile        the quality the user picked, or null to pick automatically
     * @param source         the channel's video, or null if unknown or the channel has no video
     * @param playableCodecs the video codecs the user's browser can play, see {@link #normalizeCodecs}
     * @param mayTranscode   whether the user may transcode
     * @return the plan, or null if the browser can't play the channel and the user may not transcode
     */
    @Nullable
    static EncodingPlan decide(@Nullable KijkbuisConfig.TranscodeProfile profile, @Nullable SourceVideo source,
                               @NotNull Set<String> playableCodecs, boolean mayTranscode) {
        boolean interlaced = source != null && source.interlaced();
        if (profile != null) {
            return new EncodingPlan(true, profile, interlaced, false);
        }
        // Browsers can decode interlaced video, but show it without deinterlacing, which looks combed. Users who may
        // transcode get it deinterlaced, others still get to watch it.
        if (interlaced && mayTranscode) {
            return new EncodingPlan(true, null, true, false);
        }
        String codec = source != null ? source.codec() : null;
        if (codec == null || (PASSTHROUGH_CODECS.contains(codec) && playableCodecs.contains(codec))) {
            return new EncodingPlan(false, null, false, "hevc".equals(codec));
        }
        // The browser can't play the codec, so transcode at the channel's own quality
        return mayTranscode ? new EncodingPlan(true, null, false, false) : null;
    }

    /**
     * Converts codec names as browsers report them (e.g. avc1, hvc1) to the names ffmpeg uses.
     */
    @NotNull
    public static Set<String> normalizeCodecs(@NotNull Collection<String> codecs) {
        Set<String> normalized = new HashSet<>();
        for (String codec : codecs) {
            String name = codec.strip().toLowerCase(Locale.ROOT);
            normalized.add(CODEC_ALIASES.getOrDefault(name, name));
        }
        return normalized;
    }

    /**
     * @return a short description for logs
     */
    @NotNull
    String describe() {
        if (!this.transcode) {
            return "passing video through";
        }
        String description = this.profile != null ? "transcoding to " + this.profile.id() : "transcoding at source quality";
        return this.interlaced ? description + ", deinterlaced" : description;
    }

}
