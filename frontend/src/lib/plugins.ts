import {api} from './api';
import type {PluginSection} from './types';

/**
 * Sent along with requests a plugin answers, such as its pages and forms: the browser's time zone, so the plugin knows
 * which days the user means.
 */
export const PLUGIN_HEADERS: Record<string, string> = {'X-Time-Zone': Intl.DateTimeFormat().resolvedOptions().timeZone};

/** The places on pages where plugins can add sections */
export type Slot = 'account' | 'admin-overview';

/**
 * Loads the sections plugins add to a page. They're extras, so the page still works without them if they can't be
 * loaded.
 */
export async function loadPluginSections(slot: Slot): Promise<PluginSection[]> {
    try {
        return await api<PluginSection[]>(`/api/ui/${slot}`);
    } catch {
        return [];
    }
}
