package nl.noahvanderaa.kijkbuis.stream;

import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
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
        assertEquals(COPY, EncodingPlan.decide(null, video(H264), Set.of("h264"), false));
        assertEquals(new EncodingPlan(false, null, false, true), EncodingPlan.decide(null, video(new SourceVideo("hevc", false, 1920, 1080)), Set.of("h264", "hevc"), false));
        assertEquals(COPY, EncodingPlan.decide(null, video(new SourceVideo("av1", false, 1920, 1080)), Set.of("av1"), false));
    }

    @Test
    void passesThroughWhenTheCodecIsUnknown() {
        // The stream is restarted once ffmpeg reports the codec, if needed
        assertEquals(COPY, EncodingPlan.decide(null, null, Set.of("h264"), false));
    }

    @Test
    void transcodesCodecsTheBrowserCannotPlay() {
        assertEquals(TRANSCODE_SOURCE, EncodingPlan.decide(null, video(new SourceVideo("hevc", false, 1920, 1080)), Set.of("h264"), true));
        assertEquals(TRANSCODE_SOURCE, EncodingPlan.decide(null, video(new SourceVideo("mpeg2video", false, 720, 576)), Set.of("h264", "mpeg2video"), true), "Browsers can't decode MPEG-2 from fMP4");
        assertNull(EncodingPlan.decide(null, video(new SourceVideo("hevc", false, 1920, 1080)), Set.of("h264"), false), "Users without the transcode privilege can't watch it");
    }

    @Test
    void deinterlacesInterlacedVideoForUsersWhoMayTranscode() {
        assertEquals(DEINTERLACE_SOURCE, EncodingPlan.decide(null, video(INTERLACED_H264), Set.of("h264"), true));
        assertEquals(DEINTERLACE_SOURCE, EncodingPlan.decide(null, video(new SourceVideo("mpeg2video", true, 720, 576)), Set.of("h264"), true));
        assertEquals(COPY, EncodingPlan.decide(null, video(INTERLACED_H264), Set.of("h264"), false), "Browsers can still play it, just not nicely");
        assertNull(EncodingPlan.decide(null, video(new SourceVideo("mpeg2video", true, 720, 576)), Set.of("h264"), false));
    }

    @Test
    void transcodesToPickedQuality() {
        assertEquals(new EncodingPlan(true, LOW, false, false), EncodingPlan.decide(LOW, video(H264), Set.of("h264"), true));
        assertEquals(new EncodingPlan(true, LOW, true, false), EncodingPlan.decide(LOW, video(INTERLACED_H264), Set.of("h264"), true));
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
        assertEquals(new EncodingPlan(false, null, false, false, List.of(DUTCH, ENGLISH_DESCRIPTION)),
                EncodingPlan.decide(null, new Source(H264, List.of(DUTCH, ENGLISH_DESCRIPTION)), Set.of("h264"), false));
        assertEquals(new EncodingPlan(true, LOW, false, false, List.of(DUTCH, ENGLISH_DESCRIPTION)),
                EncodingPlan.decide(LOW, new Source(H264, List.of(DUTCH, ENGLISH_DESCRIPTION)), Set.of("h264"), true), "Whatever the quality");

        assertEquals(COPY, EncodingPlan.decide(null, new Source(H264, List.of(DUTCH)), Set.of("h264"), false), "One track goes in with the video");
        assertEquals(COPY, EncodingPlan.decide(null, new Source(H264, List.of()), Set.of("h264"), false));
        assertEquals(COPY, EncodingPlan.decide(null, new Source(null, List.of(DUTCH, ENGLISH_DESCRIPTION)), Set.of("h264"), false),
                "Without video, players would take audio playlists for qualities");
        assertEquals(COPY, EncodingPlan.decide(null, new Source(H264, List.of(DUTCH, new SourceAudio(1, "none", null, false))), Set.of("h264"), false),
                "Tracks ffmpeg can't decode are left out");

        List<SourceAudio> many = IntStream.range(0, 10).mapToObj((index) -> new SourceAudio(index, "aac", null, false)).toList();
        assertEquals(many.subList(0, 8), EncodingPlan.decide(null, new Source(H264, many), Set.of("h264"), false).audioTracks());
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
        assertEquals("bwdif=mode=send_frame:deint=interlaced,scale=w='round(iw*sar*min(1,min(640/(iw*sar),360/ih))/2)*2':h='round(ih*min(1,min(640/(iw*sar),360/ih))/2)*2',setsar=1",
                profile.get(profile.indexOf("-vf") + 1), "Fits in a 640x360 box");
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
                new EncodingPlan(false, null, false, false, List.of(DUTCH, new SourceAudio(2, "mp2", null, true))));

        assertEquals(List.of("0:v:0", "0:a:0", "0:a:2"), IntStream.range(0, command.size()).filter((i) -> command.get(i).equals("-map"))
                .mapToObj((i) -> command.get(i + 1)).toList(), "The tracks are known to be there");
        assertEquals("v:0,agroup:audio a:0,agroup:audio,language:dut,default:yes a:1,agroup:audio", command.get(command.indexOf("-var_stream_map") + 1));
        assertEquals("live.m3u8", command.get(command.indexOf("-master_pl_name") + 1));
        assertEquals("delete_segments+program_date_time", command.get(command.indexOf("-hls_flags") + 1),
                "Players line the playlists up by time, since the video's segments can be longer than the audio's");
        assertEquals("http://127.0.0.1:1234/stream/token/seg_%v_%d.m4s", command.get(command.indexOf("-hls_segment_filename") + 1));
        assertEquals("http://127.0.0.1:1234/stream/token/stream_%v.m3u8", command.getLast());
        assertEquals("stream_2.m3u8", FfmpegCommand.renditionPlaylist(2));

        List<String> oneTrack = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest, COPY);
        assertEquals(List.of("0:v:0?", "0:a:0?"), IntStream.range(0, oneTrack.size()).filter((i) -> oneTrack.get(i).equals("-map"))
                .mapToObj((i) -> oneTrack.get(i + 1)).toList());
        assertFalse(oneTrack.contains("-var_stream_map"));
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
     * @return a channel with this video and one audio track
     */
    private static Source video(SourceVideo video) {
        return new Source(video, List.of(DUTCH));
    }

}
