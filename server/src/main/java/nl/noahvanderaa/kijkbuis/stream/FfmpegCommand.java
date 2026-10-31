package nl.noahvanderaa.kijkbuis.stream;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the ffmpeg command for a stream.
 */
final class FfmpegCommand {

    private static final int SEGMENT_SECONDS = 2;
    /**
     * Transcoding at source quality uses constant quality, capped at this bitrate.
     */
    private static final int SOURCE_QUALITY_CRF = 21;
    private static final int SOURCE_QUALITY_MAX_BITRATE_KBPS = 20_000;
    /**
     * The group the audio tracks are in, when each has a playlist of its own. Players pick one track of a group.
     */
    private static final String AUDIO_GROUP = "audio";
    /**
     * The name of a rendition's playlist, where ffmpeg puts its number in place of %v, see {@link #renditionPlaylist}
     */
    private static final String RENDITION_PLAYLIST = "stream_%v.m3u8";

    private FfmpegCommand() {
    }

    /**
     * When every audio track has a playlist of its own, the video and each audio track are a rendition, numbered from
     * 0 in the order they're mapped: the video, then the audio tracks.
     *
     * @return the name of a rendition's playlist
     */
    @NotNull
    static String renditionPlaylist(int rendition) {
        return RENDITION_PLAYLIST.replace("%v", String.valueOf(rendition));
    }

    /**
     * @param ffmpegPath            the ffmpeg binary
     * @param preset                the libx264 preset to transcode with
     * @param deinterlaceDoubleRate whether deinterlacing turns every field into a frame, rather than every frame
     * @param streamUrl             the channel's stream URL
     * @param ingestUri             where ffmpeg should push the stream's files to, ending with a slash
     * @param plan                  how to encode the video, and which audio tracks to keep
     */
    @NotNull
    static List<String> build(@NotNull String ffmpegPath, @NotNull String preset, boolean deinterlaceDoubleRate,
                              @NotNull String streamUrl, @NotNull URI ingestUri, @NotNull EncodingPlan plan) {
        // Info level logs the input's codecs, which is how the stream finds out what the channel uses
        List<String> command = new ArrayList<>(List.of(ffmpegPath, "-hide_banner", "-nostdin", "-nostats", "-loglevel", "info"));
        if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
            // Recover from dropped connections to the upstream
            command.addAll(List.of("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5"));
        }

        command.addAll(List.of("-i", streamUrl));
        boolean separateAudio = !plan.audioTracks().isEmpty();
        if (separateAudio) {
            // The tracks ffmpeg found when it last played the channel. If one isn't there anymore, ffmpeg stops right
            // after listing the channel's tracks, and the stream starts again with the ones it has now.
            command.addAll(List.of("-map", "0:v:0"));
            for (SourceAudio track : plan.audioTracks()) {
                command.addAll(List.of("-map", "0:a:" + track.index()));
            }
        } else {
            // Only the first video and audio track, either may be missing (e.g. for radio channels)
            command.addAll(List.of("-map", "0:v:0?", "-map", "0:a:0?"));
        }

