import type {ReactNode} from 'react';
import {type ErrorComponentProps, getRouteApi, Link, useRouter} from '@tanstack/react-router';
import {ApiError} from '#/lib/api';
import {hasPrivilege} from '#/lib/auth';
import {Brand} from './Brand';
import {Button} from './Button';
import {Clock} from './Clock';

type MenuScreenProps = {
    title: string
    children: ReactNode
    /** Lets the content fill the rest of the screen edge to edge, and scroll by itself (like the guide) */
    fill?: boolean
    /** Gives the content more room, for pages with a sidebar */
    wide?: boolean
};

/**
 * The frame of every menu. The header leaves room for the TV, which sits in the top left while a menu is open.
 */
export function MenuScreen({title, children, fill = false, wide = false}: MenuScreenProps) {
    return <div className={`bg-panel ${fill ? 'flex h-dvh flex-col' : 'min-h-dvh'}`}>
        <header className="menu-header flex shrink-0 items-start justify-between gap-4 border-b border-line">
            <div className="grid gap-3">
                <Brand/>
                <h1 className="font-mono text-xl uppercase tracking-[0.25em] sm:text-2xl">{title}</h1>
                <MenuNav/>
            </div>
            <Clock className="hidden text-2xl sm:block"/>
        </header>
        <main
            className={fill ? 'flex min-h-0 flex-1 flex-col' : `mx-auto p-4 sm:p-8 ${wide ? 'max-w-7xl' : 'max-w-5xl'}`}>
            {children}
        </main>
    </div>;
}

const linkClassName = 'border-b-2 border-transparent pb-0.5 font-mono text-xs uppercase tracking-[0.2em] text-muted hover:text-fg '
    + 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent';

const appRoute = getRouteApi('/_app');

function MenuNav() {
    const {user} = appRoute.useRouteContext();
    const activeProps = {className: 'border-accent! text-fg!'};
    return <nav className="flex flex-wrap gap-x-5 gap-y-2">
        <Link to="/channels" className={linkClassName} activeProps={activeProps}>Channels</Link>
        <Link to="/guide" className={linkClassName} activeProps={activeProps}>Guide</Link>
        <Link to="/account" className={linkClassName} activeProps={activeProps}>Account</Link>
        {hasPrivilege(user, 'admin') &&
            <Link to="/admin" className={linkClassName} activeProps={activeProps}>Admin</Link>}
    </nav>;
}

/**
 * Shown in place of a menu whose data couldn't be loaded. The TV keeps playing.
 */
export function MenuError({error}: ErrorComponentProps) {
    const router = useRouter();

    return <MenuScreen title="Something went wrong">
        <div className="grid justify-items-start gap-6">
            <p className="text-muted">{error instanceof ApiError ? error.message : 'Kijkbuis can\'t be reached right now.'}</p>
            <Button onClick={() => router.invalidate()}>Try again</Button>
        </div>
    </MenuScreen>;
}
