/**
 * The shapes of what the backend sends, see docs/frontend-guide.md.
 */

export type Programme = {
	startsAt: string
	endsAt: string
	title: string
	subtitle: string | null
	description: string | null
	category: string | null
	/** An image of any shape */
	iconUrl: string | null
	/** A portrait poster */
	posterUrl: string | null
	/** A landscape background image */
	backdropUrl: string | null
};

export type Channel = {
	id: string
	playlistId: string
	name: string
	group: string | null
	logoUrl: string | null
	channelNumber: number | null
	tvgId: string | null
	epgChannelId: string | null
	now: Programme | null
	next: Programme | null
};

export type Guide = {
	from: string
	to: string
	/** Programmes by channel ID, only for channels that have any in the window */
	channels: Record<string, Programme[]>
};

/** A quality users with the transcode privilege can pick */
export type Profile = {
	id: string
	name: string
	/** The profile is a 16:9 box of this width and height. Channels are scaled down to fit in it, never up. */
	width: number
	height: number
	bitrateKbps: number
};

/** A playlist with the details only admins see */
export type Playlist = {
	id: string
	name: string
	channelCount: number
	url: string
	/** The EPG URL set by an admin, if any */
	epgUrl: string | null
	/** The EPG URL the playlist itself advertises, if any */
	detectedEpgUrl: string | null
	/** The EPG URL that's used: epgUrl, otherwise detectedEpgUrl */
	activeEpgUrl: string | null
	refreshIntervalMinutes: number
	lastRefreshAttemptAt: string | null
	lastRefreshedAt: string | null
	lastRefreshError: string | null
	nextRefreshAt: string
	epgLastRefreshAttemptAt: string | null
	epgLastRefreshedAt: string | null
	epgLastRefreshError: string | null
	epgRefreshing: boolean
	createdAt: string
};

/** A channel with the details only admins see */
export type AdminChannel = Channel & {
	streamUrl: string
	/** The EPG channel an admin mapped the channel to, overriding its tvg-id */
	epgChannelOverride: string | null
};

/** A channel in a playlist's EPG */
export type EpgChannel = {
	id: string
	displayName: string
	iconUrl: string | null
};

export type Privilege = 'admin' | 'transcode';

/** Someone watching a channel, as admins see it */
export type ActiveStream = {
	id: string
	channelId: string
	channelName: string
	channelLogoUrl: string | null
	/** auto or a profile ID */
	quality: string
	transcoding: boolean
	sourceVideoCodec: string | null
	sourceInterlaced: boolean
	startedAt: string
	user: {id: string, username: string, displayName: string}
};

/** A user as admins see them */
export type UserDetails = {
	id: string
	username: string
	displayName: string
	privileges: Privilege[]
	groups: string[]
	createdAt: string
	lastLoginAt: string
	/** How many browsers they're logged in on */
	sessionCount: number
};

/** A section a plugin adds to a page */
export type PluginSection = {
	/** The ID of the plugin it comes from */
	plugin: string
	title: string
	components: PluginComponent[]
};

export type PluginComponent =
	| {type: 'text', text: string}
	| {type: 'stats', stats: {label: string, value: string}[]}
	| {type: 'list', items: PluginListItem[], emptyText: string | null}
	| {type: 'table', columns: string[], rows: PluginTableCell[][], emptyText: string | null}
	| PluginForm
	| PluginFilter;

/** Text, a number, a time, or nothing */
export type PluginTableCell = string | number | {time: string} | null;

export type PluginListItem = {
	title: string
	subtitle: string | null
	detail: string | null
	/** When it happened */
	time: string | null
	imageUrl: string | null
};

/** A form a plugin added, which is saved by sending its values to the plugin's endpoint */
export type PluginForm = {
	type: 'form'
	/** The plugin's endpoint, to POST the values to as a JSON object */
	action: string
	inputs: PluginInput[]
	submitLabel: string
	successText: string
};

export type PluginInput = {name: string, label: string, help: string | null} & (
	| {type: 'text', value: string, placeholder: string | null, required: boolean, maxLength: number | null, multiline: boolean}
	| {type: 'number', value: number | null, min: number | null, max: number | null, unit: string | null, required: boolean}
	| {type: 'checkbox', value: boolean}
	| {type: 'select', value: string | null, options: {value: string, label: string}[]}
	| {type: 'dateRange', value: PluginDateRange | null, required: boolean}
);

/** Days such as 2026-10-30, the last one included */
export type PluginDateRange = {from: string, to: string};

/** A plugin that's enabled, as admins see it */
export type PluginDetails = {
	id: string
	name: string
	version: string
	description: string | null
	/** The Kijkbuis version it's made for */
	apiVersion: string
	iconUrl: string | null
	/** The pages it adds to the admin screens */
	pages: {id: string, title: string, subPages: {id: string, title: string}[]}[]
};

/** Inputs that change what a plugin's page shows, by opening it again with what's filled in */
export type PluginFilter = {
	type: 'filter'
	inputs: PluginInput[]
	submitLabel: string
};

/** A page a plugin adds to the admin screens */
export type PluginPage = {
	plugin: string
	/** The page it's a sub-page of, if any */
	parent: string | null
	id: string
	title: string
	sections: PluginSection[]
};
