package nl.noahvanderaa.kijkbuis.http.handler.user;

import com.google.gson.JsonArray;
import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.user.User;
import nl.noahvanderaa.kijkbuis.user.UserDetails;
import org.jetbrains.annotations.NotNull;

/**
 * Lists everyone that ever logged in, for admins.
 */
public class ListUsersHandler extends AuthenticatedHandler {

    public ListUsersHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        JsonArray users = new JsonArray();
        for (UserDetails details : this.app.userManager().list()) {
            users.add(details.serialize(user));
        }
        Responses.json(exchange, users);
    }

}
