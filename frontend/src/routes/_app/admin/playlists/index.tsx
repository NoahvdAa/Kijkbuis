import {createFileRoute, Link, useNavigate} from '@tanstack/react-router';
import {useCallback, useState} from 'react';
import {api} from '#/lib/api';
import {playlistStatus, useAction, useReloadEvery} from '#/lib/admin';
import {formatAgo, useNow} from '#/lib/time';
import type {Playlist} from '#/lib/types';
import {AdminSection, ErrorText, StatusLight} from '#/components/admin/AdminParts';
import {PlaylistForm} from '#/components/admin/PlaylistForm';
import {Button} from '#/components/Button';

export const Route = createFileRoute('/_app/admin/playlists/')({
    loader: () => api<Playlist[]>('/api/playlists'),
    component: PlaylistsPage,
});

/**
 * The playlists the channels come from: add new ones, and see how the existing ones are doing.
 */
function PlaylistsPage() {
    const playlists = Route.useLoaderData();
    const navigate = useNavigate();
    const now = useNow();
    const [adding, setAdding] = useState(playlists.length === 0);
    // Guide data loads in the background after a refresh
    useReloadEvery(Route.id, 2_000, playlists.some((playlist) => playlist.epgRefreshing));

    return <div className="grid gap-10">
        {adding && <AdminSection title="Add a playlist">
            <PlaylistForm
                onSaved={(playlist) => navigate({
                    to: '/admin/playlists/$playlistId',
                    params: {playlistId: playlist.id}
                })}
                onCancel={playlists.length > 0 ? () => setAdding(false) : undefined}
            />
        </AdminSection>}

        <AdminSection title="Playlists"
                      actions={!adding && <Button small onClick={() => setAdding(true)}>Add playlist</Button>}>
            {playlists.length > 0
                ? <ul className="divide-y divide-line border-y border-line">
                    {playlists.map((playlist) => <li key={playlist.id}><PlaylistRow playlist={playlist} now={now}/>
                    </li>)}
                </ul>
                : <p className="text-muted">There are no playlists yet.</p>}
        </AdminSection>
    </div>;
}

function PlaylistRow({playlist, now}: { playlist: Playlist, now: number }) {
    const {status, label} = playlistStatus(playlist);
    const refreshPlaylist = useCallback(() => api(`/api/playlists/${playlist.id}/refresh`, {method: 'POST'}), [playlist.id]);
    const refresh = useAction(refreshPlaylist);

    return <div className="flex flex-wrap items-center gap-x-4 gap-y-2 py-4">
        <StatusLight status={status} label={label}/>
        <div className="grid min-w-0 flex-1 gap-0.5">
            <Link
                to="/admin/playlists/$playlistId"
                params={{playlistId: playlist.id}}
                className="w-fit truncate hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
                {playlist.name}
            </Link>
            {/* Just the host: the full URL usually contains the provider login */}
            <span className="truncate font-mono text-xs text-muted">{hostOf(playlist.url)}</span>
        </div>
        <div className="grid justify-items-end gap-0.5 text-right text-sm">
            <span>{playlist.channelCount} channels</span>
            <span className={status === 'error' ? 'text-teletext-red' : 'text-muted'}>
                {status === 'ok' && playlist.lastRefreshedAt ? `Loaded ${formatAgo(playlist.lastRefreshedAt, now)}` : label}
            </span>
        </div>
        <Button small disabled={refresh.pending}
                onClick={() => refresh.run()}>{refresh.pending ? 'Loading…' : 'Reload'}</Button>
        {refresh.error && <div className="basis-full"><ErrorText>{refresh.error}</ErrorText></div>}
    </div>;
}

function hostOf(url: string): string {
    try {
        return new URL(url).host;
    } catch {
        return url;
    }
}
