package nl.noahvanderaa.kijkbuis.api.user;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Someone who logged in to Kijkbuis through SSO.
 */
public interface User {

    /**
     * @return the user's ID, which never changes
     */
    @NotNull
    UUID id();

    /**
     * @return the username, from the identity provider
     */
    @NotNull
    String username();

    /**
     * @return the display name, from the identity provider
     */
    @NotNull
    String displayName();

    /**
     * @return the user's groups in the identity provider, as of their last login
     */
    @NotNull
    Set<String> groups();

    /**
     * @return the privileges the user's groups grant them. Admins have every privilege.
     */
    @NotNull
    Set<Privilege> privileges();

    default boolean hasPrivilege(@NotNull Privilege privilege) {
        return this.privileges().contains(privilege);
    }

}
