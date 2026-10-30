# Writing Kijkbuis plugins

Plugins add features to Kijkbuis without changing Kijkbuis itself. A plugin is a jar in the `plugins` directory next
to the config (`/data/plugins` in Docker), which Kijkbuis loads when it starts. Restart Kijkbuis after adding,
updating or removing one.

Plugins run inside Kijkbuis and can do anything it can, so only install plugins you trust.

## Setting up a project

The API isn't published anywhere yet, so plugins live in this repository for now. Add the project to
`settings.gradle.kts` and depend on the API like `plugin-watchhistory` does:

```kotlin
dependencies {
    // Kijkbuis provides the API when it loads the plugin
    compileOnly(project(":api"))
}
```

## kijkbuis-plugin.json

Every plugin has a `kijkbuis-plugin.json` in the root of its jar, so in `src/main/resources`:

```json
{
  "id": "example",
  "name": "Example",
  "version": "1.0.0",
  "description": "Does an example of something.",
  "icon": "icon.svg",
  "main": "com.example.kijkbuis.ExamplePlugin",
  "apiVersion": "0.0"
}
```

| Field         | What it is                                                                                     |
|---------------|------------------------------------------------------------------------------------------------|
| `id`          | A unique ID: lowercase letters, digits and underscores, starting with a letter. At most 32.    |
| `name`        | The name people see, such as in the log                                                        |
| `version`     | The plugin's own version                                                                       |
| `description` | Optional: what the plugin does, in a sentence or two                                           |
| `icon`        | Optional: the path of an image in the jar, such as `icon.svg`. Square, and at most 1 MB.       |
| `main`        | The plugin's main class                                                                        |
| `apiVersion`  | The Kijkbuis version the plugin is made for, as `major.minor`                                  |

Admins see the enabled plugins with their name, version, description and icon on the Plugins page of the admin
screens. The icon can be a PNG, JPEG, GIF, WebP or SVG. If it can't be used, Kijkbuis logs why and shows the plugin's
initials instead.

The API and Kijkbuis share a version (see `gradle.properties`). A plugin runs on the Kijkbuis version it's made for,
and on newer ones with the same major version: only a new major version changes the API in a way that breaks plugins.

Kijkbuis skips jars it can't load and says why in the log, then starts without them.

## The main class

The main class implements `KijkbuisPlugin`, and needs a public constructor without parameters:

```java
public class ExamplePlugin implements KijkbuisPlugin {

    @Override
    public void onEnable(PluginContext context) {
        context.logger().log(System.Logger.Level.INFO, "Hello from {0}", context.info().name());
        context.events().register(new SportsListener());
    }

    @Override
    public void onDisable() {
    }

}
```

- `onEnable` is called when Kijkbuis starts, before it handles any requests. If it throws, the plugin is disabled
  again.
- `onDisable` is called when Kijkbuis stops, after the last streams stopped, so the plugin hears about those first.
- The `PluginContext` is what the plugin can do in Kijkbuis: register listeners, log, store data, look up
  programmes and users, add to pages and schedule tasks. Keep it around if you need it later.
- `context.logger()` logs to Kijkbuis' log with the plugin's name. It's a `System.Logger`, so placeholders look like
  `{0}`.

## Events

Events work like Bukkit's. A listener is a class with handlers: methods annotated with `@EventHandler` that take a
single event. A handler gets every event of the type it takes, including subclasses.

```java
public class SportsListener implements Listener {

    @EventHandler
    public void onStreamStarting(StreamStartingEvent event) {
        if ("Sports".equals(event.channel().groupTitle()) && !event.user().groups().contains("sports")) {
            event.cancel("Sports channels are only for the sports group");
        }
    }

}
```

### Priorities

Handlers run from the lowest priority to the highest, so higher priorities have the final say. Handlers with the same
priority run in the order their listeners were registered.

| Priority            | Use it to                                                         |
|---------------------|-------------------------------------------------------------------|
| `LOWEST`, `LOW`     | Make a decision other plugins may override                        |
| `NORMAL`            | The default                                                       |
| `HIGH`, `HIGHEST`   | Have the final say                                                |
| `MONITOR`           | See what came of the event. Never change the event at this point. |

### Cancelling

Events that are called before something happens implement `Cancellable`. Cancelling one stops it from happening.
Handlers that run later still get the event and can let it go ahead after all with `setCancelled(false)`, unless they
skip cancelled events with `@EventHandler(ignoreCancelled = true)`.

