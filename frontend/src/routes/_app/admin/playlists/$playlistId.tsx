import {createFileRoute, Link, useNavigate} from '@tanstack/react-router';
import {type ReactNode, useCallback, useMemo, useState} from 'react';
import {api} from '#/lib/api';
import {errorMessage, playlistStatus, useAction, useReloadEvery} from '#/lib/admin';
import {formatAgo, formatDateTime, useNow} from '#/lib/time';
import type {AdminChannel, EpgChannel, Playlist} from '#/lib/types';
import {AdminSection, ConfirmButton, ErrorText, StatusLight} from '#/components/admin/AdminParts';
import {EpgChannelPicker} from '#/components/admin/EpgChannelPicker';
import {PlaylistForm} from '#/components/admin/PlaylistForm';
import {Button} from '#/components/Button';
import {ChannelLogo} from '#/components/ChannelLogo';
import {fieldClassName, labelClassName} from '#/components/Field';

export const Route = createFileRoute('/_app/admin/playlists/$playlistId')({
    loader: async ({params}) => {
        const [playlist, channels, epgChannels] = await Promise.all([
            api<Playlist>(`/api/playlists/${params.playlistId}`),
            api<AdminChannel[]>(`/api/channels?playlist=${params.playlistId}`),
            api<EpgChannel[]>(`/api/playlists/${params.playlistId}/epg-channels`),
        ]);
        return {playlist, channels, epgChannels};
    },
    component: PlaylistPage,
});

/**
 * One playlist: how it's doing, its settings, and which guide data each of its channels gets.
 */
function PlaylistPage() {
    const {playlist, channels, epgChannels} = Route.useLoaderData();
    const navigate = useNavigate();
    const now = useNow();
    const {status, label} = playlistStatus(playlist);
    // Guide data loads in the background after a refresh or a change
    useReloadEvery(Route.id, 2_000, playlist.epgRefreshing);

    const refreshPlaylist = useCallback(() => api(`/api/playlists/${playlist.id}/refresh`, {method: 'POST'}), [playlist.id]);
    const refresh = useAction(refreshPlaylist);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    async function deletePlaylist() {
        setDeleteError(null);
        try {
            await api(`/api/playlists/${playlist.id}`, {method: 'DELETE'});
            await navigate({to: '/admin/playlists'});
        } catch (e) {
            setDeleteError(errorMessage(e));
        }
    }

    return <div className="grid gap-12">
        <div className="grid gap-5">
            <Link to="/admin/playlists"
                  className={`${labelClassName} w-fit hover:text-fg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent`}>
                ← All playlists
            </Link>
            <AdminSection
                title={<span className="flex items-center gap-3"><StatusLight status={status}
                                                                              label={label}/>{playlist.name}</span>}
                actions={<>
                    <Button small disabled={refresh.pending}
                            onClick={() => refresh.run()}>{refresh.pending ? 'Loading…' : 'Reload now'}</Button>
                    <ConfirmButton label="Delete" confirmLabel="Delete playlist" onConfirm={deletePlaylist}/>
                </>}
            >
                {(refresh.error || deleteError) && <ErrorText>{refresh.error ?? deleteError}</ErrorText>}
                <dl className="grid gap-x-8 gap-y-5 sm:grid-cols-2">
                    <Detail label="Channels">
                        <p>{playlist.channelCount} channels</p>
                        <p className="text-sm text-muted">
                            {playlist.lastRefreshedAt ? `Loaded ${formatAgo(playlist.lastRefreshedAt, now)}` : 'Never loaded'}
                            {' · '}next {formatDateTime(playlist.nextRefreshAt)}
                        </p>
                        {playlist.lastRefreshError && <ErrorText>{playlist.lastRefreshError}</ErrorText>}
                    </Detail>
                    <Detail label="Guide data">
                        <p>{playlist.activeEpgUrl ? (playlist.epgUrl ? 'From the URL you set' : 'From the URL the playlist names') : 'None'}</p>
                        <p className="text-sm text-muted">
                            {playlist.epgRefreshing
                                ? 'Loading…'
                                : playlist.epgLastRefreshedAt ? `Loaded ${formatAgo(playlist.epgLastRefreshedAt, now)}` : playlist.activeEpgUrl ? 'Never loaded' : 'Set a guide data URL below to get programmes'}
                        </p>
                        {playlist.epgLastRefreshError && <ErrorText>{playlist.epgLastRefreshError}</ErrorText>}
                    </Detail>
                </dl>
            </AdminSection>
        </div>

        <AdminSection title="Settings">
            <PlaylistForm playlist={playlist}/>
        </AdminSection>

        {playlist.activeEpgUrl && <ChannelGuideData channels={channels} epgChannels={epgChannels}/>}
    </div>;
}

