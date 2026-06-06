import React, { useState, useEffect, useMemo } from "react";
import SearchSelect from "./SearchSelect";
import { buildTree, flattenCategories } from "../utils/utils";

export default function TransactionSplit({ transaction, setTransaction, onCancel, onSubmit, categories }) {
	// "Not Classified" is a system category seeded for every user
	// (see src/main/resources/sql/categories.sql). We resolve it by name from
	// the categories prop so we don't have to hard-code an id (each user has
	// their own row). Falls back to empty string if not found, in which case
	// the prepopulated child still works — the user just has to pick a
	// category before submitting.
	const notClassifiedCategoryId = useMemo(() => {
		const match = (categories || []).find((c) => c?.name === "Not Classified");
		return match?.id || "";
	}, [categories]);

	// Build the catchall "Unknown / Not Classified" row that absorbs whatever
	// portion of the parent amount the user hasn't yet allocated to a specific
	// child. The internal `_autoBalance` flag marks this row so the rebalance
	// logic below can find / replace / move it without confusing it with a
	// row the user manually named "Unknown". The flag is stripped at submit.
	const buildAutoBalanceRow = (amount) => ({
		description: "Unknown",
		amount: amount !== undefined && amount !== null && amount !== ""
			? (typeof amount === "number" ? amount.toFixed(2) : String(amount))
			: "",
		categoryId: notClassifiedCategoryId,
		_autoBalance: true,
	});

	/**
	 * Re-place the auto-balance Unknown row.
	 *
	 * Strips any existing auto-balance row from the list, sums the remaining
	 * (manual) rows, and appends a fresh auto-balance row at the end if the
	 * parent amount is still under-allocated. If the manual rows already cover
	 * the parent (within ₹0.01) or over-allocate it, no auto row is added —
	 * the user is then on the hook to rebalance manually, and the existing
	 * "Balance Remaining" / submit-time validation will flag mismatches.
	 *
	 * The result is that the catchall row always reflects "what's left" after
	 * every keystroke without us having to touch the manual rows the user is
	 * actively editing.
	 */
	const rebalanceAutoRow = (rows, parentAmount) => {
		const nonAuto = (rows || []).filter((r) => !r?._autoBalance);
		if (parentAmount === null || parentAmount === undefined || isNaN(parentAmount)) {
			return nonAuto;
		}
		const sum = nonAuto.reduce((acc, r) => acc + (parseFloat(r?.amount) || 0), 0);
		const remainder = parentAmount - sum;
		// 1 paisa epsilon — same tolerance the submit validation uses.
		if (remainder > 0.01) {
			return [...nonAuto, buildAutoBalanceRow(remainder)];
		}
		return nonAuto;
	};

	const parentAmountNum = parseFloat(transaction?.amount);

	const [children, setChildren] = useState(() => {
		const existingChildren = (transaction?.children || []).map((c) => ({
			...c,
			categoryId: c.category?.id || "",
		}));

		// First time opening: rebalance from an empty list seeds the
		// catchall row at the full parent amount.
		if (existingChildren.length === 0) {
			return rebalanceAutoRow([], parentAmountNum);
		}
		// Re-opening an already-split transaction: trust the existing rows.
		// We don't auto-add a catchall here because a previously-saved split
		// is, by construction, balanced; an auto row would only confuse.
		return existingChildren;
	});

	// Re-sync when the parent transaction prop changes (e.g. the user closed
	// the modal and opened it on a different row). Also re-runs once the
	// "Not Classified" category id resolves from the categories prop, so the
	// seeded row picks up the real id even if categories arrive async.
	useEffect(() => {
		if (transaction?.children && transaction.children.length > 0) {
			setChildren(
				transaction.children.map((c) => ({
					...c,
					categoryId: c.category?.id || "",
				}))
			);
		} else {
			setChildren(rebalanceAutoRow([], parentAmountNum));
		}
		// rebalanceAutoRow / parentAmountNum are intentionally omitted —
		// they're derived from `transaction` and recomputed on every render,
		// so depending on `transaction` is sufficient and avoids feedback
		// loops. (The project's lint config doesn't enforce exhaustive-deps,
		// but documenting the omission anyway.)
	}, [transaction, notClassifiedCategoryId]);

	// Escape key handler
	useEffect(() => {
		const handler = (e) => {
			if (e.key === "Escape") {
				onCancel();
			}
		};
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [onCancel]);

	const addChild = () => {
		setChildren((prev) => {
			// Insert a fresh empty row, then let rebalanceAutoRow re-place
			// the catchall at the end. This keeps the visual order
			// "manual rows on top, Unknown catchall at the bottom".
			const empty = { description: "", amount: "", categoryId: "" };
			const nonAuto = prev.filter((r) => !r._autoBalance);
			return rebalanceAutoRow([...nonAuto, empty], parentAmountNum);
		});
	};

	const treeCategories = buildTree(categories);
	const rootHome = treeCategories.filter((cat) => cat?.name === "Home");
	const limitedTree = rootHome.length > 0 ? rootHome : treeCategories;
	const flattened = flattenCategories(limitedTree);

	const updateChild = (index, key, value) => {
		setChildren((prev) => {
			const next = [...prev];
			const target = { ...next[index], [key]: value };
			// Editing the auto-balance row means the user is taking ownership
			// of it (typing a custom name, amount, or category). Clear the
			// flag so rebalanceAutoRow no longer treats it as the catchall;
			// it will then create a *new* catchall below to absorb whatever
			// remainder the user has left.
			if (next[index]?._autoBalance) {
				delete target._autoBalance;
			}
			next[index] = target;
			return rebalanceAutoRow(next, parentAmountNum);
		});
	};

	const deleteChild = (index) => {
		setChildren((prev) => {
			// Allow deleting any row, including the catchall if the user
			// explicitly wants to clear it. rebalanceAutoRow will recreate
			// the catchall on the next change if there's still a remainder,
			// so the user can never lock themselves into an unbalanced state
			// just by deleting.
			if (prev.length <= 1) {
				alert("You must have at least one child transaction");
				return prev;
			}
			const updated = prev.filter((_, idx) => idx !== index);
			return rebalanceAutoRow(updated, parentAmountNum);
		});
	};

	const submit = () => {
		// Filter out empty children (no description and no amount)
		const validChildren = children.filter(c => c.description.trim() || c.amount);
		
		if (validChildren.length === 0) {
			alert("Please add at least one child transaction with description or amount");
			return;
		}
		
		const total = validChildren.reduce((sum, c) => sum + parseFloat(c.amount || 0), 0);
		const parentAmt = parseFloat(transaction.amount);
		if (isNaN(parentAmt) || Math.abs(total - parentAmt) > 1) {
			alert(
				`Child transaction amounts must sum up to ₹${isNaN(parentAmt) ? 0 : parentAmt
				}. Entered total: ₹${total}`
			);
			return;
		}
		// Strip the internal _autoBalance marker before sending to the
		// backend — it's purely a UI bookkeeping flag and the API doesn't
		// know (or need to know) about it.
		const enrichedChildren = validChildren.map((c) => {
			const rest = { ...c };
			delete rest._autoBalance;
			return {
				...rest,
				date: transaction.date,
				type: transaction.type,
				accountId: transaction.accountId,
			};
		});
		onSubmit({
			...transaction,
			children: enrichedChildren,
		});
	};

	// Calculate current sum of child amounts
	const currentSum = children.reduce((sum, c) => sum + parseFloat(c.amount || 0), 0);
	const parentAmount = parseFloat(transaction.amount || 0);
	// Balance remaining = how much of the parent amount is NOT yet covered by
	// child entries. Positive means the user still needs to allocate more;
	// negative means they've over-allocated. Displayed alongside Current Sum
	// so the user can see at a glance how close they are to a valid split.
	const balanceRemaining = parentAmount - currentSum;

	const formatINR = (value) =>
		value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={(e) => {
					e.preventDefault();
					submit();
				}}
				className="bg-white p-6 rounded shadow-lg w-full max-w-2xl space-y-4"
			>
				<div className="flex justify-between items-start gap-4 flex-wrap">
					<h3 className="text-lg font-semibold">Split Transaction</h3>
					<div className="text-sm text-right space-y-0.5">
						<div>
							<span className="text-gray-600">Current Sum: </span>
							<span className={`font-semibold ${
								Math.abs(balanceRemaining) <= 1
									? 'text-green-600'
									: 'text-red-600'
							}`}>
								₹{formatINR(currentSum)}
							</span>
							<span className="text-gray-600"> / ₹{formatINR(parentAmount)}</span>
						</div>
						<div>
							<span className="text-gray-600">Balance Remaining: </span>
							<span className={`font-semibold ${
								Math.abs(balanceRemaining) <= 1
									? 'text-green-600'
									: balanceRemaining < 0
										? 'text-red-600'
										: 'text-yellow-700'
							}`}>
								₹{formatINR(balanceRemaining)}
							</span>
						</div>
					</div>
				</div>

				<div className="grid grid-cols-[1fr_1fr_1fr_auto] gap-2 font-bold text-sm">
					<span>Description</span>
					<span>Amount</span>
					<span>Category</span>
					<span></span>
				</div>

				{children.map((child, idx) => (
					<div key={idx} className="grid grid-cols-[1fr_1fr_1fr_auto] gap-2 items-center">
						<input
							type="text"
							value={child.description}
							onChange={(e) => updateChild(idx, "description", e.target.value)}
							className="border rounded px-2 py-1"
							placeholder="Description"
						/>
						<input
							type="number"
							value={child.amount}
							onChange={(e) => updateChild(idx, "amount", e.target.value)}
							className="border rounded px-2 py-1"
							placeholder="0.00"
						/>
					<SearchSelect
						options={flattened.map(c => ({ id: c.id, name: c.name }))}
						value={child.categoryId || ""}
						onChange={(val) => updateChild(idx, "categoryId", val)}
						placeholder="Category"
					/>
					<button
						type="button"
						onClick={() => deleteChild(idx)}
						className="text-red-600 hover:text-red-800 px-2 py-1 rounded hover:bg-red-50 transition-colors"
						title="Delete this child transaction"
					>
						🗑️
					</button>
					</div>
				))}

				<button
					type="button"
					onClick={addChild}
					className="bg-gray-200 px-4 py-1 rounded text-sm hover:bg-gray-300 transition-colors"
				>
					+ Add Child
				</button>

				<div className="flex justify-end space-x-2">
					<button type="button" onClick={onCancel} className="px-4 py-1 hover:bg-gray-100 rounded transition-colors">
						Cancel
					</button>
					<button type="submit" className="bg-blue-600 text-white px-4 py-1 rounded hover:bg-blue-700 transition-colors">
						Split
					</button>
				</div>
			</form>
		</div>
	);
}

