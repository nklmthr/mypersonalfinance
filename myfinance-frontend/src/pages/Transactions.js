import React, { useEffect, useState } from "react";
import api from "../auth/api";
import dayjs from "dayjs";
import { useSearchParams } from "react-router-dom";
import Papa from "papaparse";
import * as XLSX from "xlsx";
import { saveAs } from "file-saver";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import useDebounce from "../hooks/useDebounce";

// ---- helpers: tree + flatten ----
function buildTree(categories) {
	const map = {};
	const roots = [];

	(categories || []).forEach((cat) => {
		map[cat.id] = { ...cat, children: [] };
	});

	(categories || []).forEach((cat) => {
		if (cat.parentId) {
			map[cat.parentId]?.children.push(map[cat.id]);
		} else {
			roots.push(map[cat.id]);
		}
	});

	return roots;
}

function flattenCategories(categories, prefix = "") {
	let flat = [];
	for (const c of categories || []) {
		flat.push({ id: c.id, name: prefix + c.name });
		if (c.children?.length > 0) {
			flat = flat.concat(flattenCategories(c.children, prefix + "— "));
		}
	}
	return flat;
}

// ---- forms ----
function TransactionForm({
	transaction,
	setTransaction,
	onCancel,
	onSubmit,
	accounts,
	categories,
	mode,
}) {
	// Local state for date input to handle typing vs validation
	const [dateInputValue, setDateInputValue] = useState(
		transaction.date ? dayjs(transaction.date).format("DD/MM/YYYY HH:mm") : ""
	);

	// Update local date input when transaction changes (for edit mode)
	useEffect(() => {
		setDateInputValue(transaction.date ? dayjs(transaction.date).format("DD/MM/YYYY HH:mm") : "");
	}, [transaction.date]);

	useEffect(() => {
		const handler = (e) => e.key === "Escape" && onCancel();
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [onCancel]);

	const treeCategories = buildTree(categories);
	const flattened = flattenCategories(treeCategories);

	const submit = () => {
		// Parse the date from local state before submitting
		let finalDate = transaction.date;
		
		console.log("Submit - Original transaction.date:", transaction.date);
		console.log("Submit - Current dateInputValue:", dateInputValue);
		
		if (dateInputValue.trim()) {
			try {
				let parsedDate;
				const value = dateInputValue.trim();
				
				console.log("Attempting to parse date:", value);
				
				// Try DD/MM/YYYY HH:MM format first (most common)
				if (value.match(/^\d{1,2}\/\d{1,2}\/\d{4}\s\d{1,2}:\d{2}$/)) {
					console.log("Matching DD/MM/YYYY HH:MM format");
					// Manual parsing for DD/MM/YYYY HH:MM
					const [datePart, timePart] = value.split(' ');
					const [day, month, year] = datePart.split('/');
					const [hour, minute] = timePart.split(':');
					parsedDate = dayjs(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T${hour.padStart(2, '0')}:${minute}:00`);
				}
				// Try DD/MM/YYYY format (without time)
				else if (value.match(/^\d{1,2}\/\d{1,2}\/\d{4}$/)) {
					console.log("Matching DD/MM/YYYY format");
					const [day, month, year] = value.split('/');
					parsedDate = dayjs(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T12:00:00`);
				}
				// Try YYYY-MM-DD HH:MM format
				else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}\s\d{1,2}:\d{2}$/)) {
					console.log("Matching YYYY-MM-DD HH:MM format");
					const [datePart, timePart] = value.split(' ');
					const [hour, minute] = timePart.split(':');
					parsedDate = dayjs(`${datePart}T${hour.padStart(2, '0')}:${minute}:00`);
				}
				// Try YYYY-MM-DD format (without time)
				else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}$/)) {
					console.log("Matching YYYY-MM-DD format");
					parsedDate = dayjs(`${value}T12:00:00`);
				}
				// Try natural language parsing as fallback
				else {
					console.log("Using natural language parsing");
					parsedDate = dayjs(value);
				}
				
				console.log("Parsed dayjs object:", parsedDate);
				console.log("Is valid:", parsedDate.isValid());
				
				if (parsedDate.isValid()) {
					finalDate = parsedDate.format("YYYY-MM-DDTHH:mm:ss");
					console.log("Final formatted date:", finalDate);
				} else {
					console.log("Invalid parsed date, keeping original:", transaction.date);
				}
			} catch (error) {
				console.log("Date parsing error on submit:", error);
			}
		} else {
			console.log("Empty dateInputValue, using original date:", transaction.date);
		}
		
		console.log("Final date being submitted:", finalDate);
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
					<label className="block text-sm font-medium mb-1">Date & Time</label>
					<input
						type="text"
						placeholder="DD/MM/YYYY HH:MM (e.g., 28/09/2025 14:30)"
						value={dateInputValue}
						onChange={(e) => setDateInputValue(e.target.value)}
						onBlur={(e) => {
							const value = e.target.value.trim();
							if (!value) {
								setTransaction((t) => ({ ...t, date: "" }));
								return;
							}

							try {
								let parsedDate;
								
								// Try DD/MM/YYYY HH:MM format first (most common)
								if (value.match(/^\d{1,2}\/\d{1,2}\/\d{4}\s\d{1,2}:\d{2}$/)) {
									// Manual parsing for DD/MM/YYYY HH:MM
									const [datePart, timePart] = value.split(' ');
									const [day, month, year] = datePart.split('/');
									const [hour, minute] = timePart.split(':');
									parsedDate = dayjs(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T${hour.padStart(2, '0')}:${minute}:00`);
								}
								// Try DD/MM/YYYY format (without time)
								else if (value.match(/^\d{1,2}\/\d{1,2}\/\d{4}$/)) {
									const [day, month, year] = value.split('/');
									parsedDate = dayjs(`${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}T12:00:00`);
								}
								// Try YYYY-MM-DD HH:MM format
								else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}\s\d{1,2}:\d{2}$/)) {
									const [datePart, timePart] = value.split(' ');
									const [hour, minute] = timePart.split(':');
									parsedDate = dayjs(`${datePart}T${hour.padStart(2, '0')}:${minute}:00`);
								}
								// Try YYYY-MM-DD format (without time)
								else if (value.match(/^\d{4}-\d{1,2}-\d{1,2}$/)) {
									parsedDate = dayjs(`${value}T12:00:00`);
								}
								// Try natural language parsing as fallback
								else {
									parsedDate = dayjs(value);
								}
								
								if (parsedDate.isValid()) {
									setTransaction((t) => ({ 
										...t, 
										date: parsedDate.format("YYYY-MM-DDTHH:mm:ss")
									}));
									// Update the local state to show the formatted date
									setDateInputValue(parsedDate.format("DD/MM/YYYY HH:mm"));
								} else {
									// Show error styling or reset to original value
									e.target.style.borderColor = "red";
									setTimeout(() => {
										e.target.style.borderColor = "";
									}, 2000);
								}
							} catch (error) {
								console.log("Date parsing error:", error);
								e.target.style.borderColor = "red";
								setTimeout(() => {
									e.target.style.borderColor = "";
								}, 2000);
							}
						}}
						className="w-full border rounded px-3 py-2"
						required
					/>
					<p className="text-xs text-gray-500 mt-1">
						💡 Formats: "28/09/2025 14:30", "28/09/2025", "2025-09-28 14:30", or natural language
					</p>
				</div>

				{/* Account & Amount */}
				<div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
					<div>
						<label className="block text-sm font-medium mb-1">Account</label>
						<select
							className="w-full border rounded px-3 py-2"
							value={transaction.accountId || ""}
							onChange={(e) =>
								setTransaction((t) => ({ ...t, accountId: e.target.value }))
							}
							required
						>
							<option value="">— Select Account —</option>
							{accounts.map((a) => (
								<option key={a.id} value={a.id}>
									{a.name}
								</option>
							))}
						</select>
					</div>
					<div>
						<label className="block text-sm font-medium mb-1">Amount</label>
						<input
							type="number"
							placeholder="Amount"
							value={transaction.amount}
							onChange={(e) =>
								setTransaction((t) => ({ ...t, amount: e.target.value }))
							}
							className="w-full border rounded px-3 py-2"
							required
						/>
					</div>
				</div>

				{/* Category & Transaction Type */}
				<div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
					<div>
						<label className="block text-sm font-medium mb-1">Category</label>
						<select
							className="w-full border rounded px-3 py-2"
							value={transaction.categoryId || ""}
							onChange={(e) =>
								setTransaction((t) => ({ ...t, categoryId: e.target.value }))
							}
						>
							<option value="">— Select Category —</option>
							{flattened.map((c) => (
								<option key={c.id} value={c.id}>
									{c.name}
								</option>
							))}
						</select>
					</div>
					<div>
						<label className="block text-sm font-medium mb-1">Type</label>
						<select
							className="w-full border rounded px-3 py-2"
							value={transaction.type || "DEBIT"}
							onChange={(e) =>
								setTransaction((t) => ({ ...t, type: e.target.value }))
							}
						>
							<option value="DEBIT">Debit</option>
							<option value="CREDIT">Credit</option>
						</select>
					</div>
				</div>

				{/* Description */}
				<div>
					<label className="block text-sm font-medium mb-1">Description</label>
					<input
						type="text"
						placeholder="Description"
						value={transaction.description || ""}
						onChange={(e) =>
							setTransaction((t) => ({ ...t, description: e.target.value }))
						}
						className="w-full border rounded px-3 py-2"
						required
					/>
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

function TransferForm({ transaction, setTransaction, onCancel, onSubmit, accounts }) {
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
					<select
						className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.destinationAccountId || ""}
						onChange={(e) =>
							setTransaction((tx) => ({ ...tx, destinationAccountId: e.target.value }))
						}
						required
					>
						<option value="">— Select —</option>
						{accounts
							.filter((a) => a.id !== transaction.accountId)
							.map((a) => (
								<option key={a.id} value={a.id}>
									{a.name}
								</option>
							))}
					</select>
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

function TransactionSplit({ transaction, setTransaction, onCancel, onSubmit, categories }) {
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
	const flattened = flattenCategories(treeCategories);

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
						<select
							className="border rounded px-2 py-1"
							value={child.categoryId || ""}
							onChange={(e) => updateChild(idx, "categoryId", e.target.value)}
						>
							<option value="">— None —</option>
							{flattened.map((category) => (
								<option key={category.id} value={category.id}>
									{category.name}
								</option>
							))}
						</select>
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

// ---- main ----
export default function Transactions() {
	const [transactions, setTransactions] = useState([]);
	const [expandedParents, setExpandedParents] = useState({});
	const [accounts, setAccounts] = useState([]);
	const [currentTotal, setCurrentTotal] = useState(0);
	const [categories, setCategories] = useState([]);
	const [editTx, setEditTx] = useState(null);
	const [splitTx, setSplitTx] = useState(null);
	const [transferTx, setTransferTx] = useState(null);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [totalPages, setTotalPages] = useState(0);
	const [pageSize, setPageSize] = useState(10);
	const [totalCount, setTotalCount] = useState(0);
	const [modalContent, setModalContent] = useState(null);
	const [searchParams, setSearchParams] = useSearchParams();
	const [filterMonth, setFilterMonth] = useState(
		searchParams.get("month") || dayjs().format("YYYY-MM")
	);
	const updateUrlParams = (overrides = {}) => {
	  const params = new URLSearchParams(searchParams);

	  // Apply overrides (new values for filters)
	  Object.entries(overrides).forEach(([key, value]) => {
	    if (value && value !== "ALL") {
	      params.set(key, value);
	    } else {
	      params.delete(key); // remove empty/default values from URL
	    }
	  });

	  setSearchParams(params);
	};
	const [filterAccount, setFilterAccount] = useState(
		searchParams.get("accountId") || ""
	);
	const [filterType, setFilterType] = useState(
	  searchParams.get("type") || "ALL"
	);
	const [filterCategory, setFilterCategory] = useState(
		searchParams.get("categoryId") || ""
	);
	const [search, setSearch] = useState(
	  searchParams.get("search") || ""
	);
	const debouncedSearch = useDebounce(search, 500);
	const [refreshing, setRefreshing] = useState(false);

	// ESC key handler for modal
	useEffect(() => {
		const handleEscKey = (e) => {
			if (e.key === "Escape" && modalContent) {
				setModalContent(null);
			}
		};
		
		if (modalContent) {
			document.addEventListener("keydown", handleEscKey);
			return () => document.removeEventListener("keydown", handleEscKey);
		}
	}, [modalContent]);

	NProgress.configure({ showSpinner: false });

	const toggleExpand = (id) => {
		setExpandedParents((prev) => ({ ...prev, [id]: !prev[id] }));
	};

	const fetchData = async () => {
		setLoading(true);
		NProgress.start();
		try {
			const params = new URLSearchParams({
				page,
				size: pageSize,
				month: filterMonth || "",
				accountId: filterAccount || "",
				type: filterType || "",
				search: search || "",
			});

			if (filterCategory) {
				params.append("categoryId", filterCategory);
			}

			const [txRes, accRes, catRes, currentTotalRes] = await Promise.all([
				api.get(`/transactions?${params.toString()}`),
				api.get(`/accounts`),
				api.get(`/categories`),
				api.get(`/transactions/currentTotal?${params.toString()}`),
			]);
			setTransactions(txRes.data.content);
			setTotalPages(txRes.data.totalPages);
			setAccounts(accRes.data);
			setCategories(catRes.data);
			setTotalCount(txRes.data.totalElements);
			setCurrentTotal(currentTotalRes.data);
		} catch (error) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to fetch user profile:", err);
			}
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

	useEffect(() => {
		if (page > 0 && page >= Math.ceil(totalCount / pageSize)) {
			setPage(0);
		}
	}, [totalCount, page, pageSize]);

	useEffect(() => {
		setCurrentTotal(0);
		fetchData();
	}, [page, pageSize, filterMonth, filterAccount, filterType, filterCategory, debouncedSearch]);

	const saveTx = async (tx, method, url) => {
		setLoading(true);
		NProgress.start();
		const payload = {
			description: tx.description,
			explanation: tx.explanation || "",
			amount: tx.amount,
			date: tx.date,
			type: tx.type,
			account: { id: tx.accountId },
			category: tx.categoryId ? { id: tx.categoryId } : null,
			parent: tx.parentId ? { id: tx.parentId } : null,
		};
		console.log("Sending payload to backend:", payload);
		console.log("Date being sent:", tx.date);
		await api[method](url, payload);
		await fetchData();
		NProgress.done();
		setLoading(false);
	};

	const deleteTx = async (id) => {
		if (!window.confirm("Delete this transaction?")) return;
		setLoading(true);
		NProgress.start();
		await api
			.delete(`/transactions/${id}`)
			.then(() => {
				console.log("Transaction deleted successfully");
			})
			.catch((err) => {
				if (err.response?.status === 401) {
					localStorage.removeItem("authToken");
					navigate("/");
				} else {
					console.error("Failed to fetch user profile:", err);
				}
			});
		await fetchData();
		NProgress.done();
		setLoading(false);
	};

	const triggerDataExtraction = async () => {
		if (!window.confirm("This will trigger all data extraction services to check for new transactions from your connected email accounts. Continue?")) {
			return;
		}
		
		setRefreshing(true);
		NProgress.start();
		try {
			const response = await api.post('/data-extraction/trigger');
			if (response.data.status === 'started') {
				alert(`Data extraction started! Services running: ${response.data.services.length}\n\nCheck logs for progress. The page will refresh automatically in 30 seconds.`);
				// Auto refresh the page after 30 seconds to show new transactions
				setTimeout(() => {
					fetchData();
				}, 30000);
			}
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to trigger data extraction:", err);
				alert("Failed to trigger data extraction. Please check the logs.");
			}
		} finally {
			NProgress.done();
			setRefreshing(false);
		}
	};

	// Build + flatten once per render and reuse everywhere
	const treeCategories = buildTree(categories);
	const flattened = flattenCategories(treeCategories);

	const renderRow = (tx, isChild = false, index = 0) => {
		const baseColor = isChild
			? "bg-gray-50 border-dashed"
			: index % 2 === 0
				? "bg-blue-50"
				: "bg-blue-100";
		return (
			<div
				key={tx.id}
				className={`grid grid-cols-1 sm:grid-cols-[1.5rem_2fr_1fr_1.5fr_1fr_max-content] gap-2 py-2 px-3 rounded border items-start sm:items-center text-sm ${baseColor} border-gray-200`}
			>
				<div className="text-xs">
					{!isChild && tx.children?.length > 0 && (
						<button
							onClick={() => toggleExpand(tx.id)}
							className="text-gray-600 hover:text-black"
						>
							{expandedParents[tx.id] ? "▼" : "▶"}
						</button>
					)}
				</div>

				<div className="flex flex-col">
					<div className="flex items-center gap-1 truncate font-medium text-gray-800">
						<span className="truncate">{tx.shortDescription}</span>
						{(tx.gptDescription || tx.gptExplanation || tx.gptAmount || tx.gptType || tx.currency || tx.gptAccount) && (
							<span className="text-xs bg-blue-100 text-blue-700 px-1 rounded ml-1" title="GPT Analysis Available">🤖</span>
						)}
						{(tx.gptDescription || tx.gptExplanation || tx.gptAmount || tx.gptType || tx.currency || tx.gptAccount ||
							(tx.gptDescription || tx.description) !== tx.shortDescription ||
							(tx.gptExplanation || tx.explanation) !== tx.shortExplanation) && (
								<button
									title="View full description"
									onClick={() =>
										setModalContent({
											title: "Transaction Analysis & Comparison",
											content: (
												<div className="space-y-4">
													{/* Header with Transaction ID and Date */}
													<div className="bg-gradient-to-r from-blue-50 to-indigo-50 p-3 rounded border">
														<div className="flex justify-between items-center">
															<div>
																<h3 className="font-semibold text-gray-800">Transaction Details</h3>
																<p className="text-xs text-gray-600">{new Date(tx.date).toLocaleString()}</p>
															</div>
															<div className="text-right">
																<p className="text-sm text-gray-600">{tx.account?.name}</p>
																<p className="text-xs text-gray-500">{tx.account?.institution?.name}</p>
															</div>
														</div>
													</div>

													{/* Description Comparison */}
													<div className="grid grid-cols-2 gap-4">
														<div className="space-y-2">
															<h4 className="font-semibold text-gray-700 text-sm">📊 Original Description</h4>
															<div className="bg-gray-50 p-3 rounded border min-h-[60px]">
																<p className="text-sm">{tx.description || <span className="text-gray-400 italic">Not available</span>}</p>
															</div>
														</div>
														<div className="space-y-2">
															<h4 className="font-semibold text-blue-700 text-sm">🤖 GPT Description</h4>
															<div className="bg-blue-50 p-3 rounded border min-h-[60px]">
																<p className="text-sm">{tx.gptDescription || <span className="text-gray-400 italic">Not analyzed</span>}</p>
															</div>
														</div>
													</div>

													{/* Explanation Comparison */}
													<div className="grid grid-cols-2 gap-4">
														<div className="space-y-2">
															<h4 className="font-semibold text-gray-700 text-sm">📊 Original Explanation</h4>
															<div className="bg-gray-50 p-3 rounded border min-h-[60px]">
																<p className="text-sm">{tx.explanation || <span className="text-gray-400 italic">Not available</span>}</p>
															</div>
														</div>
														<div className="space-y-2">
															<h4 className="font-semibold text-blue-700 text-sm">🤖 GPT Explanation</h4>
															<div className="bg-blue-50 p-3 rounded border min-h-[60px]">
																<p className="text-sm">{tx.gptExplanation || <span className="text-gray-400 italic">Not analyzed</span>}</p>
															</div>
														</div>
													</div>

													{/* Other Fields */}
													<div className="grid grid-cols-1 md:grid-cols-2 gap-4">
														<div className="space-y-3">
															<h4 className="font-semibold text-gray-700 text-sm">💰 Amount & Type</h4>
															<div className="bg-gray-50 p-3 rounded border">
																<p className="text-sm mb-1">
																	<strong>Original:</strong> 
																	<span className={`font-semibold ml-1 ${tx.type === "DEBIT" ? "text-red-600" : "text-green-600"}`}>
																		₹{(typeof tx.amount === "number" ? tx.amount : 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })} ({tx.type})
																	</span>
																</p>
																{tx.gptAmount && (
																	<p className="text-sm">
																		<strong>GPT:</strong> 
																		<span className={`font-semibold ml-1 ${tx.gptType === "DEBIT" ? "text-red-600" : "text-green-600"}`}>
																			{tx.currency || "₹"}{(typeof tx.gptAmount === "number" ? tx.gptAmount : parseFloat(tx.gptAmount) || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })} ({tx.gptType || "N/A"})
																		</span>
																	</p>
																)}
															</div>
														</div>
														
														{tx.gptAccount && (
															<div className="space-y-3">
																<h4 className="font-semibold text-blue-700 text-sm">🤖 GPT Matched Account</h4>
																<div className="bg-blue-50 p-3 rounded border">
																	<p className="text-sm font-medium">{tx.gptAccount.name}</p>
																	<p className="text-xs text-gray-500">{tx.gptAccount.institution?.name}</p>
																	<p className="text-xs text-gray-500">{tx.gptAccount.accountType?.name}</p>
																</div>
															</div>
														)}
														
														{tx.currency && (
															<div className="space-y-3">
																<h4 className="font-semibold text-blue-700 text-sm">💱 Currency</h4>
																<div className="bg-blue-50 p-3 rounded border">
																	<p className="text-sm font-medium">{tx.currency}</p>
																</div>
															</div>
														)}
													</div>
												</div>
											),
										})
									}
								>
									🔍
								</button>
							)}
					</div>
					<div className="text-xs text-gray-500 break-words">
						{tx.shortExplanation}
						{/* Show original description if GPT description is being used as primary */}
						{tx.gptDescription && tx.gptDescription !== tx.description && tx.description && (
							<div className="text-gray-400 mt-1">
								Original: {tx.description.length > 40 ? tx.description.substring(0, 40) + "..." : tx.description}
							</div>
						)}
						{/* Show original explanation if GPT explanation is being used as primary */}
						{tx.gptExplanation && tx.gptExplanation !== tx.explanation && tx.explanation && (
							<div className="text-gray-400 mt-1 italic">
								Original: {tx.explanation.length > 50 ? tx.explanation.substring(0, 50) + "..." : tx.explanation}
							</div>
						)}
					</div>
				</div>

				<div className="text-gray-700">
					<span
						className={`font-semibold ${tx.type === "DEBIT" ? "text-red-600" : "text-green-600"
							}`}
					>
						{tx.currency || "₹"}
						{(typeof tx.amount === "number" ? tx.amount : 0).toLocaleString(
							"en-IN",
							{ minimumFractionDigits: 2 }
						)}
					</span>
					<span className="uppercase ml-2 text-xs bg-gray-100 rounded px-1">
						{tx.type}
					</span>
					{tx.gptAmount && tx.gptAmount !== tx.amount && (
						<div className="text-xs text-blue-600 mt-1">
							🤖 GPT: {tx.currency || "₹"}{(typeof tx.gptAmount === "number" ? tx.gptAmount : parseFloat(tx.gptAmount) || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
							{tx.gptType && tx.gptType !== tx.type && ` (${tx.gptType})`}
						</div>
					)}
					<br />
					<span className="text-xs text-gray-500">{tx.account?.name}</span>
				</div>

				<div>
					<select
						className="w-full border rounded px-2 py-1 text-xs sm:text-sm bg-blue-50"
						value={tx.category?.id || ""}
						onChange={(e) => {
							saveTx(
								{
									...tx,
									categoryId: e.target.value,
									accountId: tx.account?.id,
									parentId: tx.parent?.id,
								},
								"put",
								`/transactions/${tx.id}`
							);
						}}
					>
						<option value="">— Category —</option>
						{flattened.map((c) => (
							<option key={c.id} value={c.id}>
								{c.name}
							</option>
						))}
					</select>
				</div>

				<div className="text-xs sm:text-sm text-gray-500">
					{dayjs(tx.date).format("ddd, DD MMM YY HH:mm")}
				</div>

				<div className="flex flex-wrap gap-1 sm:space-x-2 text-xs sm:text-sm">
					{!isChild && (
						<button
							className="text-purple-600 hover:underline"
							onClick={() =>
								setSplitTx({
									...tx,
									parentId: tx.id,
									accountId: tx.account?.id,
								})
							}
						>
							Split
						</button>
					)}
					{!isChild && (!tx.children || tx.children.length === 0) && (
						<button
							className="text-teal-600 hover:underline"
							onClick={() =>
								setTransferTx({
									...tx,
									accountId: tx.account?.id,
									destinationAccountId: "",
									explanation: tx.explanation || "",
								})
							}
						>
							Transfer
						</button>
					)}
					<button
						className="text-red-600 hover:underline"
						onClick={() => deleteTx(tx.id)}
					>
						Delete
					</button>
					<button
						className="text-blue-600 hover:underline"
						onClick={() =>
							setEditTx({
								...tx,
								accountId: tx.account?.id,
								categoryId: tx.category?.id,
							})
						}
					>
						Update
					</button>
				</div>
			</div>
		);
	};

	function TransactionPageButtons({
		filterMonth,
		filterAccount,
		filterCategory,
		filterType,
		search,
		triggerDataExtraction,
		refreshing,
	}) {
		return (
			<div className="flex items-center gap-2 mt-2">
				<button
					onClick={() => {
						setFilterMonth("");
						setFilterAccount("");
						setFilterCategory("");
						setFilterType("ALL");
						setSearch("");
						setPage(0);
					}}
					className="text-sm text-red-600 underline ml-2"
				>
					Clear All Filters
				</button>

				<button
					onClick={() =>
						setEditTx({
							id: null,
							description: "",
							explanation: "",
							amount: 0,
							date: dayjs().format("YYYY-MM-DDTHH:mm"),
							type: "DEBIT",
							accountId: "",
							categoryId: "",
						})
					}
					className="bg-blue-600 text-white px-3 py-1 rounded text-sm"
				>
					Add Transaction
				</button>
				<button
					onClick={triggerDataExtraction}
					disabled={refreshing}
					className={`${refreshing 
						? 'bg-gray-400 cursor-not-allowed' 
						: 'bg-purple-600 hover:bg-purple-700'
					} text-white px-3 py-1 rounded text-sm flex items-center gap-1`}
					title="Trigger all data extraction services to fetch new transactions from email"
				>
					{refreshing ? (
						<>
							<span className="animate-spin">⟳</span>
							Extracting...
						</>
					) : (
						<>
							<span>🔄</span>
							Refresh Data
						</>
					)}
				</button>
				<button
					onClick={async () => {
						const params = new URLSearchParams({
							month: filterMonth,
							accountId: filterAccount,
							type: filterType,
							search,
						});
						if (filterCategory) params.append("categoryId", filterCategory);
						const res = await api.get(`/transactions/export?${params}`).catch((err) => {
							if (err.response?.status === 401) {
								localStorage.removeItem("authToken");
								navigate("/");
							} else {
								console.error("Failed to fetch user profile:", err);
							}
						});
						const flattenedRows = res.data.map((tx) => ({
							Date: tx.date,
							Description: tx.description,
							Explanation: tx.explanation || "",
							Amount: tx.amount,
							Type: tx.type,
							Account: tx.account?.name || "",
							Category: tx.category?.name || "",
						}));
						const csv = Papa.unparse(flattenedRows);
						const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
						saveAs(blob, "transactions.csv");
					}}
					className="bg-green-600 text-white px-3 py-1 rounded text-sm"
				>
					Export CSV
				</button>

				<button
					onClick={async () => {
						const params = new URLSearchParams({
							month: filterMonth,
							accountId: filterAccount,
							type: filterType,
							search,
						});
						if (filterCategory) params.append("categoryId", filterCategory);
						const res = await api.get(`/transactions/export?${params}`).catch((err) => {
							if (err.response?.status === 401) {
								localStorage.removeItem("authToken");
								navigate("/");
							} else {
								console.error("Failed to fetch user profile:", err);
							}
						});
						const flattenedRows = res.data.map((tx) => ({
							Date: tx.date,
							Description: tx.description,
							Explanation: tx.explanation || "",
							Amount: tx.amount,
							Type: tx.type,
							Account: tx.account?.name || "",
							Category: tx.category?.name || "",
						}));
						const worksheet = XLSX.utils.json_to_sheet(flattenedRows);
						const workbook = XLSX.utils.book_new();
						XLSX.utils.book_append_sheet(workbook, worksheet, "Transactions");
						const excelBuffer = XLSX.write(workbook, { bookType: "xlsx", type: "array" });
						const blob = new Blob([excelBuffer], { type: "application/octet-stream" });
						saveAs(blob, "transactions.xlsx");
					}}
					className="bg-yellow-600 text-white px-3 py-1 rounded text-sm"
				>
					Export XLSX
				</button>
				<button
					onClick={async () => {
						const params = new URLSearchParams({
							month: filterMonth,
							accountId: filterAccount,
							type: filterType,
							search,
						});
						if (filterCategory) params.append("categoryId", filterCategory);

						const res = await api.get(`/transactions/export?${params}`).catch((err) => {
							if (err.response?.status === 401) {
								localStorage.removeItem("authToken");
								navigate("/");
							} else {
								console.error("Failed to fetch user profile:", err);
							}
						});
						const { jsPDF } = await import("jspdf");
						const flattenedRows = res.data.map((tx) => ({
							Date: tx.date,
							Description: tx.description,
							Explanation: tx.explanation || "",
							Amount: tx.amount,
							Type: tx.type,
							Account: tx.account?.name || "",
							Category: tx.category?.name || "",
						}));
						const autoTable = (await import("jspdf-autotable")).default;
						const doc = new jsPDF();
						const headers = Object.keys(flattenedRows[0] || {});
						const rows = flattenedRows.map((row) => headers.map((key) => row[key]));

						autoTable(doc, {
							head: [headers],
							body: rows,
							styles: {
								fontSize: 8,
								cellWidth: "wrap",
							},
							columnStyles: {
								0: { cellWidth: 20 }, // Date
								1: { cellWidth: 45 }, // Description
								2: { cellWidth: 45 }, // Explanation
								3: { cellWidth: 20, halign: "right" }, // Amount
								4: { cellWidth: 15 }, // Type
								5: { cellWidth: 20 }, // Account
								6: { cellWidth: 20 }, // Category
							},
							tableWidth: "wrap",
							margin: { top: 20 },
						});

						doc.save("transactions.pdf");
					}}
					className="bg-red-600 text-white px-3 py-1 rounded text-sm ml-2"
				>
					Export PDF
				</button>
				<button
					onClick={() =>
						alert(
							`Current Total: ₹${currentTotal.toLocaleString("en-IN", {
								minimumFractionDigits: 2,
							})}`
						)
					}
					className="bg-gray-700 text-white px-3 py-1 rounded text-sm"
				>
					Current Total: ₹
					{currentTotal.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
				</button>
			</div>
		);
	}

	const renderPagination = () => {
		if (totalPages <= 1) return null;

		const pages = [];
		for (let i = 0; i < totalPages; i++) {
			if (i === 0 || i === totalPages - 1 || Math.abs(i - page) <= 2) {
				pages.push(i);
			} else if (pages.length > 0 && pages[pages.length - 1] !== -1) {
				pages.push(-1); // Ellipsis
			}
		}

		return (
			<div className="flex flex-wrap items-center gap-2 text-sm mt-4 justify-between">
				<div>
					<button
						disabled={page === 0}
						onClick={() => setPage(0)}
						className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50"
					>
						First
					</button>
					<button
						disabled={page === 0}
						onClick={() => setPage((p) => p - 1)}
						className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1"
					>
						Prev
					</button>
					{pages.map((p, idx) =>
						p === -1 ? (
							<span key={idx} className="px-2 py-1">
								...
							</span>
						) : (
							<button
								key={p}
								className={`px-2 py-1 rounded ${p === page ? "bg-blue-600 text-white" : "bg-gray-200"
									}`}
								onClick={() => setPage(p)}
							>
								{p + 1}
							</button>
						)
					)}
					<button
						disabled={page === totalPages - 1}
						onClick={() => setPage((p) => p + 1)}
						className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1"
					>
						Next
					</button>
					<button
						disabled={page === totalPages - 1}
						onClick={() => setPage(totalPages - 1)}
						className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1"
					>
						Last
					</button>
				</div>
				<div>
					<label className="mr-2">Rows:</label>
					<select
						value={pageSize}
						onChange={(e) => {
							setPageSize(+e.target.value);
							setPage(0);
						}}
						className="border px-2 py-1 rounded"
					>
						<option value={10}>10</option>
						<option value={20}>20</option>
						<option value={50}>50</option>
						<option value={100}>100</option>
					</select>
				</div>
			</div>
		);
	};

	return (
		<div className="flex flex-wrap items-center justify-between gap-4">
			{/* Filters */}
			<div className="flex flex-wrap gap-4 items-center">
				<input
					type="month"
					value={filterMonth}
					onChange={(e) => {setFilterMonth(e.target.value); updateUrlParams({ month: e.target.value });}}
					className="border px-2 py-1 rounded text-sm"
				/>

				<select
					value={filterAccount}
					onChange={(e) => {setFilterAccount(e.target.value); updateUrlParams({ accountId: e.target.value });}}
					className="border px-2 py-1 rounded text-sm"
				>
					<option value="">All Accounts</option>
					{accounts.map((a) => (
						<option key={a.id} value={a.id}>
							{a.name}
						</option>
					))}
				</select>

				<select
					value={filterCategory}
					onChange={(e) => {setFilterCategory(e.target.value); updateUrlParams({ categoryId: e.target.value });}}
					className="border px-2 py-1 rounded text-sm"
				>
					<option value="">All Categories</option>
					{flattened.map((c) => (
						<option key={c.id} value={c.id}>
							{c.name}
						</option>
					))}
				</select>

				<select
					value={filterType}
					onChange={(e) => {setFilterType(e.target.value); updateUrlParams({ type: e.target.value });}}
					className="border px-2 py-1 rounded text-sm"
				>
					<option value="ALL">All Types</option>
					<option value="CREDIT">Credit</option>
					<option value="DEBIT">Debit</option>
				</select>

				<input
					value={search}
					onChange={(e) => {setSearch(e.target.value); updateUrlParams({ search: e.target.value }); setPage(0);}}
					placeholder="Search"
					className="border px-2 py-1 rounded text-sm"
				/>

				<TransactionPageButtons
					filterMonth={filterMonth}
					filterAccount={filterAccount}
					filterCategory={filterCategory}
					filterType={filterType}
					search={debouncedSearch}
					currentTotal={currentTotal}
					triggerDataExtraction={triggerDataExtraction}
					refreshing={refreshing}
				/>
			</div>

			{renderPagination()}

			{/* Rows */}
			{transactions.flatMap((tx, idx) => [
				renderRow(tx, false, idx),
				...(expandedParents[tx.id] && tx.children
					? tx.children
						.filter((c) => typeof c === "object" && c !== null)
						.map((child) => renderRow(child, true))
					: []),
			])}

			{renderPagination()}

			{/* Modals */}
			{splitTx && (
				<TransactionSplit
					transaction={splitTx}
					setTransaction={setSplitTx}
					onCancel={() => setSplitTx(null)}
					onSubmit={async (enrichedParent) => {
						setLoading(true);
						NProgress.start();
						const childrenPayload = enrichedParent.children.map((c) => ({
							description: c.description,
							amount: c.amount,
							date: enrichedParent.date,
							type: enrichedParent.type,
							account: { id: enrichedParent.accountId },
							category: c.categoryId ? { id: c.categoryId } : null,
							parentId: enrichedParent.parentId,
						}));
						await api.post("/transactions/split", childrenPayload).then(() => {
							console.log("Transaction split successfully");
						}).catch((err) => {
							if (err.response?.status === 401) {
								localStorage.removeItem("authToken");
								navigate("/");
							} else {
								console.error("Failed to fetch user profile:", err);
							}
						});
						setSplitTx(null);
						await fetchData();
						NProgress.done();
						setLoading(false);
					}}
					categories={categories}
				/>
			)}

			{transferTx && (
				<TransferForm
					transaction={transferTx}
					setTransaction={setTransferTx}
					onCancel={() => setTransferTx(null)}
					onSubmit={async () => {
						setLoading(true);
						NProgress.start();
						await api
							.post("/transactions/transfer", {
								sourceTransactionId: transferTx.id,
								destinationAccountId: transferTx.destinationAccountId,
								explanation: transferTx.explanation,
							})
							.then(() => {
								console.log("Transaction transfer successful");
							})
							.catch((err) => {
								if (err.response?.status === 401) {
									localStorage.removeItem("authToken");
									navigate("/");
								} else {
									console.error("Failed to fetch user profile:", err);
								}
							});
						setTransferTx(null);
						await fetchData();
						NProgress.done();
						setLoading(false);
					}}
					accounts={accounts}
				/>
			)}

			{modalContent && (
				<div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
					<div className="bg-white rounded-lg shadow-lg max-w-md w-full p-4">
						<h2 className="text-lg font-semibold mb-2">{modalContent.title}</h2>
						<div className="text-sm text-gray-700 mb-4">{modalContent.content}</div>
						<button
							onClick={() => setModalContent(null)}
							className="text-blue-600 hover:underline text-sm"
						>
							Close
						</button>
					</div>
				</div>
			)}

			{editTx && accounts.length > 0 && categories.length > 0 && (
				<TransactionForm
					transaction={editTx}
					setTransaction={setEditTx}
					onCancel={() => setEditTx(null)}
					onSubmit={async (updatedTx) => {
						if (updatedTx.id) {
							await saveTx(updatedTx, "put", `/transactions/${updatedTx.id}`);
						} else {
							await saveTx(updatedTx, "post", "/transactions");
						}
						setEditTx(null);
					}}
					accounts={accounts}
					categories={categories}
					mode={editTx.id ? "edit" : "add"}
				/>
			)}

			{loading && (
				<div className="fixed inset-0 bg-white bg-opacity-40 z-50 flex items-center justify-center">
					<div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-500 h-10 w-10 animate-spin"></div>
				</div>
			)}
		</div>
	);
}