### Things to know

- There's no main thread. Events are called on the thread that caused them, so handlers must be thread-safe. They
  should also be quick, since whatever caused the event waits for them.
- A handler that throws is logged, and the other handlers still run.
- Plugins can't call events themselves.

### Available events

| Event                 | Called when                                                                | Cancellable                     |
|-----------------------|----------------------------------------------------------------------------|---------------------------------|
| `StreamStartingEvent` | Someone wants to watch a channel, before the stream starts                 | Yes, the viewer sees the reason |
| `StreamStartedEvent`  | The stream started, and the viewer's player is about to play it            | No                              |
| `StreamStoppedEvent`  | A stream stopped, with why: the viewer, being idle, an admin or a shutdown | No                              |

A stream can still fail to start after its `StreamStartingEvent`, such as when the channel is down. Streams that do
start get a `StreamStartedEvent`, and later a `StreamStoppedEvent`, always in that order. So to keep track of who's
watching what, use those two. Every player has its own stream, so each multiview screen is a stream, and switching
quality stops one stream and starts another.

## Storing data

Every plugin gets an SQLite database of its own, in `plugins/<id>/<id>.db`. `context.database()` is a `DataSource`
for it, with a small pool of connections, so close connections when you're done with them:

```java
try (Connection connection = context.database().getConnection();
     PreparedStatement statement = connection.prepareStatement("INSERT INTO watched (user_id, title) VALUES (?, ?)")) {
    ...
}
```

Plugins can't reach Kijkbuis' own database. Store IDs, such as a user's or a channel's, and look things up through the
API instead.

### Migrations

The plugin creates its tables with migrations: SQL scripts in the `migrations` directory of its jar
(`src/main/resources/migrations`). Kijkbuis runs the ones that didn't run yet before it enables the plugin, in
alphabetical order. A script can have several statements.

```sql
-- migrations/2026-10-28_12-00_Init.sql
CREATE TABLE watched (
    user_id CHAR(36) NOT NULL,
    title TEXT NOT NULL
);
```

- Never change or rename a migration that has run somewhere. Add a new one instead.
- New migrations have to come after the existing ones alphabetically, or they're skipped. Starting their names with
  the date and time, like Kijkbuis does, takes care of that.
- The migrations that run together run in one transaction. If one fails, none of them happened, and the plugin isn't
  enabled. Don't start or end transactions in the scripts themselves.
- Kijkbuis keeps track of the migrations that ran in a table called `schemashift_meta`, so don't use that name.

Removing a plugin leaves its directory behind. Delete it to get rid of the plugin's data too.

## Scheduling tasks

`context.scheduler()` runs tasks later, or every so often, like Bukkit's scheduler. Times are `Duration`s rather than
ticks:

```java
// Right away, without waiting for it, such as from an event handler
context.scheduler().run(() -> saveEntry(entry));

// Once, in 10 seconds
context.scheduler().runLater(Duration.ofSeconds(10), () -> cleanUp());

// Every minute, starting right away
Task task = context.scheduler().runRepeating(Duration.ZERO, Duration.ofMinutes(1), () -> saveProgress());
task.cancel();

// A task that takes its own Task can cancel itself
context.scheduler().runRepeating(Duration.ZERO, Duration.ofSeconds(5), (self) -> {
    if (importNextBatch() == 0) {
        self.cancel();
    }
});
```

- Every run gets a thread of its own, so a task can block (on the database, say) without holding up anything else.
  It does have to be thread-safe.
- A repeating task never runs twice at once. If a run takes longer than the period, the next one starts right after
  it, without making up for the runs it missed.
- A task that throws is logged. A repeating one keeps running.
- Tasks run at the same time as each other, so two tasks started one after the other can finish in either order.
  Keep that in mind when one writes what the other updates, such as a stream's start and its end.
- When the plugin is disabled, its tasks are cancelled first. Runs that are busy get 5 seconds to finish, then
  `onDisable` is called. Tasks that were still waiting don't run, but `run` starts tasks before it returns, so work an
  event handler hands off that way isn't lost. When Kijkbuis stops, that includes the `StreamStoppedEvent`s of the
  last streams.

## The guide

`context.guide()` has the programmes from the EPG:

```java
// What's on now, and after that. Either can be null, such as for a channel without an EPG.
Programme now = context.guide().now(channel);
Programme next = context.guide().next(channel);

// Everything that aired on the channel at some point in a time window, in order
List<Programme> programmes = context.guide().programmes(stream.channel(), stream.startedAt(), Instant.now());
```

