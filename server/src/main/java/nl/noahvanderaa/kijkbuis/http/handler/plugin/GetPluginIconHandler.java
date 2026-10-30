package nl.noahvanderaa.kijkbuis.http.handler.plugin;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.plugin.LoadedPlugin;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Serves the icon of an enabled plugin, from its jar.
 */
public class GetPluginIconHandler extends AuthenticatedHandler {

    public GetPluginIconHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) {
        LoadedPlugin plugin = this.app.pluginManager().find(Requests.pathParam(exchange, "plugin"));
        if (plugin == null || plugin.icon() == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Not found");
        }
        Responses.image(exchange, plugin.icon().contentType(), plugin.icon().data());
    }

}
