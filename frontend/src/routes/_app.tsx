import {createFileRoute, Outlet, redirect, useLocation, useNavigate} from '@tanstack/react-router';
import {useEffect} from 'react';
import {TvScreen} from '#/components/TvScreen';
import {TvProvider} from '#/lib/tv';

/**
 * Everything that needs a logged-in user. The TV lives here rather than in a page, so it stays on screen (and keeps
 * playing) while moving between menus. At / it's full screen, on any other page it sits in the top left.
 */
export const Route = createFileRoute('/_app')({
    beforeLoad: ({context, location}) => {
        if (!context.user) {
            throw redirect({to: '/login', search: {redirect: location.href}});
        }
        return {user: context.user};
    },
    component: AppLayout,
});

function AppLayout() {
    const {user} = Route.useRouteContext();
    const watching = useLocation({select: (location) => location.pathname === '/'});
    const navigate = useNavigate();

    // Escape goes back to the TV, like the back button on a remote
    useEffect(() => {
        if (watching) {
            return;
        }
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !event.defaultPrevented) {
                navigate({to: '/'});
            }
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [watching, navigate]);

    return <TvProvider>
        <TvScreen mode={watching ? 'full' : 'pip'} user={user}/>
        <Outlet/>
    </TvProvider>;
}
