import {type ErrorComponentProps, useRouter} from '@tanstack/react-router';
import {api} from '#/lib/api';
import {errorMessage} from '#/lib/admin';
import {PLUGIN_HEADERS} from '#/lib/plugins';
import type {PluginPage} from '#/lib/types';
import {ErrorText} from './admin/AdminParts';
import {Button} from './Button';
import {PluginSections} from './PluginSections';

/** What's filled in in a plugin page's filters */
export type Filters = Record<string, string | number | boolean>;

/**
 * Keeps the filters from a page's URL. Numbers and checkboxes come out of it as what they are, the rest as text.
 */
export function validateFilters(search: Record<string, unknown>): Filters {
	return Object.fromEntries(Object.entries(search)
		.filter((entry): entry is [string, string | number | boolean] => ['string', 'number', 'boolean'].includes(typeof entry[1])));
}

/**
 * Has the plugin put a page together, with what's filled in in its filters.
 *
 * @param subPage the sub-page of the page, if it's one of those
 */
export function loadPluginPage(plugin: string, page: string, subPage: string | null, filters: Filters): Promise<PluginPage> {
	// Plugins get every filter as text, like in any URL
	const query = new URLSearchParams(Object.entries(filters).map(([key, value]) => [key, String(value)]));
	const path = [plugin, 'pages', page, ...(subPage !== null ? [subPage] : [])].map(encodeURIComponent).join('/');
	return api<PluginPage>(`/api/plugins/${path}?${query}`, {headers: PLUGIN_HEADERS});
}

/**
 * A page a plugin added to the admin screens, such as a lookup of what everyone watched.
 *
 * @param onFilter opens the page again with what's filled in in its filters
 */
export function PluginPageView({page, onFilter}: {page: PluginPage, onFilter: (filters: Filters) => void}) {
	return <div className="grid gap-12">
		{page.sections.length > 0
			? <PluginSections sections={page.sections} onFilter={onFilter}/>
			: <p className="text-muted">There's nothing on this page right now.</p>}
	</div>;
}

/**
 * Shown in place of a page the plugin couldn't put together, with the rest of the admin screens still around it.
 */
export function PluginPageError({error}: ErrorComponentProps) {
	const router = useRouter();

	return <div className="grid justify-items-start gap-4">
		<ErrorText>{errorMessage(error)}</ErrorText>
		<Button small onClick={() => router.invalidate()}>Try again</Button>
	</div>;
}
