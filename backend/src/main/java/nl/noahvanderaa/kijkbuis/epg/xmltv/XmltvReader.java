package nl.noahvanderaa.kijkbuis.epg.xmltv;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads the channels and programmes of an XMLTV file one at a time, so even huge files don't need to fit in memory.
 * Gzipped files are decompressed automatically. Entries that can't be understood are skipped.
 */
public final class XmltvReader implements AutoCloseable {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final XMLInputFactory FACTORY = XMLInputFactory.newFactory();
    /**
     * Some EPGs list lots of sizes of every image. Only this many are considered per programme.
     */
    private static final int MAX_IMAGES = 32;

    static {
        // EPGs come from third parties, so don't process DTDs or external entities (XXE)
        FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        FACTORY.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    private final InputStream input;
    private final XMLStreamReader reader;

    private XmltvReader(@NotNull InputStream input, @NotNull XMLStreamReader reader) {
        this.input = input;
        this.reader = reader;
    }

    @NotNull
    public static XmltvReader open(@NotNull InputStream input) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input, BUFFER_SIZE);
        buffered.mark(2);
        boolean gzipped = buffered.read() == 0x1f && buffered.read() == 0x8b;
        buffered.reset();

        InputStream source = gzipped ? new GZIPInputStream(buffered, BUFFER_SIZE) : buffered;
        try {
            return new XmltvReader(source, FACTORY.createXMLStreamReader(source));
        } catch (XMLStreamException ex) {
            source.close();
            throw new IOException("The EPG is not valid XML: " + ex.getMessage(), ex);
        }
    }

    /**
     * @return the next channel or programme, or null at the end of the file
     */
    @Nullable
    public XmltvEntry next() throws IOException {
        try {
            while (this.reader.hasNext()) {
                if (this.reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }

                XmltvEntry entry = switch (this.reader.getLocalName()) {
                    case "channel" -> this.readChannel();
                    case "programme" -> this.readProgramme();
                    default -> null;
                };
                if (entry != null) {
                    return entry;
                }
            }
            return null;
        } catch (XMLStreamException ex) {
            throw new IOException("The EPG is not valid XML: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.reader.close();
        } catch (XMLStreamException ignored) {
            // Closing the input below is what matters
        }
        this.input.close();
    }

    @Nullable
    private XmltvChannel readChannel() throws XMLStreamException {
        String id = blankToNull(this.reader.getAttributeValue(null, "id"));
        String displayName = null;
        String iconUrl = null;

        while (this.nextTag() == XMLStreamConstants.START_ELEMENT) {
            switch (this.reader.getLocalName()) {
                case "display-name" -> displayName = firstNonNull(displayName, this.readText());
                case "icon" -> {
                    iconUrl = firstNonNull(iconUrl, blankToNull(this.reader.getAttributeValue(null, "src")));
                    this.skipElement();
                }
                default -> this.skipElement();
            }
        }

        return id != null ? new XmltvChannel(id, displayName, iconUrl) : null;
    }

    @Nullable
    private XmltvProgramme readProgramme() throws XMLStreamException {
        String channelId = blankToNull(this.reader.getAttributeValue(null, "channel"));
        Instant start = XmltvTime.parse(this.reader.getAttributeValue(null, "start"));
        Instant stop = XmltvTime.parse(this.reader.getAttributeValue(null, "stop"));
        String title = null;
        String subtitle = null;
        String description = null;
        String category = null;
        String iconUrl = null;
        List<XmltvImage> images = new ArrayList<>();

        // Images of the cast are in <credits>, which is skipped, so only the programme's own images are read
        while (this.nextTag() == XMLStreamConstants.START_ELEMENT) {
            switch (this.reader.getLocalName()) {
                case "title" -> title = firstNonNull(title, this.readText());
                case "sub-title" -> subtitle = firstNonNull(subtitle, this.readText());
                case "desc" -> description = firstNonNull(description, this.readText());
                case "category" -> category = firstNonNull(category, this.readText());
                case "icon" -> {
                    iconUrl = firstNonNull(iconUrl, httpUrl(this.reader.getAttributeValue(null, "src")));
                    this.skipElement();
                }
                case "image" -> {
                    XmltvImage image = this.readImage();
                    if (image != null && images.size() < MAX_IMAGES) {
                        images.add(image);
                    }
                }
                default -> this.skipElement();
            }
        }

        if (channelId == null || start == null || title == null || (stop != null && !stop.isAfter(start))) {
            return null;
        }
        return new XmltvProgramme(channelId, start, stop, title, subtitle, description, category, iconUrl,
                XmltvImage.bestPoster(images), XmltvImage.bestBackdrop(images));
    }

    /**
     * Reads an image, such as {@code <image type="poster" size="2" orient="P">https://…</image>}, and moves to its end
     * tag.
     *
     * @return the image, or null if it has no usable URL
     */
    @Nullable
    private XmltvImage readImage() throws XMLStreamException {
        String type = blankToNull(this.reader.getAttributeValue(null, "type"));
        String size = blankToNull(this.reader.getAttributeValue(null, "size"));
        String orient = blankToNull(this.reader.getAttributeValue(null, "orient"));
        String url = httpUrl(this.readText());
        if (url == null) {
            return null;
        }
        return new XmltvImage(url, type, size != null && size.matches("[123]") ? Integer.valueOf(size) : null, orient);
    }

    /**
     * Moves to the start tag of the next child element, or the end tag of the current element. Unlike
     * {@link XMLStreamReader#nextTag()}, stray text between elements is skipped rather than an error.
     */
    private int nextTag() throws XMLStreamException {
        while (true) {
            int event = this.reader.next();
            if (event == XMLStreamConstants.START_ELEMENT || event == XMLStreamConstants.END_ELEMENT) {
                return event;
            }
            if (event == XMLStreamConstants.END_DOCUMENT) {
                throw new XMLStreamException("The document ended inside an element");
            }
        }
    }

    /**
     * Reads the text of the current element, including the text of any nested elements, and moves to its end tag.
     */
    @Nullable
    private String readText() throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            switch (this.reader.next()) {
                case XMLStreamConstants.START_ELEMENT -> depth++;
                case XMLStreamConstants.END_ELEMENT -> depth--;
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text.append(this.reader.getText());
                default -> {
                }
            }
        }
        return blankToNull(text.toString());
    }

    /**
     * Skips the current element, including anything nested in it, and moves to its end tag.
     */
    private void skipElement() throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int event = this.reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    @Nullable
    private static String firstNonNull(@Nullable String current, @Nullable String candidate) {
        return current != null ? current : candidate;
    }

    /**
     * @return the URL if it's an http(s) URL, otherwise null. Images end up in the browser, so nothing else is let
     * through.
     */
    @Nullable
    private static String httpUrl(@Nullable String value) {
        String url = blankToNull(value);
        return url != null && (url.startsWith("http://") || url.startsWith("https://")) ? url : null;
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

}
