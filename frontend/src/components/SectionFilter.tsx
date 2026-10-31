import {type FormEvent, useState} from 'react';
import type {PluginFilter} from '#/lib/types';
import {Button} from './Button';
import {FormInput, pickedRange, startValue, type Value} from './SectionForm';

/**
 * Inputs that change what a plugin's page shows. Applying them opens the page again with what's filled in, in its URL.
 */
export function SectionFilter({filter, onApply}: {
    filter: PluginFilter,
    onApply: (search: Record<string, string | number | boolean>) => void
}) {
    const [values, setValues] = useState<Record<string, Value>>(() => Object.fromEntries(filter.inputs.map((input) => [input.name, startValue(input)])));

    function apply(event: FormEvent) {
        event.preventDefault();
        // Numbers and checkboxes as what they are, so the URL reads ?days=30 rather than ?days="30". Date ranges as
        // their days with two dots in between, such as ?period=2026-10-01..2026-10-30.
        const search: Record<string, string | number | boolean> = {};
        for (const input of filter.inputs) {
            const value = values[input.name];
            if (input.type === 'checkbox') {
                search[input.name] = value === true;
            } else if (input.type === 'dateRange') {
                const range = pickedRange(value);
                if (range) {
                    search[input.name] = `${range.from}..${range.to}`;
                }
            } else if (value !== '') {
                search[input.name] = input.type === 'number' ? Number(value) : String(value);
            }
        }
        onApply(search);
    }

    return <form onSubmit={apply} className="flex flex-wrap items-end gap-x-6 gap-y-4">
        {filter.inputs.map((input) => <FormInput
            key={input.name}
            input={input}
            value={values[input.name]}
            onChange={(value) => setValues((values) => ({...values, [input.name]: value}))}
        />)}
        <Button type="submit" small>{filter.submitLabel}</Button>
    </form>;
}
