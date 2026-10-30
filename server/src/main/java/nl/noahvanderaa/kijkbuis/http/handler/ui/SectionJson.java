package nl.noahvanderaa.kijkbuis.http.handler.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import nl.noahvanderaa.kijkbuis.api.plugin.PluginInfo;
import nl.noahvanderaa.kijkbuis.api.ui.Checkbox;
import nl.noahvanderaa.kijkbuis.api.ui.Component;
import nl.noahvanderaa.kijkbuis.api.ui.DateRangeInput;
import nl.noahvanderaa.kijkbuis.api.ui.Filter;
import nl.noahvanderaa.kijkbuis.api.ui.Form;
import nl.noahvanderaa.kijkbuis.api.ui.Input;
import nl.noahvanderaa.kijkbuis.api.ui.ItemList;
import nl.noahvanderaa.kijkbuis.api.ui.ListItem;
import nl.noahvanderaa.kijkbuis.api.ui.NumberInput;
import nl.noahvanderaa.kijkbuis.api.ui.Select;
import nl.noahvanderaa.kijkbuis.api.ui.Stats;
import nl.noahvanderaa.kijkbuis.api.ui.Table;
import nl.noahvanderaa.kijkbuis.api.ui.Text;
import nl.noahvanderaa.kijkbuis.api.ui.TextInput;
import nl.noahvanderaa.kijkbuis.plugin.UiRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the sections plugins make into JSON for the frontend, which draws them.
 */
final class SectionJson {

    private static final Set<String> IMAGE_SCHEMES = Set.of("http", "https");

    private SectionJson() {
    }

    /**
     * @param onPage whether the section is on a plugin's own page, which is the only place filters work
     */
    @NotNull
    static JsonObject serialize(@NotNull UiRegistry.PluginSection section, boolean onPage) {
        JsonObject obj = new JsonObject();

        obj.addProperty("plugin", section.plugin().id());
        obj.addProperty("title", section.section().title());

        JsonArray components = new JsonArray();
        for (Component component : section.section().components()) {
            if (component instanceof Filter && !onPage) {
                continue;
            }
            components.add(component(section.plugin(), component));
        }
        obj.add("components", components);

        return obj;
    }

    /**
     * @param plugin the plugin the component comes from
     */
    @NotNull
    private static JsonObject component(@NotNull PluginInfo plugin, @NotNull Component component) {
        JsonObject obj = new JsonObject();

        switch (component) {
            case Text text -> {
                obj.addProperty("type", "text");
                obj.addProperty("text", text.text());
            }
            case Stats stats -> {
                obj.addProperty("type", "stats");
                JsonArray items = new JsonArray();
                for (Stats.Stat stat : stats.stats()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("label", stat.label());
                    item.addProperty("value", stat.value());
                    items.add(item);
                }
                obj.add("stats", items);
            }
            case ItemList list -> {
                obj.addProperty("type", "list");
                JsonArray items = new JsonArray();
                for (ListItem listItem : list.items()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("title", listItem.title());
                    item.addProperty("subtitle", listItem.subtitle());
                    item.addProperty("detail", listItem.detail());
                    item.addProperty("time", listItem.time() != null ? listItem.time().toString() : null);
                    item.addProperty("imageUrl", httpUrl(listItem.imageUrl()));
                    items.add(item);
                }
                obj.add("items", items);
                obj.addProperty("emptyText", list.emptyText());
            }
            case Table table -> {
                obj.addProperty("type", "table");
                JsonArray columns = new JsonArray();
                table.columns().forEach(columns::add);
                obj.add("columns", columns);
                JsonArray rows = new JsonArray();
                for (List<Object> row : table.rows()) {
                    JsonArray cells = new JsonArray();
                    row.forEach((cell) -> cells.add(cell(cell)));
                    rows.add(cells);
                }
                obj.add("rows", rows);
                obj.addProperty("emptyText", table.emptyText());
            }
            case Form form -> {
                obj.addProperty("type", "form");
                obj.addProperty("action", endpointPath(plugin, form.action()));
                JsonArray inputs = new JsonArray();
                for (Input input : form.inputs()) {
                    inputs.add(input(input));
                }
                obj.add("inputs", inputs);
                obj.addProperty("submitLabel", form.submitLabel());
                obj.addProperty("successText", form.successText());
            }
            case Filter filter -> {
                obj.addProperty("type", "filter");
                JsonArray inputs = new JsonArray();
                for (Input input : filter.inputs()) {
                    inputs.add(input(input));
                }
                obj.add("inputs", inputs);
                obj.addProperty("submitLabel", filter.submitLabel());
            }
        }

        return obj;
    }

