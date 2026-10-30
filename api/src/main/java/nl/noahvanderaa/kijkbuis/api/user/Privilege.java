package nl.noahvanderaa.kijkbuis.api.user;

/**
 * Privileges are granted to users based on their SSO groups, or to everyone, see the [auth.privileges] section of the
 * config.
 */
public enum Privilege {
    /**
     * Allows the user to manage Kijkbuis, such as playlists and EPG sources.
     * Admins are granted every other privilege as well.
     */
    ADMIN,
    /**
     * Allows the user to transcode a stream into a different quality or codec.
     * When this privilege is not granted, the user can only watch streams in their original quality and codec,
     * without any transcoding. If the codec is not supported the user simply cannot watch the stream.
     */
    TRANSCODE
}
