import {useRouter} from '@tanstack/react-router';
import {useCallback, useEffect, useState} from 'react';
import {ApiError} from './api';
import type {Playlist} from './types';

/**
 * @return a message for people about what went wrong
 */
export function errorMessage(error: unknown): string {
    return error instanceof ApiError ? error.message : 'Kijkbuis can\'t be reached right now.';
}

type Action<A extends unknown[]> = {
    /** Runs the action, and reloads the page's data if it worked. Resolves to whether it worked. */
    run: (...args: A) => Promise<boolean>
    pending: boolean
    error: string | null
};

/**
 * Runs something that changes data on the backend, such as a button that refreshes a playlist, and keeps track of
 * whether it's busy and what went wrong.
 */
export function useAction<A extends unknown[]>(action: (...args: A) => Promise<unknown>): Action<A> {
    const router = useRouter();
    const [pending, setPending] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const run = useCallback(async (...args: A) => {
        setPending(true);
        setError(null);
        try {
            await action(...args);
            await router.invalidate();
            return true;
        } catch (e) {
            setError(errorMessage(e));
            return false;
        } finally {
            setPending(false);
        }
    }, [action, router]);

    return {run, pending, error};
}

/**
 * Reloads a page's data every so often, to keep things like who's watching up to date.
 *
 * @param routeId the page's route ID
 * @param enabled whether to reload at all, e.g. only while something is still loading on the backend
 */
export function useReloadEvery(routeId: string, intervalMs: number, enabled: boolean = true): void {
    const router = useRouter();

    useEffect(() => {
        if (!enabled) {
            return;
        }
        const interval = window.setInterval(() => router.invalidate({filter: (match) => match.routeId === routeId}), intervalMs);
        return () => window.clearInterval(interval);
    }, [router, routeId, intervalMs, enabled]);
}

export type Status = 'ok' | 'busy' | 'error' | 'off';

/**
 * @return how a playlist is doing, in a word and a sentence
 */
export function playlistStatus(playlist: Playlist): { status: Status, label: string } {
    if (playlist.lastRefreshError) {
        return {status: 'error', label: 'The channels couldn\'t be loaded'};
    }
    if (playlist.epgLastRefreshError) {
        return {status: 'error', label: 'The guide data couldn\'t be loaded'};
    }
    if (playlist.epgRefreshing) {
        return {status: 'busy', label: 'Loading guide data'};
    }
    if (!playlist.lastRefreshedAt) {
        return {status: 'off', label: 'Not loaded yet'};
    }
    return {status: 'ok', label: 'Up to date'};
}