        if (plan.transcode()) {
            command.addAll(List.of("-c:v", "libx264", "-preset", preset, "-profile:v", "high", "-pix_fmt", "yuv420p"));
            // TV channels are often interlaced, which browsers show badly. Only frames marked as interlaced are touched.
            // Turning every field into a frame (50i into 50p) keeps motion as smooth as on TV, but it doubles the frame
            // rate of progressive video too, so it's only done for channels that ffmpeg found to be interlaced.
            String mode = plan.interlaced() && deinterlaceDoubleRate ? "send_field" : "send_frame";
            String filters = "bwdif=mode=" + mode + ":deint=interlaced";
            if (plan.profile() != null) {
                // Fit the picture in the profile's 16:9 box by the shape it's shown at, never scaling up. The output
                // uses square pixels, so channels with non-square pixels (e.g. 720x576 shown as 16:9) come out right,
                // and wider or narrower channels (4:3, cinemascope, portrait) are scaled down as far as they need.
                String factor = "min(1,min(" + plan.profile().width() + "/(iw*sar)," + plan.profile().height() + "/ih))";
                filters += ",scale=w='round(iw*sar*" + factor + "/2)*2':h='round(ih*" + factor + "/2)*2',setsar=1";
                int bitrate = plan.profile().bitrateKbps();
                command.addAll(List.of("-vf", filters, "-b:v", bitrate + "k", "-maxrate", bitrate + "k", "-bufsize", (bitrate * 2) + "k"));
            } else {
                command.addAll(List.of("-vf", filters, "-crf", String.valueOf(SOURCE_QUALITY_CRF),
                        "-maxrate", SOURCE_QUALITY_MAX_BITRATE_KBPS + "k", "-bufsize", (SOURCE_QUALITY_MAX_BITRATE_KBPS * 2) + "k"));
            }
            // Keyframes exactly at segment boundaries, so every segment has the same length
            command.addAll(List.of("-force_key_frames", "expr:gte(t,n_forced*" + SEGMENT_SECONDS + ")"));
        } else {
            command.addAll(List.of("-c:v", "copy"));
            if (plan.hvc1Tag()) {
                command.addAll(List.of("-tag:v", "hvc1"));
            }
        }

        command.addAll(List.of(
                // TV channels often use AC-3 or MP2 audio which browsers can't play, while converting audio to stereo
                // AAC is cheap, so always do that
                "-c:a", "aac", "-ac", "2",
                "-f", "hls",
                "-hls_time", String.valueOf(SEGMENT_SECONDS),
                "-hls_list_size", "6",
                "-hls_segment_type", "fmp4",
                // With a playlist per audio track, every segment gets the time it was recorded. Players use those to
                // line up a track's playlist with the video's when switching to it. Otherwise they go by the segments'
                // numbers, which drift apart: the video can only be cut at the channel's keyframes, so its segments
                // are sometimes longer than the audio's. A track switched to then never finds its segment, and stalls.
                "-hls_flags", separateAudio ? "delete_segments+program_date_time" : "delete_segments",
                "-hls_fmp4_init_filename", "init.mp4"
        ));
        if (separateAudio) {
            // Browsers only play the first audio track of a segment, so every track gets segments and a playlist of
            // its own, and the player switches between those. A master playlist ties them together. %v is the number
            // of the rendition, which ffmpeg adds to the names of the init files too.
            command.addAll(List.of(
                    "-hls_segment_filename", ingestUri + "seg_%v_%d.m4s",
                    "-master_pl_name", Stream.PLAYLIST,
                    "-var_stream_map", streamMap(plan.audioTracks())
            ));
        } else {
            command.addAll(List.of("-hls_segment_filename", ingestUri + "seg_%d.m4s"));
        }
        command.addAll(List.of(
                // Push the files to Kijkbuis over HTTP rather than writing them to disk
                "-method", "PUT",
                "-http_persistent", "1",
                ingestUri + (separateAudio ? RENDITION_PLAYLIST : Stream.PLAYLIST)
        ));
        return command;
    }

    /**
     * @return the renditions for ffmpeg's -var_stream_map: the video, with the audio tracks as a group of which the
     * first plays unless the viewer picks another
     */
    @NotNull
    private static String streamMap(@NotNull List<SourceAudio> audioTracks) {
        StringBuilder map = new StringBuilder("v:0,agroup:" + AUDIO_GROUP);
        for (int i = 0; i < audioTracks.size(); i++) {
            map.append(" a:").append(i).append(",agroup:").append(AUDIO_GROUP);
            // Only letters, see SourceAudio
            if (audioTracks.get(i).language() != null) {
                map.append(",language:").append(audioTracks.get(i).language());
            }
            if (i == 0) {
                map.append(",default:yes");
            }
        }
        return map.toString();
    }

}
