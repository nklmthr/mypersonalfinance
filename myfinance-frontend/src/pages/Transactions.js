import React, { useEffect, useState } from "react";
import axios from "axios";
import dayjs from "dayjs";
import { useSearchParams } from "react-router-dom";
import Papa from "papaparse";
import * as XLSX from "xlsx";
import { saveAs } from "file-saver";

function TransactionForm({ transaction, setTransaction, onCancel, onSubmit, accounts, categories, mode }) {
	useEffect(() => {
		const handleKeyDown = (e) => {
			if (e.key === "Escape") {
				onCancel();
			}
		};
		document.addEventListener("keydown", handleKeyDown);
		return () => document.removeEventListener("keydown", handleKeyDown);
	}, [onCancel]);
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

function TransferForm({ transaction, setTransaction, onCancel, onSubmit, accounts }) {
	useEffect(() => {
		const handler = (e) => e.key === "Escape" && onCancel();
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [onCancel]);

	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={e => { e.preventDefault(); onSubmit(); }}
				className="bg-white p-6 rounded shadow-lg w-full max-w-md space-y-4"
			>
				<h3 className="text-lg font-semibold">Transfer Funds</h3>

				<div>
					<span className="text-sm">From Account</span>
					<div className="mt-1 block w-full border rounded px-2 py-1 bg-gray-100">
						{accounts.find(a => a.id === transaction.accountId)?.name || "Unknown"}
					</div>
				</div>

				<label className="block">
					<span className="text-sm">To Account</span>
					<select
						className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.destinationAccountId || ""}
						onChange={e => setTransaction(tx => ({ ...tx, destinationAccountId: e.target.value }))}
						required
					>
						<option value="">— Select —</option>
						{accounts
							.filter(a => a.id !== transaction.accountId)
							.map(a => (
								<option key={a.id} value={a.id}>{a.name}</option>
							))}
					</select>
				</label>

				<label className="block">
					<span className="text-sm">Explanation</span>
					<input
						type="text"
						className="mt-1 block w-full border rounded px-2 py-1"
						value={transaction.explanation || ""}
						onChange={e => setTransaction(tx => ({ ...tx, explanation: e.target.value }))}
					/>
				</label>

				<div className="flex justify-end space-x-2">
					<button type="button" onClick={onCancel} className="px-4 py-1">Cancel</button>
					<button type="submit" className="bg-blue-600 text-white px-4 py-1 rounded">Transfer</button>
				</div>
			</form>
		</div>
	);
}
export default function Transactions() {
	// all previous useStates
	const [transactions, setTransactions] = useState([]);
	const [accounts, setAccounts] = useState([]);
	const [categories, setCategories] = useState([]);
	const [editTx, setEditTx] = useState(null);
	const [addTx, setAddTx] = useState(false);
	const [splitTx, setSplitTx] = useState(null);
	const [transferTx, setTransferTx] = useState(null);
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

	useEffect(() => { setFiltersInitialized(true); }, []);

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

	// Pagination rendering skipped here for brevity...

	return (
		<div className="p-4 space-y-4">
			{/* Filters, Export, Pagination Controls etc. */}
			{/* Transactions List */}
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
					<div className="text-sm text-gray-500">
						{new Date(tx.date).toLocaleString('en-GB', {
							weekday: 'short',
							day: '2-digit',
							month: 'short',
							hour: '2-digit',
							minute: '2-digit',
							second: '2-digit',
							hour12: false
						}).replace(',', '')}
					</div>
					<div className="flex items-center space-x-2 text-sm">
						<button className="text-purple-600 hover:underline" onClick={() => setSplitTx({ ...emptyTx, parentId: tx.id, accountId: tx.account?.id })}>Split</button>
						<button
							className="text-teal-600 hover:underline"
							onClick={() =>
								setTransferTx({
									...tx,
									accountId: tx.account?.id, // ✅ Ensure this line is added
									destinationAccountId: "",
									explanation: tx.explanation || "",
								})
							}
						>
							Transfer
						</button>
						<button className="text-red-600 hover:underline" onClick={() => deleteTx(tx.id)}>Delete</button>
						<button className="text-blue-600 hover:underline" onClick={() => setEditTx({ ...tx, accountId: tx.account?.id, categoryId: tx.category?.id })}>Update</button>
					</div>
				</div>
			))}

			{/* Modals */}
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
			{transferTx && (
				<TransferForm
					transaction={transferTx}
					setTransaction={setTransferTx}
					onCancel={() => setTransferTx(null)}
					onSubmit={async () => {
						await axios.post("/api/transactions/transfer", {
							sourceTransactionId: transferTx.id,
							destinationAccountId: transferTx.destinationAccountId,
							explanation: transferTx.explanation
						});
						setTransferTx(null);
						fetchData();
					}}
					accounts={accounts}
				/>
			)}
		</div>
	);
}