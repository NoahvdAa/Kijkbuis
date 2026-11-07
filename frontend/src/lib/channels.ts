import {useEffect, useState} from 'react';
import {api} from './api';
import type {Channel} from './types';

/**
 * Loads a channel's details, and loads them again when the programme changes.
 */
export function useChannel(channelId: string | null): Channel | null {
    const [channel, setChannel] = useState<Channel | null>(null);
    const [refresh, setRefresh] = useState(0);

    useEffect(() => {
        if (!channelId) {
            setChannel(null);
            return;
        }
        let cancelled = false;
        api<Channel>(`/api/channels/${channelId}`)
            .then((loaded) => !cancelled && setChannel(loaded))
            .catch(() => {
                // The screen just doesn't show the channel's name
            });
        return () => {
            cancelled = true;
        };
    }, [channelId, refresh]);

    useEffect(() => {
        // Reload when the programme ends, or when the next one starts if nothing is on, or every few minutes if neither is
        // known
        const changesAt = channel?.now?.endsAt ?? channel?.next?.startsAt;
        const until = changesAt ? Date.parse(changesAt) - Date.now() + 5_000 : 5 * 60_000;
        const timeout = window.setTimeout(() => setRefresh((current) => current + 1), Math.max(until, 5_000));
        return () => window.clearTimeout(timeout);
    }, [channel]);

    return channel?.id === channelId ? channel : null;
}
