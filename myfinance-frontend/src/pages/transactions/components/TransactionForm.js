import React, { useState, useEffect } from "react";
import dayjs from "dayjs";
import SearchSelect from "./SearchSelect";
import LabelInput from "./LabelInput";
import { buildTree, flattenCategories } from "../utils/utils";

const inputClass =
	"w-full h-10 border border-gray-300 rounded-lg px-3 text-sm bg-white text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition box-border";
const textareaClass =
	"w-full border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition box-border resize-y min-h-[4rem]";
const searchSelectClass =
	"!h-10 !px-3 !rounded-lg border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 box-border";
const inputErrorClass = "border-red-500 focus:ring-red-500 focus:border-red-500";
const labelClass = "block text-sm font-medium text-gray-700 mb-1.5";

export default function TransactionForm({
	transaction,
	setTransaction,
	onCancel,
	onSubmit,
	accounts,
	categories,
	mode,
}) {
	const [errors, setErrors] = useState({});

	const datePart = transaction.date ? dayjs(transaction.date).format("YYYY-MM-DD") : "";
	const timePart = transaction.date ? dayjs(transaction.date).format("HH:mm") : "";

	useEffect(() => {
		const handler = (e) => e.key === "Escape" && onCancel();
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [onCancel]);

	const treeCategories = buildTree(categories);
	const rootHomeForm = treeCategories.filter((cat) => cat?.name === "Home");
	const limitedTreeForm = rootHomeForm.length > 0 ? rootHomeForm : treeCategories;
	const flattened = flattenCategories(limitedTreeForm);

	const updateDateTime = (nextDate, nextTime) => {
		setErrors((er) => ({ ...er, date: undefined }));
		if (!nextDate) {
			setTransaction((t) => ({ ...t, date: "" }));
			return;
		}
		const time = nextTime || "12:00";
		const parsed = dayjs(`${nextDate}T${time}:00`);
		if (parsed.isValid()) {
			setTransaction((t) => ({ ...t, date: parsed.format("YYYY-MM-DDTHH:mm:ss") }));
		}
	};

	const setToNow = () => {
		const now = dayjs();
		setErrors((er) => ({ ...er, date: undefined }));
		setTransaction((t) => ({ ...t, date: now.format("YYYY-MM-DDTHH:mm:ss") }));
	};

	const submit = () => {
		const newErrors = {};
		if (!transaction.date) newErrors.date = "Required";
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

		const finalDate = dayjs(transaction.date).isValid()
			? dayjs(transaction.date).format("YYYY-MM-DDTHH:mm:ss")
			: transaction.date;

		onSubmit({
			...transaction,
			amount: parseFloat(transaction.amount),
			date: finalDate,
		});
	};

	const isChild = !!(transaction.parentId || transaction.parent);

	return (
		<div className="fixed inset-0 bg-black/50 backdrop-blur-[1px] flex items-center justify-center z-50 p-4">
			<form
				onSubmit={(e) => {
					e.preventDefault();
					submit();
				}}
				className="bg-white rounded-xl shadow-2xl w-full max-w-xl overflow-hidden"
			>
				{/* Header */}
				<div className="px-6 py-4 border-b border-gray-100 bg-gradient-to-r from-slate-50 to-blue-50">
					<h3 className="text-lg font-semibold text-gray-900">
						{mode === "add" ? "Add Transaction" : "Edit Transaction"}
					</h3>
					<p className="text-sm text-gray-500 mt-0.5">
						{mode === "add" ? "Create a new transaction record" : "Update transaction details"}
					</p>
				</div>

				<div className="px-6 py-5 space-y-5 max-h-[calc(100vh-12rem)] overflow-y-auto">
					{/* Date & Time */}
					<div>
						<div className="flex items-center justify-between mb-1.5">
							<label className="text-sm font-medium text-gray-700">
								Date & Time<span className="text-red-600">*</span>
							</label>
							<button
								type="button"
								onClick={setToNow}
								className="text-xs font-medium text-blue-600 hover:text-blue-800 hover:underline"
							>
								Set to now
							</button>
						</div>
						<div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
							<div>
								<label className="block text-xs text-gray-500 mb-1">Date</label>
								<input
									type="date"
									value={datePart}
									onChange={(e) => updateDateTime(e.target.value, timePart || "12:00")}
									className={`${inputClass} ${errors.date ? inputErrorClass : ""}`}
									required
								/>
							</div>
							<div>
								<label className="block text-xs text-gray-500 mb-1">Time</label>
								<input
									type="time"
									value={timePart}
									onChange={(e) => updateDateTime(datePart || dayjs().format("YYYY-MM-DD"), e.target.value)}
									className={`${inputClass} ${errors.date ? inputErrorClass : ""}`}
									required
								/>
							</div>
						</div>
						{errors.date && <div className="text-xs text-red-600 mt-1.5">Required</div>}
						{datePart && timePart && (
							<p className="text-xs text-gray-500 mt-1.5">
								{dayjs(`${datePart}T${timePart}`).format("ddd, D MMM YYYY · HH:mm")}
							</p>
						)}
					</div>

					{/* Account, Amount & Currency */}
					<div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
						<div>
							<label className={labelClass}>Account<span className="text-red-600">*</span></label>
							<SearchSelect
								options={[{ id: "", name: "All Accounts" }, ...accounts.map(a => ({ id: a.id, name: a.name }))]}
								value={transaction.accountId || ""}
								onChange={(val) => { setTransaction((t) => ({ ...t, accountId: val })); setErrors((e) => ({ ...e, accountId: undefined })); }}
								placeholder="Account"
								error={Boolean(errors.accountId)}
								disabled={isChild}
								className={searchSelectClass}
							/>
							{errors.accountId && <div className="text-xs text-red-600 mt-1">Required</div>}
							{isChild && (
								<div className="text-xs text-amber-700 mt-1.5">Child transaction accounts cannot be modified.</div>
							)}
						</div>
						<div>
							<label className={labelClass}>Amount<span className="text-red-600">*</span></label>
							<input
								type="number"
								placeholder="0.00"
								value={transaction.amount}
								onChange={(e) => { setTransaction((t) => ({ ...t, amount: e.target.value })); setErrors((er) => ({ ...er, amount: undefined })); }}
								className={`${inputClass} ${errors.amount ? inputErrorClass : ""} ${isChild ? "bg-gray-100 cursor-not-allowed" : ""}`}
								disabled={isChild}
								title={isChild ? "Cannot change amount of a child transaction" : ""}
								required
							/>
							{errors.amount && <div className="text-xs text-red-600 mt-1">Required</div>}
							{isChild && (
								<div className="text-xs text-amber-700 mt-1.5">Edit the parent split to change amount.</div>
							)}
						</div>
						<div>
							<label className={labelClass}>Currency<span className="text-red-600">*</span></label>
							<select
								className={`${inputClass} ${errors.currency ? inputErrorClass : ""}`}
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
							{errors.currency && <div className="text-xs text-red-600 mt-1">Required</div>}
						</div>
					</div>

					{/* Category & Type */}
					<div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
						<div>
							<label className={labelClass}>Category<span className="text-red-600">*</span></label>
							<SearchSelect
								options={flattened.map(c => ({ id: c.id, name: c.name }))}
								value={transaction.categoryId || ""}
								onChange={(val) => { setTransaction((t) => ({ ...t, categoryId: val })); setErrors((e) => ({ ...e, categoryId: undefined })); }}
								placeholder="Category"
								error={Boolean(errors.categoryId)}
								className={searchSelectClass}
							/>
							{errors.categoryId && <div className="text-xs text-red-600 mt-1">Required</div>}
						</div>
						<div>
							<label className={labelClass}>Type<span className="text-red-600">*</span></label>
							<select
								className={`${inputClass} ${errors.type ? inputErrorClass : ""} ${isChild ? "bg-gray-100 cursor-not-allowed" : ""}`}
								value={transaction.type || "DEBIT"}
								onChange={(e) => { setTransaction((t) => ({ ...t, type: e.target.value })); setErrors((er) => ({ ...er, type: undefined })); }}
								disabled={isChild}
								title={isChild ? "Cannot change type of a child transaction" : ""}
							>
								<option value="DEBIT">Debit</option>
								<option value="CREDIT">Credit</option>
							</select>
							{errors.type && <div className="text-xs text-red-600 mt-1">Required</div>}
							{isChild && (
								<div className="text-xs text-amber-700 mt-1.5">Child transaction types cannot be modified.</div>
							)}
						</div>
					</div>

					{/* Description */}
					<div>
						<label className={labelClass}>Description<span className="text-red-600">*</span></label>
						<input
							type="text"
							placeholder="Description"
							value={transaction.description || ""}
							onChange={(e) => { setTransaction((t) => ({ ...t, description: e.target.value })); setErrors((er) => ({ ...er, description: undefined })); }}
							className={`${inputClass} ${errors.description ? inputErrorClass : ""}`}
							required
						/>
						{errors.description && <div className="text-xs text-red-600 mt-1">Required</div>}
					</div>

					{/* Explanation */}
					<div>
						<label className={labelClass}>Explanation</label>
						<textarea
							placeholder="Optional notes about this transaction"
							value={transaction.explanation || ""}
							onChange={(e) => setTransaction((t) => ({ ...t, explanation: e.target.value }))}
							rows={2}
							className={textareaClass}
						/>
					</div>

					{/* Labels */}
					<div>
						<label className={labelClass}>Labels</label>
						<LabelInput
							value={transaction.labels || []}
							onChange={(labels) => setTransaction((t) => ({ ...t, labels }))}
							placeholder="Add labels..."
						/>
					</div>
				</div>

				{/* Actions */}
				<div className="px-6 py-4 border-t border-gray-100 bg-gray-50 flex justify-end gap-3">
					<button
						type="button"
						onClick={onCancel}
						className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-gray-300 transition"
					>
						Cancel
					</button>
					<button
						type="submit"
						className="px-5 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-blue-500 shadow-sm transition"
					>
						{mode === "add" ? "Add Transaction" : "Save Changes"}
					</button>
				</div>
			</form>
		</div>
	);
}
