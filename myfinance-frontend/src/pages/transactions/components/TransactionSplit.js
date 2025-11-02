import React, { useState, useEffect } from "react";
import SearchSelect from "./SearchSelect";
import { buildTree, flattenCategories } from "../utils/utils";

export default function TransactionSplit({ transaction, setTransaction, onCancel, onSubmit, categories }) {
	const [children, setChildren] = useState(() =>
		(transaction?.children || []).map((c) => ({
			...c,
			categoryId: c.category?.id || "",
		}))
	);

	// Update children if transaction changes
	useEffect(() => {
		if (transaction?.children) {
			setChildren(
				transaction.children.map((c) => ({
					...c,
					categoryId: c.category?.id || "",
				}))
			);
		}
	}, [transaction]);

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
		setChildren([
			...children,
			{
				description: "",
				amount: "",
				categoryId: "",
			},
		]);
	};

	const treeCategories = buildTree(categories);
	const rootHome = treeCategories.filter((cat) => cat?.name === "Home");
	const limitedTree = rootHome.length > 0 ? rootHome : treeCategories;
	const flattened = flattenCategories(limitedTree);

	const updateChild = (index, key, value) => {
		const updated = [...children];
		updated[index][key] = value;
		setChildren(updated);
	};

	const submit = () => {
		const total = children.reduce((sum, c) => sum + parseFloat(c.amount || 0), 0);
		const parentAmt = parseFloat(transaction.amount);
		if (isNaN(parentAmt) || Math.abs(total - parentAmt) > 1) {
			alert(
				`Child transaction amounts must sum up to ₹${isNaN(parentAmt) ? 0 : parentAmt
				}. Entered total: ₹${total}`
			);
			return;
		}
		const enrichedChildren = children.map((c) => ({
			...c,
			date: transaction.date,
			type: transaction.type,
			accountId: transaction.accountId,
		}));
		onSubmit({
			...transaction,
			children: enrichedChildren,
		});
	};

	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={(e) => {
					e.preventDefault();
					submit();
				}}
				className="bg-white p-6 rounded shadow-lg w-full max-w-2xl space-y-4"
			>
				<h3 className="text-lg font-semibold">Split Transaction</h3>

				<div className="grid grid-cols-3 gap-2 font-bold text-sm">
					<span>Description</span>
					<span>Amount</span>
					<span>Category</span>
				</div>

				{children.map((child, idx) => (
					<div key={idx} className="grid grid-cols-3 gap-2">
						<input
							type="text"
							value={child.description}
							onChange={(e) => updateChild(idx, "description", e.target.value)}
							className="border rounded px-2 py-1"
						/>
						<input
							type="number"
							value={child.amount}
							onChange={(e) => updateChild(idx, "amount", e.target.value)}
							className="border rounded px-2 py-1"
						/>
					<SearchSelect
						options={flattened.map(c => ({ id: c.id, name: c.name }))}
						value={child.categoryId || ""}
						onChange={(val) => updateChild(idx, "categoryId", val)}
						placeholder="Category"
					/>
					</div>
				))}

				<button
					type="button"
					onClick={addChild}
					className="bg-gray-200 px-4 py-1 rounded text-sm"
				>
					+ Add Child
				</button>

				<div className="flex justify-end space-x-2">
					<button type="button" onClick={onCancel} className="px-4 py-1">
						Cancel
					</button>
					<button type="submit" className="bg-blue-600 text-white px-4 py-1 rounded">
						Split
					</button>
				</div>
			</form>
		</div>
	);
}

