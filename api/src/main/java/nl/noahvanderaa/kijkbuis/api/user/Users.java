package nl.noahvanderaa.kijkbuis.api.user;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Everyone who ever logged in to Kijkbuis. Users are never removed, so an ID a plugin stored keeps finding its user.
 * <p>
 * Their names and groups are as of their last login, when Kijkbuis gets them from the identity provider again. Their
 * privileges follow the config as it is now.
 */
public interface Users {

    /**
     * @return the user with this ID, or null if nobody with it ever logged in
     * @throws IllegalStateException if the users can't be read
     */
    @Nullable
    User find(@NotNull UUID id);

    /**
     * @return every user, in order of their display names, such as for a filter that picks one
     * @throws IllegalStateException if the users can't be read
     */
    @NotNull
    List<User> all();

}
