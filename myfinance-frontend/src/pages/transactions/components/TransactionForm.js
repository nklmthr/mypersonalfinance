import React, { useState, useEffect } from "react";
import dayjs from "dayjs";
import SearchSelect from "./SearchSelect";
import { buildTree, flattenCategories } from "../utils/utils";

export default function TransactionForm({
	transaction,
	setTransaction,
	onCancel,
	onSubmit,
	accounts,
	categories,
	mode,
}) {
	// Local state for date input to handle typing vs validation for automation tools
	const [dateInputValue, setDateInputValue] = useState(
		transaction.date ? dayjs(transaction.date).format("DD/MM/YYYY HH:mm") : ""
	);
	const [errors, setErrors] = useState({});

	// Keep local input in sync when transaction changes (edit mode)
	useEffect(() => {
		setDateInputValue(transaction.date ? dayjs(transaction.date).format("DD/MM/YYYY HH:mm") : "");
	}, [transaction.date]);

	useEffect(() => {
		const handler = (e) => e.key === "Escape" && onCancel();
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [onCancel]);

	const treeCategories = buildTree(categories);
	const rootHomeForm = treeCategories.filter((cat) => cat?.name === "Home");
	const limitedTreeForm = rootHomeForm.length > 0 ? rootHomeForm : treeCategories;
	const flattened = flattenCategories(limitedTreeForm);

	const submit = () => {
		// Validate required fields
		const newErrors = {};
		if (!dateInputValue.trim()) newErrors.date = "Required";
		if (!transaction.accountId) newErrors.accountId = "Required";
		if (transaction.amount === undefined || transaction.amount === null || String(transaction.amount).trim() === "") newErrors.amount = "Required";
		if (!transaction.currency) newErrors.currency = "Required";
		if (!transaction.categoryId) newErrors.categoryId = "Required";
		if (!transaction.type) newErrors.type = "Required";
		if (!transaction.description || !transaction.description.trim()) newErrors.description = "Required";

		if (Object.keys(newErrors).length > 0) {
			setErrors(newErrors);
			const fieldMap = { date: "Date", accountId: "Account", amount: "Amount", currency: "Currency", categoryId: "Category", type: "Type", description: "Description" };
			const missing = Object.keys(newErrors).map((k) => fieldMap[k]).join(", ");
			alert(`Please fill required fields: ${missing}`);
			return;
		}

		let finalDate = transaction.date;
		if (dateInputValue.trim()) {
			try {
				let parsedDate;
				const value = dateInputValue.trim();
				// Try DD/MM/YYYY HH:MM
				if (value.match(/^\d{1,2}\/\d{1,2}\/\d{4}\s\d{1,2}:\d{2}$/)) {
					const [datePart, timePart] = value.split(' ');
					const [day, month, year] = datePart.split('/');
					const [hour, minute] = timePart.split(':');
					parsedDate = dayjs(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T${hour.padStart(2, '0')}:${minute}:00`);
				}
				// DD/MM/YYYY (no time)
				else if (value.match(/^\d{1,2}\/\d{1,2}\/\d{4}$/)) {
					const [day, month, year] = value.split('/');
					parsedDate = dayjs(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T12:00:00`);
				}
				// YYYY-MM-DD HH:MM
				else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}\s\d{1,2}:\d{2}$/)) {
					const [datePart, timePart] = value.split(' ');
					const [hour, minute] = timePart.split(':');
					parsedDate = dayjs(`${datePart}T${hour.padStart(2, '0')}:${minute}:00`);
				}
				// YYYY-MM-DDTHH:MM
				else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}T\d{1,2}:\d{2}$/)) {
					parsedDate = dayjs(`${value}:00`);
				}
				// YYYY-MM-DD (no time)
				else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}$/)) {
					parsedDate = dayjs(`${value}T12:00:00`);
				}
				else {
					parsedDate = dayjs(value);
				}

				if (parsedDate.isValid()) {
					finalDate = parsedDate.format("YYYY-MM-DDTHH:mm:ss");
				}
			} catch (error) {
				// ignore parsing error and fall back to transaction.date
			}
		}
		onSubmit({
			...transaction,
			amount: parseFloat(transaction.amount),
			date: finalDate,
		});
	};

	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={(e) => {
					e.preventDefault();
					submit();
				}}
				className="bg-white p-6 rounded shadow-lg w-full max-w-xl space-y-4"
			>
				<h3 className="text-lg font-semibold">
					{mode === "add" ? "Add" : "Edit"} Transaction
				</h3>

			{/* Date */}
			<div>
			<label className="block text-sm font-medium mb-1">Date & Time<span className="text-red-600">*</span></label>
				<input
					type="text"
					placeholder="DD/MM/YYYY HH:MM or YYYY-MM-DD HH:MM"
					value={dateInputValue}
					onChange={(e) => { setDateInputValue(e.target.value); setErrors((er) => ({ ...er, date: undefined })); }}
					onBlur={(e) => {
						const value = e.target.value.trim();
						if (!value) {
							setTransaction((t) => ({ ...t, date: "" }));
							return;
						}
						try {
							let parsedDate;
							if (value.match(/^\d{1,2}\/\d{1,2}\/\d{4}\s\d{1,2}:\d{2}$/)) {
								const [datePart, timePart] = value.split(' ');
								const [day, month, year] = datePart.split('/');
								const [hour, minute] = timePart.split(':');
								parsedDate = dayjs(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T${hour.padStart(2, '0')}:${minute}:00`);
							}
							else if (value.match(/^\d{1,2}\/\d{1,2}\/\d{4}$/)) {
								const [day, month, year] = value.split('/');
								parsedDate = dayjs(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T12:00:00`);
							}
							else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}\s\d{1,2}:\d{2}$/)) {
								const [datePart, timePart] = value.split(' ');
								const [hour, minute] = timePart.split(':');
								parsedDate = dayjs(`${datePart}T${hour.padStart(2, '0')}:${minute}:00`);
							}
							else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}T\d{1,2}:\d{2}$/)) {
								parsedDate = dayjs(`${value}:00`);
							}
							else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}$/)) {
								parsedDate = dayjs(`${value}T12:00:00`);
							}
							else {
								parsedDate = dayjs(value);
							}
							if (parsedDate.isValid()) {
								setTransaction((t) => ({ ...t, date: parsedDate.format("YYYY-MM-DDTHH:mm:ss") }));
								setDateInputValue(parsedDate.format("DD/MM/YYYY HH:mm"));
							} else {
								e.target.style.borderColor = "red";
								setTimeout(() => { e.target.style.borderColor = ""; }, 2000);
							}
						} catch (error) {
							e.target.style.borderColor = "red";
							setTimeout(() => { e.target.style.borderColor = ""; }, 2000);
						}
					}}
					className={`w-full border rounded px-3 py-2 ${errors.date ? 'border-red-500' : ''}`}
					required
				/>
				{errors.date && (<div className="text-xs text-red-600 mt-1">Required</div>)}
				<p className="text-xs text-gray-500 mt-1">
					Accepted: "28/09/2025 14:30", "28/09/2025", "2025-09-28 14:30", "2025-09-28T14:30"
				</p>
			</div>

			{/* Account, Amount & Currency */}
			<div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
				<div>
					<label className="block text-sm font-medium mb-1">Account<span className="text-red-600">*</span></label>
				<SearchSelect
					options={[{ id: "", name: "All Accounts" }, ...accounts.map(a => ({ id: a.id, name: a.name }))]}
					value={transaction.accountId || ""}
					onChange={(val) => { setTransaction((t) => ({ ...t, accountId: val })); setErrors((e) => ({ ...e, accountId: undefined })); }}
					placeholder="Account"
					error={Boolean(errors.accountId)}
					disabled={!!(transaction.parentId || transaction.parent)}
				/>
				{errors.accountId && (<div className="text-xs text-red-600 mt-1">Required</div>)}
				{(transaction.parentId || transaction.parent) && (
					<div className="text-xs text-gray-600 mt-1">⚠️ Child transaction accounts cannot be modified.</div>
				)}
				</div>
				<div>
					<label className="block text-sm font-medium mb-1">Amount<span className="text-red-600">*</span></label>
					<input
						type="number"
						placeholder="Amount"
						value={transaction.amount}
						onChange={(e) => { setTransaction((t) => ({ ...t, amount: e.target.value })); setErrors((er) => ({ ...er, amount: undefined })); }}
						className={`w-full border rounded px-3 py-2 ${errors.amount ? 'border-red-500' : ''} ${transaction.parentId || transaction.parent ? 'bg-gray-100 cursor-not-allowed' : ''}`}
						disabled={!!(transaction.parentId || transaction.parent)}
						title={transaction.parentId || transaction.parent ? "Cannot change amount of a child transaction" : ""}
						required
					/>
					{errors.amount && (<div className="text-xs text-red-600 mt-1">Required</div>)}
					{(transaction.parentId || transaction.parent) && (
						<div className="text-xs text-gray-600 mt-1">⚠️ Child transaction amounts cannot be modified. Edit the parent split instead.</div>
					)}
				</div>
					<div>
						<label className="block text-sm font-medium mb-1">Currency<span className="text-red-600">*</span></label>
						<select
							className={`w-full border rounded px-3 py-2 ${errors.currency ? 'border-red-500' : ''}`}
							value={transaction.currency || "INR"}
							onChange={(e) => { setTransaction((t) => ({ ...t, currency: e.target.value })); setErrors((er) => ({ ...er, currency: undefined })); }}
						>
							<option value="INR">INR</option>
							<option value="USD">USD</option>
							<option value="EUR">EUR</option>
							<option value="GBP">GBP</option>
							<option value="JPY">JPY</option>
							<option value="AUD">AUD</option>
							<option value="CAD">CAD</option>
							<option value="CNY">CNY</option>
							<option value="SGD">SGD</option>
							<option value="AED">AED</option>
						</select>
						{errors.currency && (<div className="text-xs text-red-600 mt-1">Required</div>)}
					</div>
				</div>

				{/* Category & Transaction Type */}
				<div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
					<div>
					<label className="block text-sm font-medium mb-1">Category<span className="text-red-600">*</span></label>
					<SearchSelect
						options={flattened.map(c => ({ id: c.id, name: c.name }))}
						value={transaction.categoryId || ""}
						onChange={(val) => { setTransaction((t) => ({ ...t, categoryId: val })); setErrors((e) => ({ ...e, categoryId: undefined })); }}
						placeholder="Category"
						error={Boolean(errors.categoryId)}
					/>
					{errors.categoryId && (<div className="text-xs text-red-600 mt-1">Required</div>)}
					</div>
				<div>
					<label className="block text-sm font-medium mb-1">Type<span className="text-red-600">*</span></label>
					<select
						className={`w-full border rounded px-3 py-2 ${errors.type ? 'border-red-500' : ''} ${transaction.parentId || transaction.parent ? 'bg-gray-100 cursor-not-allowed' : ''}`}
						value={transaction.type || "DEBIT"}
						onChange={(e) => { setTransaction((t) => ({ ...t, type: e.target.value })); setErrors((er) => ({ ...er, type: undefined })); }}
						disabled={!!(transaction.parentId || transaction.parent)}
						title={transaction.parentId || transaction.parent ? "Cannot change type of a child transaction" : ""}
					>
						<option value="DEBIT">Debit</option>
						<option value="CREDIT">Credit</option>
					</select>
					{errors.type && (<div className="text-xs text-red-600 mt-1">Required</div>)}
					{(transaction.parentId || transaction.parent) && (
						<div className="text-xs text-gray-600 mt-1">⚠️ Child transaction types cannot be modified.</div>
					)}
				</div>
				</div>

				{/* Description */}
				<div>
				<label className="block text-sm font-medium mb-1">Description<span className="text-red-600">*</span></label>
					<input
						type="text"
						placeholder="Description"
						value={transaction.description || ""}
					onChange={(e) => { setTransaction((t) => ({ ...t, description: e.target.value })); setErrors((er) => ({ ...er, description: undefined })); }}
					className={`w-full border rounded px-3 py-2 ${errors.description ? 'border-red-500' : ''}`}
						required
					/>
				{errors.description && (<div className="text-xs text-red-600 mt-1">Required</div>)}
				</div>

				{/* Explanation */}
				<div>
					<label className="block text-sm font-medium mb-1">Explanation</label>
					<textarea
						placeholder="Explanation (optional)"
						value={transaction.explanation || ""}
						onChange={(e) =>
							setTransaction((t) => ({ ...t, explanation: e.target.value }))
						}
						className="w-full border rounded px-3 py-2"
					/>
				</div>

				{/* Actions */}
				<div className="flex justify-end space-x-2">
					<button
						type="button"
						onClick={onCancel}
						className="px-4 py-1 border rounded"
					>
						Cancel
					</button>
					<button type="submit" className="bg-blue-600 text-white px-4 py-1 rounded">
						{mode === "add" ? "Add" : "Save"}
					</button>
				</div>
			</form>
		</div>
	);
}

