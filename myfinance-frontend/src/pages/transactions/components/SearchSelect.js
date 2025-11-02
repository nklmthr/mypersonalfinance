import React, { useState, useEffect } from "react";

// Reusable searchable select (combobox) for Accounts/Categories
export default function SearchSelect({ options, value, onChange, placeholder, error = false, disabled = false }) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    const containerRef = React.useRef(null);

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
            if (only.id !== value) {
                onChange(only.id);
            }
            setOpen(false);
        }
    }, [query, open, options, value, onChange, disabled]);

    const handleSelectOption = (option) => {
        if (disabled) return;
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
                <div className="absolute z-50 bg-white border rounded shadow max-h-48 overflow-auto w-full mt-1">
                    {filtered.map(o => (
                        <div
                            key={o.id || 'all'}
                            className={`px-2 py-1 text-sm cursor-pointer hover:bg-blue-50 ${o.id === value ? 'bg-blue-100' : ''}`}
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

