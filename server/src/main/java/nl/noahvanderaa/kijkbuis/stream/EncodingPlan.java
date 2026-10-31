package nl.noahvanderaa.kijkbuis.stream;

import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How a stream's video is encoded, and which audio tracks it gets. Audio is always converted to AAC, see
 * {@link FfmpegCommand}.
 *
 * @param transcode   whether to transcode the video to H.264, rather than passing it through as is
 * @param profile     the quality to transcode to, or null to keep the channel's quality
 * @param interlaced  whether the transcoded video is interlaced, so it's deinterlaced at the configured rate
 * @param hvc1Tag     whether to tag passed-through HEVC as hvc1 rather than hev1, which Safari needs
 * @param audioTracks the audio tracks that each get a playlist of their own, so the viewer can switch between them.
 *                    Empty if the channel's first audio track, if any, goes in with the video, which is what happens
 *                    with one audio track, and for channels without video.
 */
public record EncodingPlan(boolean transcode, @Nullable KijkbuisConfig.TranscodeProfile profile, boolean interlaced,
                           boolean hvc1Tag, @NotNull List<SourceAudio> audioTracks) {

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
     * Every audio track is converted to AAC, so channels with more tracks than this only offer the first ones.
     */
    private static final int MAX_AUDIO_TRACKS = 8;

    public EncodingPlan {
        audioTracks = List.copyOf(audioTracks);
    }

    /**
     * A plan that keeps the channel's first audio track with the video.
     */
    public EncodingPlan(boolean transcode, @Nullable KijkbuisConfig.TranscodeProfile profile, boolean interlaced,
                        boolean hvc1Tag) {
        this(transcode, profile, interlaced, hvc1Tag, List.of());
    }

    /**
     * Decides how to encode a stream.
     *
     * @param profile        the quality the user picked, or null to pick automatically
     * @param source         what the channel has, or null if that's not known yet
     * @param playableCodecs the video codecs the user's browser can play, see {@link #normalizeCodecs}
     * @param mayTranscode   whether the user may transcode
     * @return the plan, or null if the browser can't play the channel and the user may not transcode
     */
    @Nullable
    static EncodingPlan decide(@Nullable KijkbuisConfig.TranscodeProfile profile, @Nullable Source source,
                               @NotNull Set<String> playableCodecs, boolean mayTranscode) {
        SourceVideo video = source != null ? source.video() : null;
        List<SourceAudio> audioTracks = switchableAudio(source);
        boolean interlaced = video != null && video.interlaced();
        if (profile != null) {
            return new EncodingPlan(true, profile, interlaced, false, audioTracks);
        }
        // Browsers can decode interlaced video, but show it without deinterlacing, which looks combed. Users who may
        // transcode get it deinterlaced, others still get to watch it.
        if (interlaced && mayTranscode) {
            return new EncodingPlan(true, null, true, false, audioTracks);
        }
        String codec = video != null ? video.codec() : null;
        if (codec == null || (PASSTHROUGH_CODECS.contains(codec) && playableCodecs.contains(codec))) {
            return new EncodingPlan(false, null, false, "hevc".equals(codec), audioTracks);
        }
        // The browser can't play the codec, so transcode at the channel's own quality
        return mayTranscode ? new EncodingPlan(true, null, false, false, audioTracks) : null;
    }

    /**
     * @return the audio tracks the viewer can switch between: the ones ffmpeg can decode, if there are several.
     * Channels without video keep their first track only. Without a video playlist, players would take the audio
     * playlists for qualities, and switch between them by themselves.
     */
    @NotNull
    private static List<SourceAudio> switchableAudio(@Nullable Source source) {
        if (source == null || source.video() == null) {
            return List.of();
        }
        List<SourceAudio> playable = source.audio().stream().filter(SourceAudio::playable).limit(MAX_AUDIO_TRACKS).toList();
        return playable.size() > 1 ? playable : List.of();
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
        String description;
        if (!this.transcode) {
            description = "passing video through";
        } else {
            description = this.profile != null ? "transcoding to " + this.profile.id() : "transcoding at source quality";
            if (this.interlaced) {
                description += ", deinterlaced";
            }
        }
        return this.audioTracks.isEmpty() ? description : description + ", with " + this.audioTracks.size() + " audio tracks";
    }

}
