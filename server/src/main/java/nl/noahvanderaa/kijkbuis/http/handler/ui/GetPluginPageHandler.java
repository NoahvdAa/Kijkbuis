package nl.noahvanderaa.kijkbuis.http.handler.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.api.ui.Section;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.plugin.UiRegistry;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Puts together a plugin's page or sub-page for an admin. The query of the request has what's filled in in the page's
 * filters.
 */
public class GetPluginPageHandler extends AuthenticatedHandler {

    private static final Logger LOGGER = LogManager.getLogger(GetPluginPageHandler.class);

    public GetPluginPageHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) {
        String plugin = Requests.pathParam(exchange, "plugin");
        String subPage = Requests.pathParam(exchange, "subPage");
        UiRegistry.RegisteredPage page = subPage != null
                ? this.app.uiRegistry().page(plugin, Requests.pathParam(exchange, "page"), subPage)
                : this.app.uiRegistry().page(plugin, null, Requests.pathParam(exchange, "page"));
        if (page == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Not found");
        }

        List<Section> sections;
        try {
            sections = page.provider().sections(user, new QueryFormValues(exchange.getQueryParameters(), Requests.timeZone(exchange)));
        } catch (Exception | LinkageError ex) {
            LOGGER.error("{} failed to put together its page {}", page.plugin().name(), page.id(), ex);
            throw new ApiException(StatusCodes.INTERNAL_SERVER_ERROR, "Something went wrong while putting this page together");
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("plugin", page.plugin().id());
        obj.addProperty("parent", page.parent());
        obj.addProperty("id", page.id());
        obj.addProperty("title", page.title());
        JsonArray items = new JsonArray();
        for (Section section : sections) {
            items.add(SectionJson.serialize(new UiRegistry.PluginSection(page.plugin(), section), true));
        }
        obj.add("sections", items);
        Responses.json(exchange, obj);
    }

}
