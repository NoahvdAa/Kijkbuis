package nl.noahvanderaa.kijkbuis.http.handler.ui;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import nl.noahvanderaa.kijkbuis.KijkbuisApp;
import nl.noahvanderaa.kijkbuis.api.ui.FormException;
import nl.noahvanderaa.kijkbuis.http.ApiException;
import nl.noahvanderaa.kijkbuis.http.Requests;
import nl.noahvanderaa.kijkbuis.http.handler.AuthenticatedHandler;
import nl.noahvanderaa.kijkbuis.plugin.UiRegistry;
import nl.noahvanderaa.kijkbuis.user.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Saves a form from a plugin's section, by handing it to the plugin. The path is /api/plugins/&lt;plugin id&gt;/&lt;form
 * action&gt;, and the body a JSON object with a value per input. Responds with 204 once it's saved, or with 400 and the
 * plugin's reason if it can't be.
 */
public class SubmitFormHandler extends AuthenticatedHandler {

    private static final Logger LOGGER = LogManager.getLogger(SubmitFormHandler.class);

    public SubmitFormHandler(@NotNull KijkbuisApp app) {
        super(app);
    }

    @Override
    public void handleRequest(@NotNull User user, @NotNull HttpServerExchange exchange) throws Exception {
        // The action is the rest of the path, which can have slashes in it
        String action = Requests.pathParam(exchange, "*");
        UiRegistry.RegisteredForm form = action != null ? this.app.uiRegistry().form(Requests.pathParam(exchange, "plugin"), action) : null;
        if (form == null) {
            throw new ApiException(StatusCodes.NOT_FOUND, "Not found");
        }

        JsonFormValues values = new JsonFormValues(Requests.jsonBody(exchange), Requests.timeZone(exchange));
        try {
            form.handler().handle(user, values);
        } catch (FormException ex) {
            throw new ApiException(StatusCodes.BAD_REQUEST, Objects.requireNonNullElse(ex.getMessage(), "This can't be saved"));
        } catch (Exception | LinkageError ex) {
            LOGGER.error("{} failed to save the form sent to {}", form.plugin().name(), action, ex);
            throw new ApiException(StatusCodes.INTERNAL_SERVER_ERROR, "Something went wrong while saving this");
        }
        exchange.setStatusCode(StatusCodes.NO_CONTENT);
    }

}
