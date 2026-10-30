import {createFileRoute, Link, Outlet, redirect, useRouterState} from '@tanstack/react-router';
import {Fragment} from 'react';
import {api} from '#/lib/api';
import {hasPrivilege} from '#/lib/auth';
import type {PluginDetails} from '#/lib/types';
import {MenuError, MenuScreen} from '#/components/MenuScreen';

/**
 * The admin dashboard: a sidebar with its sections, like the settings of a TV. The TV keeps playing in the corner.
 */
export const Route = createFileRoute('/_app/admin')({
	beforeLoad: ({context}) => {
		if (!hasPrivilege(context.user, 'admin')) {
			throw redirect({to: '/'});
		}
	},
	// For the pages plugins add to the menu. Without them, the rest of the admin screens still work.
	loader: () => api<PluginDetails[]>('/api/plugins').catch(() => []),
	component: AdminLayout,
	errorComponent: MenuError,
});

const linkClassName = 'whitespace-nowrap border-b-2 border-transparent px-3 py-2.5 font-mono text-xs uppercase tracking-[0.2em] text-muted '
	+ 'hover:text-fg focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent md:border-b-0 md:border-l-2';
const activeProps = {className: 'border-accent! text-fg! md:bg-raised'};

function AdminLayout() {
	const plugins = Route.useLoaderData();
	const pathname = useRouterState({select: (state) => state.location.pathname});

	return <MenuScreen title="Admin" wide>
		<div className="grid gap-8 md:grid-cols-[11rem_minmax(0,1fr)] md:gap-10">
			{/* Sticks below the TV in the corner when scrolling, rather than sliding under it */}
			<nav aria-label="Admin" className="flex overflow-x-auto border-b border-line md:sticky md:top-[calc(var(--pip-height)+2*var(--pip-margin))] md:flex-col md:self-start md:border-b-0">
				<Link to="/admin" activeOptions={{exact: true}} className={linkClassName} activeProps={activeProps}>Overview</Link>
				<Link to="/admin/playlists" className={linkClassName} activeProps={activeProps}>Playlists</Link>
				<Link to="/admin/users" className={linkClassName} activeProps={activeProps}>Users</Link>
				<Link to="/admin/plugins" activeOptions={{exact: true}} className={linkClassName} activeProps={activeProps}>Plugins</Link>
				{plugins.flatMap((plugin) => plugin.pages.map((page) => {
					// Sub-pages only show while the page or one of them is open
					const path = `/admin/plugins/${plugin.id}/${page.id}`;
					const open = pathname === path || pathname.startsWith(`${path}/`);
					return <Fragment key={`${plugin.id}/${page.id}`}>
						{/* Exact, so it isn't active on its sub-pages, but whatever is filled in in the page's filters doesn't matter */}
						<Link
							to="/admin/plugins/$plugin/$page"
							params={{plugin: plugin.id, page: page.id}}
							activeOptions={{exact: true, includeSearch: false}}
							className={`${linkClassName} ${open ? 'text-fg!' : ''}`}
							activeProps={activeProps}
						>
							{page.title}
						</Link>
						{open && page.subPages.map((subPage) => <Link
							key={subPage.id}
							to="/admin/plugins/$plugin/$page/$subPage"
							params={{plugin: plugin.id, page: page.id, subPage: subPage.id}}
							className={`${linkClassName} md:pl-7`}
							activeProps={activeProps}
						>
							{subPage.title}
						</Link>)}
					</Fragment>;
				}))}
			</nav>
			<div className="min-w-0">
				<Outlet/>
			</div>
		</div>
	</MenuScreen>;
}
