package nl.noahvanderaa.kijkbuis.http.handler.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.undertow.server.HttpServerExchange;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.plugin.LoadedPlugin;
import nl.noahvanderaa.kijkbuis.plugin.UiRegistry;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * Lists the enabled plugins, in the order they were enabled, with the pages they add to the admin screens.
 */
public class ListPluginsHandler extends AuthenticatedHandler {

    public ListPluginsHandler(@NotNull KijkbuisApp app) {
        super(app, Privilege.ADMIN);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) {
        JsonArray plugins = new JsonArray();
        for (LoadedPlugin plugin : this.app.pluginManager().plugins()) {
            PluginInfo info = plugin.info();
            JsonObject obj = new JsonObject();
            obj.addProperty("id", info.id());
            obj.addProperty("name", info.name());
            obj.addProperty("version", info.version());
            obj.addProperty("description", info.description());
            obj.addProperty("apiVersion", info.apiVersion());
            // The version changes with the icon, so browsers can keep it for good
            obj.addProperty("iconUrl", plugin.icon() != null ? "/api/plugins/" + info.id() + "/icon?v=" + plugin.icon().version() : null);
            JsonArray pages = new JsonArray();
            for (UiRegistry.RegisteredPage page : this.app.uiRegistry().pages(info.id(), null)) {
                JsonObject item = page(page);
                JsonArray subPages = new JsonArray();
                for (UiRegistry.RegisteredPage subPage : this.app.uiRegistry().pages(info.id(), page.id())) {
                    subPages.add(page(subPage));
                }
                item.add("subPages", subPages);
                pages.add(item);
            }
            obj.add("pages", pages);
            plugins.add(obj);
        }
        Responses.json(exchange, plugins);
    }

    @NotNull
    private static JsonObject page(@NotNull UiRegistry.RegisteredPage page) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", page.id());
        obj.addProperty("title", page.title());
        return obj;
    }

}
