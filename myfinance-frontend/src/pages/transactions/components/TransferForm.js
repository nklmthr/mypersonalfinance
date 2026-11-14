import React, { useEffect, useState } from "react";
import SearchSelect from "./SearchSelect";

export default function TransferForm({ transaction, onCancel, onSubmit, accounts, loading }) {
	const [destinations, setDestinations] = useState([
		{ accountId: "", amount: transaction.amount ?? "", explanation: "" },
	]);

	useEffect(() => {
		const handler = (e) => e.key === "Escape" && onCancel();
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [onCancel]);

	const sourceAmount = parseFloat(transaction.amount || 0);
	const totalAllocated = destinations.reduce((s, d) => s + parseFloat(d.amount || 0), 0);
	const remaining = Math.round((sourceAmount - totalAllocated) * 100) / 100;
	const isBalanced = Math.abs(remaining) < 0.01;
	const usedAccountIds = new Set(destinations.map((d) => d.accountId).filter(Boolean));
	const hasDuplicates = usedAccountIds.size < destinations.filter((d) => d.accountId).length;
	const canSubmit = isBalanced && destinations.every((d) => d.accountId) && !hasDuplicates && !loading;

	const updateRow = (index, field, value) => {
		setDestinations((prev) => {
			const next = [...prev];
			next[index] = { ...next[index], [field]: value };
			return next;
		});
	};

	const addRow = () => {
		if (remaining <= 0) return;
		setDestinations((prev) => [...prev, { accountId: "", amount: remaining, explanation: "" }]);
	};

	const removeRow = (index) =>
		setDestinations((prev) => prev.filter((_, i) => i !== index));

	const handleSubmit = (e) => {
		e.preventDefault();
		if (!canSubmit) return;
		onSubmit({
			sourceTransactionId: transaction.id,
			destinations: destinations.map((d) => ({
				accountId: d.accountId,
				amount: parseFloat(d.amount),
				explanation: d.explanation || "",
			})),
		});
	};

	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={handleSubmit}
				className="bg-white p-6 rounded shadow-lg w-full max-w-lg space-y-4 max-h-[90vh] overflow-y-auto"
			>
				<h3 className="text-lg font-semibold">Transfer Funds</h3>

				{/* Source info */}
				<div className="grid grid-cols-2 gap-3">
					<div>
						<span className="text-sm text-gray-600">From Account</span>
						<div className="mt-1 block w-full border rounded px-2 py-1 bg-gray-100 text-sm">
							{accounts.find((a) => a.id === transaction.accountId)?.name || "Unknown"}
						</div>
					</div>
					<div>
						<span className="text-sm text-gray-600">Amount</span>
						<div className="mt-1 block w-full border rounded px-2 py-1 bg-gray-100 text-sm">
							₹{Number(transaction.amount).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
						</div>
					</div>
				</div>

				{/* Destinations */}
				<div className="space-y-3">
					<div className="flex items-center justify-between">
						<span className="text-sm font-medium text-gray-700">Destinations</span>
						<button
							type="button"
							onClick={addRow}
							disabled={remaining <= 0}
							className={`text-xs ${remaining > 0 ? "text-blue-600 hover:text-blue-800" : "text-gray-400 cursor-not-allowed"}`}
						>
							+ Add destination
						</button>
					</div>

					{destinations.map((dest, i) => (
						<div key={i} className="border rounded p-3 space-y-2 bg-gray-50">
							<div className="flex items-center justify-between">
								<span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
									Destination {i + 1}
								</span>
								{destinations.length > 1 && (
									<button
										type="button"
										onClick={() => removeRow(i)}
										className="text-xs text-red-500 hover:text-red-700"
									>
										Remove
									</button>
								)}
							</div>
							<div>
								<span className="text-xs text-gray-600">To Account</span>
								<SearchSelect
									options={[
										{ id: "", name: "— Select —" },
										...accounts
											.filter((a) => a.id === dest.accountId || !usedAccountIds.has(a.id))
											.map((a) => ({ id: a.id, name: a.name })),
									]}
									value={dest.accountId}
									onChange={(val) => updateRow(i, "accountId", val)}
									placeholder="Select account…"
								/>
							</div>
							<div className="grid grid-cols-2 gap-2">
								<div>
									<span className="text-xs text-gray-600">Amount (₹)</span>
									<input
										type="number"
										min="0.01"
										step="0.01"
										className="mt-1 block w-full border rounded px-2 py-1 text-sm"
										value={dest.amount}
										onChange={(e) => updateRow(i, "amount", e.target.value)}
										placeholder="0.00"
									/>
								</div>
								<div>
									<span className="text-xs text-gray-600">Explanation</span>
									<input
										type="text"
										className="mt-1 block w-full border rounded px-2 py-1 text-sm"
										value={dest.explanation}
										onChange={(e) => updateRow(i, "explanation", e.target.value)}
										placeholder="Optional"
									/>
								</div>
							</div>
						</div>
					))}
				</div>

				{/* Balance indicator */}
				<div className={`flex items-center justify-between text-sm font-medium rounded px-3 py-2 ${
					isBalanced ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700"
				}`}>
					<span>Remaining</span>
					<span>
						{remaining >= 0 ? "₹" : "-₹"}
						{Math.abs(remaining).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
					</span>
				</div>

				<div className="flex justify-end space-x-2 pt-1">
					<button type="button" onClick={onCancel} disabled={loading} className="px-4 py-1 text-sm border rounded">
						Cancel
					</button>
					<button
						type="submit"
						disabled={!canSubmit}
						className="bg-blue-600 text-white px-4 py-1 rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed"
					>
						{loading ? "Transferring…" : "Transfer"}
					</button>
				</div>
			</form>
		</div>
	);
}