function Detail({label, children}: { label: string, children: ReactNode }) {
    return <div className="grid content-start gap-1">
        <dt className={labelClassName}>{label}</dt>
        <dd className="grid gap-1">{children}</dd>
    </div>;
}

/**
 * Which EPG channel each channel gets its programmes from. Channels whose tvg-id isn't in the EPG get none, and can be
 * mapped to the right one by hand.
 */
function ChannelGuideData({channels, epgChannels}: { channels: AdminChannel[], epgChannels: EpgChannel[] }) {
    const [search, setSearch] = useState('');
    const [onlyMissing, setOnlyMissing] = useState(false);
    const [picking, setPicking] = useState<AdminChannel | null>(null);

    const epgNames = useMemo(() => new Map(epgChannels.map((epgChannel) => [epgChannel.id, epgChannel.displayName])), [epgChannels]);
    const hasGuideData = useCallback((channel: AdminChannel) => channel.epgChannelId !== null && epgNames.has(channel.epgChannelId), [epgNames]);
    const withGuideData = channels.filter(hasGuideData).length;

    const shown = useMemo(() => {
        const query = search.trim().toLowerCase();
        return channels.filter((channel) => (!onlyMissing || !hasGuideData(channel)) && (!query || channel.name.toLowerCase().includes(query)));
    }, [channels, search, onlyMissing, hasGuideData]);

    return <AdminSection title={`Guide data per channel · ${withGuideData} of ${channels.length}`}>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
            <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Channel name"
                aria-label="Search channels"
                className={`${fieldClassName} min-w-48 flex-1`}
            />
            <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={onlyMissing} onChange={(event) => setOnlyMissing(event.target.checked)}
                       className="size-4 accent-accent"/>
                Only channels without guide data
            </label>
        </div>

        {shown.length > 0
            ? <ul className="divide-y divide-line border-y border-line">
                {shown.map((channel) => <li key={channel.id}
                                            className="flex items-center gap-4 py-3 [contain-intrinsic-size:auto_4rem] [content-visibility:auto]">
                    <ChannelLogo channel={channel} className="size-10 shrink-0"/>
                    <div className="grid min-w-0 flex-1 gap-0.5">
                        <span className="truncate">
                            {channel.channelNumber !== null &&
                                <span className="mr-2 font-mono text-sm text-muted">{channel.channelNumber}</span>}
                            {channel.name}
                        </span>
                        <span className="truncate text-sm">
                            {hasGuideData(channel)
                                ? <span
                                    className="text-muted">{epgNames.get(channel.epgChannelId!)}{channel.epgChannelOverride && ' · set by hand'}</span>
                                : <span className="text-teletext-red">No guide data{channel.epgChannelId && <span
                                    className="font-mono text-xs"> ({channel.epgChannelId} isn't in the EPG)</span>}</span>}
                        </span>
                    </div>
                    <Button small aria-label={`Change guide data for ${channel.name}`}
                            onClick={() => setPicking(channel)}>Change</Button>
                </li>)}
            </ul>
            :
            <p className="text-muted">{channels.length === 0 ? 'This playlist has no channels.' : 'No channels match.'}</p>}

        {picking && <EpgChannelPicker channel={picking} epgChannels={epgChannels} onClose={() => setPicking(null)}/>}
    </AdminSection>;
}
