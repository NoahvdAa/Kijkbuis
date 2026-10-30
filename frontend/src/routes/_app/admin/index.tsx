import {createFileRoute, Link} from '@tanstack/react-router';
import {useCallback} from 'react';
import {api} from '#/lib/api';
import {playlistStatus, useAction, useReloadEvery} from '#/lib/admin';
import {loadPluginSections} from '#/lib/plugins';
import {codecName} from '#/lib/media';
import {AUTO_QUALITY, useProfiles} from '#/lib/profiles';
import {formatAgo, formatDuration, useNow} from '#/lib/time';
import type {ActiveStream, Playlist, Profile} from '#/lib/types';
import {AdminSection, Badge, ErrorText, StatusLight} from '#/components/admin/AdminParts';
import {Button, smallButtonClassName} from '#/components/Button';
import {ChannelLogo} from '#/components/ChannelLogo';
import {PluginSections} from '#/components/PluginSections';

export const Route = createFileRoute('/_app/admin/')({
	loader: async () => {
		const [streams, playlists, sections] = await Promise.all([
			api<ActiveStream[]>('/api/streams'),
			api<Playlist[]>('/api/playlists'),
			loadPluginSections('admin-overview'),
		]);
		return {streams, playlists, sections};
	},
	component: OverviewPage,
});

/**
 * What's going on: who's watching what, and whether the playlists are doing fine.
 */
function OverviewPage() {
	const {streams, playlists, sections} = Route.useLoaderData();
	const now = useNow(5_000);
	const profiles = useProfiles(true);
	useReloadEvery(Route.id, 5_000);

	return <div className="grid gap-12">
		<AdminSection title={`Watching now · ${streams.length}`}>
			{streams.length > 0
				? <ul className="grid gap-3 xl:grid-cols-2">
					{streams.map((stream) => <li key={stream.id}><StreamCard stream={stream} profiles={profiles} now={now}/></li>)}
				</ul>
				: <p className="text-muted">Nobody is watching right now.</p>}
		</AdminSection>

		<AdminSection title="Playlists" actions={<Link to="/admin/playlists" className={smallButtonClassName}>Manage</Link>}>
			{playlists.length > 0
				? <ul className="divide-y divide-line border-y border-line">
					{playlists.map((playlist) => <li key={playlist.id}><PlaylistSummary playlist={playlist} now={now}/></li>)}
				</ul>
				: <p className="text-muted">There are no playlists yet. Add one to get channels.</p>}
		</AdminSection>

		<PluginSections sections={sections}/>
	</div>;
}

function StreamCard({stream, profiles, now}: {stream: ActiveStream, profiles: Profile[], now: number}) {
	const stopStream = useCallback(() => api(`/api/streams/${stream.id}`, {method: 'DELETE'}), [stream.id]);
	const stop = useAction(stopStream);
	const quality = stream.quality === AUTO_QUALITY ? 'Auto' : profiles.find((profile) => profile.id === stream.quality)?.name ?? stream.quality;

	return <div className="flex gap-4 border border-line p-4">
		<ChannelLogo channel={{name: stream.channelName, logoUrl: stream.channelLogoUrl}} className="size-12 shrink-0"/>
		<div className="grid min-w-0 flex-1 content-start gap-2.5">
			<div className="min-w-0">
				<p className="truncate font-mono text-sm uppercase tracking-[0.15em]">{stream.channelName}</p>
				<p className="truncate text-sm text-muted">
					{stream.user.displayName} · for {formatDuration(now - Date.parse(stream.startedAt))}
				</p>
			</div>
			<ul className="flex flex-wrap gap-1.5" aria-label="Playback">
				<Badge>{quality}</Badge>
				{/* Transcoding is what costs CPU */}
				<Badge accent={stream.transcoding}>{stream.transcoding ? 'Transcoding' : 'Direct'}</Badge>
				{stream.sourceVideoCodec && <Badge>{codecName(stream.sourceVideoCodec)}</Badge>}
				{stream.sourceInterlaced && <Badge>Interlaced</Badge>}
			</ul>
			{stop.error && <ErrorText>{stop.error}</ErrorText>}
		</div>
		<div className="shrink-0">
			<Button small danger aria-label={`Stop ${stream.user.displayName}'s stream of ${stream.channelName}`} disabled={stop.pending} onClick={() => stop.run()}>Stop</Button>
		</div>
	</div>;
}

function PlaylistSummary({playlist, now}: {playlist: Playlist, now: number}) {
	const {status, label} = playlistStatus(playlist);

	return <Link
		to="/admin/playlists/$playlistId"
		params={{playlistId: playlist.id}}
		className="flex flex-wrap items-center gap-x-4 gap-y-1 py-3 hover:bg-raised focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent sm:px-2"
	>
		<StatusLight status={status} label={label}/>
		<span className="min-w-0 flex-1 truncate">{playlist.name}</span>
		<span className="text-sm text-muted">{playlist.channelCount} channels</span>
		<span className={`text-sm ${status === 'error' ? 'text-teletext-red' : 'text-muted'}`}>
			{status === 'ok' && playlist.lastRefreshedAt ? `Loaded ${formatAgo(playlist.lastRefreshedAt, now)}` : label}
		</span>
	</Link>;
}
