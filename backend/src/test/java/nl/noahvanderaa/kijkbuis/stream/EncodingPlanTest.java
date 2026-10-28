package nl.noahvanderaa.kijkbuis.stream;

import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

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

    @Test
    void picksPassthroughWhenTheBrowserCanPlayTheCodec() {
        assertEquals(COPY, EncodingPlan.decide(null, H264, Set.of("h264"), false));
        assertEquals(new EncodingPlan(false, null, false, true), EncodingPlan.decide(null, new SourceVideo("hevc", false, 1920, 1080), Set.of("h264", "hevc"), false));
        assertEquals(COPY, EncodingPlan.decide(null, new SourceVideo("av1", false, 1920, 1080), Set.of("av1"), false));
    }

    @Test
    void passesThroughWhenTheCodecIsUnknown() {
        // The stream is restarted once ffmpeg reports the codec, if needed
        assertEquals(COPY, EncodingPlan.decide(null, null, Set.of("h264"), false));
    }

    @Test
    void transcodesCodecsTheBrowserCannotPlay() {
        assertEquals(TRANSCODE_SOURCE, EncodingPlan.decide(null, new SourceVideo("hevc", false, 1920, 1080), Set.of("h264"), true));
        assertEquals(TRANSCODE_SOURCE, EncodingPlan.decide(null, new SourceVideo("mpeg2video", false, 720, 576), Set.of("h264", "mpeg2video"), true), "Browsers can't decode MPEG-2 from fMP4");
        assertNull(EncodingPlan.decide(null, new SourceVideo("hevc", false, 1920, 1080), Set.of("h264"), false), "Users without the transcode privilege can't watch it");
    }

    @Test
    void deinterlacesInterlacedVideoForUsersWhoMayTranscode() {
        assertEquals(DEINTERLACE_SOURCE, EncodingPlan.decide(null, INTERLACED_H264, Set.of("h264"), true));
        assertEquals(DEINTERLACE_SOURCE, EncodingPlan.decide(null, new SourceVideo("mpeg2video", true, 720, 576), Set.of("h264"), true));
        assertEquals(COPY, EncodingPlan.decide(null, INTERLACED_H264, Set.of("h264"), false), "Browsers can still play it, just not nicely");
        assertNull(EncodingPlan.decide(null, new SourceVideo("mpeg2video", true, 720, 576), Set.of("h264"), false));
    }

    @Test
    void transcodesToPickedQuality() {
        assertEquals(new EncodingPlan(true, LOW, false, false), EncodingPlan.decide(LOW, H264, Set.of("h264"), true));
        assertEquals(new EncodingPlan(true, LOW, true, false), EncodingPlan.decide(LOW, INTERLACED_H264, Set.of("h264"), true));
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
    void deinterlacesAtConfiguredRate() {
        URI ingest = URI.create("http://127.0.0.1:1234/stream/token/");

        List<String> doubleRate = FfmpegCommand.build("ffmpeg", "veryfast", true, "http://upstream.example/1.ts", ingest, DEINTERLACE_SOURCE);
        assertEquals("bwdif=mode=send_field:deint=interlaced", doubleRate.get(doubleRate.indexOf("-vf") + 1));

        List<String> singleRate = FfmpegCommand.build("ffmpeg", "veryfast", false, "http://upstream.example/1.ts", ingest, new EncodingPlan(true, LOW, true, false));
        assertTrue(singleRate.get(singleRate.indexOf("-vf") + 1).startsWith("bwdif=mode=send_frame:deint=interlaced,scale="));
    }

}
