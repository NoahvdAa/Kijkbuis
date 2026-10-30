package nl.noahvanderaa.kijkbuis.http.handler.ui;

import com.google.gson.JsonArray;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.api.ui.Slot;
import nl.noahvanderaa.kijkbuis.api.user.Privilege;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.Responses;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.plugin.UiRegistry;
import nl.noahvanderaa.kijkbuis.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lists the sections plugins add to a page, for the user who opened it. The slot in the path is account or
 * admin-overview, and only admins can see the admin overview's.
 */
public class ListSectionsHandler extends AuthenticatedHandler {

    private static final Map<String, Slot> SLOTS = Arrays.stream(Slot.values())
            .collect(Collectors.toUnmodifiableMap(ListSectionsHandler::slotId, Function.identity()));

    public ListSectionsHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) {
        Slot slot = SLOTS.get(Requests.pathParam(exchange, "slot"));
        if (slot == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "There is no such place for plugins to add to");
        }
        if (slot == Slot.ADMIN_OVERVIEW && !user.hasPrivilege(Privilege.ADMIN)) {
            throw new ApiException(StatusCodes.FORBIDDEN, "You are not allowed to do this");
        }

        JsonArray sections = new JsonArray();
        for (UiRegistry.PluginSection section : this.app.uiRegistry().sections(slot, user)) {
            sections.add(SectionJson.serialize(section, false));
        }
        Responses.json(exchange, sections);
    }

    /**
     * @return how the slot is named in URLs, such as admin-overview
     */
    @NotNull
    private static String slotId(@NotNull Slot slot) {
        return slot.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

}
