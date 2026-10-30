package nl.noahvanderaa.kijkbuis.http.handler.user;

import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

public class GetCurrentUserHandler extends AuthenticatedHandler {

    public GetCurrentUserHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) {
        Responses.json(exchange, user.serialize(user));
    }

}
