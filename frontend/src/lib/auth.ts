import {api, ApiError} from './api';
import type {Privilege} from './types';

export type {Privilege};

export type User = {
    id: string
    username: string
    displayName: string
    privileges: Privilege[]
    groups: string[]
};

let currentUser: Promise<User | null> | null = null;

/**
 * Loads the logged-in user. The result is remembered, so this is cheap to call on every navigation.
 *
 * @return the user, or null if nobody is logged in
 * @throws if the backend can't be reached
 */
export function loadUser(): Promise<User | null> {
    currentUser ??= api<User>('/api/me').catch((error) => {
        if (error instanceof ApiError && error.status === 401) {
            return null;
        }
        // Try again next time
        currentUser = null;
        throw error;
    });
    return currentUser;
}

export async function logout(): Promise<void> {
    await api('/api/auth/logout', {method: 'POST'});
    currentUser = Promise.resolve(null);
}

/**
 * @param redirect the path to return to once logged in
 * @return the URL that starts logging in. It must be opened as a page, since it sends the user to the identity provider.
 */
export function loginUrl(redirect: string): string {
    return `/api/auth/login?redirect=${encodeURIComponent(redirect)}`;
}

/**
 * @return the path if it's a path within Kijkbuis, otherwise undefined
 */
export function safeRedirect(path: unknown): string | undefined {
    return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') ? path : undefined;
}

export function hasPrivilege(user: User, privilege: Privilege): boolean {
    return user.privileges.includes(privilege);
}
