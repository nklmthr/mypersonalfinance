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

function TransactionForm({ transaction, setTransaction, onCancel, onSubmit, accounts, categories, mode }) {
	useEffect(() => {
		const handler = (e) => e.key === "Escape" && onCancel();
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [onCancel]);
	const flattenCategories = (categories, prefix = "") => {
			let flat = [];
			for (const c of categories) {
				flat.push({ id: c.id, name: prefix + c.name });
				if (c.children?.length > 0) {
					flat = flat.concat(flattenCategories(c.children, prefix + "— "));
				}
			}
			return flat;
		};

	const submit = () => {
		onSubmit({
			...transaction,
			amount: parseFloat(transaction.amount),
		});
	};

	return (
		<div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
			<form
				onSubmit={(e) => { e.preventDefault(); submit(); }}
				className="bg-white p-6 rounded shadow-lg w-full max-w-xl space-y-4"
			>
				<h3 className="text-lg font-semibold">{mode === "add" ? "Add" : "Edit"} Transaction</h3>

				{/* Date */}
				<div>
					<label className="block text-sm font-medium mb-1">Date</label>
					<input
						type="datetime-local"
						value={transaction.date ? transaction.date.substring(0, 16) : ""}
						onChange={e => setTransaction(t => ({ ...t, date: e.target.value }))}
						className="w-full border rounded px-3 py-2"
						required
					/>
				</div>

				{/* Account & Amount */}
				<div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
					<div>
						<label className="block text-sm font-medium mb-1">Account</label>
						<select
							className="w-full border rounded px-3 py-2"
							value={transaction.accountId || ""}
							onChange={e => setTransaction(t => ({ ...t, accountId: e.target.value }))}
							required
						>
							<option value="">— Select Account —</option>
							{accounts.map(a => (
								<option key={a.id} value={a.id}>{a.name}</option>
							))}
						</select>
					</div>
					<div>
						<label className="block text-sm font-medium mb-1">Amount</label>
						<input
							type="number"
							placeholder="Amount"
							value={transaction.amount}
							onChange={e => setTransaction(t => ({ ...t, amount: e.target.value }))}
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
							onChange={e => setTransaction(t => ({ ...t, categoryId: e.target.value }))}
						>
							<option value="">— Select Category —</option>
							{flattenCategories(categories).map(c => (
								<option key={c.id} value={c.id}>{c.name}</option>
							))}
						</select>
					</div>
					<div>
						<label className="block text-sm font-medium mb-1">Type</label>
						<select
							className="w-full border rounded px-3 py-2"
							value={transaction.type || "DEBIT"}
							onChange={e => setTransaction(t => ({ ...t, type: e.target.value }))}
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
						onChange={e => setTransaction(t => ({ ...t, description: e.target.value }))}
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
						onChange={e => setTransaction(t => ({ ...t, explanation: e.target.value }))}
						className="w-full border rounded px-3 py-2"
					/>
				</div>

				{/* Actions */}
				<div className="flex justify-end space-x-2">
					<button type="button" onClick={onCancel} className="px-4 py-1 border rounded">Cancel</button>
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
				onSubmit={(e) => { e.preventDefault(); onSubmit(); }}
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
function TransactionSplit({ transaction, setTransaction, onCancel, onSubmit, categories }) {
  const [children, setChildren] = useState(() =>
    (transaction?.children || []).map(c => ({
      ...c,
      categoryId: c.category?.id || "",
    }))
  );

  // Update children if transaction changes
  useEffect(() => {
    if (transaction?.children) {
      setChildren(
        transaction.children.map(c => ({
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
        categoryId: ""
      }
    ]);
  };

  const flattenCategories = (categories, prefix = "") => {
    let flat = [];
    const categoryList = Array.isArray(categories) && categories.length === 1 && categories[0].children
      ? categories[0].children
      : categories;

    for (const c of categoryList) {
      flat.push({ id: c.id, name: prefix + c.name });
      if (c.children && c.children.length > 0) {
        flat = flat.concat(flattenCategories(c.children, prefix + "— "));
      }
    }
    return flat;
  };

  const updateChild = (index, key, value) => {
    const updated = [...children];
    updated[index][key] = value;
    setChildren(updated);
  };

  const submit = () => {
    const total = children.reduce((sum, c) => sum + parseFloat(c.amount || 0), 0);
    const parentAmt = parseFloat(transaction.amount);
    if (isNaN(parentAmt) || Math.abs(total - parentAmt) > 1) {
      alert(`Child transaction amounts must sum up to ₹${isNaN(parentAmt) ? 0 : parentAmt}. Entered total: ₹${total}`);
      return;
    }
    const enrichedChildren = children.map(c => ({
      ...c,
      date: transaction.date,
      type: transaction.type,
      accountId: transaction.accountId
    }));
    onSubmit({
      ...transaction,
      children: enrichedChildren
    });
  };

  const flattenedCategories = flattenCategories(categories);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
      <form
        onSubmit={e => { e.preventDefault(); submit(); }}
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
              onChange={e => updateChild(idx, "description", e.target.value)}
              className="border rounded px-2 py-1"
            />
            <input
              type="number"
              value={child.amount}
              onChange={e => updateChild(idx, "amount", e.target.value)}
              className="border rounded px-2 py-1"
            />
            <select
              className="border rounded px-2 py-1"
              value={child.categoryId || ""}
              onChange={(e) => updateChild(idx, "categoryId", e.target.value)}
            >
              <option value="">— None —</option>
              {flattenedCategories.map(category => (
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
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-1"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="bg-blue-600 text-white px-4 py-1 rounded"
          >
            Split
          </button>
        </div>
      </form>
    </div>
  );
}
export default function Transactions() {
	const [transactions, setTransactions] = useState([]);
	const [expandedParents, setExpandedParents] = useState({});
	const [accounts, setAccounts] = useState([]);
	const [categories, setCategories] = useState([]);
	const [editTx, setEditTx] = useState(null);
	const [splitTx, setSplitTx] = useState(null);
	const [transferTx, setTransferTx] = useState(null);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [totalPages, setTotalPages] = useState(0);
	const [pageSize, setPageSize] = useState(10);
	const [totalCount, setTotalCount] = useState(0);

	const [searchParams] = useSearchParams();
	const [filterMonth, setFilterMonth] = useState(searchParams.get("month") || "");
	const [filterAccount, setFilterAccount] = useState(searchParams.get("accountId") || '');
	const [filterType, setFilterType] = useState('ALL');
	const [filterCategory, setFilterCategory] = useState(searchParams.get("categoryId") || '');
	const [search, setSearch] = useState('');
	const debouncedSearch = useDebounce(search, 500);

	NProgress.configure({ showSpinner: false });


	const toggleExpand = (id) => {
		setExpandedParents(prev => ({ ...prev, [id]: !prev[id] }));
	};

	const fetchData = async () => {
		setLoading(true);
		NProgress.start();
		try {
			const params = new URLSearchParams({
				page, size: pageSize,
				month: filterMonth,
				accountId: filterAccount,
				type: filterType,
				search
			});
			if (filterCategory) params.append("categoryId", filterCategory);

			const [txRes, accRes, catRes] = await Promise.all([
				api.get(`/transactions?${params}`),
				api.get("/accounts"),
				api.get("/categories"),
			]);
			setTransactions(txRes.data.content);
			setTotalPages(txRes.data.totalPages);
			setAccounts(accRes.data);
			setCategories(catRes.data);
			setTotalCount(txRes.data.totalElements);
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};
	useEffect(() => {
		if (page > 0 && page >= Math.ceil(totalCount / pageSize)) {
			setPage(0); // 👈 If out of range, reset to first page
		}
	}, [totalCount, page, pageSize]);

	useEffect(() => { fetchData(); }, [page, pageSize, filterMonth, filterAccount, filterType, filterCategory, debouncedSearch]);
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
			parent: tx.parentId ? { id: tx.parentId } : null
		};
		await api[method](url, payload);
		await fetchData();
		NProgress.done();
		setLoading(false);
	};

	const deleteTx = async (id) => {
		if (!window.confirm("Delete this transaction?")) return;
		setLoading(true);
		NProgress.start();
		await api.delete(`/transactions/${id}`);
		await fetchData();
		NProgress.done();
		setLoading(false);
	};

	const flattenCategories = (categories, prefix = "") => {
	    let flat = [];
	    // Always treat categories as an array
	    const categoryList = Array.isArray(categories) ? categories : [categories];
	    
	    for (const c of categoryList) {
	        flat.push({ id: c.id, name: prefix + c.name });
	        console.log(prefix + c.name);
	        if (c.children && c.children.length > 0) {
	            flat = flat.concat(flattenCategories(c.children, prefix + "— "));
	        }
	    }
	    return flat;
	};


	const renderRow = (tx, isChild = false, index = 0) => {
		const baseColor = isChild ? "bg-gray-50 border-dashed" :
			index % 2 === 0 ? "bg-blue-50" : "bg-blue-100";
		return (
			<div
				key={tx.id}
				className={`grid grid-cols-1 sm:grid-cols-[1.5rem_2fr_1fr_1.5fr_1fr_max-content] gap-2 py-2 px-3 rounded border items-start sm:items-center text-sm ${baseColor} border-gray-200`}
			>

				<div className="text-xs">
					{!isChild && tx.children?.length > 0 && (
						<button onClick={() => toggleExpand(tx.id)} className="text-gray-600 hover:text-black">
							{expandedParents[tx.id] ? "▼" : "▶"}
						</button>
					)}
				</div>

				<div className="flex flex-col">
					<div className="truncate font-medium text-gray-800">{tx.description}</div>
					<div className="text-xs text-gray-500 break-words">{tx.explanation}</div>
				</div>

				<div className="text-gray-700">
					<span className={`font-semibold ${tx.type === "DEBIT" ? "text-red-600" : "text-green-600"}`}>
						₹{tx.amount.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
					</span>
					<span className="uppercase ml-2 text-xs bg-gray-100 rounded px-1">{tx.type}</span><br />
					<span className="text-xs text-gray-500">{tx.account?.name}</span>
				</div>

				<div>
					<select
						className="w-full border rounded px-2 py-1 text-xs sm:text-sm bg-blue-50"
						value={tx.category?.id || ""}
						onChange={e => {
							saveTx({ ...tx, categoryId: e.target.value, accountId: tx.account?.id, parentId: tx.parent?.id }, "put", `/transactions/${tx.id}`);
						}}
					>
						<option value="">— Category —</option>
						{flattenCategories(categories).map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
					</select>
				</div>

				<div className="text-xs sm:text-sm text-gray-500">
					{dayjs(tx.date).format("ddd, DD MMM YY HH:mm")}
				</div>

				<div className="flex flex-wrap gap-1 sm:space-x-2 text-xs sm:text-sm">
					{!isChild && (
						<button className="text-purple-600 hover:underline" onClick={() => setSplitTx({ ...tx, parentId: tx.id, accountId: tx.account?.id })}>Split</button>
					)}
					{!isChild && (!tx.children || tx.children.length === 0) && (
						<button className="text-teal-600 hover:underline" onClick={() => setTransferTx({ ...tx, accountId: tx.account?.id, destinationAccountId: "", explanation: tx.explanation || "" })}>Transfer</button>
					)}
					<button className="text-red-600 hover:underline" onClick={() => deleteTx(tx.id)}>Delete</button>
					<button className="text-blue-600 hover:underline" onClick={() => setEditTx({ ...tx, accountId: tx.account?.id, categoryId: tx.category?.id })}>Update</button>
				</div>
			</div>
		)
	};

	function TransactionPageButtons({ filterMonth, filterAccount, filterCategory, filterType, search }) {
		return (
			<div className="flex items-center gap-2 mt-2">
				<button
					onClick={() => {
						setFilterMonth("");
						setFilterAccount("");
						setFilterCategory("");
						setFilterType("ALL");
						setSearch("");
						setPage(0); // Optional: reset to first page when filters cleared
					}}
					className="text-sm text-red-600 underline ml-2"
				>
					Clear All Filters
				</button>

				<button
					onClick={() => setEditTx({
						id: null, description: "", explanation: "",
						amount: 0, date: dayjs().format("YYYY-MM-DDTHH:mm"),
						type: "DEBIT", accountId: "", categoryId: ""
					})}
					className="bg-blue-600 text-white px-3 py-1 rounded text-sm"
				>
					Add Transaction
				</button>
				<button
					onClick={async () => {
						const params = new URLSearchParams({
							month: filterMonth,
							accountId: filterAccount,
							type: filterType,
							search
						});
						if (filterCategory) params.append("categoryId", filterCategory);
						const res = await api.get(`/transactions/export?${params}`);
						const csv = Papa.unparse(res.data);
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
							search
						});
						if (filterCategory) params.append("categoryId", filterCategory);
						const res = await api.get(`/transactions/export?${params}`);
						const worksheet = XLSX.utils.json_to_sheet(res.data);
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

				    const res = await api.get(`/transactions/export?${params}`);

				    // Create a PDF using jsPDF
				    const { jsPDF } = await import("jspdf");
				    const doc = new jsPDF();

				    // Optional: AutoTable plugin for tables
				    const autoTable = (await import("jspdf-autotable")).default;

				    const headers = Object.keys(res.data[0] || {});
				    const rows = res.data.map(row => headers.map(key => row[key]));

				    autoTable(doc, {
				      head: [headers],
				      body: rows,
				      styles: { fontSize: 8 },
				      margin: { top: 20 },
				    });

				    doc.save("transactions.pdf");
				  }}
				  className="bg-red-600 text-white px-3 py-1 rounded text-sm ml-2"
				>
				  Export PDF
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
			} else if (
				(pages.length > 0 && pages[pages.length - 1] !== -1)
			) {
				pages.push(-1); // Ellipsis
			}
		}

		return (
			<div className="flex flex-wrap items-center gap-2 text-sm mt-4 justify-between">
				<div>
					<button disabled={page === 0} onClick={() => setPage(0)} className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50">First</button>
					<button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1">Prev</button>
					{pages.map((p, idx) =>
						p === -1 ? (
							<span key={idx} className="px-2 py-1">...</span>
						) : (
							<button
								key={p}
								className={`px-2 py-1 rounded ${p === page ? "bg-blue-600 text-white" : "bg-gray-200"}`}
								onClick={() => setPage(p)}
							>
								{p + 1}
							</button>
						)
					)}
					<button disabled={page === totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1">Next</button>
					<button disabled={page === totalPages - 1} onClick={() => setPage(totalPages - 1)} className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1">Last</button>
				</div>
				<div>
					<label className="mr-2">Rows:</label>
					<select value={pageSize} onChange={(e) => { setPageSize(+e.target.value); setPage(0); }} className="border px-2 py-1 rounded">
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
					onChange={e => setFilterMonth(e.target.value)}
					className="border px-2 py-1 rounded text-sm"
				/>

				<select
					value={filterAccount}
					onChange={e => setFilterAccount(e.target.value)}
					className="border px-2 py-1 rounded text-sm"
				>
					<option value="">All Accounts</option>
					{accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
				</select>

				<select
					value={filterCategory}
					onChange={e => setFilterCategory(e.target.value)}
					className="border px-2 py-1 rounded text-sm"
				>
					<option value="">All Categories</option>
					{flattenCategories(categories).map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
				</select>

				<select
					value={filterType}
					onChange={e => setFilterType(e.target.value)}
					className="border px-2 py-1 rounded text-sm"
				>
					<option value="ALL">All Types</option>
					<option value="CREDIT">Credit</option>
					<option value="DEBIT">Debit</option>
				</select>

				<input
					value={search}
					onChange={e => setSearch(e.target.value)}
					placeholder="Search"
					className="border px-2 py-1 rounded text-sm"
				/>


				<TransactionPageButtons
					filterMonth={filterMonth}
					filterAccount={filterAccount}
					filterCategory={filterCategory}
					filterType={filterType}
					search={debouncedSearch}
				/>

			</div>


			{renderPagination()}

			{/* Rows */}
			{transactions.flatMap((tx, idx) => [
			  renderRow(tx, false, idx),
			  ...(expandedParents[tx.id] && tx.children
			    ? tx.children.map(child => renderRow(child, true))
			    : [])
			])}

			{renderPagination()}

			{/* Modals */}
			{splitTx && (
				<TransactionSplit transaction={splitTx} setTransaction={setSplitTx} onCancel={() => setSplitTx(null)} onSubmit={async (enrichedParent) => {
					setLoading(true); NProgress.start();
					const childrenPayload = enrichedParent.children.map(c => ({
						description: c.description, amount: c.amount,
						date: enrichedParent.date, type: enrichedParent.type,
						account: { id: enrichedParent.accountId },
						category: c.categoryId ? { id: c.categoryId } : null,
						parentId: enrichedParent.parentId
					}));
					await api.post("/transactions/split", childrenPayload);
					setSplitTx(null); await fetchData(); NProgress.done(); setLoading(false);
				}} categories={categories} />
			)}

			{transferTx && (
				<TransferForm transaction={transferTx} setTransaction={setTransferTx} onCancel={() => setTransferTx(null)} onSubmit={async () => {
					setLoading(true); NProgress.start();
					await api.post("/transactions/transfer", {
						sourceTransactionId: transferTx.id,
						destinationAccountId: transferTx.destinationAccountId,
						explanation: transferTx.explanation
					});
					setTransferTx(null); await fetchData(); NProgress.done(); setLoading(false);
				}} accounts={accounts} />
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
