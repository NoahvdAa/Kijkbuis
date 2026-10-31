import {createFileRoute, useNavigate} from '@tanstack/react-router';
import {useState} from 'react';
import {ApiError} from '#/lib/api';
import {logout} from '#/lib/auth';
import {loadPluginSections} from '#/lib/plugins';
import {MenuScreen} from '#/components/MenuScreen';
import {Button} from '#/components/Button';
import {PluginSections} from '#/components/PluginSections';
import {PRIVILEGE_NAMES, Tags} from '#/components/Tags';

export const Route = createFileRoute('/_app/account')({
    loader: () => loadPluginSections('account'),
    component: AccountPage,
});

function AccountPage() {
    const {user} = Route.useRouteContext();
    const sections = Route.useLoaderData();
    const navigate = useNavigate();
    const [loggingOut, setLoggingOut] = useState(false);
    const [error, setError] = useState<string | null>(null);

    async function logOut() {
        setLoggingOut(true);
        setError(null);
        try {
            await logout();
            await navigate({to: '/login'});
        } catch (e) {
            setError(e instanceof ApiError ? e.message : 'Kijkbuis can\'t be reached right now.');
            setLoggingOut(false);
        }
    }

    return <MenuScreen title="Account">
        <dl className="grid gap-x-10 gap-y-5 sm:grid-cols-[max-content_1fr]">
            <Detail label="Name">{user.displayName}</Detail>
            <Detail label="Username">{user.username}</Detail>
            <Detail label="Privileges">
                <Tags values={user.privileges.map((privilege) => PRIVILEGE_NAMES[privilege] ?? privilege)}/>
            </Detail>
            <Detail label="Groups">
                <Tags values={user.groups}/>
            </Detail>
        </dl>

        {sections.length > 0 && <div className="mt-12 grid gap-12">
            <PluginSections sections={sections}/>
        </div>}

        <div className="mt-12 grid justify-items-start gap-3">
            <Button onClick={logOut} disabled={loggingOut}>{loggingOut ? 'Logging out…' : 'Log out'}</Button>
            {error && <p role="alert" className="text-teletext-red">{error}</p>}
        </div>
    </MenuScreen>;
}

function Detail({label, children}: { label: string, children: React.ReactNode }) {
    return <>
        <dt className="font-mono text-xs uppercase tracking-[0.2em] text-muted sm:pt-1">{label}</dt>
        <dd className="-mt-3 sm:mt-0">{children}</dd>
    </>;
}
