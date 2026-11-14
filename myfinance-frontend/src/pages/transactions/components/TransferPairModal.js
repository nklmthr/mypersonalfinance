import React from "react";
import { getCurrencySymbol } from "../utils/currency";

function TransferLeg({ tx, label, accent }) {
	const isDebit = tx?.type === "DEBIT";
	const border = accent === "from" ? "border-red-200" : "border-green-200";
	const headerBg = accent === "from" ? "bg-red-50" : "bg-green-50";
	const headerText = accent === "from" ? "text-red-800" : "text-green-800";

	if (!tx) {
		return (
			<div className={`border ${border} rounded-lg p-3 bg-gray-50 min-h-[8rem] flex items-center justify-center`}>
				<span className="text-sm text-gray-400 italic">Not available</span>
			</div>
		);
	}

	return (
		<div className={`border ${border} rounded-lg overflow-hidden bg-white`}>
			<div className={`${headerBg} px-3 py-2 border-b ${border}`}>
				<div className={`text-xs font-semibold uppercase tracking-wide ${headerText}`}>{label}</div>
				<div className="font-semibold text-gray-900 text-sm truncate" title={tx.account?.name}>
					{tx.account?.name || "Unknown account"}
				</div>
			</div>
			<div className="p-3 space-y-2 text-sm">
				<div className="flex items-baseline justify-between gap-2">
					<span
						className={`text-lg font-bold tabular-nums ${isDebit ? "text-red-600" : "text-green-600"}`}
					>
						{getCurrencySymbol(tx.currency)}
						{(typeof tx.amount === "number" ? tx.amount : 0).toLocaleString("en-IN", {
							minimumFractionDigits: 2,
						})}
					</span>
					<span
						className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
							isDebit ? "bg-red-100 text-red-800" : "bg-green-100 text-green-800"
						}`}
					>
						{tx.type}
					</span>
				</div>
				<div>
					<div className="text-xs text-gray-500 uppercase tracking-wide">Description</div>
					<p className="text-gray-800 break-words">{tx.description || "—"}</p>
				</div>
				{tx.explanation && (
					<div>
						<div className="text-xs text-gray-500 uppercase tracking-wide">Explanation</div>
						<p className="text-gray-700 text-xs break-words">{tx.explanation}</p>
					</div>
				)}
				<div className="grid grid-cols-2 gap-2 pt-1 border-t border-gray-100">
					<div>
						<div className="text-xs text-gray-500 uppercase tracking-wide">Category</div>
						<div className="text-gray-800 text-xs">{tx.category?.name || "—"}</div>
					</div>
					<div>
						<div className="text-xs text-gray-500 uppercase tracking-wide">Date</div>
						<div className="text-gray-800 text-xs">
							{tx.date
								? new Date(tx.date).toLocaleString("en-IN", {
										year: "numeric",
										month: "short",
										day: "numeric",
										hour: "2-digit",
										minute: "2-digit",
									})
								: "—"}
						</div>
					</div>
				</div>
			</div>
		</div>
	);
}

/**
 * View of a transfer: one source leg + one or more destination legs.
 * Accepts either:
 *   - Legacy: { fromTx, toTx }  (single destination, backward compat)
 *   - New model: { fromTx, toTxList }  (multiple destinations as TransferSplitDTO[])
 */
export default function TransferPairModal({ fromTx, toTx, toTxList }) {
	// Normalize: toTxList takes priority; fall back to legacy toTx wrapped in array
	const destinations = toTxList?.length
		? toTxList
		: toTx
		? [{ destTransaction: toTx, amount: toTx?.amount }]
		: [];

	const currency = fromTx?.currency;
	const totalAmount = destinations.reduce((s, d) => {
		const amt = d.destTransaction ? d.amount : (d.amount ?? 0);
		return s + (typeof amt === "number" ? amt : Number(amt) || 0);
	}, 0);

	const destNames = destinations.map((d) => d.destTransaction?.account?.name).filter(Boolean);

	return (
		<div className="w-full space-y-3">
			<div className="bg-gradient-to-r from-purple-50 to-indigo-50 border border-purple-200 rounded-lg px-3 py-2 flex flex-wrap items-center justify-between gap-2">
				<div className="text-sm text-gray-800">
					<span className="font-semibold">Transfer</span>
					{fromTx?.account?.name && destNames.length > 0 && (
						<span className="text-gray-600">
							{" "}· {fromTx.account.name}
							<span className="mx-1 text-purple-500">→</span>
							{destNames.join(", ")}
						</span>
					)}
				</div>
				{totalAmount > 0 && (
					<div className="text-sm font-bold tabular-nums text-gray-900">
						{getCurrencySymbol(currency)}
						{totalAmount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
					</div>
				)}
			</div>

			<div className={`grid gap-3 ${destinations.length > 1 ? "grid-cols-1" : "grid-cols-1 md:grid-cols-2"}`}>
				<TransferLeg tx={fromTx} label="From" accent="from" />
				{destinations.map((d, i) => (
					<TransferLeg
						key={i}
						tx={d.destTransaction ?? d}
						label={destinations.length > 1 ? `To (${i + 1})` : "To"}
						accent="to"
					/>
				))}
			</div>
		</div>
	);
}