Kijkbuis keeps programmes for up to two weeks ahead, and for 12 hours after they ended. `next` is only looked for in
the next 12 hours. The image URLs come straight from the EPG, so browsers may not be able to reach them.

## Users

Events and pages hand plugins the users involved, but a plugin that stores user IDs can look them up again with
`context.users()`:

```java
// Null if nobody with this ID ever logged in
User user = context.users().find(userId);

// Everyone who ever logged in, in order of their display names, such as for a filter that picks one
Select who = new Select("user", "User").option("", "Anyone");
for (User user : context.users().all()) {
    who.option(user.id().toString(), user.displayName());
}
```

- Users are never removed, so a stored ID keeps finding its user.
- Names and groups are as of the user's last login, when Kijkbuis gets them from the identity provider again.
  Privileges follow the config as it is now.
- Both read Kijkbuis' database, and throw an `IllegalStateException` if that fails.

## Adding to pages

Plugins can add sections to some of Kijkbuis' pages. They don't send HTML: they describe what to show with a few
components, and Kijkbuis draws them so they fit in. Register a `SectionProvider` for a slot, which is asked what to
show every time someone opens the page:

```java
context.ui().register(Slot.ACCOUNT, (viewer) -> List.of(new Section("Recently watched",
        new Stats().add("This week", "12 h").add("Channels", "4"),
        new ItemList()
                .emptyText("You haven't watched anything yet")
                .add(new ListItem("The news").subtitle("NPO 1").detail("25 min").time(watchedAt).imageUrl(backdropUrl))
)));
```

| Slot             | Where                                            |
|------------------|--------------------------------------------------|
| `ACCOUNT`        | The account page, about the viewer themselves     |
| `ADMIN_OVERVIEW` | The admin overview, which only admins see         |

| Component  | Shows                                                                                                |
|------------|------------------------------------------------------------------------------------------------------|
| `Text`     | A paragraph. Line breaks are kept.                                                                   |
| `Stats`    | A row of figures with a label each. Format the figures yourself, such as `12 h`.                     |
| `ItemList` | Rows with a title, and optionally a subtitle, a short detail, a time and a 16:9 thumbnail. With `emptyText` for when it has no rows. |
| `Table`    | Rows with the same columns. See below.                                                               |
| `Form`     | Inputs to fill in, and a button that saves them to the plugin's endpoint. See below.                |

### Tables

```java
Table table = new Table("User", "Plays", "Hours", "Last watched").emptyText("Nobody watched anything yet");
for (UserStats stats : statsPerUser) {
    table.row(stats.name(), stats.plays(), stats.hours(), stats.lastWatchedAt());
}
```

Each cell is text, a number, an `Instant` or `null` for an empty cell. Numbers are shown in the viewer's own format
(such as 1,234), and columns of them line up on the right. Instants are shown in the viewer's own time zone. A row
with more or fewer cells than columns, or a cell of another kind, throws right away. On narrow screens, wide tables
scroll by themselves.

### Forms

A form lets people change something, such as the plugin's settings. Saving it sends what's filled in to a handler
of the plugin, which the form names with its action:

```java
new Form("settings")
        .add(new NumberInput("retentionDays", "Keep history for").value(settings.retentionDays()).min(1).max(365).unit("days").required(true))
        .add(new Checkbox("enabled", "Keep a history").value(settings.enabled()))
        .add(new Select("visibility", "Who sees it").option("me", "Only me").option("admins", "Me and admins").value(settings.visibility()))
        .submitLabel("Save settings");
```

Register the handler for the action, usually in `onEnable`:

```java
context.ui().handleForm("settings", (viewer, values) -> {
    if (!viewer.hasPrivilege(Privilege.ADMIN)) {
        throw new FormException("Only admins can change these settings");
    }
    Long days = values.number("retentionDays");
    if (days == null || days < 1 || days > 365) {
        throw new FormException("Keep history for must be between 1 and 365 days");
    }
    saveSettings(days, values.checked("enabled"), values.string("visibility"));
});
```

`values` has what was filled in, by the names of the inputs:

