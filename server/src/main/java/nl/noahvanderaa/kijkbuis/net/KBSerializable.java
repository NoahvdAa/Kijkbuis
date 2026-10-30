package nl.noahvanderaa.kijkbuis.net;

import com.google.gson.JsonObject;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

public interface KBSerializable {

    /**
     * Returns this object as its JSON representation.
     * The user requesting the object is passed along, so details can be shown or hidden depending on their privileges.
     *
     * @param user the user accessing the object.
     * @return the object serialized as json
     */
    @NotNull
    JsonObject serialize(@NotNull User user);

}
