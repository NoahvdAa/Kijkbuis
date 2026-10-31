package nl.noahvanderaa.kijkbuis.stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What ffmpeg found in a channel.
 *
 * @param video the channel's video, or null if it has none
 * @param audio the channel's audio tracks, in the order ffmpeg lists them
 */
record Source(@Nullable SourceVideo video, @NotNull List<SourceAudio> audio) {

    Source {
        audio = List.copyOf(audio);
    }

    /**
     * @return a short description for logs
     */
    @NotNull
    String describe() {
        String video = this.video != null ? this.video.describe() + " video" : "no video";
        return video + " and " + (this.audio.size() == 1 ? "1 audio track" : this.audio.size() + " audio tracks");
    }

}
