import {createFileRoute} from '@tanstack/react-router';
import {loadPluginPage, PluginPageError, PluginPageView, validateFilters} from '#/components/PluginPage';

export const Route = createFileRoute('/_app/admin/plugins/$plugin/$page/')({
	validateSearch: validateFilters,
	loaderDeps: ({search}) => ({search}),
	loader: ({params, deps}) => loadPluginPage(params.plugin, params.page, null, deps.search),
	component: Page,
	errorComponent: PluginPageError,
});

function Page() {
	const navigate = Route.useNavigate();
	return <PluginPageView page={Route.useLoaderData()} onFilter={(search) => navigate({search})}/>;
}
