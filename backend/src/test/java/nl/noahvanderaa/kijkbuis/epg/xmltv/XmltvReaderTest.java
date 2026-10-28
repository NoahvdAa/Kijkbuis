package nl.noahvanderaa.kijkbuis.epg.xmltv;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XmltvReaderTest {

    private static final String EPG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE tv SYSTEM "xmltv.dtd">
            <tv generator-info-name="test">
              <channel id="npo1.nl">
                <display-name lang="nl">NPO 1</display-name>
                <display-name>NPO1</display-name>
                <icon src="http://logos.example/npo1.png"/>
              </channel>
              <programme start="20261026200000 +0100" stop="20261026203000 +0100" channel="npo1.nl">
                <title lang="nl">Journaal &amp; Weer</title>
                <title lang="en">News &amp; Weather</title>
                <sub-title>Avond</sub-title>
                <desc>Het <b>nieuws</b> van vandaag.</desc>
                <credits><director>Someone</director></credits>
                <category>Nieuws</category>
                <category>Actualiteiten</category>
                <icon src="http://images.example/journaal.jpg"/>
              </programme>
            </tv>
            """;
    private static final List<XmltvEntry> EXPECTED = List.of(
            new XmltvChannel("npo1.nl", "NPO 1", "http://logos.example/npo1.png"),
            new XmltvProgramme("npo1.nl", Instant.parse("2026-10-26T19:00:00Z"), Instant.parse("2026-10-26T19:30:00Z"),
                    "Journaal & Weer", "Avond", "Het nieuws van vandaag.", "Nieuws", "http://images.example/journaal.jpg", null, null)
    );

    @Test
    void readsChannelsAndProgrammes() throws IOException {
        assertEquals(EXPECTED, read(EPG.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsGzippedFiles() throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(EPG.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(EXPECTED, read(compressed.toByteArray()));
    }

    @Test
    void skipsEntriesItCannotUse() throws IOException {
        List<XmltvEntry> entries = read("""
                <tv>
                  <channel><display-name>No ID</display-name></channel>
                  stray text
                  <programme start="20261026200000" channel="a"><desc>No title</desc></programme>
                  <programme channel="a"><title>No start</title></programme>
                  <programme start="tomorrow" channel="a"><title>Invalid start</title></programme>
                  <programme start="20261026200000" stop="20261026190000" channel="a"><title>Ends before it starts</title></programme>
                  <programme start="20261026200000"><title>No channel</title></programme>
                  <programme start="20261026200000" channel="a"><title>No stop</title></programme>
                </tv>
                """.getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(new XmltvProgramme("a", Instant.parse("2026-10-26T20:00:00Z"), null, "No stop", null, null, null, null, null, null)), entries);
    }

    @Test
    void picksPostersAndBackdropsFromImages() throws IOException {
        List<XmltvEntry> entries = read("""
                <tv>
                  <programme start="20261026200000" channel="a">
                    <title>Film</title>
                    <image type="poster" size="3" orient="P">https://images.example/poster-large.jpg</image>
                    <image type="poster" size="2" orient="P">https://images.example/poster-medium.jpg</image>
                    <image type="still" size="3" orient="L">https://images.example/still.jpg</image>
                    <image type="backdrop" size="2" orient="L">https://images.example/backdrop-medium.jpg</image>
                    <image type="backdrop" size="3" orient="L">https://images.example/backdrop-large.jpg</image>
                  </programme>
                  <programme start="20261026210000" channel="a">
                    <title>Episode</title>
                    <credits><actor>Someone<image orient="P">https://images.example/actor.jpg</image></actor></credits>
                    <icon src="javascript:alert(1)"/>
                    <image type="poster">data:image/png;base64,AAAA</image>
                    <image type="still"><![CDATA[ https://images.example/scene.jpg ]]></image>
                  </programme>
                </tv>
                """.getBytes(StandardCharsets.UTF_8));

        XmltvProgramme film = (XmltvProgramme) entries.get(0);
        assertEquals("https://images.example/poster-medium.jpg", film.posterUrl(), "Posters are shown small, so medium is best");
        assertEquals("https://images.example/backdrop-large.jpg", film.backdropUrl());

        XmltvProgramme episode = (XmltvProgramme) entries.get(1);
        assertNull(episode.posterUrl(), "Images of the cast aren't the programme's, and only http(s) URLs are used");
        assertNull(episode.iconUrl());
        assertEquals("https://images.example/scene.jpg", episode.backdropUrl(), "A still works as a background when there's no backdrop");
    }

    @Test
    void doesNotResolveExternalEntities() {
        // A malicious EPG could otherwise read files from the server
        assertThrows(IOException.class, () -> read("""
                <?xml version="1.0"?>
                <!DOCTYPE tv [<!ENTITY secret SYSTEM "file:///etc/hosts">]>
                <tv><programme start="20261026200000" channel="a"><title>&secret;</title></programme></tv>
                """.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsFilesThatAreNotXml() {
        IOException ex = assertThrows(IOException.class, () -> read("<html>Oops".getBytes(StandardCharsets.UTF_8)));
        assertEquals(true, ex.getMessage().startsWith("The EPG is not valid XML"), ex.getMessage());
    }

    @Test
    void parsesTimestamps() {
        assertEquals(Instant.parse("2026-10-26T19:00:00Z"), XmltvTime.parse("20261026200000 +0100"));
        assertEquals(Instant.parse("2026-10-26T22:30:00Z"), XmltvTime.parse("20261026200000 -0230"));
        assertEquals(Instant.parse("2026-10-26T19:00:00Z"), XmltvTime.parse("20261026200000+0100"));
        assertEquals(Instant.parse("2026-10-26T19:00:00Z"), XmltvTime.parse("20261026200000 +01:00"));
        assertEquals(Instant.parse("2026-10-26T19:00:00Z"), XmltvTime.parse("202610262000 +0100"));
        assertEquals(Instant.parse("2026-10-26T20:00:00Z"), XmltvTime.parse("20261026200000"));
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"), XmltvTime.parse("20261026"));

        assertNull(XmltvTime.parse("2026102620000"));
        assertNull(XmltvTime.parse("20261326200000"));
        assertNull(XmltvTime.parse("20261026200000 GMT"));
        assertNull(XmltvTime.parse(""));
        assertNull(XmltvTime.parse(null));
    }

    @NotNull
    private static List<XmltvEntry> read(byte @NotNull [] data) throws IOException {
        List<XmltvEntry> entries = new ArrayList<>();
        try (XmltvReader reader = XmltvReader.open(new ByteArrayInputStream(data))) {
            XmltvEntry entry;
            while ((entry = reader.next()) != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

}
