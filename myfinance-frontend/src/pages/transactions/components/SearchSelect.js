import React, { useState, useEffect, useRef } from "react";

// Reusable searchable select (combobox) for Accounts/Categories
export default function SearchSelect({ options, value, onChange, placeholder, error = false, disabled = false, className = "" }) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");
    const [highlightedIndex, setHighlightedIndex] = useState(-1);
    const containerRef = React.useRef(null);
    const listRef = useRef(null);
    // Tracks the id we most recently fired onChange for. The parent's `value`
    // can lag during async saves (await api.put + refetch); without this guard
    // a second click before value catches up would fire a duplicate PUT.
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
                setHighlightedIndex(-1);
            }
        };
        document.addEventListener('click', handler);
        return () => document.removeEventListener('click', handler);
    }, []);

    const filtered = options.filter(o => normalize(o.name).includes(normalize(query)));

    // Keep highlighted option visible in the scrollable list
    useEffect(() => {
        if (!open || highlightedIndex < 0 || !listRef.current) return;
        const el = listRef.current.children[highlightedIndex];
        if (el && typeof el.scrollIntoView === "function") {
            el.scrollIntoView({ block: "nearest" });
        }
    }, [highlightedIndex, open]);

    // Auto-select when typing narrows to a single non-placeholder option (inline
    // category on the Transactions list relies on this to submit without a click).
    useEffect(() => {
        if (!open || disabled) return;
        const norm = normalize(query);
        if (!norm) return; // only after user types something
        const filteredNonPlaceholder = options.filter(o => (o.id || o.id === 0) && normalize(o.name).includes(norm));
        if (filteredNonPlaceholder.length === 1) {
            const only = filteredNonPlaceholder[0];
            if (only.id !== value && lastFiredIdRef.current !== only.id) {
                lastFiredIdRef.current = only.id;
                onChange(only.id);
            }
            setOpen(false);
            setHighlightedIndex(-1);
            setQuery(only.name);
        }
    }, [query, open, options, value, onChange, disabled]);

    const handleSelectOption = (option) => {
        if (disabled) return;
        if (option.id === value || lastFiredIdRef.current === option.id) {
            setQuery(option.name);
            setOpen(false);
            setHighlightedIndex(-1);
            return;
        }
        lastFiredIdRef.current = option.id;
        onChange(option.id);
        setQuery(option.name);
        setOpen(false);
        setHighlightedIndex(-1);
    };

    const handleKeyDown = (e) => {
        if (disabled) return;

        if (e.key === "ArrowDown") {
            e.preventDefault();
            if (!open) {
                setOpen(true);
                setQuery("");
                setHighlightedIndex(0);
                return;
            }
            if (filtered.length === 0) return;
            setHighlightedIndex((i) => {
                const next = i < 0 ? 0 : (i + 1) % filtered.length;
                return next;
            });
            return;
        }

        if (e.key === "ArrowUp") {
            e.preventDefault();
            if (!open) {
                setOpen(true);
                setQuery("");
                setHighlightedIndex(0);
                return;
            }
            if (filtered.length === 0) return;
            setHighlightedIndex((i) => (i <= 0 ? filtered.length - 1 : i - 1));
            return;
        }

        if (e.key === "Enter") {
            if (!open || highlightedIndex < 0 || highlightedIndex >= filtered.length) return;
            e.preventDefault();
            handleSelectOption(filtered[highlightedIndex]);
            return;
        }

        if (e.key === "Escape") {
            if (!open) return;
            e.preventDefault();
            setOpen(false);
            setHighlightedIndex(-1);
            setQuery(selected ? selected.name : "");
        }
    };

    return (
        <div ref={containerRef} className="relative w-full">
            <input
                value={query}
                onChange={(e) => {
                    if (disabled) return;
                    setQuery(e.target.value);
                    setOpen(true);
                    setHighlightedIndex(0);
                }}
                onFocus={() => {
                    if (disabled) return;
                    setOpen(true);
                    setQuery("");
                    setHighlightedIndex(0);
                }}
                onKeyDown={handleKeyDown}
                placeholder={placeholder}
                aria-invalid={error ? "true" : "false"}
                aria-expanded={open}
                aria-autocomplete="list"
                role="combobox"
                disabled={disabled}
                className={`border px-2 py-1 rounded text-sm w-full bg-white text-gray-900 placeholder:text-gray-400 ${error ? 'border-red-500' : ''} ${disabled ? 'bg-gray-100 cursor-not-allowed' : ''} ${className}`}
            />
            {open && !disabled && (
                <div
                    ref={listRef}
                    role="listbox"
                    className="absolute z-50 bg-white border rounded shadow max-h-48 overflow-auto w-full mt-1 text-gray-900"
                >
                    {filtered.map((o, index) => (
                        <div
                            key={o.id || 'all'}
                            role="option"
                            aria-selected={index === highlightedIndex}
                            className={`px-2 py-1 text-sm cursor-pointer text-gray-900 ${
                                index === highlightedIndex
                                    ? 'bg-blue-100'
                                    : o.id === value
                                        ? 'bg-blue-50'
                                        : 'hover:bg-blue-50'
                            }`}
                            onMouseDown={(e) => e.preventDefault()}
                            onMouseEnter={() => setHighlightedIndex(index)}
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
