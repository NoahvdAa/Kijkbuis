package nl.noahvanderaa.kijkbuis.playlist.m3u;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses extended M3U playlists as used for IPTV, such as:
 * <pre>
 * #EXTM3U url-tvg="http://example.com/epg.xml"
 * #EXTINF:-1 tvg-id="npo1.nl" tvg-logo="http://example.com/npo1.png" group-title="NL",NPO 1
 * http://example.com/stream/1
 * </pre>
 * Parsing is lenient: lines that can't be understood are skipped rather than failing the whole playlist.
 */
public final class M3UParser {

    private static final String HEADER = "#EXTM3U";
    private static final String EXTINF = "#EXTINF:";
    private static final String EXTGRP = "#EXTGRP:";
    private static final Pattern URL = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://\\S+$");

    private M3UParser() {
    }

    @NotNull
    public static M3UPlaylist parse(@NotNull BufferedReader reader) throws IOException {
        Map<String, String> headerAttributes = Map.of();
        List<M3UEntry> entries = new ArrayList<>();
        ExtInf extInf = null;
        String group = null;

        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (firstLine && line.startsWith("﻿")) {
                line = line.substring(1);
            }
            firstLine = false;
            line = line.strip();

            if (line.startsWith(HEADER)) {
                headerAttributes = parseAttributes(line, HEADER.length()).values();
            } else if (line.startsWith(EXTINF)) {
                extInf = parseExtInf(line);
            } else if (line.startsWith(EXTGRP)) {
                group = line.substring(EXTGRP.length()).strip();
            } else if (URL.matcher(line).matches()) {
                entries.add(toEntry(extInf, group, line));
                extInf = null;
                group = null;
            }
            // Anything else is either a directive we don't use (such as #EXTVLCOPT), or junk like an HTML error page
        }

        return new M3UPlaylist(headerAttributes, entries);
    }

    @NotNull
    private static M3UEntry toEntry(@Nullable ExtInf extInf, @Nullable String group, @NotNull String url) {
        Map<String, String> attributes = extInf != null ? extInf.attributes() : Map.of();

        String name = extInf != null ? blankToNull(extInf.title()) : null;
        if (name == null) {
            name = blankToNull(attributes.get("tvg-name"));
        }
        if (name == null) {
            name = url;
        }

        String groupTitle = blankToNull(attributes.get("group-title"));
        if (groupTitle == null) {
            groupTitle = blankToNull(group);
        }

        Integer channelNumber = null;
        String rawChannelNumber = blankToNull(attributes.get("tvg-chno"));
        if (rawChannelNumber != null) {
            try {
                channelNumber = Integer.parseInt(rawChannelNumber);
            } catch (NumberFormatException ignored) {
                // Not a usable channel number, leave it out
            }
        }

        return new M3UEntry(name, url, blankToNull(attributes.get("tvg-id")), blankToNull(attributes.get("tvg-logo")), groupTitle, channelNumber);
    }

    /**
     * Parses a line like {@code #EXTINF:-1 tvg-id="a" group-title="b",Title}.
     */
    @NotNull
    private static ExtInf parseExtInf(@NotNull String line) {
        // Skip the duration, which is -1 for live streams anyway
        int index = EXTINF.length();
        while (index < line.length() && line.charAt(index) != ',' && !Character.isWhitespace(line.charAt(index))) {
            index++;
        }

        Attributes attributes = parseAttributes(line, index);
        String title = attributes.end() < line.length() ? line.substring(attributes.end() + 1).strip() : "";
        return new ExtInf(attributes.values(), title);
    }

    /**
     * Parses {@code key="value"} attributes starting at the given index, up to the first comma outside of quotes.
     * Unquoted values are accepted too, they end at whitespace or a comma.
     */
    @NotNull
    private static Attributes parseAttributes(@NotNull String line, int start) {
        Map<String, String> values = new HashMap<>();
        int index = start;
        while (index < line.length()) {
            char c = line.charAt(index);
            if (c == ',') {
                break;
            }
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }

            int keyStart = index;
            while (index < line.length() && line.charAt(index) != '=' && line.charAt(index) != ',' && !Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            String key = line.substring(keyStart, index).toLowerCase(Locale.ROOT);
            if (index >= line.length() || line.charAt(index) != '=') {
                // A token without a value, ignore it
                continue;
            }
            index++;

            String value;
            if (index < line.length() && line.charAt(index) == '"') {
                int end = line.indexOf('"', index + 1);
                if (end == -1) {
                    end = line.length();
                }
                value = line.substring(index + 1, end);
                index = Math.min(end + 1, line.length());
            } else {
                int valueStart = index;
                while (index < line.length() && line.charAt(index) != ',' && !Character.isWhitespace(line.charAt(index))) {
                    index++;
                }
                value = line.substring(valueStart, index);
            }
            values.putIfAbsent(key, value);
        }

        return new Attributes(values, index);
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record ExtInf(@NotNull Map<String, String> attributes, @NotNull String title) {
    }

    /**
     * @param values the parsed attributes, with lowercase keys
     * @param end    the index where parsing stopped, which is either a comma or the end of the line
     */
    private record Attributes(@NotNull Map<String, String> values, int end) {
    }

}
