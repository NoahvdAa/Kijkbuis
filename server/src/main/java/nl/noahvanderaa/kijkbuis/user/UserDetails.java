package nl.noahvanderaa.kijkbuis.user;

import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.net.KBSerializable;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * A user as admins see them in the list of users.
 *
 * @param user         the user
 * @param createdAt    when they first logged in
 * @param lastLoginAt  when they last logged in
 * @param sessionCount how many browsers they're logged in on
 */
public record UserDetails(@NotNull User user, @NotNull Instant createdAt, @NotNull Instant lastLoginAt, int sessionCount) implements KBSerializable {

    @Override
    @NotNull
    public JsonObject serialize(@NotNull User user) {
        JsonObject obj = this.user.serialize(user);

        obj.addProperty("createdAt", this.createdAt.toString());
        obj.addProperty("lastLoginAt", this.lastLoginAt.toString());
        obj.addProperty("sessionCount", this.sessionCount);

        return obj;
    }

}
