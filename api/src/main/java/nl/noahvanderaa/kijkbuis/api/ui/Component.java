package nl.noahvanderaa.kijkbuis.api.ui;

/**
 * Something a {@link Section} shows. Plugins describe what to show, and Kijkbuis draws it so it fits in with the rest
 * of the page. All text is plain text, so it can't contain HTML or Markdown.
 */
public sealed interface Component permits Text, Stats, ItemList, Table, Form, Filter {
}
