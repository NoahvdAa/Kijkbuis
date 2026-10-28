import {createFileRoute, redirect} from '@tanstack/react-router';
import {loginUrl, safeRedirect} from '#/lib/auth';
import {Brand} from '#/components/Brand';
import {buttonClassName} from '#/components/Button';

export const Route = createFileRoute('/login')({
	validateSearch: (search: Record<string, unknown>): {redirect?: string} => ({
		redirect: safeRedirect(search.redirect),
	}),
	beforeLoad: ({context, search}) => {
		if (context.user) {
			throw redirect({href: search.redirect ?? '/'});
		}
	},
	component: LoginPage,
});

function LoginPage() {
	const {redirect} = Route.useSearch();

	return <div className="grid min-h-dvh place-items-center bg-screen p-6">
		<div className="grid justify-items-center gap-8 text-center">
			<Brand large/>
			<p className="text-muted">Log in to start watching.</p>
			{/* A full page load, since logging in goes through the identity provider */}
			<a href={loginUrl(redirect ?? '/')} className={buttonClassName}>Log in</a>
		</div>
	</div>;
}
