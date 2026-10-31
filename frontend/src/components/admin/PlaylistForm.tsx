import {useRouter} from '@tanstack/react-router';
import {type FormEvent, useState} from 'react';
import {api} from '#/lib/api';
import {errorMessage} from '#/lib/admin';
import type {Playlist} from '#/lib/types';
import {Button} from '../Button';
import {Field, fieldClassName} from '../Field';
import {ErrorText} from './AdminParts';

/** How often a playlist can be reloaded, in minutes */
const REFRESH_INTERVALS: [number, string][] = [
    [30, 'Every 30 minutes'],
    [60, 'Every hour'],
    [3 * 60, 'Every 3 hours'],
    [6 * 60, 'Every 6 hours'],
    [12 * 60, 'Every 12 hours'],
    [24 * 60, 'Every day'],
    [7 * 24 * 60, 'Every week'],
];
const DEFAULT_REFRESH_INTERVAL = 12 * 60;

type PlaylistFormProps = {
    /** The playlist to change, or nothing to add a new one */
    playlist?: Playlist
    onSaved?: (playlist: Playlist) => void
    onCancel?: () => void
};

/**
 * Adds a playlist, or changes one. The backend loads the channels before it responds, so a wrong URL shows up right
 * away.
 */
export function PlaylistForm({playlist, onSaved, onCancel}: PlaylistFormProps) {
    const router = useRouter();
    const [name, setName] = useState(playlist?.name ?? '');
    const [url, setUrl] = useState(playlist?.url ?? '');
    const [epgUrl, setEpgUrl] = useState(playlist?.epgUrl ?? '');
    const [refreshInterval, setRefreshInterval] = useState(playlist?.refreshIntervalMinutes ?? DEFAULT_REFRESH_INTERVAL);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState<string | null>(null);

    /** Wraps a field's setter, so changing it clears the "Saved" note */
    function edited<T>(set: (value: T) => void): (value: T) => void {
        return (value) => {
            set(value);
            setSaved(false);
        };
    }

    // Keep an interval set through the API visible, even if it isn't one of the usual ones
    const intervals = REFRESH_INTERVALS.some(([minutes]) => minutes === refreshInterval)
        ? REFRESH_INTERVALS
        : [...REFRESH_INTERVALS, [refreshInterval, `Every ${refreshInterval} minutes`] as [number, string]].sort(([a], [b]) => a - b);

    async function save(event: FormEvent) {
        event.preventDefault();
        setSaving(true);
        setError(null);

        const fields = {
            name: name.trim(),
            url: url.trim(),
            epgUrl: epgUrl.trim() || null,
            refreshIntervalMinutes: refreshInterval
        };
        try {
            let result: Playlist;
            if (playlist) {
                // Only send what changed, since a new URL makes the backend load the channels again
                const changes = Object.fromEntries(Object.entries(fields).filter(([key, value]) => value !== playlist[key as keyof typeof fields]));
                result = Object.keys(changes).length > 0
                    ? await api<Playlist>(`/api/playlists/${playlist.id}`, {method: 'PATCH', json: changes})
                    : playlist;
            } else {
                result = await api<Playlist>('/api/playlists', {method: 'POST', json: fields});
            }
            await router.invalidate();
            setSaved(true);
            onSaved?.(result);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setSaving(false);
        }
    }

    return <form onSubmit={save} className="grid gap-5 sm:grid-cols-2">
        <Field label="Name">
            <input required value={name} onChange={(event) => edited(setName)(event.target.value)} maxLength={255}
                   placeholder="My provider" className={fieldClassName}/>
        </Field>
        <Field label="Reload channels">
            <select value={refreshInterval} onChange={(event) => edited(setRefreshInterval)(Number(event.target.value))}
                    className={fieldClassName}>
                {intervals.map(([minutes, label]) => <option key={minutes} value={minutes}>{label}</option>)}
            </select>
        </Field>
        <Field label="Playlist URL"
               hint="An M3U playlist. It usually contains your provider login, so only admins see it."
               className="sm:col-span-2">
            <input required type="url" value={url} onChange={(event) => edited(setUrl)(event.target.value)}
                   placeholder="https://" spellCheck={false} className={`${fieldClassName} font-mono text-sm`}/>
        </Field>
        <Field
            label="Guide data URL (optional)"
            hint={playlist?.detectedEpgUrl
                ? 'Leave empty to use the one the playlist names.'
                : 'An XMLTV file. Leave empty to use the one the playlist names, if any.'}
            className="sm:col-span-2"
        >
            <input
                type="url"
                value={epgUrl}
                onChange={(event) => edited(setEpgUrl)(event.target.value)}
                placeholder={playlist?.detectedEpgUrl ?? 'https://'}
                spellCheck={false}
                className={`${fieldClassName} font-mono text-sm`}
            />
        </Field>

        <div className="flex flex-wrap items-center gap-3 sm:col-span-2">
            <Button type="submit" small disabled={saving}>
                {saving ? (playlist ? 'Saving…' : 'Loading channels…') : (playlist ? 'Save' : 'Add playlist')}
            </Button>
            {onCancel && <Button small disabled={saving} onClick={onCancel}>Cancel</Button>}
            {saved && !error &&
                <p role="status" className="font-mono text-xs uppercase tracking-[0.2em] text-teletext-green">Saved</p>}
            {error && <ErrorText>{error}</ErrorText>}
        </div>
    </form>;
}
