package nl.noahvanderaa.kijkbuis.epg.xmltv;

import org.jetbrains.annotations.Nullable;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses XMLTV timestamps such as {@code 20261026203000 +0100}. Less precise timestamps like {@code 202610262030}
 * are allowed, and without an offset the time is in UTC.
 */
final class XmltvTime {

    private static final Pattern FORMAT = Pattern.compile("^(\\d{8}(?:\\d{2}){0,3})\\s*(?:([+-]\\d{2}):?(\\d{2}))?$");
    private static final DateTimeFormatter DIGITS = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

    private XmltvTime() {
    }

    /**
     * @return the time, or null if it isn't a valid XMLTV timestamp
     */
    @Nullable
    static Instant parse(@Nullable String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = FORMAT.matcher(value.strip());
        if (!matcher.matches()) {
            return null;
        }

        try {
            String digits = (matcher.group(1) + "000000").substring(0, 14);
            ZoneOffset offset = matcher.group(2) != null
                    ? ZoneOffset.ofHoursMinutes(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(2).charAt(0) + matcher.group(3)))
                    : ZoneOffset.UTC;
            return LocalDateTime.parse(digits, DIGITS).toInstant(offset);
        } catch (DateTimeException ex) {
            return null;
        }
    }

}
