import React, { useEffect } from "react";
import SearchSelect from "./SearchSelect";

export default function TransferForm({ transaction, setTransaction, onCancel, onSubmit, accounts }) {
	useEffect(() => {
		const handler = (e) => e.key === "Escape" && onCancel();
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [onCancel]);

	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={(e) => {
					e.preventDefault();
					onSubmit();
				}}
				className="bg-white p-6 rounded shadow-lg w-full max-w-md space-y-4"
			>
				<h3 className="text-lg font-semibold">Transfer Funds</h3>

				<div>
					<span className="text-sm">From Account</span>
					<div className="mt-1 block w-full border rounded px-2 py-1 bg-gray-100">
						{accounts.find((a) => a.id === transaction.accountId)?.name || "Unknown"}
					</div>
				</div>
				<div>
					<span className="text-sm">Amount</span>
					<div className="mt-1 block w-full border rounded px-2 py-1 bg-gray-100">
						₹{transaction.amount}
					</div>
				</div>
				<label className="block">
					<span className="text-sm">To Account</span>
					<SearchSelect
						options={[{ id: "", name: "— Select —" }, ...accounts.map(a => ({ id: a.id, name: a.name }))]}
						value={transaction.destinationAccountId || ""}
						onChange={(val) => setTransaction((tx) => ({ ...tx, destinationAccountId: val }))}
						placeholder="To Account"
					/>
				</label>

				<label className="block">
					<span className="text-sm">Explanation</span>
					<input
						type="text"
						className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.explanation || ""}
						onChange={(e) =>
							setTransaction((tx) => ({ ...tx, explanation: e.target.value }))
						}
					/>
				</label>

				<div className="flex justify-end space-x-2">
					<button type="button" onClick={onCancel} className="px-4 py-1">
						Cancel
					</button>
					<button type="submit" className="bg-blue-600 text-white px-4 py-1 rounded">
						Transfer
					</button>
				</div>
			</form>
		</div>
	);
}

