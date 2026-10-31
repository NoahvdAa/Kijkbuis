import type {Privilege} from '#/lib/types';

export const PRIVILEGE_NAMES: Record<Privilege, string> = {
    admin: 'Administrator',
    transcode: 'Change quality',
};

/**
 * Short labels, such as groups or privileges.
 */
export function Tags({values, empty = 'None'}: { values: string[], empty?: string }) {
    if (values.length === 0) {
        return <span className="text-muted">{empty}</span>;
    }
    return <ul className="flex flex-wrap gap-2">
        {values.map((value) => <li key={value}
                                   className="border border-line bg-raised px-2 py-0.5 font-mono text-sm">{value}</li>)}
    </ul>;
}
