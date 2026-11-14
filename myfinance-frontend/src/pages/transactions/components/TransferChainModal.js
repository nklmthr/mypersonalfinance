import React, { useEffect, useState } from "react";
import api from "../../../auth/api";
import { getCurrencySymbol } from "../utils/currency";

function ChainNode({ tx, index, total }) {
	if (!tx) return null;
	const isDebit = tx.type === "DEBIT";
	const isFirst = index === 0;
	const isLast = index === total - 1;

	return (
		<div className="flex flex-col items-stretch">
			<div
				className={`border rounded-lg overflow-hidden bg-white ${
					isDebit ? "border-red-200" : "border-green-200"
				}`}
			>
				<div
					className={`px-3 py-2 border-b flex items-center justify-between ${
						isDebit ? "bg-red-50 border-red-200" : "bg-green-50 border-green-200"
					}`}
				>
					<div>
						<div
							className={`text-xs font-semibold uppercase tracking-wide ${
								isDebit ? "text-red-700" : "text-green-700"
							}`}
						>
							{isFirst ? "Source" : isLast ? "Destination" : "Intermediate"}
						</div>
						<div className="font-semibold text-gray-900 text-sm">
							{tx.account?.name || "Unknown account"}
						</div>
					</div>
					<span
						className={`text-base font-bold tabular-nums ${
							isDebit ? "text-red-600" : "text-green-600"
						}`}
					>
						{getCurrencySymbol(tx.currency)}
						{(typeof tx.amount === "number" ? tx.amount : 0).toLocaleString("en-IN", {
							minimumFractionDigits: 2,
						})}
					</span>
				</div>
				<div className="px-3 py-2 text-xs text-gray-700 space-y-0.5">
					<div className="truncate">{tx.description || "—"}</div>
					{tx.explanation && <div className="text-gray-500 italic">{tx.explanation}</div>}
					<div className="text-gray-400">
						{tx.date
							? new Date(tx.date).toLocaleString("en-IN", {
									year: "numeric",
									month: "short",
									day: "numeric",
									hour: "2-digit",
									minute: "2-digit",
								})
							: ""}
					</div>
				</div>
			</div>
			{index < total - 1 && (
				<div className="flex justify-center py-1 text-purple-400 text-lg select-none">↓</div>
			)}
		</div>
	);
}

/**
 * Fetches and displays the full transfer chain starting from any node.
 * Traverses: linkedTransferId links + transferSplits recursively.
 * Props: startTx (the transaction the badge was clicked on)
 */
export default function TransferChainModal({ startTx }) {
	const [chain, setChain] = useState(null);
	const [error, setError] = useState(null);

	useEffect(() => {
		let cancelled = false;

		async function fetchFull(id) {
			const res = await api.get(`/transactions/${id}`);
			return res.data;
		}

		async function buildChain() {
			try {
				// Walk up to find the root DEBIT of the chain
				async function findRoot(tx) {
					// If this tx is a credit that has a linkedTransferId, its pair debit may
					// itself be a destination split — keep walking up
					if (tx.linkedTransferId) {
						const linked = await fetchFull(tx.linkedTransferId);
						if (linked.type === "DEBIT") {
							// linked is the debit; check if that debit is itself a split destination
							const linkedFull = await fetchFull(linked.id);
							// If the debit account matches the credit account, it's a chained debit
							// (created when a credit was used as source). Walk up from it.
							if (linkedFull.linkedTransferId) {
								const higher = await findRoot(linkedFull);
								if (higher) return higher;
							}
							return linkedFull;
						}
					}
					return null;
				}

				// Start from the clicked tx; fetch full detail
				let full = await fetchFull(startTx.id);

				// If credit with linkedTransferId, try to find the root debit
				if (full.type === "CREDIT" && full.linkedTransferId) {
					const root = await findRoot(full);
					if (root) full = root;
				}

				// Now walk DOWN the chain from root
				const visited = new Set();
				const nodes = [];

				async function traverse(tx) {
					if (visited.has(tx.id)) return;
					visited.add(tx.id);
					nodes.push(tx);

					// New model: transferSplits
					if (tx.transferSplits && tx.transferSplits.length > 0) {
						for (const split of tx.transferSplits) {
							const dest = split.destTransaction;
							if (dest) {
								const destFull = await fetchFull(dest.id);
								await traverse(destFull);
							}
						}
					} else if (tx.linkedTransferId) {
						// Legacy 1-to-1
						const linked = await fetchFull(tx.linkedTransferId);
						if (!visited.has(linked.id)) {
							visited.add(linked.id);
							nodes.push(linked);
							// If the credit has its own linkedTransferId pointing to a chained debit
							if (linked.linkedTransferId && !visited.has(linked.linkedTransferId)) {
								const chainedDebit = await fetchFull(linked.linkedTransferId);
								await traverse(chainedDebit);
							}
						}
					}
				}

				await traverse(full);

				if (!cancelled) setChain(nodes);
			} catch (err) {
				if (!cancelled)
					setError(err.response?.data?.message || err.message || "Failed to load chain");
			}
		}

		buildChain();
		return () => { cancelled = true; };
	}, [startTx.id]);

	if (error) {
		return <div className="text-sm text-red-600 p-4">{error}</div>;
	}

	if (!chain) {
		return (
			<div className="flex items-center justify-center py-8 text-sm text-gray-500">
				Loading transfer chain…
			</div>
		);
	}

	const totalOut = chain
		.filter((t) => t.type === "DEBIT")
		.reduce((s, t) => s + (typeof t.amount === "number" ? t.amount : 0), 0);

	return (
		<div className="w-full space-y-2">
			<div className="bg-gradient-to-r from-purple-50 to-indigo-50 border border-purple-200 rounded-lg px-3 py-2 flex items-center justify-between gap-2">
				<div className="text-sm font-semibold text-gray-800">
					Transfer chain · {chain.length} transaction{chain.length !== 1 ? "s" : ""}
				</div>
				{totalOut > 0 && (
					<div className="text-sm font-bold tabular-nums text-gray-900">
						{getCurrencySymbol(chain[0]?.currency)}
						{totalOut.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
					</div>
				)}
			</div>

			<div className="space-y-0">
				{chain.map((tx, i) => (
					<ChainNode key={tx.id} tx={tx} index={i} total={chain.length} />
				))}
			</div>
		</div>
	);
}
