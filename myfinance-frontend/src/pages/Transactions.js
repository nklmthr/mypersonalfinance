import React, { useEffect, useState } from "react";
import axios from "axios";
import dayjs from "dayjs";
import { useSearchParams } from "react-router-dom";
import Papa from "papaparse";
import * as XLSX from "xlsx";
import { saveAs } from "file-saver";

function TransactionForm({ transaction, setTransaction, onCancel, onSubmit, accounts, categories, mode }) {
	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={e => { e.preventDefault(); onSubmit(); }}
				className="bg-white p-6 rounded shadow-lg w-full max-w-md space-y-4"
			>
				<h3 className="text-lg font-semibold">{mode === "add" ? "Add" : mode === "split" ? "Split" : "Edit"} Transaction</h3>

				<label className="block">
					<span className="text-sm">Date</span>
					<input type="date" className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.date.slice(0, 10)}
						onChange={e => setTransaction(tx => ({ ...tx, date: e.target.value }))}
						required />
				</label>

				<label className="block">
					<span className="text-sm">Description</span>
					<input type="text" className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.description}
						onChange={e => setTransaction(tx => ({ ...tx, description: e.target.value }))}
						required />
				</label>

				<label className="block">
					<span className="text-sm">Explanation</span>
					<input type="text" className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.explanation || ""}
						onChange={e => setTransaction(tx => ({ ...tx, explanation: e.target.value }))}
					/>
				</label>

				<label className="block">
					<span className="text-sm">Amount</span>
					<input type="number" step="0.01" className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.amount}
						onChange={e => setTransaction(tx => ({ ...tx, amount: e.target.value }))}
						required />
				</label>

				<label className="block">
					<span className="text-sm">Type</span>
					<select className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.type}
						onChange={e => setTransaction(tx => ({ ...tx, type: e.target.value }))}>
						<option>DEBIT</option>
						<option>CREDIT</option>
					</select>
				</label>

				<label className="block">
					<span className="text-sm">Account</span>
					<select className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.accountId}
						onChange={e => setTransaction(tx => ({ ...tx, accountId: e.target.value }))}
						required>
						<option value="">— Select —</option>
						{accounts.map(a => (
							<option key={a.id} value={a.id}>{a.name}</option>
						))}
					</select>
				</label>

				<label className="block">
					<span className="text-sm">Category</span>
					<select className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.categoryId}
						onChange={e => setTransaction(tx => ({ ...tx, categoryId: e.target.value }))}>
						<option value="">— None —</option>
						{categories.map(c => (
							<option key={c.id} value={c.id}>{c.name}</option>
						))}
					</select>
				</label>

				<div className="flex justify-end space-x-2">
					<button type="button" onClick={onCancel} className="px-4 py-1">Cancel</button>
					<button type="submit" className="bg-blue-600 text-white px-4 py-1 rounded">
						{mode === "add" ? "Add" : mode === "split" ? "Split" : "Save"}
					</button>
				</div>
			</form>
		</div>
	);
}

