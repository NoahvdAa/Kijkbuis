package nl.noahvanderaa.kijkbuis.user;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.net.KBSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A user that logged in through SSO.
 *
 * @param id          the Kijkbuis user ID
 * @param username    the username, taken from the identity provider
 * @param displayName the display name, taken from the identity provider
 * @param groups      the user's groups, as of their last login
 * @param privileges  the privileges the user's groups grant them, with admin already expanded to every privilege
 */
public record User(
        @NotNull UUID id,
        @NotNull String username,
        @NotNull String displayName,
        @NotNull Set<String> groups,
        @NotNull Set<Privilege> privileges
) implements KBSerializable {

    public boolean hasPrivilege(@NotNull Privilege privilege) {
        return this.privileges.contains(privilege);
    }

    @Override
    @NotNull
    public JsonObject serialize(@NotNull User user) {
        JsonObject obj = new JsonObject();

        obj.addProperty("id", this.id.toString());
        obj.addProperty("username", this.username);
        obj.addProperty("displayName", this.displayName);

        JsonArray privileges = new JsonArray();
        this.privileges.forEach((privilege) -> privileges.add(privilege.name().toLowerCase(Locale.ROOT)));
        obj.add("privileges", privileges);

        if (user.id.equals(this.id) || user.hasPrivilege(Privilege.ADMIN)) {
            JsonArray groups = new JsonArray();
            this.groups.forEach(groups::add);
            obj.add("groups", groups);
        }

        return obj;
    }

}
