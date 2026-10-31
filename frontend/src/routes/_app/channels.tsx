import {createFileRoute, useNavigate, useRouter} from '@tanstack/react-router';
import {useEffect, useMemo, useState} from 'react';
import {api} from '#/lib/api';
import {formatTime, progressOf, useNow} from '#/lib/time';
import {useTv} from '#/lib/tv';
import type {Channel, Programme} from '#/lib/types';
import {ChannelLogo} from '#/components/ChannelLogo';
import {EpgImage} from '#/components/EpgImage';
import {fieldClassName} from '#/components/Field';
import {MenuError, MenuScreen} from '#/components/MenuScreen';
import {ProgrammeTitle, programmeTitleText} from '#/components/ProgrammeTitle';

export const Route = createFileRoute('/_app/channels')({
    loader: () => api<Channel[]>('/api/channels'),
    component: ChannelsPage,
    errorComponent: MenuError,
});

function ChannelsPage() {
    const channels = Route.useLoaderData();
    const {tiles, activeTileId, watch} = useTv();
    const navigate = useNavigate();
    const router = useRouter();
    const now = useNow();
    const [search, setSearch] = useState('');
    const [group, setGroup] = useState('');

    // Keep what's on up to date
    useEffect(() => {
        const interval = window.setInterval(() => router.invalidate({filter: (match) => match.routeId === Route.id}), 60_000);
        return () => window.clearInterval(interval);
    }, [router]);

    const groups = useMemo(() => [...new Set(channels.map((channel) => channel.group).filter((name) => name !== null))], [channels]);
    const shown = useMemo(() => {
        const query = search.trim().toLowerCase();
        return channels.filter((channel) => (!group || channel.group === group) && (!query || channel.name.toLowerCase().includes(query)));
    }, [channels, search, group]);

    const activeScreen = tiles.findIndex((tile) => tile.id === activeTileId) + 1;
    // Which screens each channel is on, such as "On screen 1, 3", or just "On screen" without multiview
    const screenLabels = useMemo(() => {
        const screens = new Map<string, number[]>();
        tiles.forEach((tile, index) => tile.channelId && screens.set(tile.channelId, [...screens.get(tile.channelId) ?? [], index + 1]));
        return new Map([...screens].map(([channelId, numbers]) => [channelId, tiles.length > 1 ? `On screen ${numbers.join(', ')}` : 'On screen']));
    }, [tiles]);

    function watchChannel(channel: Channel) {
        watch(channel.id);
        navigate({to: '/'});
    }

    return <MenuScreen title="Channels">
        <div className="flex flex-wrap items-end gap-3">
            <label className="grid min-w-48 flex-1 gap-1.5">
                <span className="font-mono text-xs uppercase tracking-[0.2em] text-muted">Search</span>
                <input type="search" value={search} onChange={(event) => setSearch(event.target.value)}
                       placeholder="Channel name" className={fieldClassName}/>
            </label>
            {groups.length > 1 && <label className="grid gap-1.5">
                <span className="font-mono text-xs uppercase tracking-[0.2em] text-muted">Group</span>
                <select value={group} onChange={(event) => setGroup(event.target.value)} className={fieldClassName}>
                    <option value="">All groups</option>
                    {groups.map((name) => <option key={name} value={name}>{name}</option>)}
                </select>
            </label>}
        </div>

        {tiles.length > 1 && <p className="mt-4 font-mono text-xs uppercase tracking-[0.2em] text-accent">
            Picking a channel for screen {activeScreen}
        </p>}

        {shown.length > 0
            ? <ul className="mt-6 divide-y divide-line border-y border-line">
                {shown.map((channel) => <li key={channel.id}
                                            className="[contain-intrinsic-size:auto_5rem] [content-visibility:auto]">
                    <ChannelRow channel={channel} now={now} screenLabel={screenLabels.get(channel.id)}
                                onWatch={() => watchChannel(channel)}/>
                </li>)}
            </ul>
            : <p className="mt-10 text-muted">
                {channels.length === 0 ? 'There are no channels yet. An admin needs to add a playlist first.' : 'No channels match your search.'}
            </p>}
    </MenuScreen>;
}

type ChannelRowProps = {
    channel: Channel
    now: number
    /** Which screens the channel is on, if any */
    screenLabel?: string
    onWatch: () => void
};

/**
 * A landscape picture of what's on, on wider screens. Rows without one keep the space, so the titles line up.
 */
function ProgrammeThumbnail({programme}: { programme: Programme | null }) {
    const image = programme && (programme.backdropUrl ?? programme.iconUrl ?? programme.posterUrl);
    return <div className="hidden aspect-video w-full md:block">
        {image && <EpgImage src={image} lazy className="size-full bg-raised object-cover"/>}
    </div>;
}

function ChannelRow({channel, now, screenLabel, onWatch}: ChannelRowProps) {
    return <button
        type="button"
        onClick={onWatch}
        aria-label={`Watch ${channel.name}${channel.now ? `, now: ${programmeTitleText(channel.now)}` : ''}`}
        className="grid w-full grid-cols-[3rem_1fr] items-center gap-x-4 px-2 py-3 text-left hover:bg-raised focus-visible:bg-raised focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent sm:grid-cols-[3ch_3.5rem_1fr_minmax(0,16rem)] md:grid-cols-[3ch_3.5rem_6rem_1fr_minmax(0,16rem)]"
    >
        <span className="hidden font-mono text-sm text-muted tabular-nums sm:block">{channel.channelNumber}</span>
        <ChannelLogo channel={channel} className="w-full"/>
        <ProgrammeThumbnail programme={channel.now}/>

        <div className="grid min-w-0 gap-1">
            <p className="flex items-baseline gap-3">
                <span className="truncate font-medium">{channel.name}</span>
                {screenLabel && <span
                    className="shrink-0 font-mono text-xs uppercase tracking-[0.15em] text-accent">{screenLabel}</span>}
            </p>
            {channel.now
                ? <>
                    <p className="truncate text-sm text-muted">
                        <span className="font-mono tabular-nums">{formatTime(channel.now.startsAt)}</span>
                        <ProgrammeTitle programme={channel.now}/>
                    </p>
                    <div className="h-0.5 bg-line" aria-hidden="true">
                        <div className="h-full bg-accent" style={{width: `${progressOf(channel.now, now) * 100}%`}}/>
                    </div>
                </>
                :
                <p className="truncate text-sm text-muted">{channel.next ? 'Nothing on right now' : 'No programme information'}</p>}
        </div>

        <p className="hidden truncate text-sm text-muted sm:block">
            {channel.next && <><span className="font-mono tabular-nums">{formatTime(channel.next.startsAt)}</span>
                <ProgrammeTitle programme={channel.next}/></>}
        </p>
    </button>;
}
