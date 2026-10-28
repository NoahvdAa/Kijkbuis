import {createRootRoute, type ErrorComponentProps, Outlet, useRouter} from '@tanstack/react-router';
import {TanStackRouterDevtoolsPanel} from '@tanstack/react-router-devtools';
import {TanStackDevtools} from '@tanstack/react-devtools';
import {ApiError} from '#/lib/api';
import {loadUser} from '#/lib/auth';
import {Brand} from '#/components/Brand';
import {Button} from '#/components/Button';
import {NotFound} from '#/components/NotFound';

import '../styles.css';

export const Route = createRootRoute({
	beforeLoad: async () => ({user: await loadUser()}),
	component: RootComponent,
	errorComponent: RootError,
	// Unknown pages are normally caught inside the app, so the TV stays on. This is for anything that slips past that.
	notFoundComponent: RootNotFound,
});

function RootComponent() {
	return <>
		<Outlet/>
		<TanStackDevtools
			config={{
				position: 'bottom-right',
			}}
			plugins={[
				{
					name: 'TanStack Router',
					render: <TanStackRouterDevtoolsPanel/>,
				},
			]}
		/>
	</>;
}

function RootNotFound() {
	return <div className="grid min-h-dvh place-items-center bg-screen p-6">
		<NotFound/>
	</div>;
}

/**
 * Shown when Kijkbuis can't be reached, or something else went wrong while loading a page.
 */
function RootError({error}: ErrorComponentProps) {
	const router = useRouter();
	// fetch() fails with a TypeError when the server can't be reached, and the dev server's proxy responds with a 502
	const unreachable = error instanceof TypeError || (error instanceof ApiError && error.status >= 502);

	return <div className="grid min-h-dvh place-items-center bg-screen p-6">
		<div className="grid justify-items-center gap-8 text-center">
			<Brand large/>
			<p className="text-muted">{unreachable ? 'Kijkbuis can\'t be reached right now.' : 'Something went wrong.'}</p>
			<Button onClick={() => router.invalidate()}>Try again</Button>
		</div>
	</div>;
}
