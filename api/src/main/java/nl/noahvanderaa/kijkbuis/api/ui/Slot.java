package nl.noahvanderaa.kijkbuis.api.ui;

/**
 * A place on Kijkbuis' pages where plugins can add sections.
 */
public enum Slot {
    /**
     * The account page, which every user has. Sections here are about the viewer themselves.
     */
    ACCOUNT,
    /**
     * The admin overview, which only admins see.
     */
    ADMIN_OVERVIEW
}
