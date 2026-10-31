import {useRouter} from '@tanstack/react-router';
import {type FormEvent, useId, useState} from 'react';
import {api} from '#/lib/api';
import {errorMessage} from '#/lib/admin';
import {PLUGIN_HEADERS} from '#/lib/plugins';
import type {PluginDateRange, PluginForm, PluginInput} from '#/lib/types';
import {ErrorText} from './admin/AdminParts';
import {Button} from './Button';
import {Field, fieldClassName, labelClassName} from './Field';

/**
 * What's filled in. Numbers stay text until the form is sent, so they can be empty or half typed. A date range has an
 * empty string for a day that isn't picked yet.
 */
export type Value = string | boolean | PluginDateRange;

/**
 * A form a plugin added to a section. Saving sends what's filled in to the plugin's endpoint, then reloads the page's
 * data so it shows the result.
 */
export function SectionForm({form}: { form: PluginForm }) {
    const router = useRouter();
    // Only taken from the form once, so pages that reload by themselves don't undo what's being typed
    const [values, setValues] = useState<Record<string, Value>>(() => Object.fromEntries(form.inputs.map((input) => [input.name, startValue(input)])));
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState<string | null>(null);

    function change(name: string, value: Value) {
        setValues((values) => ({...values, [name]: value}));
        setSaved(false);
    }

    async function save(event: FormEvent) {
        event.preventDefault();
        setSaving(true);
        setError(null);
        try {
            const json = Object.fromEntries(form.inputs.map((input) => [input.name, sentValue(input, values[input.name])]));
            await api(form.action, {method: 'POST', json, headers: PLUGIN_HEADERS});
            await router.invalidate();
            setSaved(true);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setSaving(false);
        }
    }

    return <form onSubmit={save} className="grid gap-5 sm:grid-cols-2">
        {form.inputs.map((input) => <FormInput key={input.name} input={input} value={values[input.name]}
                                               onChange={(value) => change(input.name, value)}/>)}

        <div className="flex flex-wrap items-center gap-3 sm:col-span-2">
            <Button type="submit" small disabled={saving}>{saving ? 'Saving…' : form.submitLabel}</Button>
            {saved && !error && <p role="status"
                                   className="font-mono text-xs uppercase tracking-[0.2em] text-teletext-green">{form.successText}</p>}
            {error && <ErrorText>{error}</ErrorText>}
        </div>
    </form>;
}

/**
 * An input from a plugin, with its label and help.
 */
export function FormInput({input, value, onChange}: {
    input: PluginInput,
    value: Value,
    onChange: (value: Value) => void
}) {
    switch (input.type) {
        case 'text': {
            const props = {
                value: String(value),
                onChange: (event: { target: { value: string } }) => onChange(event.target.value),
                required: input.required,
                maxLength: input.maxLength ?? undefined,
                placeholder: input.placeholder ?? undefined,
                className: fieldClassName,
            };
            return <Field label={input.label} hint={input.help} className={input.multiline ? 'sm:col-span-2' : ''}>
                {input.multiline ? <textarea rows={4} {...props}/> : <input {...props}/>}
            </Field>;
        }
        case 'number':
            return <Field label={input.label} hint={input.help}>
                <span className="flex items-center gap-3">
                    <input
                        type="number"
                        step={1}
                        inputMode="numeric"
                        value={String(value)}
                        onChange={(event) => onChange(event.target.value)}
                        required={input.required}
                        min={input.min ?? undefined}
                        max={input.max ?? undefined}
                        className={`${fieldClassName} w-36 font-mono tabular-nums`}
                    />
                    {input.unit && <span className="text-muted">{input.unit}</span>}
                </span>
            </Field>;
        case 'checkbox':
            return <label className="grid content-start gap-1.5 sm:col-span-2">
                <span className="flex items-center gap-3">
                    <input type="checkbox" checked={value === true} onChange={(event) => onChange(event.target.checked)}
                           className="size-4 accent-accent"/>
                    <span>{input.label}</span>
                </span>
                {/* Lined up with the label, past the checkbox */}
                {input.help && <span className="pl-7 text-sm text-muted">{input.help}</span>}
            </label>;
        case 'select':
            return <Field label={input.label} hint={input.help}>
                <select value={String(value)} onChange={(event) => onChange(event.target.value)}
                        className={fieldClassName}>
                    {input.options.map((option) => <option key={option.value}
                                                           value={option.value}>{option.label}</option>)}
                </select>
            </Field>;
        case 'dateRange':
            return <DateRangeInput input={input} value={dateRange(value)} onChange={onChange}/>;
        default:
            // A kind of input this frontend doesn't know
            return null;
    }
}

/**
 * Two days, the last one no earlier than the first. Picking one makes the other required too, so it's never sent half
 * filled in. The days get a fixed width, since phones size date inputs unevenly by themselves.
 */
function DateRangeInput({input, value, onChange}: {
    input: PluginInput & { type: 'dateRange' },
    value: PluginDateRange,
    onChange: (value: Value) => void
}) {
    const labelId = useId();

    return <div role="group" aria-labelledby={labelId} className="grid content-start gap-1.5">
        <span id={labelId} className={labelClassName}>{input.label}</span>
        <span className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <input
                type="date"
                aria-label="From"
                value={value.from}
                onChange={(event) => onChange({...value, from: event.target.value})}
                required={input.required || value.to !== ''}
                max={value.to || undefined}
                className={`${fieldClassName} w-44 font-mono tabular-nums`}
            />
            <span className="text-muted">to</span>
            <input
                type="date"
                aria-label="To"
                value={value.to}
                onChange={(event) => onChange({...value, to: event.target.value})}
                required={input.required || value.from !== ''}
                min={value.from || undefined}
                className={`${fieldClassName} w-44 font-mono tabular-nums`}
            />
        </span>
        {input.help && <span className="text-sm text-muted">{input.help}</span>}
    </div>;
}

function dateRange(value: Value): PluginDateRange {
    return typeof value === 'object' ? value : {from: '', to: ''};
}

/**
 * @return the days of a date range with both picked, or null if it's left empty
 */
export function pickedRange(value: Value): PluginDateRange | null {
    const range = dateRange(value);
    return range.from !== '' && range.to !== '' ? range : null;
}

export function startValue(input: PluginInput): Value {
    switch (input.type) {
        case 'number':
            return input.value?.toString() ?? '';
        case 'checkbox':
            return input.value;
        case 'dateRange':
            return input.value ?? {from: '', to: ''};
        default:
            return input.value ?? '';
    }
}

/**
 * @return the value as the endpoint gets it: numbers as numbers, and date ranges as their days, or null if left empty
 */
function sentValue(input: PluginInput, value: Value): unknown {
    if (input.type === 'number') {
        return value === '' ? null : Number(value);
    }
    if (input.type === 'dateRange') {
        return pickedRange(value);
    }
    return value;
}
