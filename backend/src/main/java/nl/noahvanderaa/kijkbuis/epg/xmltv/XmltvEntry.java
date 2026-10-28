package nl.noahvanderaa.kijkbuis.epg.xmltv;

/**
 * Something read from an XMLTV file: either a channel or a programme.
 */
public sealed interface XmltvEntry permits XmltvChannel, XmltvProgramme {
}