    /**
     * @return the cell of a table: text and numbers as themselves, and an instant as an object, so the frontend can tell
     * it apart from text
     */
    @NotNull
    private static JsonElement cell(@Nullable Object cell) {
        return switch (cell) {
            case null -> JsonNull.INSTANCE;
            case String text -> new JsonPrimitive(text);
            case Number number -> new JsonPrimitive(number);
            case Instant time -> {
                JsonObject obj = new JsonObject();
                obj.addProperty("time", time.toString());
                yield obj;
            }
            // Table only takes the kinds above
            default -> throw new IllegalArgumentException("A table can't show a " + cell.getClass().getName());
        };
    }

    @NotNull
    private static JsonObject input(@NotNull Input input) {
        JsonObject obj = new JsonObject();

        obj.addProperty("name", input.name());
        obj.addProperty("label", input.label());
        obj.addProperty("help", input.help());
        switch (input) {
            case TextInput text -> {
                obj.addProperty("type", "text");
                obj.addProperty("value", text.value());
                obj.addProperty("placeholder", text.placeholder());
                obj.addProperty("required", text.required());
                obj.addProperty("maxLength", text.maxLength());
                obj.addProperty("multiline", text.multiline());
            }
            case NumberInput number -> {
                obj.addProperty("type", "number");
                obj.addProperty("value", number.value());
                obj.addProperty("min", number.min());
                obj.addProperty("max", number.max());
                obj.addProperty("unit", number.unit());
                obj.addProperty("required", number.required());
            }
            case Checkbox checkbox -> {
                obj.addProperty("type", "checkbox");
                obj.addProperty("value", checkbox.value());
            }
            case Select select -> {
                obj.addProperty("type", "select");
                JsonArray options = new JsonArray();
                for (Select.Option option : select.options()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("value", option.value());
                    item.addProperty("label", option.label());
                    options.add(item);
                }
                obj.add("options", options);
                // The frontend always gets a value it has an option for
                boolean known = select.options().stream().anyMatch((option) -> option.value().equals(select.value()));
                obj.addProperty("value", known ? select.value() : select.options().isEmpty() ? null : select.options().getFirst().value());
            }
            case DateRangeInput range -> {
                obj.addProperty("type", "dateRange");
                if (range.from() != null && range.to() != null) {
                    JsonObject value = new JsonObject();
                    value.addProperty("from", range.from().toString());
                    value.addProperty("to", range.to().toString());
                    obj.add("value", value);
                } else {
                    obj.add("value", JsonNull.INSTANCE);
                }
                obj.addProperty("required", range.required());
            }
        }

        return obj;
    }

    /**
     * @param path the path of the endpoint within the plugin, see {@link Form#Form(String)}
     * @return where the plugin's endpoint is
     */
    @NotNull
    private static String endpointPath(@NotNull PluginInfo plugin, @NotNull String path) {
        return "/api/plugins/" + plugin.id() + "/" + path;
    }

    /**
     * @return the URL if it's an http(s) URL, which is all a page should load images from
     */
    @Nullable
    private static String httpUrl(@Nullable String url) {
        if (url == null) {
            return null;
        }
        try {
            String scheme = URI.create(url).getScheme();
            return scheme != null && IMAGE_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT)) ? url : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}
