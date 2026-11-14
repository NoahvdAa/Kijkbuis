package nl.noahvanderaa.kijkbuis.stream;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The master playlist of a stream without video whose audio tracks each have a playlist of their own.
 * <p>
 * ffmpeg writes the master playlist of streams with video, but not one that works without: it lists every audio track
 * as a stream of its own, besides listing them as the tracks to switch between. Players take those streams for
 * qualities of the same thing, and switch between them by themselves. So this lists one stream, the first track, with
 * the tracks as its audio group.
 */
final class AudioOnlyPlaylist {

    /**
     * What players expect a stream to take at most. Like ffmpeg, it's the audio's bitrate with a tenth on top for
     * what the segments add to that.
     */
    private static final int BANDWIDTH = FfmpegCommand.AUDIO_BITRATE_KBPS * 1100;
    /**
     * AAC as ffmpeg encodes it, which is the low complexity kind
     */
    private static final String CODEC = "mp4a.40.2";

    private AudioOnlyPlaylist() {
    }

    /**
     * @param audioTracks the stream's audio tracks, in the order of their playlists, which are numbered from 0
     * @return the playlist, in which the first track plays unless the viewer picks another
     */
    static byte @NotNull [] build(@NotNull List<SourceAudio> audioTracks) {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n");
        for (int i = 0; i < audioTracks.size(); i++) {
            playlist.append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"").append(FfmpegCommand.AUDIO_GROUP)
                    .append("\",NAME=\"audio_").append(i)
                    .append("\",DEFAULT=").append(i == 0 ? "YES" : "NO");
            // Only letters, see SourceAudio
            if (audioTracks.get(i).language() != null) {
                playlist.append(",LANGUAGE=\"").append(audioTracks.get(i).language()).append('"');
            }
            playlist.append(",CHANNELS=\"2\",URI=\"").append(FfmpegCommand.renditionPlaylist(i)).append("\"\n");
        }
        // The stream is the first track's playlist, which is how players tell that track doesn't need loading twice
        playlist.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(BANDWIDTH)
                .append(",CODECS=\"").append(CODEC)
                .append("\",AUDIO=\"").append(FfmpegCommand.AUDIO_GROUP).append("\"\n")
                .append(FfmpegCommand.renditionPlaylist(0)).append('\n');
        return playlist.toString().getBytes(StandardCharsets.UTF_8);
    }

}
