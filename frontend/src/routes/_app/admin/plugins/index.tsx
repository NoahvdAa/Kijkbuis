import {createFileRoute, Link} from '@tanstack/react-router';
import {api} from '#/lib/api';
import type {PluginDetails} from '#/lib/types';
import {AdminSection} from '#/components/admin/AdminParts';
import {ChannelLogo} from '#/components/ChannelLogo';

export const Route = createFileRoute('/_app/admin/plugins/')({
	loader: () => api<PluginDetails[]>('/api/plugins'),
	component: PluginsPage,
});

/**
 * The plugins Kijkbuis enabled. They're jars in the plugins directory next to the config, and only change when
 * Kijkbuis restarts, so there's nothing to manage here.
 */
function PluginsPage() {
	const plugins = Route.useLoaderData();

	return <AdminSection title={`Plugins · ${plugins.length}`}>
		{plugins.length > 0
			? <ul className="divide-y divide-line border-y border-line">
				{plugins.map((plugin) => <li key={plugin.id}><PluginRow plugin={plugin}/></li>)}
			</ul>
			: <p className="text-muted">
				No plugins are enabled. To add one, put its jar in the plugins directory next to the config, and restart Kijkbuis.
			</p>}
	</AdminSection>;
}

function PluginRow({plugin}: {plugin: PluginDetails}) {
	return <div className="flex gap-4 py-4 sm:px-2">
		{/* Shows the plugin's initials when it has no icon */}
		<ChannelLogo channel={{name: plugin.name, logoUrl: plugin.iconUrl}} className="size-12 shrink-0"/>
		<div className="grid min-w-0 flex-1 content-start gap-1">
			<p className="flex flex-wrap items-baseline gap-x-3">
				<span className="font-medium">{plugin.name}</span>
				<span className="font-mono text-xs text-muted">{plugin.version}</span>
			</p>
			{plugin.description && <p className="text-sm text-muted">{plugin.description}</p>}
			<p className="font-mono text-xs text-muted">{plugin.id} · made for Kijkbuis {plugin.apiVersion}</p>
			{plugin.pages.length > 0 && <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
				{plugin.pages.map((page) => <Link
					key={page.id}
					to="/admin/plugins/$plugin/$page"
					params={{plugin: plugin.id, page: page.id}}
					className="text-accent underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
				>
					{page.title}
				</Link>)}
			</p>}
		</div>
	</div>;
}
