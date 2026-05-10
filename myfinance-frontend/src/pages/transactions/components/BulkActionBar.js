import React, { useState } from "react";
import SearchSelect from "./SearchSelect";
import LabelInput from "./LabelInput";

/**
 * Sticky bottom bar shown whenever ≥1 transaction is selected. Lets the user
 * apply a category or labels (add/replace) to the entire selection.
 *
 * Props:
 * - selectedCount: number of currently selected transactions
 * - selectedIds: Set<string> | string[] (only used by the "Apply" callbacks)
 * - flattenedCategories: list of {id, name} for the category picker
 * - onClear():           clear the selection
 * - onApplyCategory({ ids, categoryId, categoryName }) → Promise<{updated, requested, skipped}>
 * - onApplyLabels({ ids, labels, mode }) → Promise<{updated, requested, skipped}>
 *   where labels is an array of label objects ({id?, name}) and mode is 'ADD' | 'REPLACE'
 */
export default function BulkActionBar({
	selectedCount,
	selectedIds,
	flattenedCategories,
	onClear,
	onApplyCategory,
	onApplyLabels,
}) {
	const [activePanel, setActivePanel] = useState(null); // 'category' | 'labels' | null
	const [pendingCategoryId, setPendingCategoryId] = useState("");
	const [pendingLabels, setPendingLabels] = useState([]);
	const [labelMode, setLabelMode] = useState("ADD");
	const [busy, setBusy] = useState(false);
	const [confirm, setConfirm] = useState(null); // { kind, payload, summary }
	const [result, setResult] = useState(null);

	if (!selectedCount) return null;

	const ids = Array.isArray(selectedIds) ? selectedIds : Array.from(selectedIds || []);

	const closePanels = () => {
		setActivePanel(null);
		setPendingCategoryId("");
		setPendingLabels([]);
		setLabelMode("ADD");
	};

	const requestApplyCategory = () => {
		if (!pendingCategoryId) return;
		const cat = flattenedCategories.find((c) => c.id === pendingCategoryId);
		setConfirm({
			kind: "category",
			summary: `Assign category "${cat?.name || pendingCategoryId}" to ${selectedCount} selected transaction${selectedCount === 1 ? "" : "s"}?`,
			payload: { ids, categoryId: pendingCategoryId, categoryName: cat?.name || "" },
		});
	};

	const requestApplyLabels = () => {
		if (labelMode === "ADD" && pendingLabels.length === 0) return;
		const labelText = pendingLabels.length
			? pendingLabels.map((l) => l.name).join(", ")
			: "(none)";
		const verb = labelMode === "REPLACE" ? "Replace labels with" : "Add labels";
		setConfirm({
			kind: "labels",
			summary: `${verb} ${labelText} on ${selectedCount} selected transaction${selectedCount === 1 ? "" : "s"}?`,
			payload: { ids, labels: pendingLabels, mode: labelMode },
		});
	};

	const runConfirmed = async () => {
		if (!confirm) return;
		setBusy(true);
		try {
			let res;
			if (confirm.kind === "category") {
				res = await onApplyCategory(confirm.payload);
			} else if (confirm.kind === "labels") {
				res = await onApplyLabels(confirm.payload);
			}
			setResult({ kind: confirm.kind, ...res });
		} catch (err) {
			console.error("Bulk operation failed", err);
			setResult({
				kind: confirm.kind,
				updated: 0,
				requested: ids.length,
				skipped: [],
				error:
					err?.response?.data?.error ||
					err?.response?.data?.message ||
					err?.message ||
					"Bulk operation failed",
			});
		} finally {
			setBusy(false);
			setConfirm(null);
		}
	};

	const closeResult = () => {
		setResult(null);
		closePanels();
	};

	return (
		<>
			<div className="w-full my-2">
				<div className="bg-gray-900 text-white rounded-lg shadow-md border border-gray-700">
					{activePanel === "category" && (
						<div className="p-3 border-b border-gray-700 bg-gray-800">
							<div className="flex items-center gap-2 mb-2">
								<span className="text-xs font-semibold uppercase tracking-wide text-gray-300">
									Assign category
								</span>
							</div>
							<div className="flex flex-wrap items-center gap-2">
								<div className="flex-1 min-w-[200px]">
									<SearchSelect
										options={flattenedCategories.map((c) => ({ id: c.id, name: c.name }))}
										value={pendingCategoryId}
										onChange={(val) => setPendingCategoryId(val)}
										placeholder="Pick a category…"
									/>
								</div>
								<button
									type="button"
									onClick={requestApplyCategory}
									disabled={!pendingCategoryId || busy}
									className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-3 py-1.5 rounded text-sm"
								>
									Apply
								</button>
								<button
									type="button"
									onClick={closePanels}
									className="bg-gray-700 hover:bg-gray-600 text-white px-3 py-1.5 rounded text-sm"
								>
									Cancel
								</button>
							</div>
						</div>
					)}

					{activePanel === "labels" && (
						<div className="p-3 border-b border-gray-700 bg-gray-800">
							<div className="flex items-center justify-between gap-2 mb-2">
								<span className="text-xs font-semibold uppercase tracking-wide text-gray-300">
									{labelMode === "REPLACE" ? "Replace labels" : "Add labels"}
								</span>
								<div className="inline-flex rounded overflow-hidden border border-gray-600 text-xs">
									<button
										type="button"
										onClick={() => setLabelMode("ADD")}
										className={`px-2 py-0.5 ${labelMode === "ADD" ? "bg-blue-600 text-white" : "bg-gray-700 text-gray-300"}`}
									>
										Add
									</button>
									<button
										type="button"
										onClick={() => setLabelMode("REPLACE")}
										className={`px-2 py-0.5 ${labelMode === "REPLACE" ? "bg-blue-600 text-white" : "bg-gray-700 text-gray-300"}`}
									>
										Replace
									</button>
								</div>
							</div>
							<div className="bg-white text-gray-900 rounded p-2">
								<LabelInput
									value={pendingLabels}
									onChange={setPendingLabels}
									placeholder={labelMode === "REPLACE" ? "Type labels (empty = clear all)…" : "Type labels to add…"}
								/>
							</div>
							<div className="flex items-center gap-2 mt-2 justify-end">
								<button
									type="button"
									onClick={requestApplyLabels}
									disabled={busy || (labelMode === "ADD" && pendingLabels.length === 0)}
									className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-3 py-1.5 rounded text-sm"
								>
									Apply
								</button>
								<button
									type="button"
									onClick={closePanels}
									className="bg-gray-700 hover:bg-gray-600 text-white px-3 py-1.5 rounded text-sm"
								>
									Cancel
								</button>
							</div>
						</div>
					)}

					<div className="flex items-center gap-3 px-3 py-2">
						<span className="text-sm font-medium">
							{selectedCount} selected
						</span>
						<div className="h-5 w-px bg-gray-600" />
						<button
							type="button"
							onClick={() =>
								setActivePanel((p) => (p === "category" ? null : "category"))
							}
							className={`text-sm px-3 py-1 rounded ${activePanel === "category" ? "bg-blue-600" : "bg-gray-700 hover:bg-gray-600"}`}
						>
							Category…
						</button>
						<button
							type="button"
							onClick={() =>
								setActivePanel((p) => (p === "labels" ? null : "labels"))
							}
							className={`text-sm px-3 py-1 rounded ${activePanel === "labels" ? "bg-blue-600" : "bg-gray-700 hover:bg-gray-600"}`}
						>
							Labels…
						</button>
						<button
							type="button"
							onClick={() => {
								closePanels();
								onClear();
							}}
							className="text-sm text-gray-300 hover:text-white ml-auto"
						>
							Clear
						</button>
					</div>
				</div>
			</div>

			{confirm && (
				<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
					<div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
						<h2 className="text-lg font-semibold text-gray-900 mb-2">Confirm bulk update</h2>
						<p className="text-sm text-gray-700 mb-6">{confirm.summary}</p>
						<div className="flex gap-2 justify-end">
							<button
								type="button"
								onClick={() => setConfirm(null)}
								disabled={busy}
								className="px-4 py-2 text-sm border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50"
							>
								Cancel
							</button>
							<button
								type="button"
								onClick={runConfirmed}
								disabled={busy}
								className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
							>
								{busy ? "Applying…" : "Apply"}
							</button>
						</div>
					</div>
				</div>
			)}

			{result && (
				<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
					<div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
						<h2 className="text-lg font-semibold text-gray-900 mb-2">Bulk update result</h2>
						{result.error ? (
							<p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded px-3 py-2 mb-4">
								{result.error}
							</p>
						) : (
							<p className="text-sm text-gray-700 mb-4">
								Updated <strong>{result.updated}</strong> of{" "}
								<strong>{result.requested}</strong> transactions
								{result.skipped && result.skipped.length > 0 && (
									<>. Skipped <strong>{result.skipped.length}</strong>.</>
								)}
							</p>
						)}
						{result.skipped && result.skipped.length > 0 && (
							<div className="mb-4 max-h-48 overflow-y-auto border border-gray-200 rounded p-2 bg-gray-50">
								<ul className="text-xs text-gray-700 space-y-1">
									{result.skipped.map((s) => (
										<li key={s.id} className="truncate">
											<span className="text-gray-400 mr-1">·</span>
											<span className="font-mono text-gray-500">{s.id.slice(0, 8)}</span>{" "}
											{s.reason}
										</li>
									))}
								</ul>
							</div>
						)}
						<div className="flex justify-end">
							<button
								type="button"
								onClick={closeResult}
								className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
							>
								Close
							</button>
						</div>
					</div>
				</div>
			)}
		</>
	);
}
