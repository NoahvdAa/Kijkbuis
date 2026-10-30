import {createFileRoute} from '@tanstack/react-router';
import {useCallback} from 'react';
import {api} from '#/lib/api';
import {useAction} from '#/lib/admin';
import {formatAgo, useNow} from '#/lib/time';
import type {UserDetails} from '#/lib/types';
import {AdminSection, ConfirmButton, ErrorText} from '#/components/admin/AdminParts';
import {PRIVILEGE_NAMES, Tags} from '#/components/Tags';

export const Route = createFileRoute('/_app/admin/users')({
	loader: () => api<UserDetails[]>('/api/users'),
	component: UsersPage,
});

/**
 * Everyone who has logged in. Accounts come from the identity provider, so there's nothing to create or edit here.
 */
function UsersPage() {
	const users = Route.useLoaderData();
	const {user: me} = Route.useRouteContext();
	const now = useNow();

	return <AdminSection title={`Users · ${users.length}`}>
		<ul className="divide-y divide-line border-y border-line">
			{users.map((user) => <li key={user.id}><UserRow user={user} isMe={user.id === me.id} now={now}/></li>)}
		</ul>
	</AdminSection>;
}

function UserRow({user, isMe, now}: {user: UserDetails, isMe: boolean, now: number}) {
	const signOutUser = useCallback(() => api(`/api/users/${user.id}/sessions`, {method: 'DELETE'}), [user.id]);
	const signOut = useAction(signOutUser);

	return <div className="grid gap-3 py-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] lg:items-center lg:gap-6">
		<div className="grid min-w-0 gap-0.5">
			<p className="truncate">
				{user.displayName}
				{isMe && <span className="ml-2 font-mono text-xs uppercase tracking-[0.15em] text-accent">You</span>}
			</p>
			<p className="truncate text-sm text-muted">
				{user.username} · logged in {formatAgo(user.lastLoginAt, now)}
				{user.sessionCount > 0 ? ` · on ${user.sessionCount} ${user.sessionCount === 1 ? 'browser' : 'browsers'}` : ' · signed out'}
			</p>
		</div>
		<div className="grid gap-2 text-sm">
			<Tags values={user.privileges.map((privilege) => PRIVILEGE_NAMES[privilege] ?? privilege)} empty="Can watch"/>
			{user.groups.length > 0 && <p className="truncate text-muted" title={user.groups.join(', ')}>Groups: {user.groups.join(', ')}</p>}
		</div>
		<div className="grid justify-items-start gap-2 lg:justify-items-end">
			<ConfirmButton
				label="Sign out"
				confirmLabel={isMe ? 'Sign me out everywhere' : 'Sign out everywhere'}
				disabled={signOut.pending || (user.sessionCount === 0 && !isMe)}
				onConfirm={() => signOut.run()}
			/>
			{signOut.error && <ErrorText>{signOut.error}</ErrorText>}
		</div>
	</div>;
}
