import React, { useState, useEffect, useRef } from "react";
import api from "../../../auth/api";

export default function LabelInput({ value = [], onChange, placeholder = "Add labels..." }) {
	const [inputValue, setInputValue] = useState("");
	const [suggestions, setSuggestions] = useState([]);
	const [showSuggestions, setShowSuggestions] = useState(false);
	const [loading, setLoading] = useState(false);
	const containerRef = useRef(null);
	const inputRef = useRef(null);

	const selectedLabels = value || [];

	useEffect(() => {
		const handler = (e) => {
			if (containerRef.current && !containerRef.current.contains(e.target)) {
				setShowSuggestions(false);
			}
		};
		document.addEventListener("click", handler);
		return () => document.removeEventListener("click", handler);
	}, []);

	const searchLabels = async (query) => {
		if (!query || query.trim().length === 0) {
			setSuggestions([]);
			return;
		}

		setLoading(true);
		try {
			const response = await api.get("/labels/search", {
				params: { q: query.trim() }
			});
			// Filter out already selected labels
			const available = response.data.filter(
				label => !selectedLabels.some(selected => selected.id === label.id || selected.name === label.name)
			);
			setSuggestions(available);
		} catch (error) {
			console.error("Error searching labels:", error);
			setSuggestions([]);
		} finally {
			setLoading(false);
		}
	};

	const handleInputChange = (e) => {
		const newValue = e.target.value;
		setInputValue(newValue);
		if (newValue.trim().length > 0) {
			setShowSuggestions(true);
			searchLabels(newValue);
		} else {
			setShowSuggestions(false);
			setSuggestions([]);
		}
	};

	const handleKeyDown = (e) => {
		if (e.key === "Enter" && inputValue.trim()) {
			e.preventDefault();
			addLabel(inputValue.trim());
		} else if (e.key === "Backspace" && inputValue === "" && selectedLabels.length > 0) {
			// Remove last label if input is empty and backspace is pressed
			removeLabel(selectedLabels[selectedLabels.length - 1]);
		} else if (e.key === "Escape") {
			setShowSuggestions(false);
			setInputValue("");
		} else if (e.key === "ArrowDown" && suggestions.length > 0) {
			e.preventDefault();
			// Focus first suggestion - could be enhanced to navigate through suggestions
		}
	};

	const addLabel = (labelName) => {
		if (!labelName || labelName.trim() === "") return;
		
		const trimmedName = labelName.trim();
		
		// Check if label already exists
		if (selectedLabels.some(l => l.name === trimmedName || l.name?.toLowerCase() === trimmedName.toLowerCase())) {
			setInputValue("");
			setShowSuggestions(false);
			return;
		}

		// Check if it's an existing suggestion
		const existingSuggestion = suggestions.find(
			s => s.name.toLowerCase() === trimmedName.toLowerCase()
		);

		if (existingSuggestion) {
			// Use existing label
			onChange([...selectedLabels, existingSuggestion]);
		} else {
			// Create new label (without id - will be created on backend)
			const newLabel = { name: trimmedName };
			onChange([...selectedLabels, newLabel]);
		}

		setInputValue("");
		setShowSuggestions(false);
		setSuggestions([]);
	};

	const removeLabel = (labelToRemove) => {
		onChange(selectedLabels.filter(l => 
			l.id !== labelToRemove.id || l.name !== labelToRemove.name
		));
	};

	const handleSuggestionClick = (suggestion) => {
		addLabel(suggestion.name);
	};

	return (
		<div ref={containerRef} className="relative w-full">
			<div className="flex flex-wrap gap-2 items-center min-h-[2.5rem] border rounded px-2 py-1 bg-white">
				{selectedLabels.map((label, index) => (
					<span
						key={label.id || label.name || index}
						className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-100 text-blue-800 rounded text-sm"
					>
						{label.name}
						<button
							type="button"
							onClick={() => removeLabel(label)}
							className="text-blue-600 hover:text-blue-800 focus:outline-none"
							aria-label={`Remove label ${label.name}`}
						>
							×
						</button>
					</span>
				))}
				<input
					ref={inputRef}
					type="text"
					value={inputValue}
					onChange={handleInputChange}
					onKeyDown={handleKeyDown}
					onFocus={() => {
						if (inputValue.trim()) {
							setShowSuggestions(true);
							searchLabels(inputValue);
						}
					}}
					placeholder={selectedLabels.length === 0 ? placeholder : ""}
					className="flex-1 min-w-[120px] outline-none text-sm"
				/>
			</div>
			{showSuggestions && (suggestions.length > 0 || loading || inputValue.trim()) && (
				<div className="absolute z-50 bg-white border rounded shadow-lg max-h-48 overflow-auto w-full mt-1">
					{loading ? (
						<div className="px-2 py-1 text-xs text-gray-500">Loading...</div>
					) : suggestions.length > 0 ? (
						suggestions.map((suggestion) => (
							<div
								key={suggestion.id || suggestion.name}
								className="px-2 py-1 text-sm cursor-pointer hover:bg-blue-50"
								onMouseDown={(e) => e.preventDefault()}
								onClick={() => handleSuggestionClick(suggestion)}
							>
								{suggestion.name}
							</div>
						))
					) : (
						<div className="px-2 py-1 text-xs text-gray-500">
							Press Enter to create "{inputValue.trim()}"
						</div>
					)}
				</div>
			)}
		</div>
	);
}

