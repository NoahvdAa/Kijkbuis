package nl.noahvanderaa.kijkbuis.http.handler.stream;

import com.google.gson.JsonArray;
import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.stream.Stream;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Lists what everyone is watching, for admins.
 */
public class ListStreamsHandler extends AuthenticatedHandler {

    public ListStreamsHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) {
        JsonArray streams = new JsonArray();
        for (Stream stream : this.app.streamManager().all()) {
            streams.add(stream.serialize(user));
        }
        Responses.json(exchange, streams);
    }

}
