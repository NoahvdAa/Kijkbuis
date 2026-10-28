package nl.noahvanderaa.kijkbuis.epg.xmltv;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * An {@code <image>} of a programme, which newer XMLTV files have next to (or instead of) the {@code <icon>}.
 *
 * @param url    the image's URL
 * @param type   poster, backdrop, still, person or character, or null if not given
 * @param size   1 (small), 2 (medium) or 3 (large), or null if not given
 * @param orient P (portrait) or L (landscape), or null if not given
 */
record XmltvImage(@NotNull String url, @Nullable String type, @Nullable Integer size, @Nullable String orient) {

    /**
     * Posters are shown fairly small, so a medium one is best. Images without a size come before small ones, since
     * they're probably not small.
     */
    private static final List<Integer> POSTER_SIZES = Arrays.asList(2, 3, null, 1);
    /**
     * Backdrops fill a large part of the screen.
     */
    private static final List<Integer> BACKDROP_SIZES = Arrays.asList(3, 2, null, 1);
    private static final int NOT_SUITABLE = -1;

    /**
     * @return the best poster of a programme, or null if it has none
     */
    @Nullable
    static String bestPoster(@NotNull Collection<XmltvImage> images) {
        return best(images, XmltvImage::posterRank);
    }

    /**
     * @return the best background image of a programme: a backdrop, or else a still from it. Null if it has neither.
     */
    @Nullable
    static String bestBackdrop(@NotNull Collection<XmltvImage> images) {
        return best(images, XmltvImage::backdropRank);
    }

    /**
     * @return how well this works as a poster, lower is better, or {@link #NOT_SUITABLE}
     */
    private int posterRank() {
        if (!"poster".equals(this.type) && !(this.type == null && "P".equals(this.orient))) {
            return NOT_SUITABLE;
        }
        int orientation = "L".equals(this.orient) ? 1 : 0;
        return orientation * 10 + POSTER_SIZES.indexOf(this.size);
    }

    /**
     * @return how well this works as a background, lower is better, or {@link #NOT_SUITABLE}
     */
    private int backdropRank() {
        int type;
        if ("backdrop".equals(this.type)) {
            type = 0;
        } else if ("still".equals(this.type)) {
            type = 1;
        } else if (this.type == null && "L".equals(this.orient)) {
            type = 2;
        } else {
            return NOT_SUITABLE;
        }
        int orientation = "P".equals(this.orient) ? 1 : 0;
        return type * 100 + orientation * 10 + BACKDROP_SIZES.indexOf(this.size);
    }

    @Nullable
    private static String best(@NotNull Collection<XmltvImage> images, @NotNull ToIntFunction<XmltvImage> rank) {
        // min() keeps the first of equally good images
        return images.stream()
                .filter((image) -> rank.applyAsInt(image) != NOT_SUITABLE)
                .min(Comparator.comparingInt(rank))
                .map(XmltvImage::url)
                .orElse(null);
    }

}