export default function Transactions() {
	const exportCSV = async () => {
		try {
			const params = new URLSearchParams({
				month: filterMonth,
				accountId: filterAccount,
				type: filterType,
				categoryId: filterCategory,
				search,
			});

			const res = await axios.get(`/api/transactions/export?${params}`);
			const exportData = res.data;

			const flatData = exportData.map(tx => ({
				Date: tx.date,
				Description: tx.description,
				Explanation: tx.explanation,
				Amount: tx.amount,
				Type: tx.type,
				Account: tx.account?.name || "",
				Category: tx.category?.name || ""
			}));

			const csv = Papa.unparse(flatData);
			const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
			saveAs(blob, `transactions_${filterMonth}.csv`);
		} catch (err) {
			console.error("Export CSV failed:", err);
			alert("Export failed.");
		}
	};

	const exportExcel = async () => {
		try {
			const params = new URLSearchParams({
				month: filterMonth,
				accountId: filterAccount,
				type: filterType,
				categoryId: filterCategory,
				search,
			});

			const res = await axios.get(`/api/transactions/export?${params}`);
			const exportData = res.data;

			const flatData = exportData.map(tx => ({
				Date: tx.date,
				Description: tx.description,
				Explanation: tx.explanation,
				Amount: tx.amount,
				Type: tx.type,
				Account: tx.account?.name || "",
				Category: tx.category?.name || ""
			}));

			const worksheet = XLSX.utils.json_to_sheet(flatData);
			const workbook = XLSX.utils.book_new();
			XLSX.utils.book_append_sheet(workbook, worksheet, "Transactions");

			XLSX.writeFile(workbook, `transactions_${filterMonth}.xlsx`);
		} catch (err) {
			console.error("Export Excel failed:", err);
			alert("Export failed.");
		}
	};

	const [transactions, setTransactions] = useState([]);
	const [accounts, setAccounts] = useState([]);
	const [categories, setCategories] = useState([]);

	const [editTx, setEditTx] = useState(null);
	const [addTx, setAddTx] = useState(false);
	const [splitTx, setSplitTx] = useState(null);
	const [totalPages, setTotalPages] = useState(1);
	const [loading, setLoading] = useState(false);
	const [filtersInitialized, setFiltersInitialized] = useState(false);
	const [page, setPage] = useState(0);
	const pageSize = 10;

	const [searchParams] = useSearchParams();
	const urlMonth = searchParams.get("month");
	const urlCategoryId = searchParams.get("categoryId");
	const currentMonth = dayjs().format("YYYY-MM");

	const [filterMonth, setFilterMonth] = useState(urlMonth || currentMonth);
	const [filterAccount, setFilterAccount] = useState('');
	const [filterType, setFilterType] = useState('ALL');
	const [filterCategory, setFilterCategory] = useState(urlCategoryId || '');
	const [search, setSearch] = useState('');

	useEffect(() => {
		setFiltersInitialized(true);
	}, []);

	const emptyTx = {
		id: null,
		date: new Date().toISOString(),
		description: "",
		explanation: "",
		amount: "",
		type: "DEBIT",
		accountId: "",
		categoryId: "",
		parentId: null,
	};

	const fetchData = async () => {
		setLoading(true);
		try {
			const params = new URLSearchParams({
				page,
				size: pageSize,
				month: filterMonth,
				accountId: filterAccount,
				type: filterType,
				search
			});
			if (filterCategory) params.append("categoryId", filterCategory);

			const [txRes, accRes, catRes] = await Promise.all([
				axios.get(`/api/transactions?${params}`),
				axios.get("/api/accounts"),
				axios.get("/api/categories"),
			]);
			setTransactions(txRes.data.content);
			setTotalPages(txRes.data.totalPages);
			setAccounts(accRes.data);
			setCategories(catRes.data);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		if (!filtersInitialized) return;
		fetchData();
	}, [page, filterMonth, filterAccount, filterType, filterCategory, search, filtersInitialized]);

	const saveTx = async (tx, method, url) => {
		const payload = {
			description: tx.description,
			explanation: tx.explanation || "",
			amount: tx.amount,
			date: tx.date,
			type: tx.type,
			account: { id: tx.accountId },
			category: tx.categoryId ? { id: tx.categoryId } : null,
			parent: tx.parentId ? { id: tx.parentId } : null
		};
		await axios[method](url, payload);
		fetchData();
	};

	const deleteTx = async (id) => {
		if (!window.confirm("Delete this transaction?")) return;
		await axios.delete(`/api/transactions/${id}`);
		fetchData();
	};

	const getPaginationRange = (current, total) => {
		const delta = 2;
		const range = [];
		const rangeWithDots = [];
		let l;

		for (let i = 0; i < total; i++) {
			if (i === 0 || i === total - 1 || (i >= current - delta && i <= current + delta)) {
				range.push(i);
			}
		}

		for (let i of range) {
			if (l !== undefined) {
				if (i - l === 2) {
					rangeWithDots.push(l + 1);
				} else if (i - l > 2) {
					rangeWithDots.push("...");
				}
			}
			rangeWithDots.push(i);
			l = i;
		}
		return rangeWithDots;
	};
	
	function flattenCategories(categories, prefix = "") {
	  let flat = [];
	  for (const c of categories) {
	    flat.push({ id: c.id, name: prefix + c.name });
	    if (c.children && c.children.length > 0) {
	      flat = flat.concat(flattenCategories(c.children, prefix + "— "));
	    }
	  }
	  return flat;
	}

	const paginationControls = (
		<div className="flex flex-wrap items-center justify-between gap-2 mb-4">
			<button
				className="px-3 py-1 rounded border disabled:opacity-50"
				onClick={() => setPage(p => Math.max(p - 1, 0))}
				disabled={page === 0}
			>
				← Prev
			</button>

			<div className="flex items-center gap-1 flex-wrap justify-center">
				{getPaginationRange(page, totalPages).map((p, i) =>
					p === "..." ? (
						<span key={`ellipsis-${i}`} className="px-2 text-gray-400">…</span>
					) : (
						<button
							key={p}
							onClick={() => setPage(p)}
							className={`
								px-3 py-1 border rounded text-sm
								${p === page
									? "bg-blue-600 text-white font-semibold"
									: "bg-white text-gray-700 hover:bg-gray-100"}
							`}
						>
							{p + 1}
						</button>
					)
				)}
			</div>

			<button
				className="px-3 py-1 rounded border disabled:opacity-50"
				onClick={() => setPage(p => Math.min(p + 1, totalPages - 1))}
				disabled={page >= totalPages - 1}
			>
				Next →
			</button>
		</div>
	);



	return (
		<div className="p-4 space-y-4">
			<div className="flex gap-3 justify-end mb-2">
				<button
					onClick={exportCSV}
					className="bg-green-600 text-white px-3 py-1 rounded hover:bg-green-700 text-sm"
				>
					⬇ CSV
				</button>
				<button
					onClick={exportExcel}
					className="bg-emerald-600 text-white px-3 py-1 rounded hover:bg-emerald-700 text-sm"
				>
					⬇ Excel
				</button>
			</div>
			<div className="flex justify-between items-center">
				<h2 className="text-2xl font-semibold">💸 Transactions</h2>
				<button
					onClick={() => {
						setEditTx(null);
						setSplitTx(null);
						setAddTx(true);
					}}
					className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 text-sm sm:text-base"
				>Add</button>
			</div>

			<div className="grid grid-cols-1 sm:grid-cols-6 gap-4 mb-4">
				<input
					type="text"
					className="border rounded px-2 py-1"
					placeholder="Search description or explanation"
					value={search}
					onChange={(e) => setSearch(e.target.value)}
				/>
				<select value={filterMonth} onChange={e => setFilterMonth(e.target.value)} className="border rounded px-2 py-1">
					{[...Array(12)].map((_, i) => {
						const d = new Date();
						d.setMonth(d.getMonth() - i);
						const value = d.toISOString().slice(0, 7);
						return <option key={value} value={value}>{value}</option>;
					})}
				</select>
				<select value={filterAccount} onChange={e => setFilterAccount(e.target.value)} className="border rounded px-2 py-1">
					<option value="">All Accounts</option>
					{accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
				</select>
				<select value={filterType} onChange={e => setFilterType(e.target.value)} className="border rounded px-2 py-1">
					<option value="ALL">All</option>
					<option value="DEBIT">DEBIT</option>
					<option value="CREDIT">CREDIT</option>
				</select>
				<select value={filterCategory} onChange={e => setFilterCategory(e.target.value)} className="border rounded px-2 py-1">
				  <option value="">All Categories</option>
				  {flattenCategories(categories).map(c => (
				    <option key={c.id} value={c.id}>{c.name}</option>
				  ))}
				</select>
				<button
					onClick={() => {
						setFilterMonth(currentMonth);
						setFilterAccount('');
						setFilterType('ALL');
						setFilterCategory('');
						setSearch('');
						setPage(0);
					}}
					className="bg-gray-300 text-black px-4 py-1 rounded hover:bg-gray-400"
				>Clear Filters</button>
			</div>

			{paginationControls}

			{loading ? (
				<div className="w-full h-2 bg-gray-200 rounded">
					<div className="h-2 bg-blue-600 rounded animate-pulse" style={{ width: "100%" }} />
				</div>
			) : (
				<div className="space-y-2">
					{transactions.map(tx => (
						<div key={tx.id} className="grid grid-cols-1 sm:grid-cols-[2fr_1fr_1.5fr_1fr_max-content] gap-2 py-2 px-3 bg-white rounded border border-gray-200 items-center">
							<div>
								<div className="truncate">{tx.description}</div>
								<div className="text-xs text-gray-500">{tx.explanation}</div>
							</div>
							<div className="text-gray-700">
								<span className={`
			      font-semibold 
			      ${tx.type === "DEBIT" ? "text-red-600" : "text-green-600"}
			    `}>
									₹{tx.amount}
								</span>
								<span className="uppercase ml-2 text-xs bg-gray-100 rounded px-1">
									{tx.type}
								</span><br />
								<span className="text-sm">{tx.account?.name}</span>
							</div>
							<select
							  className="w-full border rounded px-2 py-1 text-sm"
							  value={tx.category?.id || ""}
							  onChange={e => {
							    saveTx(
							      {
							        ...tx,
							        categoryId: e.target.value,
							        accountId: tx.account?.id,
							        parentId: tx.parent?.id
							      },
							      "put",
							      `/api/transactions/${tx.id}`
							    );
							  }}
							>
							  <option value="">— Category —</option>
							  {flattenCategories(categories).map(c => (
							    <option key={c.id} value={c.id}>{c.name}</option>
							  ))}
							</select>

							<div className="text-sm text-gray-500">{tx.date.replace("T", " ").slice(0, 19)}</div>
							<div className="flex items-center space-x-2 text-sm">
								<button className="text-purple-600 hover:underline" onClick={() => setSplitTx({ ...emptyTx, parentId: tx.id, accountId: tx.account?.id })}>Split</button>
								<button className="text-red-600 hover:underline" onClick={() => deleteTx(tx.id)}>Delete</button>
								<button className="text-blue-600 hover:underline" onClick={() => setEditTx({ ...tx, accountId: tx.account?.id, categoryId: tx.category?.id })}>Update</button>
							</div>
						</div>
					))}
				</div>
			)}

			{paginationControls}

			{addTx && (
				<TransactionForm
					transaction={{ ...emptyTx }}
					setTransaction={() => { }}
					onCancel={() => setAddTx(false)}
					onSubmit={async () => {
						await saveTx(emptyTx, "post", "/api/transactions");
						setAddTx(false);
					}}
					accounts={accounts}
					categories={categories}
					mode="add"
				/>
			)}
			{editTx && (
				<TransactionForm
					transaction={editTx}
					setTransaction={setEditTx}
					onCancel={() => setEditTx(null)}
					onSubmit={async () => {
						await saveTx(editTx, "put", `/api/transactions/${editTx.id}`);
						setEditTx(null);
					}}
					accounts={accounts}
					categories={categories}
					mode="edit"
				/>
			)}
			{splitTx && (
				<TransactionForm
					transaction={splitTx}
					setTransaction={setSplitTx}
					onCancel={() => setSplitTx(null)}
					onSubmit={async () => {
						await saveTx(splitTx, "post", "/api/transactions");
						setSplitTx(null);
					}}
					accounts={accounts}
					categories={categories}
					mode="split"
				/>
			)}
		</div>
	);
}
