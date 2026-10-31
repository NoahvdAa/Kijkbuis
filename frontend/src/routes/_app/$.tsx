import {createFileRoute} from '@tanstack/react-router';
import {MenuScreen} from '#/components/MenuScreen';
import {NotFound} from '#/components/NotFound';

/**
 * Any page Kijkbuis doesn't have. It's a menu like the others, so the TV keeps playing in the corner.
 */
export const Route = createFileRoute('/_app/$')({component: NotFoundPage});

function NotFoundPage() {
    return <MenuScreen title="Not found">
        <NotFound/>
    </MenuScreen>;
}