| Input            | Read it with             | Gives                                  |
|------------------|--------------------------|----------------------------------------|
| `TextInput`      | `values.string(name)`    | The text, which is empty if left empty |
| `NumberInput`    | `values.number(name)`    | A `Long`, or `null` if left empty      |
| `Checkbox`       | `values.checked(name)`   | Whether it's checked                   |
| `Select`         | `values.string(name)`    | The value of the picked option         |
| `DateRangeInput` | `values.dateRange(name)` | A `DateRange`, or `null` if left empty |

- Returning means it's saved. The viewer sees the form's success text, and the page loads again with the new values.
- Throw a `FormException` to tell the viewer why it can't be saved. Any other exception is logged, and the viewer only
  hears that something went wrong.
- Check everything again in the handler, including whether the viewer may save it. The browser checks `required`,
  `min`, `max` and `maxLength`, but anyone who's logged in can send anything. Values that are missing or of another
  kind than the input's come back as `null`, or `false` for checkboxes.
- A `DateRange` has its first and last day as `from` and `to`, both included. They're the viewer's days, so it also
  has their time zone: `startsAt()` is when the first day starts for them, and `endsAt()` when the last one ends, which
  is no longer in the range. Look things up between those, or check one with `contains(instant)`.
  `values.timeZone()` is the viewer's time zone too, for days such as today.
- Handlers are called on the request's thread, so they must be thread-safe.
- Handlers only get the values, not the HTTP request itself. Handling requests fully is for a later version of the
  API.

### Pages of its own

A plugin can also add pages to the admin screens, such as a lookup of what everyone watched. They get a link in the
admin menu, only admins can open them, and they're made of the same sections and components:

```java
context.ui().registerPage("playback", "Playback", (viewer, filters) -> {
    long days = Objects.requireNonNullElse(filters.number("days"), 7L);
    String user = filters.string("user");
    return List.of(new Section("What was watched",
            new Filter()
                    .add(new NumberInput("days", "Look back").value(days).min(1).max(90).unit("days"))
                    .add(new TextInput("user", "User").value(user != null ? user : "").placeholder("Anyone")),
            lookUp(days, user)
    ));
});
```

- The page's URL is `/admin/plugins/<plugin id>/<page id>`. Page IDs are lowercase letters, digits and dashes.
- A page can have sub-pages, such as one for each part of a report. Their links sit right below the page's in the
  menu, and only show while the page or one of its sub-pages is open. They work the same as pages, at
  `/admin/plugins/<plugin id>/<page id>/<sub-page id>`, but can't have sub-pages of their own:

  ```java
  context.ui().registerPage("playback", "Playback", this::overview)
          .subPage("users", "Users", this::users)
          .subPage("channels", "Channels", this::channels);
  ```
- A `Filter` holds inputs that change what the page shows. Applying it opens the page again with what's filled in,
  which the provider gets as `filters`, read the same way as a form's values. What's filled in is in the URL, so a
  lookup can be bookmarked, and the back button goes to the one before.
- When the page is first opened, nothing is filled in. Pick the defaults then, and put them in the filter's inputs, so
  the admin sees what the page is showing. Do the same with what was filled in: the filter shows what the plugin says.
  For a date range, such as the last week:

  ```java
  DateRange period = filters.dateRange("period");
  if (period == null) {
      LocalDate today = LocalDate.now(filters.timeZone());
      period = new DateRange(today.minusDays(6), today, filters.timeZone());
  }
  new Filter().add(new DateRangeInput("period", "Between").value(period));
  ```
- Filters only work on a plugin's own pages. In sections elsewhere, they're left out.
- A provider that throws is logged, and the admin sees the page couldn't be put together.

### Things to know

- Everything is plain text, so there's nothing to escape.
- Times are shown in the viewer's own time zone, so pass an `Instant` rather than formatting it yourself.
- Only http(s) image URLs are used.
- Providers are called on the request's thread, so they must be thread-safe and quick. One that throws is logged, and
  the page is shown without its sections.
- Sections show up in the order plugins were enabled, then in the order their providers were registered.

## What plugins can use

Each plugin gets a class loader that only sees the JDK and the API (`nl.noahvanderaa.kijkbuis.api`). Kijkbuis' own
classes and libraries are hidden.

To use a library, bundle it in the plugin's jar. There's no need to relocate it: plugins can't see each other's
classes or Kijkbuis' libraries, so different versions of a library don't clash.

The Docker image only has the parts of the JDK that Kijkbuis uses itself. That's most of it, but a plugin that needs a
module that isn't there (such as `jdk.httpserver`) can't run in the image.
