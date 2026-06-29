import React, { useState, useEffect, useRef } from "react";

// Reusable searchable select (combobox) for Accounts/Categories
export default function SearchSelect({ options, value, onChange, placeholder, error = false, disabled = false }) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    const containerRef = React.useRef(null);
    // Tracks the id we most recently fired onChange for via auto-select. The
    // parent's `value` prop can lag behind during async saves (await api.put +
    // refetch), and `options` / `onChange` get fresh references on every parent
    // render — without this guard, the auto-select effect re-runs and fires
    // onChange repeatedly for the same id, triggering duplicate PUTs.
    const lastFiredIdRef = useRef(null);

    const normalize = (s) => (s || "")
        .toLowerCase()
        .replace(/[-–—]+/g, " ") // ignore indent dashes
        .replace(/\s+/g, " ");

    const selected = options.find(o => o.id === value);

    useEffect(() => {
        // Display the selected option name, including "All" options
        if (selected) {
            setQuery(selected.name);
        } else {
            setQuery("");
        }
        // Once the parent's value catches up to what we fired, clear the guard
        // so the user can pick a different option later.
        if (value && lastFiredIdRef.current === value) {
            lastFiredIdRef.current = null;
        }
    }, [value, selected?.name]);

    useEffect(() => {
        const handler = (e) => {
            if (containerRef.current && !containerRef.current.contains(e.target)) {
                setOpen(false);
            }
        };
        document.addEventListener('click', handler);
        return () => document.removeEventListener('click', handler);
    }, []);

    const filtered = options.filter(o => normalize(o.name).includes(normalize(query)));

    // Auto-select when a single non-placeholder option remains
    useEffect(() => {
        if (!open || disabled) return;
        const norm = normalize(query);
        if (!norm) return; // only after user types something
        const filteredNonPlaceholder = options.filter(o => (o.id || o.id === 0) && normalize(o.name).includes(norm));
        if (filteredNonPlaceholder.length === 1) {
            const only = filteredNonPlaceholder[0];
            // Don't fire if value already matches, AND don't re-fire while the
            // parent is still mid-save and value hasn't caught up to what we
            // just fired.
            if (only.id !== value && lastFiredIdRef.current !== only.id) {
                lastFiredIdRef.current = only.id;
                onChange(only.id);
            }
            setOpen(false);
        }
    }, [query, open, options, value, onChange, disabled]);

    const handleSelectOption = (option) => {
        if (disabled) return;
        if (option.id === value || lastFiredIdRef.current === option.id) {
            setQuery(option.name);
            setOpen(false);
            return;
        }
        lastFiredIdRef.current = option.id;
        onChange(option.id);
        // Set query to the selected name immediately
        setQuery(option.name);
        setOpen(false);
    };

    return (
        <div ref={containerRef} className="relative w-full">
            <input
                value={query}
                onChange={(e) => { if (!disabled) { setQuery(e.target.value); setOpen(true); } }}
                onFocus={() => { if (!disabled) { setOpen(true); setQuery(""); } }}
                placeholder={placeholder}
                aria-invalid={error ? "true" : "false"}
                disabled={disabled}
                className={`border px-2 py-1 rounded text-sm w-full ${error ? 'border-red-500' : ''} ${disabled ? 'bg-gray-100 cursor-not-allowed' : ''}`}
            />
            {open && !disabled && (
                <div className="absolute z-50 bg-white border rounded shadow max-h-48 overflow-auto w-full mt-1 text-gray-900">
                    {filtered.map(o => (
                        <div
                            key={o.id || 'all'}
                            className={`px-2 py-1 text-sm cursor-pointer hover:bg-blue-50 text-gray-900 ${o.id === value ? 'bg-blue-100' : ''}`}
                            onMouseDown={(e) => e.preventDefault()}
                            onClick={() => handleSelectOption(o)}
                        >
                            {o.name}
                        </div>
                    ))}
                    {filtered.length === 0 && (
                        <div className="px-2 py-1 text-xs text-gray-500">No matches</div>
                    )}
                </div>
            )}
        </div>
    );
}

