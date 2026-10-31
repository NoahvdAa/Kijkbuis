import {formatDateTime} from '#/lib/time';
import type {PluginComponent, PluginListItem, PluginSection, PluginTableCell} from '#/lib/types';
import {AdminSection} from './admin/AdminParts';
import {EpgImage} from './EpgImage';
import {SectionFilter} from './SectionFilter';
import {SectionForm} from './SectionForm';

type OnFilter = (search: Record<string, string | number | boolean>) => void;

/**
 * The sections plugins add to a page. Plugins only say what to show, so it's drawn here to fit in with the rest of
 * Kijkbuis.
 *
 * @param onFilter opens the page again with what's filled in in a filter, on a plugin's own page
 */
export function PluginSections({sections, onFilter}: { sections: PluginSection[], onFilter?: OnFilter }) {
    return sections.map((section, index) => <AdminSection key={`${section.plugin}-${index}`} title={section.title}>
        {section.components.map((component, componentIndex) => <Component key={componentIndex} component={component}
                                                                          onFilter={onFilter}/>)}
    </AdminSection>);
}

function Component({component, onFilter}: { component: PluginComponent, onFilter?: OnFilter }) {
    switch (component.type) {
        case 'text':
            return <p className="whitespace-pre-line">{component.text}</p>;
        case 'stats':
            return <Stats stats={component.stats}/>;
        case 'list':
            return <ItemList items={component.items} emptyText={component.emptyText}/>;
        case 'table':
            return <Table columns={component.columns} rows={component.rows} emptyText={component.emptyText}/>;
        case 'form':
            return <SectionForm form={component}/>;
        case 'filter':
            // Starts over with what the plugin says is filled in, each time the page opens with other filters
            return onFilter ?
                <SectionFilter key={JSON.stringify(component.inputs.map((input) => input.value))} filter={component}
                               onApply={onFilter}/> : null;
        default:
            // A kind of component this frontend doesn't know
            return null;
    }
}

function Stats({stats}: { stats: { label: string, value: string }[] }) {
    return <dl className="grid grid-cols-[repeat(auto-fill,minmax(10rem,1fr))] gap-3">
        {stats.map((stat, index) => <div key={index} className="border border-line p-4">
            <dt className="font-mono text-xs uppercase tracking-[0.2em] text-muted">{stat.label}</dt>
            <dd className="mt-2 font-mono text-2xl tabular-nums">{stat.value}</dd>
        </div>)}
    </dl>;
}

function ItemList({items, emptyText}: { items: PluginListItem[], emptyText: string | null }) {
    if (items.length === 0) {
        return <p className="text-muted">{emptyText ?? 'Nothing here yet.'}</p>;
    }

    // Items without an image keep an empty space for it, so the titles line up
    const hasImages = items.some((item) => item.imageUrl);
    return <ul className="divide-y divide-line border-y border-line">
        {items.map((item, index) => <li key={index} className="flex items-center gap-4 py-3 sm:px-2">
            {hasImages && <div className="aspect-video w-24 shrink-0 bg-raised sm:w-32">
                {item.imageUrl && <EpgImage src={item.imageUrl} lazy className="size-full object-cover"/>}
            </div>}
            <div className="grid min-w-0 flex-1 gap-1">
                <p className="truncate">{item.title}</p>
                {item.subtitle && <p className="truncate text-sm text-muted">{item.subtitle}</p>}
            </div>
            {(item.detail || item.time) &&
                <div className="grid shrink-0 justify-items-end gap-1 font-mono text-sm text-muted tabular-nums">
                    {item.detail && <span>{item.detail}</span>}
                    {item.time && <time dateTime={item.time}>{formatDateTime(item.time)}</time>}
                </div>}
        </li>)}
    </ul>;
}

function Table({columns, rows, emptyText}: { columns: string[], rows: PluginTableCell[][], emptyText: string | null }) {
    if (rows.length === 0) {
        return <p className="text-muted">{emptyText ?? 'Nothing here yet.'}</p>;
    }

    // Columns of numbers line up on the right, heading included. Empty cells don't count either way.
    const numeric = columns.map((_, column) => rows.some((row) => typeof row[column] === 'number')
        && rows.every((row) => row[column] === null || typeof row[column] === 'number'));
    // Scrolls by itself on narrow screens, rather than making the page wider
    return <div className="overflow-x-auto">
        <table className="w-full border-y border-line text-sm">
            <thead>
            <tr className="border-b border-line">
                {columns.map((column, index) => <th
                    key={index}
                    scope="col"
                    className={`px-3 py-2.5 font-mono text-xs font-normal uppercase tracking-[0.2em] whitespace-nowrap text-muted ${numeric[index] ? 'text-right' : 'text-left'}`}
                >
                    {column}
                </th>)}
            </tr>
            </thead>
            <tbody className="divide-y divide-line">
            {rows.map((row, rowIndex) => <tr key={rowIndex}>
                {row.map((cell, index) => <td key={index}
                                              className={`px-3 py-2.5 ${numeric[index] ? 'text-right' : ''}`}>
                    <TableCell cell={cell}/>
                </td>)}
            </tr>)}
            </tbody>
        </table>
    </div>;
}

function TableCell({cell}: { cell: PluginTableCell }) {
    if (cell === null) {
        return null;
    }
    if (typeof cell === 'number') {
        return <span className="font-mono tabular-nums">{cell.toLocaleString()}</span>;
    }
    if (typeof cell === 'object') {
        return <time dateTime={cell.time}
                     className="font-mono whitespace-nowrap tabular-nums">{formatDateTime(cell.time)}</time>;
    }
    return cell;
}
