package nl.noahvanderaa.kijkbuis.epg.xmltv;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A programme. Where an element appears several times (e.g. titles in different languages), the first one is used.
 *
 * @param channelId   the ID of the channel the programme airs on
 * @param start       when the programme starts
 * @param stop        when the programme ends, which XMLTV allows leaving out
 * @param title       the title
 * @param subtitle    the sub-title, such as the episode name
 * @param description the description
 * @param category    the first category
 * @param iconUrl     the first icon
 * @param posterUrl   the best poster, see {@link XmltvImage#bestPoster}
 * @param backdropUrl the best background image, see {@link XmltvImage#bestBackdrop}
 */
public record XmltvProgramme(
        @NotNull String channelId,
        @NotNull Instant start,
        @Nullable Instant stop,
        @NotNull String title,
        @Nullable String subtitle,
        @Nullable String description,
        @Nullable String category,
        @Nullable String iconUrl,
        @Nullable String posterUrl,
        @Nullable String backdropUrl
) implements XmltvEntry {
}
