package nl.noahvanderaa.kijkbuis.http.handler.stream;

import com.google.gson.JsonArray;
import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.config.KijkbuisConfig;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Lists the qualities users can pick when starting a stream. Picking one requires the transcode privilege.
 */
public class ListProfilesHandler extends AuthenticatedHandler {

    public ListProfilesHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) {
        JsonArray profiles = new JsonArray();
        for (KijkbuisConfig.TranscodeProfile profile : this.app.config().transcoding().profiles()) {
            profiles.add(profile.serialize(user));
        }
        Responses.json(exchange, profiles);
    }

}
