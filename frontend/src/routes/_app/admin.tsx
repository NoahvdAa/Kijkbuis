import {createFileRoute, Link, Outlet, redirect} from '@tanstack/react-router';
import {hasPrivilege} from '#/lib/auth';
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
	component: AdminLayout,
	errorComponent: MenuError,
});

const linkClassName = 'whitespace-nowrap border-b-2 border-transparent px-3 py-2.5 font-mono text-xs uppercase tracking-[0.2em] text-muted '
	+ 'hover:text-fg focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent md:border-b-0 md:border-l-2';
const activeProps = {className: 'border-accent! text-fg! md:bg-raised'};

function AdminLayout() {
	return <MenuScreen title="Admin" wide>
		<div className="grid gap-8 md:grid-cols-[11rem_minmax(0,1fr)] md:gap-10">
			{/* Sticks below the TV in the corner when scrolling, rather than sliding under it */}
			<nav aria-label="Admin" className="flex overflow-x-auto border-b border-line md:sticky md:top-[calc(var(--pip-height)+2*var(--pip-margin))] md:flex-col md:self-start md:border-b-0">
				<Link to="/admin" activeOptions={{exact: true}} className={linkClassName} activeProps={activeProps}>Overview</Link>
				<Link to="/admin/playlists" className={linkClassName} activeProps={activeProps}>Playlists</Link>
				<Link to="/admin/users" className={linkClassName} activeProps={activeProps}>Users</Link>
			</nav>
			<div className="min-w-0">
				<Outlet/>
			</div>
		</div>
	</MenuScreen>;
}
