import {useCallback, useId, useMemo, useRef, useState} from 'react';
import {api} from '#/lib/api';
import {useAction} from '#/lib/admin';
import {useDismiss} from '#/lib/dismiss';
import type {AdminChannel, EpgChannel} from '#/lib/types';
import {fieldClassName} from '../Field';
import {ErrorText} from './AdminParts';

/** EPGs can have thousands of channels, so only the best matches are listed */
const MAX_RESULTS = 100;

type EpgChannelPickerProps = {
    channel: AdminChannel
    epgChannels: EpgChannel[]
    onClose: () => void
};

/**
 * Picks the EPG channel a channel gets its guide data from, for when its tvg-id doesn't match the EPG.
 */
export function EpgChannelPicker({channel, epgChannels, onClose}: EpgChannelPickerProps) {
    const titleId = useId();
    const dialog = useRef<HTMLDivElement>(null);
    // The channel's name is usually close to what the EPG calls it
    const [search, setSearch] = useState(channel.name);
    useDismiss(true, dialog, onClose);

    const saveMapping = useCallback(
        (epgChannelId: string | null) => api(`/api/channels/${channel.id}`, {method: 'PATCH', json: {epgChannelId}}),
        [channel.id]
    );
    const save = useAction(saveMapping);

    const matches = useMemo(() => {
        const query = search.trim().toLowerCase();
        const found = query
            ? epgChannels.filter((epgChannel) => epgChannel.displayName.toLowerCase().includes(query) || epgChannel.id.toLowerCase().includes(query))
            : epgChannels;
        return found.slice(0, MAX_RESULTS);
    }, [epgChannels, search]);

    async function pick(epgChannelId: string | null) {
        if (await save.run(epgChannelId)) {
            onClose();
        }
    }

    const optionClassName = 'flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-raised focus-visible:outline-2 focus-visible:-outline-offset-2 '
        + 'focus-visible:outline-accent disabled:opacity-50';

    return <div className="fixed inset-0 z-40 grid place-items-center bg-screen/70 p-4">
        <div
            ref={dialog}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            className="grid max-h-[85dvh] w-full max-w-lg grid-rows-[auto_auto_minmax(0,1fr)_auto] gap-4 border border-line bg-panel p-5 shadow-2xl"
        >
            <div className="grid gap-1">
                <h3 id={titleId} className="font-mono text-sm uppercase tracking-[0.2em]">Guide data
                    for {channel.name}</h3>
                <p className="text-sm text-muted">Pick the channel in the EPG that has this channel's programmes.</p>
            </div>

            <input
                type="search"
                autoFocus
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search the EPG's channels"
                aria-label="Search the EPG's channels"
                className={fieldClassName}
            />

            <ul className="-mx-2 overflow-y-auto">
                {channel.epgChannelOverride && <li>
                    <button type="button" disabled={save.pending} onClick={() => pick(null)}
                            className={optionClassName}>
                        <span className="flex-1">Use the playlist's tvg-id</span>
                        <span className="font-mono text-xs text-muted">{channel.tvgId ?? 'none'}</span>
                    </button>
                </li>}
                {matches.map((epgChannel) => <li key={epgChannel.id}>
                    <button
                        type="button"
                        disabled={save.pending}
                        aria-pressed={epgChannel.id === channel.epgChannelId}
                        onClick={() => pick(epgChannel.id)}
                        className={`${optionClassName} ${epgChannel.id === channel.epgChannelId ? 'text-accent' : ''}`}
                    >
                        <span className="min-w-0 flex-1 truncate">{epgChannel.displayName}</span>
                        <span className="max-w-[45%] truncate font-mono text-xs text-muted">{epgChannel.id}</span>
                    </button>
                </li>)}
                {matches.length === 0 && <li className="px-3 py-2 text-muted">No EPG channels match.</li>}
            </ul>

            {save.error && <ErrorText>{save.error}</ErrorText>}
        </div>
    </div>;
}
