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

    private FfmpegCommand() {
    }

    /**
     * @param ffmpegPath            the ffmpeg binary
     * @param preset                the libx264 preset to transcode with
     * @param deinterlaceDoubleRate whether deinterlacing turns every field into a frame, rather than every frame
     * @param streamUrl             the channel's stream URL
     * @param ingestUri             where ffmpeg should push the stream's files to, ending with a slash
     * @param plan                  how to encode the video
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

        command.addAll(List.of(
                "-i", streamUrl,
                // Only the first video and audio track, either may be missing (e.g. for radio channels)
                "-map", "0:v:0?", "-map", "0:a:0?"
        ));

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
                "-hls_flags", "delete_segments",
                "-hls_fmp4_init_filename", "init.mp4",
                "-hls_segment_filename", ingestUri + "seg_%d.m4s",
                // Push the files to Kijkbuis over HTTP rather than writing them to disk
                "-method", "PUT",
                "-http_persistent", "1",
                ingestUri + Stream.PLAYLIST
        ));
        return command;
    }

}
