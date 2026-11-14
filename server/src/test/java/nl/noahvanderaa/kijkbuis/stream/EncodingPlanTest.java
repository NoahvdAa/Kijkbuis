package nl.noahvanderaa.kijkbuis.stream;

import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodingPlanTest {

    private static final KijkbuisConfig.TranscodeProfile LOW = new KijkbuisConfig.TranscodeProfile("low", "Low", 360, 700);
    private static final SourceVideo H264 = new SourceVideo("h264", false, 1920, 1080);
    private static final SourceVideo INTERLACED_H264 = new SourceVideo("h264", true, 1920, 1080);
    private static final EncodingPlan COPY = new EncodingPlan(false, null, false, false);
    private static final EncodingPlan TRANSCODE_SOURCE = new EncodingPlan(true, null, false, false);
    private static final EncodingPlan DEINTERLACE_SOURCE = new EncodingPlan(true, null, true, false);
    private static final SourceAudio DUTCH = new SourceAudio(0, "ac3", "dut", false);
    private static final SourceAudio ENGLISH_DESCRIPTION = new SourceAudio(1, "mp2", "eng", true);

    @Test
    void picksPassthroughWhenTheBrowserCanPlayTheCodec() {
        assertEquals(COPY, EncodingPlan.decide(null, false, video(H264), Set.of("h264"), false, 0));
        assertEquals(new EncodingPlan(false, null, false, true), EncodingPlan.decide(null, false, video(new SourceVideo("hevc", false, 1920, 1080)), Set.of("h264", "hevc"), false, 0));
        assertEquals(COPY, EncodingPlan.decide(null, false, video(new SourceVideo("av1", false, 1920, 1080)), Set.of("av1"), false, 0));
    }

    @Test
    void passesThroughWhenTheCodecIsUnknown() {
        // The stream is restarted once ffmpeg reports the codec, if needed
        assertEquals(COPY, EncodingPlan.decide(null, false, null, Set.of("h264"), false, 0));
    }

    @Test
    void transcodesCodecsTheBrowserCannotPlay() {
        assertEquals(TRANSCODE_SOURCE, EncodingPlan.decide(null, false, video(new SourceVideo("hevc", false, 1920, 1080)), Set.of("h264"), true, 0));
        assertEquals(TRANSCODE_SOURCE, EncodingPlan.decide(null, false, video(new SourceVideo("mpeg2video", false, 720, 576)), Set.of("h264", "mpeg2video"), true, 0), "Browsers can't decode MPEG-2 from fMP4");
        assertNull(EncodingPlan.decide(null, false, video(new SourceVideo("hevc", false, 1920, 1080)), Set.of("h264"), false, 0), "Users without the transcode privilege can't watch it");
    }

    @Test
    void deinterlacesInterlacedVideoForUsersWhoMayTranscode() {
        assertEquals(DEINTERLACE_SOURCE, EncodingPlan.decide(null, false, video(INTERLACED_H264), Set.of("h264"), true, 0));
        assertEquals(DEINTERLACE_SOURCE, EncodingPlan.decide(null, false, video(new SourceVideo("mpeg2video", true, 720, 576)), Set.of("h264"), true, 0));
        assertEquals(COPY, EncodingPlan.decide(null, false, video(INTERLACED_H264), Set.of("h264"), false, 0), "Browsers can still play it, just not nicely");
        assertNull(EncodingPlan.decide(null, false, video(new SourceVideo("mpeg2video", true, 720, 576)), Set.of("h264"), false, 0));
    }

    @Test
    void transcodesToPickedQuality() {
        assertEquals(new EncodingPlan(true, LOW, false, false), EncodingPlan.decide(LOW, false, video(H264), Set.of("h264"), true, 0));
        assertEquals(new EncodingPlan(true, LOW, true, false), EncodingPlan.decide(LOW, false, video(INTERLACED_H264), Set.of("h264"), true, 0));
    }

    @Test
    void neverTranscodesChannelsOverTheSizeLimit() {
        Source uhd = video(new SourceVideo("hevc", false, 3840, 2160));
        assertEquals(new EncodingPlan(false, null, false, true), EncodingPlan.decide(LOW, false, uhd, Set.of("h264", "hevc"), true, 1080),
                "It plays at its own quality, whatever the user picked");
        assertNull(EncodingPlan.decide(null, false, uhd, Set.of("h264"), true, 1080), "Browsers that can't play it would need it transcoded");
        assertNull(EncodingPlan.decide(LOW, false, uhd, Set.of("h264"), true, 1080));
        assertEquals(COPY, EncodingPlan.decide(null, false, video(new SourceVideo("h264", true, 3840, 2160)), Set.of("h264"), true, 1080),
                "It's not deinterlaced either");

        assertEquals(new EncodingPlan(true, LOW, false, false), EncodingPlan.decide(LOW, false, video(H264), Set.of("h264"), true, 1080),
                "A channel the size of the limit is transcoded");
        assertEquals(new EncodingPlan(true, LOW, false, false), EncodingPlan.decide(LOW, false, uhd, Set.of("h264", "hevc"), true, 2160));
        assertEquals(new EncodingPlan(true, LOW, false, false), EncodingPlan.decide(LOW, false, uhd, Set.of("h264", "hevc"), true, 0), "0 is no limit");
        assertEquals(new EncodingPlan(true, LOW, false, false), EncodingPlan.decide(LOW, false, null, Set.of("h264"), true, 1080),
                "The stream is restarted once ffmpeg reports the size, if needed");
    }

    @Test
    void measuresChannelsByTheSizeTheyAreShownAt() {
        assertTrue(new SourceVideo("h264", false, 1920, 1080).fitsIn(1080));
        assertTrue(new SourceVideo("h264", false, 1920, 800).fitsIn(1080));
        assertTrue(new SourceVideo("h264", false, 1024, 576).fitsIn(576), "720x576 shown as 16:9");
        assertFalse(new SourceVideo("h264", false, 1920, 1080).fitsIn(720));
        assertFalse(new SourceVideo("h264", false, 2560, 1080).fitsIn(1080), "Ultrawide is too wide, if not too tall");
        assertTrue(new SourceVideo("h264", false, 1080, 1920).fitsIn(1080), "Portrait takes as much to transcode as landscape");
        assertTrue(new SourceVideo("h264", false, 1080, 1080).fitsIn(1080));
        assertFalse(new SourceVideo("hevc", false, 2160, 3840).fitsIn(1080), "Portrait 4K");
        assertFalse(new SourceVideo("h264", false, 1080, 2560).fitsIn(1080), "Too tall, if not too wide");
        assertTrue(new SourceVideo("h264", false, 0, 0).fitsIn(1080), "There's no telling without a size");
        assertTrue(new SourceVideo("hevc", false, 7680, 4320).fitsIn(0));
    }

    @Test
    void parsesFfmpegLog() {
        assertEquals(INTERLACED_H264, SourceVideo.parse("  Stream #0:0[0x100]: Video: h264 (High) ([27][0][0][0] / 0x001B), yuv420p(tv, bt709, top first), 1920x1080 [SAR 1:1 DAR 16:9], 25 fps, 25 tbr, 90k tbn"));
        assertEquals(new SourceVideo("mpeg2video", true, 1024, 576), SourceVideo.parse("  Stream #0:0[0x100]: Video: mpeg2video (Main) ([2][0][0][0] / 0x0002), yuv420p(tv, bottom first), 720x576 [SAR 64:45 DAR 16:9], 25 fps, 25 tbr, 90k tbn"),
                "720x576 with wide pixels is shown at 1024x576");
        assertEquals(INTERLACED_H264, SourceVideo.parse("  Stream #0:1: Video: h264 (High), yuv420p(top coded first (swapped)), 1920x1080, 25 fps"));
        assertEquals(new SourceVideo("h264", false, 1280, 720), SourceVideo.parse("  Stream #0:0[0x100]: Video: h264 (High) ([27][0][0][0] / 0x001B), yuv420p(tv, bt709, progressive), 1280x720 [SAR 1:1 DAR 16:9], 50 fps, 50 tbr, 90k tbn"));
        assertEquals(new SourceVideo("h264", false, 1920, 1080), SourceVideo.parse("  Stream #0:0: Video: h264 (High), yuv420p(progressive), 1440x1080 [SAR 4:3 DAR 16:9], 25 fps"), "HDV");
        assertEquals(new SourceVideo("h264", false, 1920, 800), SourceVideo.parse("  Stream #0:0: Video: h264 (High), yuv420p(progressive), 1920x800 [SAR 1:1 DAR 12:5], 25 fps"));
        assertEquals(new SourceVideo("h264", false, 1080, 1920), SourceVideo.parse("  Stream #0:0: Video: h264 (High), yuv420p(progressive), 1080x1920, 25 fps"), "Without a SAR, pixels are square");
        assertEquals(new SourceVideo("h264", false, 720, 576), SourceVideo.parse("  Stream #0:0: Video: h264 (High), yuv420p, 720x576 [SAR 0:1 DAR 0:1], 25 fps"), "An unknown SAR counts as square");
        assertEquals(new SourceVideo("hevc", false, 3840, 2160), SourceVideo.parse("  Stream #0:0[0x100]: Video: hevc (Main 10), yuv420p10le(tv), 3840x2160, 50 fps"), "Unknown field order counts as progressive");
        assertEquals(new SourceVideo("h264", false, 0, 0), SourceVideo.parse("  Stream #0:0[0x100]: Video: h264 ([27][0][0][0] / 0x001B), none"), "The codec tag isn't a size");
        assertNull(SourceVideo.parse("  Stream #0:1[0x101](dut): Audio: ac3 ([129][0][0][0] / 0x0081), 48000 Hz, stereo, fltp, 192 kb/s"));
    }

    @Test
    void givesAudioTracksPlaylistsOfTheirOwnWhenThereAreSeveral() {
        assertEquals(new EncodingPlan(false, false, null, false, false, List.of(DUTCH, ENGLISH_DESCRIPTION)),
                EncodingPlan.decide(null, false, new Source(H264, List.of(DUTCH, ENGLISH_DESCRIPTION)), Set.of("h264"), false, 0));
        assertEquals(new EncodingPlan(false, true, LOW, false, false, List.of(DUTCH, ENGLISH_DESCRIPTION)),
                EncodingPlan.decide(LOW, false, new Source(H264, List.of(DUTCH, ENGLISH_DESCRIPTION)), Set.of("h264"), true, 0), "Whatever the quality");

        assertEquals(COPY, EncodingPlan.decide(null, false, new Source(H264, List.of(DUTCH)), Set.of("h264"), false, 0), "One track goes in with the video");
        assertEquals(COPY, EncodingPlan.decide(null, false, new Source(H264, List.of()), Set.of("h264"), false, 0));
        assertEquals(COPY, EncodingPlan.decide(null, false, new Source(H264, List.of(DUTCH, new SourceAudio(1, "none", null, false))), Set.of("h264"), false, 0),
                "Tracks ffmpeg can't decode are left out");

        List<SourceAudio> many = IntStream.range(0, 10).mapToObj((index) -> new SourceAudio(index, "aac", null, false)).toList();
        assertEquals(many.subList(0, 8), EncodingPlan.decide(null, false, new Source(H264, many), Set.of("h264"), false, 0).audioTracks());
    }

    @Test
    void leavesTheVideoOutForAudioOnly() {
        assertEquals(EncodingPlan.audioOnly(List.of()), EncodingPlan.decide(null, true, video(H264), Set.of("h264"), false, 0));
        assertEquals(EncodingPlan.audioOnly(List.of(DUTCH, ENGLISH_DESCRIPTION)),
                EncodingPlan.decide(null, true, new Source(H264, List.of(DUTCH, ENGLISH_DESCRIPTION)), Set.of("h264"), false, 0),
                "The viewer can still switch between the audio tracks");
        assertEquals(EncodingPlan.audioOnly(List.of()), EncodingPlan.decide(null, true, null, Set.of("h264"), false, 0),
                "The stream is restarted once ffmpeg reports what the channel has, if needed");
        assertEquals(EncodingPlan.audioOnly(List.of()),
                EncodingPlan.decide(null, true, video(new SourceVideo("mpeg2video", true, 720, 576)), Set.of("h264"), false, 0),
                "Video the browser can't play doesn't matter if it's left out");
        assertEquals(EncodingPlan.audioOnly(List.of()), EncodingPlan.decide(null, true, video(new SourceVideo("hevc", false, 3840, 2160)), Set.of("h264"), true, 1080),
                "Neither does its size");
    }

    @Test
    void playsChannelsWithoutSoundWithTheirVideo() {
        assertEquals(COPY, EncodingPlan.decide(null, true, new Source(H264, List.of()), Set.of("h264"), false, 0));
        assertEquals(COPY, EncodingPlan.decide(null, true, new Source(H264, List.of(new SourceAudio(0, "none", null, false))), Set.of("h264"), false, 0),
                "Sound ffmpeg can't decode is no sound");
        assertEquals(TRANSCODE_SOURCE, EncodingPlan.decide(null, true, new Source(new SourceVideo("mpeg2video", false, 720, 576), List.of()), Set.of("h264"), true, 0));
        assertNull(EncodingPlan.decide(null, true, new Source(new SourceVideo("mpeg2video", false, 720, 576), List.of()), Set.of("h264"), false, 0));
    }

    @Test
    void channelsWithoutVideoAreAudioOnlyWhateverThePick() {
        Source radio = new Source(null, List.of(DUTCH));
        assertEquals(EncodingPlan.audioOnly(List.of()), EncodingPlan.decide(null, false, radio, Set.of("h264"), false, 0));
        assertEquals(EncodingPlan.audioOnly(List.of()), EncodingPlan.decide(null, true, radio, Set.of("h264"), false, 0));
        assertEquals(EncodingPlan.audioOnly(List.of()), EncodingPlan.decide(LOW, false, radio, Set.of("h264"), true, 0), "There's nothing to transcode");
        assertEquals(EncodingPlan.audioOnly(List.of(DUTCH, ENGLISH_DESCRIPTION)),
                EncodingPlan.decide(null, false, new Source(null, List.of(DUTCH, ENGLISH_DESCRIPTION)), Set.of("h264"), false, 0));
    }

    @Test
    void parsesAudioTracks() {
        assertEquals(DUTCH, SourceAudio.parse("  Stream #0:1[0x101](dut): Audio: ac3 ([129][0][0][0] / 0x0081), 48000 Hz, stereo, fltp, 192 kb/s", 0));
        assertEquals(ENGLISH_DESCRIPTION, SourceAudio.parse("  Stream #0:2[0x102](eng): Audio: mp2 (mp3float) ([3][0][0][0] / 0x0003), 44100 Hz, mono, fltp, 384 kb/s, start 1.400000 (visual impaired) (descriptions)", 1));
        assertEquals(new SourceAudio(0, "aac", null, false), SourceAudio.parse("  Stream #0:1: Audio: aac (LC), 48000 Hz, stereo, fltp", 0));
        assertEquals(new SourceAudio(2, "eac3", null, false), SourceAudio.parse("  Stream #0:3[0x103](und): Audio: eac3, 48000 Hz, 5.1(side), fltp, 384 kb/s", 2),
                "und means the channel doesn't say");
        assertNull(SourceAudio.parse("  Stream #0:0[0x100]: Video: h264 (High), yuv420p, 1920x1080", 0));
    }

    @Test
    void normalizesBrowserCodecNames() {
        assertEquals(Set.of("h264", "hevc", "av1", "vp9"), EncodingPlan.normalizeCodecs(List.of("avc1", "HVC1", "hev1", "av01", "vp9", "h264")));
    }

    @Test
    void buildsCommands() {
        URI ingest = URI.create("http://127.0.0.1:1234/stream/token/");

        List<String> copy = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest, new EncodingPlan(false, null, false, true));
        assertEquals("copy", copy.get(copy.indexOf("-c:v") + 1));
        assertEquals("hvc1", copy.get(copy.indexOf("-tag:v") + 1));
        assertTrue(copy.contains("-reconnect"));
        assertEquals("http://127.0.0.1:1234/stream/token/live.m3u8", copy.getLast());

        List<String> profile = FfmpegCommand.build("ffmpeg", "veryfast", true, "rtsp://upstream.example/1", ingest, new EncodingPlan(true, LOW, false, false));
        assertEquals("libx264", profile.get(profile.indexOf("-c:v") + 1));
        assertEquals("bwdif=mode=send_frame:deint=interlaced,scale=w='round(iw*sar*min(1,min(640/max(iw*sar,ih),360/min(iw*sar,ih)))/2)*2'"
                        + ":h='round(ih*min(1,min(640/max(iw*sar,ih),360/min(iw*sar,ih)))/2)*2',setsar=1",
                profile.get(profile.indexOf("-vf") + 1), "Fits in a 640x360 box, or 360x640 for portrait channels");
        assertEquals("700k", profile.get(profile.indexOf("-b:v") + 1));
        assertFalse(profile.contains("-reconnect"), "Reconnect options only exist for HTTP");

        List<String> source = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest, TRANSCODE_SOURCE);
        assertEquals("bwdif=mode=send_frame:deint=interlaced", source.get(source.indexOf("-vf") + 1), "Progressive channels keep their frame rate");
        assertTrue(source.contains("-crf"));
        assertFalse(source.contains("-b:v"));
    }

    @Test
    void buildsAPlaylistPerAudioTrack() {
        URI ingest = URI.create("http://127.0.0.1:1234/stream/token/");
        List<String> command = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest,
                new EncodingPlan(false, false, null, false, false, List.of(DUTCH, new SourceAudio(2, "mp2", null, true))));

        assertEquals(List.of("0:v:0", "0:a:0", "0:a:2"), maps(command), "The tracks are known to be there");
        assertEquals("v:0,agroup:audio a:0,agroup:audio,language:dut,default:yes a:1,agroup:audio", command.get(command.indexOf("-var_stream_map") + 1));
        assertEquals("live.m3u8", command.get(command.indexOf("-master_pl_name") + 1));
        assertEquals("delete_segments+program_date_time", command.get(command.indexOf("-hls_flags") + 1),
                "Players line the playlists up by time, since the video's segments can be longer than the audio's");
        assertEquals("http://127.0.0.1:1234/stream/token/seg_%v_%d.m4s", command.get(command.indexOf("-hls_segment_filename") + 1));
        assertEquals("http://127.0.0.1:1234/stream/token/stream_%v.m3u8", command.getLast());
        assertEquals("stream_2.m3u8", FfmpegCommand.renditionPlaylist(2));

        List<String> oneTrack = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest, COPY);
        assertEquals(List.of("0:v:0?", "0:a:0?"), maps(oneTrack));
        assertFalse(oneTrack.contains("-var_stream_map"));
    }

    @Test
    void buildsAudioOnlyCommands() {
        URI ingest = URI.create("http://127.0.0.1:1234/stream/token/");

        List<String> oneTrack = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest, EncodingPlan.audioOnly(List.of()));
        assertEquals(List.of("0:a:0"), maps(oneTrack), "Without a question mark, since ffmpeg picks the video by itself if nothing is mapped");
        assertFalse(oneTrack.contains("-c:v"));
        assertFalse(oneTrack.contains("-var_stream_map"));
        assertEquals("aac", oneTrack.get(oneTrack.indexOf("-c:a") + 1));
        assertEquals("http://127.0.0.1:1234/stream/token/live.m3u8", oneTrack.getLast());

        List<String> twoTracks = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest,
                EncodingPlan.audioOnly(List.of(DUTCH, new SourceAudio(2, "mp2", null, true))));
        assertEquals(List.of("0:a:0", "0:a:2"), maps(twoTracks));
        assertFalse(twoTracks.contains("-c:v"));
        assertEquals("a:0 a:1", twoTracks.get(twoTracks.indexOf("-var_stream_map") + 1));
        assertFalse(twoTracks.contains("-master_pl_name"), "Kijkbuis writes that one itself");
        assertEquals("delete_segments+program_date_time", twoTracks.get(twoTracks.indexOf("-hls_flags") + 1));
        assertEquals("http://127.0.0.1:1234/stream/token/stream_%v.m3u8", twoTracks.getLast());
    }

    @Test
    void tiesAudioOnlyTracksTogether() {
        assertEquals("""
                #EXTM3U
                #EXT-X-VERSION:7
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="audio_0",DEFAULT=YES,LANGUAGE="dut",CHANNELS="2",URI="stream_0.m3u8"
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="audio_1",DEFAULT=NO,CHANNELS="2",URI="stream_1.m3u8"
                #EXT-X-STREAM-INF:BANDWIDTH=140800,CODECS="mp4a.40.2",AUDIO="audio"
                stream_0.m3u8
                """, new String(AudioOnlyPlaylist.build(List.of(DUTCH, new SourceAudio(2, "mp2", null, true))), StandardCharsets.UTF_8),
                "One stream, so players don't take the tracks for qualities");
    }

    @Test
    void deinterlacesAtConfiguredRate() {
        URI ingest = URI.create("http://127.0.0.1:1234/stream/token/");

        List<String> doubleRate = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest, DEINTERLACE_SOURCE);
        assertEquals("bwdif=mode=send_field:deint=interlaced", doubleRate.get(doubleRate.indexOf("-vf") + 1));

        List<String> singleRate = FfmpegCommand.build("ffmpeg", "veryfast", false, "http://upstream.example/1.ts", ingest, new EncodingPlan(true, LOW, true, false));
        assertTrue(singleRate.get(singleRate.indexOf("-vf") + 1).startsWith("bwdif=mode=send_frame:deint=interlaced,scale="));
    }

    /**
     * @return what the command maps, in order
     */
    private static List<String> maps(List<String> command) {
        return IntStream.range(0, command.size()).filter((i) -> command.get(i).equals("-map")).mapToObj((i) -> command.get(i + 1)).toList();
    }

    /**
     * @return a channel with this video and one audio track
     */
    private static Source video(SourceVideo video) {
        return new Source(video, List.of(DUTCH));
    }

}
