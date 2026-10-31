/**
 * An error response from the backend. The message is meant for users, so it can be shown as is.
 */
export class ApiError extends Error {

    readonly status: number;

    constructor(status: number, message: string) {
        super(message);
        this.status = status;
    }

}

type ApiOptions = Omit<RequestInit, 'body'> & {
    /** Sent as the JSON request body */
    json?: unknown
};

/**
 * Calls the backend.
 *
 * @throws ApiError if the backend responds with an error
 * @throws TypeError if the backend can't be reached
 */
export async function api<T>(path: string, {json, headers, ...options}: ApiOptions = {}): Promise<T> {
    const response = await fetch(path, {
        ...options,
        headers: json !== undefined ? {'Content-Type': 'application/json', ...headers} : headers,
        body: json !== undefined ? JSON.stringify(json) : undefined,
    });

    if (response.status === 401 && path !== '/api/me') {
        // The session expired. Log in again and come back here; reloading also forgets the old user.
        window.location.assign(`/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`);
    }

    if (!response.ok) {
        let message = `Something went wrong (HTTP ${response.status})`;
        try {
            const body = await response.json();
            if (typeof body.error === 'string') {
                message = body.error;
            }
        } catch {
            // Not a JSON error, keep the generic message
        }
        throw new ApiError(response.status, message);
    }

    return response.status === 204 ? undefined as T : await response.json() as T;
}
