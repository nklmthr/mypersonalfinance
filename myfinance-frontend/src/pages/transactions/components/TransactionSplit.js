import React, { useState, useEffect } from "react";
import SearchSelect from "./SearchSelect";
import { buildTree, flattenCategories } from "../utils/utils";

export default function TransactionSplit({ transaction, setTransaction, onCancel, onSubmit, categories }) {
	const [children, setChildren] = useState(() => {
		const existingChildren = (transaction?.children || []).map((c) => ({
			...c,
			categoryId: c.category?.id || "",
		}));
		
		// If no existing children, add one empty child by default
		if (existingChildren.length === 0) {
			return [{
				description: "",
				amount: "",
				categoryId: "",
			}];
		}
		
		return existingChildren;
	});

	// Update children if transaction changes
	useEffect(() => {
		if (transaction?.children && transaction.children.length > 0) {
			setChildren(
				transaction.children.map((c) => ({
					...c,
					categoryId: c.category?.id || "",
				}))
			);
		} else {
			// Add one empty child by default if none exist
			setChildren([{
				description: "",
				amount: "",
				categoryId: "",
			}]);
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
		
		// If user starts typing in description and this is the last child, add a new empty child
		if (key === "description" && value.length > 0 && index === children.length - 1) {
			const lastChild = children[children.length - 1];
			// Only add if the last child has some content
			if (lastChild.description === "" && lastChild.amount === "" && lastChild.categoryId === "") {
				updated.push({
					description: "",
					amount: "",
					categoryId: "",
				});
			}
		}
		
		setChildren(updated);
	};

	const deleteChild = (index) => {
		// Prevent deleting if only one child remains
		if (children.length <= 1) {
			alert("You must have at least one child transaction");
			return;
		}
		const updated = children.filter((_, idx) => idx !== index);
		setChildren(updated);
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
		const enrichedChildren = validChildren.map((c) => ({
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

	// Calculate current sum of child amounts
	const currentSum = children.reduce((sum, c) => sum + parseFloat(c.amount || 0), 0);

	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={(e) => {
					e.preventDefault();
					submit();
				}}
				className="bg-white p-6 rounded shadow-lg w-full max-w-2xl space-y-4"
			>
				<div className="flex justify-between items-center">
					<h3 className="text-lg font-semibold">Split Transaction</h3>
					<div className="text-sm">
						<span className="text-gray-600">Current Sum: </span>
						<span className={`font-semibold ${
							Math.abs(currentSum - parseFloat(transaction.amount || 0)) <= 1 
								? 'text-green-600' 
								: 'text-red-600'
						}`}>
							₹{currentSum.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
						</span>
						<span className="text-gray-600"> / ₹{parseFloat(transaction.amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
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

