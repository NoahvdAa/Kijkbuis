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
 * @param audioOnly   whether the stream has no video: the user picked audio only, or the channel has none. Nothing
 *                    else about the video applies then.
 * @param transcode   whether to transcode the video to H.264, rather than passing it through as is
 * @param profile     the quality to transcode to, or null to keep the channel's quality
 * @param interlaced  whether the transcoded video is interlaced, so it's deinterlaced at the configured rate
 * @param hvc1Tag     whether to tag passed-through HEVC as hvc1 rather than hev1, which Safari needs
 * @param audioTracks the audio tracks that each get a playlist of their own, so the viewer can switch between them.
 *                    Empty if the stream only gets the channel's first audio track, if any, in with the video. That's
 *                    what happens with one audio track.
 */
public record EncodingPlan(boolean audioOnly, boolean transcode, @Nullable KijkbuisConfig.TranscodeProfile profile,
                           boolean interlaced, boolean hvc1Tag, @NotNull List<SourceAudio> audioTracks) {

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
        this(false, transcode, profile, interlaced, hvc1Tag, List.of());
    }

    /**
     * @param audioTracks the audio tracks that each get a playlist of their own, see {@link #audioTracks()}
     * @return a plan without video
     */
    @NotNull
    public static EncodingPlan audioOnly(@NotNull List<SourceAudio> audioTracks) {
        return new EncodingPlan(true, false, null, false, false, audioTracks);
    }

    /**
     * Decides how to encode a stream.
     *
     * @param profile         the quality the user picked, or null to pick automatically
     * @param audioOnly       whether the user picked audio only, which leaves the video out
     * @param source          what the channel has, or null if that's not known yet
     * @param playableCodecs  the video codecs the user's browser can play, see {@link #normalizeCodecs}
     * @param mayTranscode    whether the user may transcode
     * @param maxSourceHeight the largest channel that's transcoded, see
     *                        {@link KijkbuisConfig.TranscodingConfig#maxSourceHeight()}
     * @return the plan, or null if the browser can't play the channel, and the user may not transcode or the channel
     * is too large to
     */
    @Nullable
    static EncodingPlan decide(@Nullable KijkbuisConfig.TranscodeProfile profile, boolean audioOnly, @Nullable Source source,
                               @NotNull Set<String> playableCodecs, boolean mayTranscode, int maxSourceHeight) {
        SourceVideo video = source != null ? source.video() : null;
        List<SourceAudio> audioTracks = switchableAudio(source);
        // Channels without video are audio only whatever the user picked, and channels without sound never are: those
        // play with their video. Until it's known what the channel has, it goes by what the user picked.
        if (source == null ? audioOnly : source.hasAudio() && (audioOnly || video == null)) {
            return audioOnly(audioTracks);
        }
        boolean interlaced = video != null && video.interlaced();
        // Channels over the size limit are passed through or not played at all, whatever the user picked
        boolean transcodable = mayTranscode && (video == null || video.fitsIn(maxSourceHeight));
        if (profile != null && transcodable) {
            return new EncodingPlan(false, true, profile, interlaced, false, audioTracks);
        }
        // Browsers can decode interlaced video, but show it without deinterlacing, which looks combed. Users who may
        // transcode get it deinterlaced, others still get to watch it.
        if (interlaced && transcodable) {
            return new EncodingPlan(false, true, null, true, false, audioTracks);
        }
        String codec = video != null ? video.codec() : null;
        if (codec == null || (PASSTHROUGH_CODECS.contains(codec) && playableCodecs.contains(codec))) {
            return new EncodingPlan(false, false, null, false, "hevc".equals(codec), audioTracks);
        }
        // The browser can't play the codec, so transcode at the channel's own quality
        return transcodable ? new EncodingPlan(false, true, null, false, false, audioTracks) : null;
    }

    /**
     * @return the audio tracks the viewer can switch between: the ones ffmpeg can decode, if there are several
     */
    @NotNull
    private static List<SourceAudio> switchableAudio(@Nullable Source source) {
        if (source == null) {
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
     * @return how many playlists the stream has besides the one that ties them together, when every audio track has
     * one of its own: one for the video, if any, and one for each audio track. They're numbered from 0 in that order,
     * see {@link FfmpegCommand#renditionPlaylist}.
     */
    int renditions() {
        return (this.audioOnly ? 0 : 1) + this.audioTracks.size();
    }

    /**
     * @return a short description for logs
     */
    @NotNull
    String describe() {
        String description;
        if (this.audioOnly) {
            description = "audio only";
        } else if (!this.transcode) {
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
