package nl.noahvanderaa.kijkbuis.playlist.m3u;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class M3UParserTest {

    @Test
    void parsesEntriesAndAttributes() throws IOException {
        M3UPlaylist playlist = parse("""
                #EXTM3U url-tvg="http://epg.example/a.xml,http://epg.example/b.xml"
                #EXTINF:-1 tvg-id="npo1.nl" tvg-name="NPO_1" tvg-logo="http://logos.example/npo1.png" group-title="NL, HD" tvg-chno="1",NPO 1, the first
                http://streams.example/npo1
                """);

        assertEquals("http://epg.example/a.xml", playlist.epgUrl());
        assertEquals(List.of(new M3UEntry("NPO 1, the first", "http://streams.example/npo1", "npo1.nl", "http://logos.example/npo1.png", "NL, HD", 1)), playlist.entries());
    }

    @Test
    void fallsBackWhenFieldsAreMissing() throws IOException {
        M3UPlaylist playlist = parse("""
                #EXTM3U
                http://streams.example/bare
                #EXTINF:-1 tvg-name="Named",
                http://streams.example/named
                #EXTGRP:Sports
                #EXTINF:-1 tvg-chno="abc",Grouped
                http://streams.example/grouped
                #EXTINF:-1 group-title="" tvg-id=" ",Empty attributes
                http://streams.example/empty
                """);

        assertNull(playlist.epgUrl());
        assertEquals(List.of(
                new M3UEntry("http://streams.example/bare", "http://streams.example/bare", null, null, null, null),
                new M3UEntry("Named", "http://streams.example/named", null, null, null, null),
                new M3UEntry("Grouped", "http://streams.example/grouped", null, null, "Sports", null),
                new M3UEntry("Empty attributes", "http://streams.example/empty", null, null, null, null)
        ), playlist.entries());
    }

    @Test
    void skipsJunkAndHandlesBomAndCrlf() throws IOException {
        M3UPlaylist playlist = parse("﻿#EXTM3U x-tvg-url=\"http://epg.example/x.xml\"\r\n"
                + "#EXTINF:-1,One\r\n"
                + "http://streams.example/one\r\n"
                + "<html><body>Error</body></html>\r\n"
                + "#EXTVLCOPT:http-user-agent=Foo\r\n"
                + "not a url\r\n"
                + "#EXTINF:-1,Two\r\n"
                + "http://streams.example/two\r\n");

        assertEquals("http://epg.example/x.xml", playlist.epgUrl());
        assertEquals(List.of("One", "Two"), playlist.entries().stream().map(M3UEntry::name).toList());
    }

    @Test
    void acceptsUnquotedAttributesAndSurvivesUnterminatedQuotes() throws IOException {
        M3UPlaylist playlist = parse("""
                #EXTINF:-1 tvg-id=plain.nl group-title=News,Plain
                http://streams.example/plain
                #EXTINF:-1 tvg-id="broken,Broken
                http://streams.example/broken
                """);

        assertEquals(new M3UEntry("Plain", "http://streams.example/plain", "plain.nl", null, "News", null), playlist.entries().getFirst());
        assertEquals("http://streams.example/broken", playlist.entries().get(1).url());
    }

    @NotNull
    private static M3UPlaylist parse(@NotNull String text) throws IOException {
        return M3UParser.parse(new BufferedReader(new StringReader(text)));
    }

}
