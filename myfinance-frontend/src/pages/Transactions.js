import React, { useEffect, useState, useMemo } from "react";
import api from "../auth/api";
import dayjs from "dayjs";
import { useSearchParams, useNavigate } from "react-router-dom";
import Papa from "papaparse";
import * as XLSX from "xlsx";
import { saveAs } from "file-saver";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import useDebounce from "../hooks/useDebounce";
import { useErrorModal } from "../auth/ErrorModalContext";

// Import extracted components
import FetchToolbar from "./transactions/components/FetchToolbar";
import SearchSelect from "./transactions/components/SearchSelect";
import TransactionForm from "./transactions/components/TransactionForm";
import TransferForm from "./transactions/components/TransferForm";
import TransferPairModal from "./transactions/components/TransferPairModal";
import TransactionSplit from "./transactions/components/TransactionSplit";
import TransactionComparisonModal from "./transactions/components/TransactionComparisonModal";
import TransactionDetailsModal from "./transactions/components/TransactionDetailsModal";
import TransactionAttachments from "./transactions/components/TransactionAttachments";
import BulkActionBar from "./transactions/components/BulkActionBar";
import LabelInput from "./transactions/components/LabelInput";
import { buildTree, flattenCategories } from "./transactions/utils/utils";
import { getCategoryColor } from "./transactions/utils/categoryColors";
import { getCurrencySymbol, getAmountSizeClass } from "./transactions/utils/currency";


// Fixed-length truncation for displayed transaction descriptions/explanations.
// Keeps every row visually uniform; full text is always available via the
// title tooltip and the click-to-open details modal.
const DESCRIPTION_DISPLAY_LIMIT = 32;
const EXPLANATION_DISPLAY_LIMIT = 50;
const truncateText = (text, limit) => {
	if (!text) return "";
	const str = String(text);
	return str.length > limit ? str.slice(0, limit - 1).trimEnd() + "…" : str;
};

// ---- main ----
export default function Transactions() {
	const navigate = useNavigate();
	const { showModal } = useErrorModal();
	// Read initial URL params eagerly so all useState initializers below can derive
	// their defaults from them. We deliberately read once via window.location rather
	// than the React Router hook, because useSearchParams() must be called inside
	// the component body and we want this snapshot available before any state.
	const initialUrlParams = (() => {
		if (typeof window === "undefined") return new URLSearchParams();
		return new URLSearchParams(window.location.search);
	})();
	const [transactions, setTransactions] = useState([]);
	const [expandedParents, setExpandedParents] = useState({});
	const [accounts, setAccounts] = useState([]);
	const [currentTotal, setCurrentTotal] = useState(0);
	const [categories, setCategories] = useState([]);
	const [editTx, setEditTx] = useState(null);
	const [splitTx, setSplitTx] = useState(null);
	const [transferTx, setTransferTx] = useState(null);
	const [labelEditTx, setLabelEditTx] = useState(null);
	const [attachmentsTx, setAttachmentsTx] = useState(null);
	// Bulk selection: stable Set of selected transaction IDs that survives pagination
	// but is cleared whenever the filter set changes (see effect below).
	const [selectedIds, setSelectedIds] = useState(() => new Set());
	// Tracks transactions whose inline category dropdown is currently saving.
	// Used to suppress duplicate PUTs that the inline picker can otherwise fire
	// (e.g. from React re-renders during an in-flight save, double-tap, or
	// SearchSelect's auto-select effect re-running when parent refs change).
	const [savingCategoryIds, setSavingCategoryIds] = useState(() => new Set());
	const [loading, setLoading] = useState(false);
	// page/pageSize are persisted in URL via the param-sync effect below so a
	// browser refresh keeps the user on the same row range. Parsed defensively
	// in case the URL was hand-edited.
	const [page, setPage] = useState(() => {
		const p = parseInt(initialUrlParams.get("page") || "0", 10);
		return Number.isFinite(p) && p >= 0 ? p : 0;
	});
	const [totalPages, setTotalPages] = useState(0);
	const [pageSize, setPageSize] = useState(() => {
		const s = parseInt(initialUrlParams.get("size") || "10", 10);
		return Number.isFinite(s) && s > 0 ? s : 10;
	});
	const [totalCount, setTotalCount] = useState(0);
	const [modalContent, setModalContent] = useState(null);
	const [deleteConfirmation, setDeleteConfirmation] = useState(null);
	const [searchParams, setSearchParams] = useSearchParams();
	const [filterMonth, setFilterMonth] = useState(
		searchParams.get("month") || dayjs().format("YYYY-MM")
	);
	const [filterDate, setFilterDate] = useState(
		searchParams.get("date") || ""
	);
	const [filterStartDate, setFilterStartDate] = useState(
		searchParams.get("startDate") || ""
	);
	const [filterEndDate, setFilterEndDate] = useState(
		searchParams.get("endDate") || ""
	);
	const updateUrlParams = (overrides = {}) => {
	  const params = new URLSearchParams(searchParams);

	  // Apply overrides (new values for filters)
	  Object.entries(overrides).forEach(([key, value]) => {
	    if (value && value !== "ALL" && value !== null && value !== undefined) {
	      params.set(key, value);
	    } else {
	      params.delete(key); // remove empty/default values from URL
	    }
	  });

	  setSearchParams(params);
	};

	// Keep page / pageSize in the URL so refreshing or sharing the link preserves them.
	// Defaults (page=0, size=10) are represented by absence of the param to keep URLs clean.
	useEffect(() => {
		const params = new URLSearchParams(searchParams);
		const setOrDelete = (key, value) => {
			if (value === null || value === undefined || value === "") {
				params.delete(key);
			} else {
				params.set(key, value);
			}
		};
		setOrDelete("page", page > 0 ? String(page) : "");
		setOrDelete("size", pageSize !== 10 ? String(pageSize) : "");
		// Avoid pushing a new history entry if nothing actually changed.
		// `searchParams` and `setSearchParams` are intentionally omitted from
		// the dep array — they're stable enough from useSearchParams, and
		// including them would re-fire this effect on every URL change (which
		// is exactly what this effect causes), creating a feedback loop.
		if (params.toString() !== searchParams.toString()) {
			setSearchParams(params, { replace: true });
		}
	}, [page, pageSize]);
	const [filterAccount, setFilterAccount] = useState(
		searchParams.get("accountId") || ""
	);
	const [filterType, setFilterType] = useState(
	  searchParams.get("type") || "ALL"
	);
	const [filterCategory, setFilterCategory] = useState(
		searchParams.get("categoryId") || ""
	);
	const [filterLabel, setFilterLabel] = useState(
		searchParams.get("labelId") || ""
	);
	const [search, setSearch] = useState(
	  searchParams.get("search") || ""
	);
	const [filterHasAttachments, setFilterHasAttachments] = useState(
		searchParams.get("hasAttachments") === "true"
	);
	const debouncedSearch = useDebounce(search, 500);
const [refreshing, setRefreshing] = useState(false);
const [availableServices, setAvailableServices] = useState([]);
const [selectedServices, setSelectedServices] = useState([]);
const [labels, setLabels] = useState([]);

// Sorting state - null means no sort (default)
const [sortBy, setSortBy] = useState(searchParams.get("sortBy") || null);
const [sortDir, setSortDir] = useState(searchParams.get("sortDir") || null);

// Unified date filter: choose Month, Date, or Range (default Month unless URL has date or startDate)
const [filterMode, setFilterMode] = useState(
	searchParams.get("startDate") ? "range" : searchParams.get("date") ? "date" : searchParams.get("noMonth") ? "range" : "month"
);

// (moved to top-level above)

	// ESC key handler for modals
	useEffect(() => {
		const handleEscKey = (e) => {
			if (e.key === "Escape") {
				if (attachmentsTx) {
					setAttachmentsTx(null);
				} else if (labelEditTx) {
					setLabelEditTx(null);
				} else if (modalContent) {
					setModalContent(null);
				} else if (deleteConfirmation) {
					setDeleteConfirmation(null);
				}
			}
		};
		
		if (modalContent || deleteConfirmation || labelEditTx || attachmentsTx) {
			document.addEventListener("keydown", handleEscKey);
			return () => document.removeEventListener("keydown", handleEscKey);
		}
	}, [modalContent, deleteConfirmation, labelEditTx, attachmentsTx]);

	NProgress.configure({ showSpinner: false });

// Load reference data once on mount — these don't change with filters
useEffect(() => {
    (async () => {
        try {
            const [accRes, catRes, labelsRes, configRes] = await Promise.all([
                api.get('/accounts'),
                api.get('/categories'),
                api.get('/labels'),
                api.get('/data-extraction/configurations'),
            ]);
            setAccounts(accRes.data);
            setCategories(catRes.data);
            setLabels(labelsRes.data);
            const list = configRes.data?.configurations || [];
            setAvailableServices(list);
            setSelectedServices(list);
        } catch (err) {
            console.error('Failed to load reference data:', err);
        }
    })();
}, []);

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
				accountId: filterAccount || "",
				type: filterType || "",
				search: search || "",
			});

			// Only add sort parameters if sorting is active
			if (sortBy && sortDir) {
				params.set("sortBy", sortBy);
				params.set("sortDir", sortDir);
			}

            if (filterMode === 'month' && filterMonth) {
                params.set('month', filterMonth);
            }
            if (filterMode === 'date' && filterDate) {
                params.set('date', filterDate);
            }
            if (filterMode === 'range' && filterStartDate && filterEndDate) {
                params.set('startDate', filterStartDate);
                params.set('endDate', filterEndDate);
            }

			if (filterCategory) {
				params.append("categoryId", filterCategory);
			}
			if (filterLabel) {
				params.append("labelId", filterLabel);
			}
			if (filterHasAttachments) {
				params.append("hasAttachments", "true");
			}

			const txRes = await api.get(`/transactions?${params.toString()}`);

			setTransactions(txRes.data.content);
			setTotalPages(txRes.data.totalPages);
			setTotalCount(txRes.data.totalElements);
			setCurrentTotal(txRes.data.currentTotal);
		} catch (error) {
			if (error.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to fetch user profile:", error);
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
    }, [page, pageSize, filterMode, filterMonth, filterDate, filterStartDate, filterEndDate, filterAccount, filterType, filterCategory, filterLabel, filterHasAttachments, debouncedSearch, sortBy, sortDir]);

	const saveTx = async (tx, method, url) => {
		setLoading(true);
		NProgress.start();
		try {
		const payload = {
			description: tx.description,
			explanation: tx.explanation || "",
			amount: tx.amount,
			date: tx.date,
			type: tx.type,
			currency: tx.currency || null,
			account: { id: tx.accountId },
			category: tx.categoryId ? { id: tx.categoryId } : null,
			parent: tx.parentId ? { id: tx.parentId } : null,
			labels: tx.labels || [],
		};
			console.log("Sending payload to backend:", payload);
			console.log("Date being sent:", tx.date);
			await api[method](url, payload);
			await fetchData();
		} catch (err) {
			console.error("Failed to save transaction:", err);
			// Error is already handled by api.js interceptor (shows modal)
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

	// Inline category change from the transaction list. Guards against the
	// duplicate PUTs that caused MySQL deadlocks on prediction_actual_txn_mapping:
	//   1. Skip when the value didn't actually change (no-op edits).
	//   2. Skip when a save for the same row is already in flight (double-tap,
	//      React re-render, SearchSelect auto-select effect re-running).
	// The savingCategoryIds set drives the SearchSelect's `disabled` prop too,
	// so the picker visually locks until the save round-trip completes.
	const handleInlineCategoryChange = async (tx, val) => {
		const currentCategoryId = tx.category?.id || "";
		if (val === currentCategoryId) return;
		if (savingCategoryIds.has(tx.id)) return;

		setSavingCategoryIds((prev) => {
			const next = new Set(prev);
			next.add(tx.id);
			return next;
		});
		try {
			await saveTx(
				{
					...tx,
					categoryId: val,
					accountId: tx.account?.id,
					parentId: tx.parent?.id,
				},
				"put",
				`/transactions/${tx.id}`
			);
		} finally {
			setSavingCategoryIds((prev) => {
				if (!prev.has(tx.id)) return prev;
				const next = new Set(prev);
				next.delete(tx.id);
				return next;
			});
		}
	};

	const deleteTx = async (id) => {
		setDeleteConfirmation(id);
	};

	const updateLabels = async (tx, newLabels) => {
		setLoading(true);
		NProgress.start();
		try {
			const payload = {
				id: tx.id,
				description: tx.description,
				explanation: tx.explanation || "",
				amount: tx.amount,
				date: tx.date,
				type: tx.type,
				currency: tx.currency || null,
				account: { id: tx.account?.id },
				category: tx.category ? { id: tx.category.id } : null,
				parentId: tx.parentId || null,
				labels: newLabels || [],
			};
			console.log("Updating labels:", payload);
			await api.put(`/transactions/${tx.id}`, payload);
			await fetchData();
			setLabelEditTx(null);
		} catch (err) {
			console.error("Failed to update labels:", err);
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

	const confirmDelete = async () => {
		const id = deleteConfirmation;
		setDeleteConfirmation(null);
		setLoading(true);
		NProgress.start();
		try {
			await api.delete(`/transactions/${id}`);
			console.log("Transaction deleted successfully");
			await fetchData();
		} catch (err) {
			console.error("Failed to delete transaction:", err);
			// Error is already handled by api.js interceptor (shows modal)
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

const triggerDataExtraction = async (servicesToRun) => {
		const serviceCount = servicesToRun && servicesToRun.length > 0 ? servicesToRun.length : 'all';
		const serviceText = servicesToRun && servicesToRun.length > 0 
			? `${servicesToRun.length} selected service(s)` 
			: 'all data extraction services';
		
		return new Promise((resolve) => {
			showModal(
				`This will trigger ${serviceText} to check for new transactions from your connected email accounts. Continue?`,
				() => {
					// User confirmed
					setRefreshing(true);
					NProgress.start();
					performDataExtraction(servicesToRun, resolve);
				},
				() => {
					// User cancelled
					resolve();
				}
			);
		});
	};

	const performDataExtraction = async (servicesToRun, resolve) => {
		try {
			// Prepare request body with configurations array
			const requestBody = servicesToRun && servicesToRun.length > 0
				? { configurations: servicesToRun }
				: {};
			
			// The backend request blocks until extraction completes (typically 15-20 seconds)
			const response = await api.post('/data-extraction/trigger', requestBody);
			
			if (response.data.status === 'started') {
				// Backend has completed extraction, now refresh transactions
				try {
					await fetchData();
					showModal(`Data extraction completed!\n\nTransactions have been refreshed. ${response.data.configurations.length} service(s) processed.`);
				} catch (err) {
					console.error('Error refreshing transactions:', err);
					showModal('Data extraction completed but failed to refresh transactions. Please refresh the page manually.');
				}
			} else {
				showModal('Data extraction returned unexpected status: ' + response.data.status);
			}
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to trigger data extraction:", err);
				const errorMsg = err.response?.data?.message || err.response?.data?.error || "Failed to trigger data extraction. Please check the logs.";
				showModal(`Error: ${errorMsg}`);
			}
		} finally {
			setRefreshing(false);
			NProgress.done();
			resolve();
		}
	};

	// Build + flatten once per render and reuse everywhere
	const treeCategories = buildTree(categories);
	const rootHome = treeCategories.filter((cat) => cat?.name === "Home");
	const limitedTree = rootHome.length > 0 ? rootHome : treeCategories;
	const flattened = flattenCategories(limitedTree);

	const displayedRows = useMemo(() => {
		return (transactions || []).reduce((acc, tx) => {
			const childCount = expandedParents[tx.id] && Array.isArray(tx.children)
				? tx.children.filter((c) => typeof c === "object" && c !== null).length
				: 0;
			return acc + 1 + childCount;
		}, 0);
	}, [transactions, expandedParents]);

	// Bulk selection helpers
	const isSelectable = (tx) => {
		if (!tx || !tx.id) return false;
		// Children (split parts) inherit category from parent; skip them in bulk.
		if (tx.parentId) return false;
		return true;
	};
	const selectablePageIds = useMemo(
		() => (transactions || []).filter(isSelectable).map((tx) => tx.id),
		[transactions]
	);
	const allOnPageSelected = selectablePageIds.length > 0 && selectablePageIds.every((id) => selectedIds.has(id));
	const someOnPageSelected = !allOnPageSelected && selectablePageIds.some((id) => selectedIds.has(id));
	const toggleRowSelection = (id) => {
		setSelectedIds((prev) => {
			const next = new Set(prev);
			if (next.has(id)) next.delete(id);
			else next.add(id);
			return next;
		});
	};
	const togglePageSelection = () => {
		setSelectedIds((prev) => {
			const next = new Set(prev);
			if (allOnPageSelected) {
				selectablePageIds.forEach((id) => next.delete(id));
			} else {
				selectablePageIds.forEach((id) => next.add(id));
			}
			return next;
		});
	};
	const clearSelection = () => setSelectedIds(new Set());

	// Clear selection whenever the underlying filter set changes — avoids accidentally
	// applying a bulk action to rows that are no longer visible.
	useEffect(() => {
		setSelectedIds(new Set());
	}, [filterMode, filterMonth, filterDate, filterStartDate, filterEndDate, filterAccount, filterType, filterCategory, filterLabel, filterHasAttachments, debouncedSearch]);

	const applyBulkCategory = async ({ ids, categoryId }) => {
		const res = await api.post("/transactions/bulk/category", {
			transactionIds: ids,
			categoryId,
		});
		await fetchData();
		clearSelection();
		return res.data;
	};

	const applyBulkLabels = async ({ ids, labels, mode }) => {
		const res = await api.post("/transactions/bulk/labels", {
			transactionIds: ids,
			labels: (labels || []).map((l) => ({ id: l.id || null, name: l.name })),
			mode,
		});
		await fetchData();
		clearSelection();
		return res.data;
	};

	const currentPageSum = useMemo(() => {
		return (transactions || []).reduce((sum, tx) => {
			const amount = typeof tx.amount === "number" ? tx.amount : 0;
			// CREDIT adds to sum, DEBIT subtracts from sum
			const multiplier = tx.type === "CREDIT" ? 1 : -1;
			
			// Only add parent amounts since backend returns parent with sum of children
			// Do NOT add child amounts separately to avoid double-counting
			return sum + (amount * multiplier);
		}, 0);
	}, [transactions]);

	// Helper function to create professional transaction comparison modal (with GPT data)
	const createComparisonModal = (tx) => {
		return <TransactionComparisonModal tx={tx} />;
	};

	// Helper function to create simple transaction details modal (without GPT data)
	const createDetailsModal = (tx) => {
		return <TransactionDetailsModal tx={tx} />;
	};

	const handleTransferLinkClick = async (currentTx, linkedTransferId, e) => {
		e.stopPropagation();

		let linkedTx = transactions.find(tx => tx.id === linkedTransferId);

		if (!linkedTx) {
			try {
				setLoading(true);
				NProgress.start();
				const response = await api.get(`/transactions/${linkedTransferId}`);
				linkedTx = response.data;
			} catch (err) {
				console.error("Failed to fetch linked transaction:", err);
				showModal(`Failed to fetch linked transaction: ${err.response?.data?.message || err.message}`);
				return;
			} finally {
				NProgress.done();
				setLoading(false);
			}
		}

		if (!linkedTx) return;

		const pair = [currentTx, linkedTx];
		const fromTx = pair.find((t) => t.type === "DEBIT") || currentTx;
		const toTx = pair.find((t) => t.type === "CREDIT") || linkedTx;

		setModalContent({
			title: "Transfer details",
			content: <TransferPairModal fromTx={fromTx} toTx={toTx} />,
		});
	};

	const renderRow = (tx, isChild = false, index = 0) => {
		const baseColor = isChild
			? "bg-gray-50 border-dashed"
			: index % 2 === 0
				? "bg-blue-50"
				: "bg-blue-100";
		const hasGpt = typeof tx.gptDescription === 'string' && tx.gptDescription.trim().length > 0;
		return (
			<div
				key={tx.id}
				className={`grid w-full grid-cols-1 sm:grid-cols-[24px_minmax(0,3fr)_minmax(180px,2fr)_minmax(0,2fr)_minmax(0,1fr)_minmax(0,2fr)] gap-2 py-2 px-3 rounded border items-center text-sm ${baseColor} border-gray-200`}
			>
				<div className="text-xs sm:col-span-1 hidden sm:flex items-center justify-center">
					{!isChild && tx.children?.length > 0 ? (
						<div className="flex items-center gap-1">
							<input
								type="checkbox"
								checked={selectedIds.has(tx.id)}
								onChange={() => toggleRowSelection(tx.id)}
								className="cursor-pointer"
								title="Select for bulk action"
								onClick={(e) => e.stopPropagation()}
							/>
							<button
								onClick={() => toggleExpand(tx.id)}
								className="text-gray-600 hover:text-black"
								title="Toggle children"
							>
								{expandedParents[tx.id] ? "▼" : "▶"}
							</button>
						</div>
					) : !isChild ? (
						<input
							type="checkbox"
							checked={selectedIds.has(tx.id)}
							onChange={() => toggleRowSelection(tx.id)}
							className="cursor-pointer"
							title="Select for bulk action"
							onClick={(e) => e.stopPropagation()}
						/>
					) : null}
				</div>

				<div className={`flex flex-col min-w-0 ${isChild ? 'pl-3' : ''}`}>
					{/* The icon row deliberately stays overflow-visible so the
					    attachment count badge can sit slightly above the row.
					    The description/explanation children still clip themselves
					    via `truncate`, so we don't need overflow-hidden here. */}
					<div className="flex items-center gap-1 min-w-0 font-medium text-gray-800">
						{!isChild && tx.children?.length > 0 && (
							<button
								title="Toggle children"
								className="flex-shrink-0 text-gray-600 hover:text-black sm:hidden"
								onClick={() => toggleExpand(tx.id)}
							>
								{expandedParents[tx.id] ? "▼" : "▶"}
							</button>
						)}
						<span
							className="flex-1 truncate min-w-0 cursor-pointer hover:text-blue-600"
							title={(tx.description || tx.shortDescription || "") + (hasGpt ? "  •  ✨ Click to view AI analysis" : "  •  🔍 Click to view details")}
							onClick={() =>
								setModalContent({
									title: hasGpt ? "Transaction Analysis & Comparison" : "Transaction Details",
									content: hasGpt ? createComparisonModal(tx) : createDetailsModal(tx),
								})}
						>
							{truncateText(tx.shortDescription || tx.description, DESCRIPTION_DISPLAY_LIMIT)}
						</span>
						<button
							title={tx.labels && tx.labels.length > 0 ? `Edit labels (${tx.labels.length})` : "Add labels"}
								className={`flex-shrink-0 text-sm px-1 cursor-pointer transition-colors ${
									tx.labels && tx.labels.length > 0
										? "text-blue-600 hover:text-blue-800"
										: "text-gray-400 hover:text-gray-600"
								}`}
								onClick={(e) => {
									e.stopPropagation();
									setLabelEditTx({
										...tx,
										labels: tx.labels || []
									});
								}}
							>
								{tx.labels && tx.labels.length > 0 ? "🏷️" : "🏷"}
							</button>
						{(() => {
							const attachmentCount = tx.attachmentCount || 0;
							const hasAttachments = attachmentCount > 0;
							return (
								<button
									title={hasAttachments
										? `${attachmentCount} attachment${attachmentCount === 1 ? "" : "s"} — click to view or upload more`
										: "View / upload receipt attachments (image or PDF)"}
									className={`relative flex-shrink-0 text-sm px-1 cursor-pointer transition-colors ${hasAttachments
										? "text-blue-600 hover:text-blue-800"
										: "text-gray-400 hover:text-gray-600 opacity-70 hover:opacity-100"}`}
									onClick={(e) => {
										e.stopPropagation();
										setAttachmentsTx(tx);
									}}
								>
									<span aria-hidden="true">📎</span>
									{hasAttachments && (
										<span
											aria-label={`${attachmentCount} attachment${attachmentCount === 1 ? "" : "s"}`}
											className="absolute -top-1 -right-0.5 inline-flex items-center justify-center min-w-[14px] h-[14px] px-0.5 text-[10px] font-bold leading-none text-white bg-blue-600 rounded-full ring-1 ring-white"
										>
											{attachmentCount > 99 ? "99+" : attachmentCount}
										</span>
									)}
								</button>
							);
						})()}
					</div>
					{(tx.shortExplanation || tx.explanation) && (
						<div 
							className="truncate text-xs text-gray-500 cursor-pointer hover:text-blue-600"
							title={(tx.explanation || tx.shortExplanation || "") + (hasGpt ? "  •  ✨ Click to view AI analysis comparison" : "  •  🔍 Click to view transaction details")}
							onClick={() =>
								setModalContent({
									title: hasGpt ? "Transaction Analysis & Comparison" : "Transaction Details",
									content: hasGpt ? createComparisonModal(tx) : createDetailsModal(tx),
								})}
						>
							{truncateText(tx.shortExplanation || tx.explanation, EXPLANATION_DISPLAY_LIMIT)}
						</div>
					)}

						{/* Display labels if any */}
					{tx.labels && tx.labels.length > 0 && (
								<div className="flex flex-wrap gap-1 mt-1">
									{tx.labels.slice(0, 3).map((label, idx) => (
										<span
											key={label.id || label.name || idx}
											className="inline-block px-1.5 py-0.5 bg-blue-100 text-blue-700 rounded text-xs"
											title={label.name}
										>
											{label.name}
										</span>
									))}
									{tx.labels.length > 3 && (
										<span className="inline-block px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded text-xs">
											+{tx.labels.length - 3}
										</span>
									)}
								</div>
							)}
					</div>

			<div className="text-gray-700 sm:text-right">
				<span className="whitespace-nowrap inline-flex items-baseline gap-1 sm:justify-end w-full">
					<span
						className={`font-semibold tabular-nums ${getAmountSizeClass(tx.amount)} ${
							tx.type === "DEBIT" ? "text-red-600" : "text-green-600"
						}`}
					>
						<span className="text-xs text-gray-500 mr-0.5 font-normal">
							{getCurrencySymbol(tx.currency)}
						</span>
						{(typeof tx.amount === "number" ? tx.amount : 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
					</span>
					<span className="uppercase text-[10px] bg-gray-100 text-gray-600 rounded px-1 inline-block min-w-[44px] text-center">
						{tx.type}
					</span>
				</span>
					{(() => {
						const linkedId = tx.linkedTransferId;
						const reverseLinkedTx = transactions.find(otherTx =>
							otherTx.id !== tx.id && otherTx.linkedTransferId === tx.id
						);
						const hasLink = linkedId || reverseLinkedTx;
						const linkTargetId = linkedId || (reverseLinkedTx ? reverseLinkedTx.id : null);
						if (hasLink && linkTargetId) {
							return (
								<button
									onClick={(e) => {
										e.stopPropagation();
										handleTransferLinkClick(tx, linkTargetId, e);
									}}
									className="ml-2 text-xs bg-purple-100 text-purple-700 rounded px-2 py-0.5 inline-flex items-center gap-1 hover:bg-purple-200 cursor-pointer transition-colors"
									title="View transfer (from → to)"
									type="button"
								>
									🔗 Transfer
								</button>
							);
						}
						return null;
					})()}
					<>
						<br />
						<span className="text-xs text-gray-500">
							{tx.account?.name}
						</span>
					</>
				</div>

				<div className="order-3 sm:order-none">
					{(() => {
						const c = getCategoryColor(tx.category?.id || tx.category?.name);
						return (
							<div className="flex items-center gap-2">
								<span
									className={`flex-shrink-0 w-2.5 h-2.5 rounded-full ${tx.category?.id ? c.dot : "bg-gray-300"}`}
									title={tx.category?.name || "Uncategorised"}
								/>
								<div className="flex-1 min-w-0">
									<SearchSelect
										options={flattened.map(co => ({ id: co.id, name: co.name }))}
										value={tx.category?.id || ""}
										disabled={savingCategoryIds.has(tx.id)}
										onChange={(val) => {
											handleInlineCategoryChange(tx, val);
										}}
										placeholder="Category"
									/>
								</div>
							</div>
						);
					})()}
				</div>

				<div className="hidden sm:flex flex-col text-xs sm:text-sm text-gray-500 self-start sm:self-center order-4 sm:order-none leading-tight">
					<span>{dayjs(tx.date).format("DD MMM YYYY")}</span>
					<span className="text-xs text-gray-400">{dayjs(tx.date).format("ddd, h:mm A")}</span>
				</div>

				<div className="hidden sm:flex flex-wrap gap-2 text-xs sm:text-sm justify-start sm:justify-end order-5 sm:order-none">
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
							title="Split this transaction into multiple parts with different categories"
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
							title="Mark this as a transfer between accounts (creates linked transactions)"
						>
							Transfer
						</button>
					)}
					<button
						className="text-red-600 hover:underline"
						onClick={() => deleteTx(tx.id)}
						title="Delete this transaction permanently (cannot be undone)"
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
								currency: tx.currency || 'INR',
								labels: tx.labels || [],
							})
						}
						title="Edit transaction details (amount, description, category, etc.)"
					>
						Update
					</button>
				</div>

				{/* Mobile footer: date + actions */}
				<div className="sm:hidden flex items-center justify-between mt-2 text-xs">
						<div className="text-gray-500">{dayjs(tx.date).format("ddd, DD MMM YYYY h:mm A")}</div>
						<div className="flex gap-3">
							{!isChild && (
								<button
									className="text-purple-600"
									onClick={() =>
										setSplitTx({
											...tx,
											parentId: tx.id,
											accountId: tx.account?.id,
										})
									}
									title="Split this transaction into multiple parts with different categories"
								>
									Split
								</button>
							)}
							{!isChild && (!tx.children || tx.children.length === 0) && (
								<button
									className="text-teal-600"
									onClick={() =>
										setTransferTx({
											...tx,
											accountId: tx.account?.id,
											destinationAccountId: "",
											explanation: tx.explanation || "",
										})
									}
									title="Mark this as a transfer between accounts (creates linked transactions)"
								>
									Transfer
								</button>
							)}
							<button 
								className="text-red-600" 
								onClick={() => deleteTx(tx.id)}
								title="Delete this transaction permanently (cannot be undone)"
							>
								Delete
							</button>
							<button
								className="text-blue-600"
								onClick={() =>
									setEditTx({
										...tx,
										accountId: tx.account?.id,
										categoryId: tx.category?.id,
										currency: tx.currency || 'INR',
									})
								}
								title="Edit transaction details (amount, description, category, etc.)"
							>
								Update
							</button>
						</div>
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
    availableServices,
    selectedServices,
    setSelectedServices,
		refreshing,
}) {
		return (
			<div className="w-full bg-white border border-blue-200 rounded-md p-3 shadow-sm space-y-2">
				{/* Line 1: Service selector + Fetch */}
				<div className="flex flex-wrap items-center gap-2">
					<select
						value={selectedServices.length === availableServices.length ? 'ALL' : (selectedServices[0] || 'ALL')}
						onChange={(e) => {
							const val = e.target.value;
							if (val === 'ALL') {
								setSelectedServices(availableServices);
							} else {
								setSelectedServices([val]);
							}
						}}
						className="border px-3 py-2 rounded text-sm min-w-[260px] bg-blue-50"
						title="Select a data extraction service or All"
					>
						<option value="ALL">All Services</option>
						{(availableServices || []).map((svc) => (
							<option key={svc} value={svc}>{svc}</option>
						))}
					</select>
				<button
					onClick={() => triggerDataExtraction(selectedServices)}
					disabled={refreshing}
					className={`${refreshing 
						? 'bg-gray-400 cursor-not-allowed' 
						: 'bg-purple-600 hover:bg-purple-700'
					} text-white px-4 py-2 rounded text-sm shadow`}
					title="Trigger selected data extraction services to fetch new transactions"
				>
					Fetch
				</button>
				</div>

				{/* Line 2: Filters and quick actions */}
				<div className="flex flex-wrap items-center gap-2 justify-between">
					<div className="flex flex-wrap items-center gap-2">
						<button
							onClick={() =>
								setEditTx({
									id: null,
									description: '',
									explanation: '',
									amount: 0,
									date: dayjs().format('YYYY-MM-DDTHH:mm'),
									type: 'DEBIT',
									labels: [],
								currency: 'INR',
									accountId: '',
									categoryId: '',
								})
							}
                    className="bg-green-500 text-white px-4 py-2 rounded text-sm shadow hover:bg-green-600"
						>
							Add
						</button>
						<button
                        onClick={async () => {
								const params = new URLSearchParams({
                                ...(filterMode === 'month' && filterMonth ? { month: filterMonth } : {}),
                                ...(filterMode === 'date' && filterDate ? { date: filterDate } : {}),
									accountId: filterAccount,
									type: filterType,
									search,
								});
								if (filterCategory) params.append('categoryId', filterCategory);
								if (filterLabel) params.append('labelId', filterLabel);
								try {
									const res = await api.get(`/transactions/export?${params}`);
									const flattenedRows = res.data.map((tx) => ({
										Date: tx.date,
										Description: tx.description,
										Explanation: tx.explanation || '',
										Amount: tx.amount,
										Type: tx.type,
										Account: tx.account?.name || '',
										Category: tx.category?.name || '',
									}));
									const csv = Papa.unparse(flattenedRows);
									const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
									saveAs(blob, 'transactions.csv');
								} catch (err) {
									if (err.response?.status === 401) {
										localStorage.removeItem('authToken');
										navigate('/');
									} else {
										alert('Failed to export CSV. Please try again.');
									}
								}
							}}
							className="bg-blue-500 text-white px-3 py-1 rounded text-sm hover:bg-blue-600"
						>
							CSV
						</button>
						<button
                        onClick={async () => {
								const params = new URLSearchParams({
                                ...(filterMode === 'month' && filterMonth ? { month: filterMonth } : {}),
                                ...(filterMode === 'date' && filterDate ? { date: filterDate } : {}),
									accountId: filterAccount,
									type: filterType,
									search,
								});
								if (filterCategory) params.append('categoryId', filterCategory);
								if (filterLabel) params.append('labelId', filterLabel);
								try {
									const res = await api.get(`/transactions/export?${params}`);
									const flattenedRows = res.data.map((tx) => ({
										Date: tx.date,
										Description: tx.description,
										Explanation: tx.explanation || '',
										Amount: tx.amount,
										Type: tx.type,
										Account: tx.account?.name || '',
										Category: tx.category?.name || '',
									}));
									const worksheet = XLSX.utils.json_to_sheet(flattenedRows);
									const workbook = XLSX.utils.book_new();
									XLSX.utils.book_append_sheet(workbook, worksheet, 'Transactions');
									const excelBuffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
									const blob = new Blob([excelBuffer], { type: 'application/octet-stream' });
									saveAs(blob, 'transactions.xlsx');
								} catch (err) {
									if (err.response?.status === 401) {
										localStorage.removeItem('authToken');
										navigate('/');
									} else {
										alert('Failed to export XLSX. Please try again.');
									}
								}
							}}
							className="bg-blue-500 text-white px-3 py-1 rounded text-sm hover:bg-blue-600"
						>
							XLSX
						</button>
						<button
                        onClick={async () => {
								const params = new URLSearchParams({
                                ...(filterMode === 'month' && filterMonth ? { month: filterMonth } : {}),
                                ...(filterMode === 'date' && filterDate ? { date: filterDate } : {}),
									accountId: filterAccount,
									type: filterType,
									search,
								});
								if (filterCategory) params.append('categoryId', filterCategory);
								if (filterLabel) params.append('labelId', filterLabel);
								try {
								const res = await api.get(`/transactions/export?${params}`);
								const { jsPDF } = await import('jspdf');
								const flattenedRows = res.data.map((tx) => ({
									Date: dayjs(tx.date).isValid() ? dayjs(tx.date).format('DD/MMM') : '',
									Description: tx.description,
									Amount: tx.amount,
									Type: tx.type,
									Account: tx.account?.name || '',
								}));
								const autoTable = (await import('jspdf-autotable')).default;
								const doc = new jsPDF();
								const headers = ['Date', 'Description', 'Amount', 'Type', 'Account'];
								const rows = flattenedRows.map((row) => headers.map((key) => row[key]));

								autoTable(doc, {
									head: [headers],
									body: rows,
									styles: {
										fontSize: 8,
										cellWidth: 'wrap',
									},
									columnStyles: {
										0: { cellWidth: 20 },
										1: { cellWidth: 70 },
										2: { cellWidth: 25, halign: 'right' },
										3: { cellWidth: 15 },
										4: { cellWidth: 30 },
									},
									tableWidth: 'wrap',
									margin: { top: 20 },
								});

								doc.save('transactions.pdf');
								} catch (err) {
									if (err.response?.status === 401) {
										localStorage.removeItem('authToken');
										navigate('/');
									} else {
										alert('Failed to export PDF. Please try again.');
									}
								}
							}}
							className="bg-blue-500 text-white px-3 py-1 rounded text-sm hover:bg-blue-600"
						>
							PDF
						</button>
					</div>
					<div>
						<button
							onClick={() =>
								alert(
									`Current Total: ₹${currentTotal.toLocaleString('en-IN', {
										minimumFractionDigits: 2,
									})}`
								)
							}
                    className="bg-yellow-200 text-gray-800 px-4 py-2 rounded text-sm shadow hover:bg-yellow-300"
						>
                    Total: ₹{currentTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
						</button>
					</div>
				</div>

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
						title="Go to first page"
					>
						First
					</button>
					<button
						disabled={page === 0}
						onClick={() => setPage((p) => p - 1)}
						className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1"
						title="Go to previous page"
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
								title={`Go to page ${p + 1}`}
							>
								{p + 1}
							</button>
						)
					)}
					<button
						disabled={page === totalPages - 1}
						onClick={() => setPage((p) => p + 1)}
						className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1"
						title="Go to next page"
					>
						Next
					</button>
					<button
						disabled={page === totalPages - 1}
						onClick={() => setPage(totalPages - 1)}
						className="px-2 py-1 rounded bg-gray-200 disabled:opacity-50 ml-1"
						title="Go to last page"
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
                {/* Section 1: Fetch toolbar */}
                <FetchToolbar
                    availableServices={availableServices}
                    selectedServices={selectedServices}
                    setSelectedServices={setSelectedServices}
                    refreshing={refreshing}
                    triggerDataExtraction={triggerDataExtraction}
                    currentTotal={currentTotal}
                />

                {/* Section 2: Filters in two rows */}
                <div className="w-full bg-white border border-blue-200 rounded-md p-2 shadow-sm space-y-2">
                    {/* First row: Date, Type, and Search */}
                    <div className={`grid grid-cols-2 gap-2 items-center ${filterMode === 'range' ? 'md:grid-cols-5' : 'md:grid-cols-4'}`}>
                    <select
                        value={filterMode}
                        onChange={(e) => {
                            const val = e.target.value;
                            setFilterMode(val);
                            if (val === 'month') {
                                updateUrlParams({ date: '', startDate: '', endDate: '' });
                            } else if (val === 'date') {
                                updateUrlParams({ month: '', startDate: '', endDate: '' });
                            } else if (val === 'range') {
                                updateUrlParams({ month: '', date: '' });
                            }
                        }}
                        className="border px-2 py-1 rounded text-sm w-full"
                        title="Choose Month, Date, or Range filter"
                    >
                        <option value="month">By Month</option>
                        <option value="date">By Date</option>
                        <option value="range">By Range</option>
                    </select>
                    {filterMode === 'month' ? (
                        <input
                            type="month"
                            value={filterMonth}
                            onChange={(e) => { setFilterMonth(e.target.value); updateUrlParams({ month: e.target.value, date: '', startDate: '', endDate: '' }); }}
                            className="border px-2 py-1 rounded text-sm w-full"
                        />
                    ) : filterMode === 'date' ? (
                        <input
                            type="date"
                            value={filterDate}
                            onChange={(e) => { setFilterDate(e.target.value); updateUrlParams({ date: e.target.value, month: '', startDate: '', endDate: '' }); }}
                            className="border px-2 py-1 rounded text-sm w-full"
                        />
                    ) : (
                        <>
                            <input
                                type="date"
                                value={filterStartDate}
                                onChange={(e) => { setFilterStartDate(e.target.value); updateUrlParams({ startDate: e.target.value, month: '', date: '' }); }}
                                placeholder="Start Date"
                                className="border px-2 py-1 rounded text-sm w-full"
                                title="Start date for range filter"
                            />
                            <input
                                type="date"
                                value={filterEndDate}
                                onChange={(e) => { setFilterEndDate(e.target.value); updateUrlParams({ endDate: e.target.value, month: '', date: '' }); }}
                                placeholder="End Date"
                                className="border px-2 py-1 rounded text-sm w-full"
                                title="End date for range filter"
                            />
                        </>
                    )}
                    <select
                        value={filterType}
                        onChange={(e) => {setFilterType(e.target.value); updateUrlParams({ type: e.target.value });}}
                        className="border px-2 py-1 rounded text-sm w-full"
                    >
                        <option value="ALL">All Types</option>
                        <option value="CREDIT">Credit</option>
                        <option value="DEBIT">Debit</option>
                    </select>
                    <div className="relative flex-1">
                        <input
                            value={search}
                            onChange={(e) => {setSearch(e.target.value); updateUrlParams({ search: e.target.value }); setPage(0);}}
                            placeholder="Search"
                            className="border px-2 py-1 pr-6 rounded text-sm w-full"
                        />
                        {search && (
                            <button
                                onClick={() => { setSearch(''); updateUrlParams({ search: '' }); setPage(0); }}
                                className="absolute right-1 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-700 text-lg leading-none"
                                tabIndex={-1}
                            >
                                ×
                            </button>
                        )}
                    </div>
                    </div>
                    {/* Second row: Account, Category, and Label */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-2 items-center">
                        <SearchSelect
                            options={[{ id: '', name: 'All Accounts' }, ...accounts.map(a => ({ id: a.id, name: a.name }))]}
                            value={filterAccount}
                            onChange={(val) => { setFilterAccount(val); updateUrlParams({ accountId: val }); }}
                            placeholder="Account"
                        />
                        <SearchSelect
                            options={[{ id: '', name: 'All Categories' }, ...flattened.map(c => ({ id: c.id, name: c.name }))]}
                            value={filterCategory}
                            onChange={(val) => { setFilterCategory(val); updateUrlParams({ categoryId: val }); }}
                            placeholder="Category"
                        />
                        <SearchSelect
                            options={[{ id: '', name: 'All Labels' }, ...labels.map(l => ({ id: l.id, name: l.name }))]}
                            value={filterLabel}
                            onChange={(val) => { setFilterLabel(val); updateUrlParams({ labelId: val }); }}
                            placeholder="Label"
                        />
						{/* Has Attachments filter */}
						<div className="flex items-center">
							<input
								type="checkbox"
								checked={filterHasAttachments}
								onChange={(e) => {
									setFilterHasAttachments(e.target.checked);
									setPage(0);
									updateUrlParams({ hasAttachments: e.target.checked ? "true" : "" });
								}}
								className="mr-2"
								id="has-attachments"
							/>
							<label htmlFor="has-attachments" className="text-sm text-gray-700 cursor-pointer whitespace-nowrap">
								📎 Has attachments
							</label>
						</div>
                    </div>
                </div>

                {/* Combined Section: Actions (Add + Export + Sort + Reset/Clear) */}
				<div className="w-full bg-white border border-blue-200 rounded-md p-2 shadow-sm flex flex-wrap items-center gap-2">
                    <button
                        onClick={() =>
                            setEditTx({
                                id: null,
                                description: '',
                                explanation: '',
                                amount: 0,
                                date: dayjs().format('YYYY-MM-DDTHH:mm'),
                                type: 'DEBIT',
                                labels: [],
								currency: 'INR',
                                accountId: '',
                                categoryId: '',
                            })
                        }
                        className="bg-green-500 text-white px-2 py-1 text-xs sm:px-2 sm:py-1 sm:text-sm rounded shadow hover:bg-green-600 w-full sm:w-auto"
                        title="Add a new transaction manually"
                    >
                        Add
                    </button>
                    <button
                        onClick={async () => {
                            const params = new URLSearchParams({
                                ...(filterMode === 'month' && filterMonth ? { month: filterMonth } : {}),
                                ...(filterMode === 'date' && filterDate ? { date: filterDate } : {}),
                                ...(filterMode === 'range' && filterStartDate && filterEndDate ? { startDate: filterStartDate, endDate: filterEndDate } : {}),
                                accountId: filterAccount,
                                type: filterType,
                                search: debouncedSearch,
                            });
                            if (filterCategory) params.append('categoryId', filterCategory);
                            if (filterLabel) params.append('labelId', filterLabel);
                            const res = await api.get(`/transactions/export?${params}`).catch((err) => {
                                if (err.response?.status === 401) {
                                    localStorage.removeItem('authToken');
                                    navigate('/');
                                } else {
                                    console.error('Failed to fetch user profile:', err);
                                }
                            });
                            const flattenedRows = res.data.map((tx) => ({
                                Date: tx.date,
                                Description: tx.description,
                                Explanation: tx.explanation || '',
                                Amount: tx.amount,
                                Type: tx.type,
                                Account: tx.account?.name || '',
                                Category: tx.category?.name || '',
                            }));
                            const csv = Papa.unparse(flattenedRows);
                            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
                            saveAs(blob, 'transactions.csv');
                        }}
                        className="bg-blue-500 text-white px-2 py-1 text-xs sm:px-2 sm:py-1 sm:text-sm rounded hover:bg-blue-600"
                        title="Export filtered transactions to CSV file (comma-separated values, opens in Excel/Sheets)"
                    >
                        CSV
                    </button>
                    <button
                        onClick={async () => {
                            const params = new URLSearchParams({
                                ...(filterMode === 'month' && filterMonth ? { month: filterMonth } : {}),
                                ...(filterMode === 'date' && filterDate ? { date: filterDate } : {}),
                                ...(filterMode === 'range' && filterStartDate && filterEndDate ? { startDate: filterStartDate, endDate: filterEndDate } : {}),
                                accountId: filterAccount,
                                type: filterType,
                                search: debouncedSearch,
                            });
                            if (filterCategory) params.append('categoryId', filterCategory);
                            if (filterLabel) params.append('labelId', filterLabel);
                            const res = await api.get(`/transactions/export?${params}`).catch((err) => {
                                if (err.response?.status === 401) {
                                    localStorage.removeItem('authToken');
                                    navigate('/');
                                } else {
                                    console.error('Failed to fetch user profile:', err);
                                }
                            });
                            const flattenedRows = res.data.map((tx) => ({
                                Date: tx.date,
                                Description: tx.description,
                                Explanation: tx.explanation || '',
                                Amount: tx.amount,
                                Type: tx.type,
                                Account: tx.account?.name || '',
                                Category: tx.category?.name || '',
                            }));
                            const worksheet = XLSX.utils.json_to_sheet(flattenedRows);
                            const workbook = XLSX.utils.book_new();
                            XLSX.utils.book_append_sheet(workbook, worksheet, 'Transactions');
                            const excelBuffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
                            const blob = new Blob([excelBuffer], { type: 'application/octet-stream' });
                            saveAs(blob, 'transactions.xlsx');
                        }}
                        className="bg-blue-500 text-white px-2 py-1 text-xs sm:px-2 sm:py-1 sm:text-sm rounded hover:bg-blue-600"
                        title="Export filtered transactions to Excel file (.xlsx format, preserves formatting)"
                    >
                        XLSX
                    </button>
                    <button
                        onClick={async () => {
                            const params = new URLSearchParams({
                                ...(filterMode === 'month' && filterMonth ? { month: filterMonth } : {}),
                                ...(filterMode === 'date' && filterDate ? { date: filterDate } : {}),
                                ...(filterMode === 'range' && filterStartDate && filterEndDate ? { startDate: filterStartDate, endDate: filterEndDate } : {}),
                                accountId: filterAccount,
                                type: filterType,
                                search: debouncedSearch,
                            });
                            if (filterCategory) params.append('categoryId', filterCategory);
                            if (filterLabel) params.append('labelId', filterLabel);
                            const res = await api.get(`/transactions/export?${params}`).catch((err) => {
                                if (err.response?.status === 401) {
                                    localStorage.removeItem('authToken');
                                    navigate('/');
                                } else {
                                    console.error('Failed to fetch user profile:', err);
                                }
                            });
                            const { jsPDF } = await import('jspdf');
                            const flattenedRows = res.data.map((tx) => ({
                                Date: dayjs(tx.date).isValid() ? dayjs(tx.date).format('DD/MMM') : '',
                                Description: tx.description,
                                Amount: tx.amount,
                                Type: tx.type,
                                Account: tx.account?.name || '',
                            }));
                            const autoTable = (await import('jspdf-autotable')).default;
                            const doc = new jsPDF();
                            const headers = ['Date', 'Description', 'Amount', 'Type', 'Account'];
                            const rows = flattenedRows.map((row) => headers.map((key) => row[key]));

                            // Compute totals (CREDIT adds, DEBIT subtracts) for the summary footer
                            const totalCredit = res.data.reduce(
                                (sum, tx) => sum + (tx.type === 'CREDIT' && typeof tx.amount === 'number' ? tx.amount : 0),
                                0
                            );
                            const totalDebit = res.data.reduce(
                                (sum, tx) => sum + (tx.type === 'DEBIT' && typeof tx.amount === 'number' ? tx.amount : 0),
                                0
                            );
                            const netTotal = totalCredit - totalDebit;
                            const fmt = (n) => n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

                            autoTable(doc, {
                                head: [headers],
                                body: rows,
                                foot: [
                                    ['', `Total Credits (${res.data.filter((t) => t.type === 'CREDIT').length})`, fmt(totalCredit), 'CREDIT', ''],
                                    ['', `Total Debits (${res.data.filter((t) => t.type === 'DEBIT').length})`, fmt(totalDebit), 'DEBIT', ''],
                                    ['', `Net Total (${res.data.length} txns)`, fmt(netTotal), '', ''],
                                ],
                                showFoot: 'lastPage',
                                styles: { fontSize: 8, cellWidth: 'wrap' },
                                footStyles: { fillColor: [230, 230, 230], textColor: 20, fontStyle: 'bold', halign: 'right' },
                                columnStyles: { 0: { cellWidth: 20 }, 1: { cellWidth: 70 }, 2: { cellWidth: 25, halign: 'right' }, 3: { cellWidth: 15 }, 4: { cellWidth: 30 } },
                                tableWidth: 'wrap',
                                margin: { top: 20 },
                            });
                            doc.save('transactions.pdf');
                        }}
                        className="bg-blue-500 text-white px-2 py-1 text-xs sm:px-2 sm:py-1 sm:text-sm rounded hover:bg-blue-600"
                        title="Export filtered transactions to PDF file (printable/shareable document format)"
                    >
                        PDF
                    </button>

                    {/* Vertical separator */}
                    <div className="h-6 w-px bg-gray-300 mx-1"></div>

                    {/* Sort buttons */}
                    <span className="text-xs sm:text-sm font-semibold text-gray-700">Sort:</span>
                    <button
                        onClick={() => {
                            setPage(0);
                            if (sortBy === "date") {
                                if (sortDir === "asc") {
                                    // Second click: change to descending
                                    setSortDir("desc");
                                    updateUrlParams({ sortBy: "date", sortDir: "desc" });
                                } else {
                                    // Third click: remove sort
                                    setSortBy(null);
                                    setSortDir(null);
                                    updateUrlParams({ sortBy: null, sortDir: null });
                                }
                            } else {
                                // First click: set to ascending
                                setSortBy("date");
                                setSortDir("asc");
                                updateUrlParams({ sortBy: "date", sortDir: "asc" });
                            }
                        }}
                        className={`px-2 py-1 text-xs sm:text-sm rounded transition-colors ${
                            sortBy === "date"
                                ? "bg-blue-500 text-white"
                                : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                        }`}
                        title={
                            sortBy === "date"
                                ? sortDir === "asc"
                                    ? "Sort by date (descending) - Click to change"
                                    : "Clear date sort - Click to remove"
                                : "Sort by date (ascending)"
                        }
                    >
                        Date {sortBy === "date" ? (sortDir === "asc" ? "↑" : "↓") : ""}
                    </button>
                    <button
                        onClick={() => {
                            setPage(0);
                            if (sortBy === "amount") {
                                if (sortDir === "asc") {
                                    // Second click: change to descending
                                    setSortDir("desc");
                                    updateUrlParams({ sortBy: "amount", sortDir: "desc" });
                                } else {
                                    // Third click: remove sort
                                    setSortBy(null);
                                    setSortDir(null);
                                    updateUrlParams({ sortBy: null, sortDir: null });
                                }
                            } else {
                                // First click: set to ascending
                                setSortBy("amount");
                                setSortDir("asc");
                                updateUrlParams({ sortBy: "amount", sortDir: "asc" });
                            }
                        }}
                        className={`px-2 py-1 text-xs sm:text-sm rounded transition-colors ${
                            sortBy === "amount"
                                ? "bg-blue-500 text-white"
                                : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                        }`}
                        title={
                            sortBy === "amount"
                                ? sortDir === "asc"
                                    ? "Sort by amount (descending) - Click to change"
                                    : "Clear amount sort - Click to remove"
                                : "Sort by amount (ascending)"
                        }
                    >
                        Amount {sortBy === "amount" ? (sortDir === "asc" ? "↑" : "↓") : ""}
                    </button>

                    {/* Vertical separator */}
                    <div className="h-6 w-px bg-gray-300 mx-1"></div>

                    {/* Reset/Clear All buttons */}
                    <button
                        onClick={() => {
                            setFilterAccount('');
                            setFilterCategory('');
                            setFilterLabel('');
                            setFilterType('ALL');
                            setSearch('');
                            setFilterHasAttachments(false);
                            setPage(0);
                            updateUrlParams({ 
                                accountId: '', 
                                categoryId: '', 
                                labelId: '',
                                type: 'ALL', 
                                search: '',
                                hasAttachments: ''
                            });
                        }}
                        className="bg-orange-500 text-white px-2 py-1 rounded text-xs hover:bg-orange-600 whitespace-nowrap"
                        title="Reset filters (keeps month/date selection, clears account, category, label, type, search, and attachments)"
                    >
                        Reset
                    </button>
                    <button
                        onClick={() => {
                            setFilterMonth('');
                            setFilterDate('');
                            setFilterStartDate('');
                            setFilterEndDate('');
                            setFilterMode('month');
                            setFilterAccount('');
                            setFilterCategory('');
                            setFilterLabel('');
                            setFilterType('ALL');
                            setSearch('');
                            setFilterHasAttachments(false);
                            setSortBy(null);
                            setSortDir(null);
                            setPage(0);
                            setPageSize(10);
                            // page / size are kept in sync by
                            // the param-sync effect; we only need to clear the
                            // explicit filter params here.
                            updateUrlParams({ 
                                month: '', 
                                date: '', 
                                startDate: '', 
                                endDate: '', 
                                accountId: '', 
                                categoryId: '', 
                                labelId: '',
                                type: 'ALL', 
                                search: '',
                                hasAttachments: '',
                                sortBy: null,
                                sortDir: null
                            });
                        }}
                        className="bg-red-500 text-white px-2 py-1 rounded text-xs hover:bg-red-600 whitespace-nowrap"
                        title="Clear all filters including date selections (resets everything to default)"
                    >
                        Clear All
                    </button>

					<div className="ml-auto text-right text-xs sm:text-sm">
						<div className="inline-flex items-center gap-2 bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200 rounded-lg px-3 py-1.5 shadow-sm">
							<span className="font-semibold text-gray-700">Current Page:</span>
							<div className="flex items-center gap-3 divide-x divide-gray-300">
								<span className="font-medium text-blue-700">
									₹{currentPageSum.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
								</span>
								<span className="pl-3 text-gray-600">
									{displayedRows} {displayedRows === 1 ? 'row' : 'rows'}
								</span>
							</div>
						</div>
					</div>
                </div>

			{renderPagination()}

			<BulkActionBar
				selectedCount={selectedIds.size}
				selectedIds={selectedIds}
				flattenedCategories={flattened}
				onClear={clearSelection}
				onApplyCategory={applyBulkCategory}
				onApplyLabels={applyBulkLabels}
			/>

			{/* Column Headers (desktop only - row layout collapses on mobile) */}
			<div className="hidden sm:grid w-full grid-cols-[24px_minmax(0,3fr)_minmax(180px,2fr)_minmax(0,2fr)_minmax(0,1fr)_minmax(0,2fr)] gap-2 px-3 py-2 bg-gray-100 border border-gray-300 rounded-t text-xs font-semibold text-gray-700 uppercase tracking-wide">
				<div className="flex items-center justify-center">
					{selectablePageIds.length > 0 && (
						<input
							type="checkbox"
							ref={(el) => { if (el) el.indeterminate = someOnPageSelected; }}
							checked={allOnPageSelected}
							onChange={togglePageSelection}
							className="cursor-pointer"
							title={allOnPageSelected ? "Unselect all on this page" : "Select all on this page"}
						/>
					)}
				</div>
				<div>Description</div>
				<div className="text-right">Amount / Account</div>
				<div>Category</div>
				<div>Date</div>
				<div className="text-right">Actions</div>
			</div>

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
						try {
							const childrenPayload = enrichedParent.children.map((c) => ({
								id: null,
								date: enrichedParent.date,
								amount: parseFloat(c.amount),
								description: c.description,
								shortDescription: null,
								explanation: null,
								shortExplanation: null,
								type: enrichedParent.type,
								account: { id: enrichedParent.accountId },
								category: c.categoryId ? { id: c.categoryId } : null,
								parentId: enrichedParent.parentId,
								children: [],
								gptAmount: null,
								gptDescription: null,
								gptExplanation: null,
								gptType: null,
								currency: enrichedParent.currency || 'INR',
								gptAccount: null,
							}));
							await api.post("/transactions/split", childrenPayload);
							console.log("Transaction split successfully");
							setSplitTx(null);
							await fetchData();
						} catch (err) {
							console.error("Failed to split transaction:", err);
							// Error is already handled by api.js interceptor (shows modal)
						} finally {
							NProgress.done();
							setLoading(false);
						}
					}}
					categories={categories}
				/>
			)}

			{transferTx && (
				<TransferForm
					transaction={transferTx}
					setTransaction={setTransferTx}
					onCancel={() => setTransferTx(null)}
					loading={loading}
					onSubmit={async () => {
						setLoading(true);
						NProgress.start();
						try {
							await api.post("/transactions/transfer", {
								sourceTransactionId: transferTx.id,
								destinationAccountId: transferTx.destinationAccountId,
								explanation: transferTx.explanation,
							});
							console.log("Transaction transfer successful");
							setTransferTx(null);
							await fetchData();
						} catch (err) {
							console.error("Failed to transfer transaction:", err);
							// Error is already handled by api.js interceptor (shows modal)
						} finally {
							NProgress.done();
							setLoading(false);
						}
					}}
					accounts={accounts}
				/>
			)}

			{attachmentsTx && (
				<TransactionAttachments
					transaction={attachmentsTx}
					onClose={() => setAttachmentsTx(null)}
					onCountChange={(txId, count) => {
						// Update the badge in place without a full refetch so the
						// list reflects uploads/deletes immediately when the modal
						// closes (or while it's open).
						setTransactions((prev) =>
							prev.map((t) => (t.id === txId ? { ...t, attachmentCount: count } : t))
						);
					}}
				/>
			)}

			{labelEditTx && (
				<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
					<div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
						<div className="flex justify-between items-center mb-4">
							<h2 className="text-lg font-semibold text-gray-800">
								Edit Labels
							</h2>
							<button
								onClick={() => setLabelEditTx(null)}
								className="text-gray-500 hover:text-gray-700 text-2xl leading-none"
								title="Close"
							>
								×
							</button>
						</div>

						<div className="mb-4">
							<div className="text-sm text-gray-600 mb-2">
								<strong>Transaction:</strong> {labelEditTx.description}
							</div>
							<div className="text-xs text-gray-500 mb-3">
							Amount: {getCurrencySymbol(labelEditTx.currency)}
							{(typeof labelEditTx.amount === "number" ? labelEditTx.amount : 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
								{" • "}
								{dayjs(labelEditTx.date).format("DD MMM YYYY")}
							</div>
						</div>

						<div className="mb-4">
							<label className="block text-sm font-medium text-gray-700 mb-2">
								Labels
							</label>
							<LabelInput
								value={labelEditTx.labels || []}
								onChange={(newLabels) => {
									setLabelEditTx({
										...labelEditTx,
										labels: newLabels
									});
								}}
								placeholder="Add or search labels..."
							/>
							<p className="text-xs text-gray-500 mt-2">
								Type to search existing labels or press Enter to create new ones
							</p>
						</div>

						<div className="flex gap-2 justify-end">
							<button
								onClick={() => setLabelEditTx(null)}
								className="px-4 py-2 text-sm border border-gray-300 rounded hover:bg-gray-50 transition-colors"
							>
								Cancel
							</button>
							<button
								onClick={() => {
									updateLabels(labelEditTx, labelEditTx.labels);
								}}
								className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
							>
								Save Labels
							</button>
						</div>
					</div>
				</div>
			)}

			{modalContent && (
				<div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50 p-2 sm:p-4 overflow-y-auto">
					<div className={`bg-white rounded-lg shadow-lg w-full p-4 my-2 sm:my-4 ${
						modalContent.title === "Transaction Details" 
							? "max-w-[95vw] sm:max-w-lg md:max-w-2xl"
							: modalContent.title === "Transfer details"
							? "max-w-[95vw] sm:max-w-2xl md:max-w-4xl"
							: "max-w-[95vw] sm:max-w-xl md:max-w-3xl lg:max-w-4xl"
					}`}>
						<h2 className="text-base sm:text-lg font-semibold mb-3">{modalContent.title}</h2>
						<div className="text-xs sm:text-sm text-gray-700 mb-3 max-h-[70vh] sm:max-h-[75vh] overflow-y-auto overflow-x-hidden">
							{modalContent.content}
						</div>
						<div className="flex justify-end">
						<button
							onClick={() => {
								setModalContent(null);
							}}
								className="text-blue-600 hover:underline text-xs sm:text-sm px-3 sm:px-4 py-1.5 sm:py-2 bg-blue-50 rounded hover:bg-blue-100 transition-colors"
						>
							Close
						</button>
						</div>
					</div>
				</div>
			)}

			{deleteConfirmation && (() => {
				// Find the transaction being deleted
				const txToDelete = transactions.find(tx => tx.id === deleteConfirmation);
				
				// Check if this transaction has a linked transfer (points to another)
				const hasLinkedTransfer = txToDelete?.linkedTransferId;
				
				// Check if another transaction points to this one (reverse link)
				const isReferencedByTransfer = transactions.some(tx => 
					tx.linkedTransferId === deleteConfirmation && tx.id !== deleteConfirmation
				);
				
				// Determine if this is part of a transfer (either direction)
				const isPartOfTransfer = hasLinkedTransfer || isReferencedByTransfer;
				
				return (
				<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
					<div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
						<div className="p-6">
							<h2 className="text-xl font-semibold text-gray-900 mb-2">Delete Transaction</h2>
								{isPartOfTransfer ? (
									<div className="mb-6">
										<p className="text-gray-600 mb-3">
											This transaction is part of a transfer. Deleting it will delete <strong className="text-red-600">both transactions</strong> (debit and credit sides of the transfer).
										</p>
										<p className="text-sm text-gray-500 italic">
											This action cannot be undone.
										</p>
									</div>
								) : (
							<p className="text-gray-600 mb-6">
								Are you sure you want to delete this transaction? This action cannot be undone.
							</p>
								)}
							<div className="flex gap-3 justify-end">
								<button
									onClick={() => setDeleteConfirmation(null)}
									className="px-4 py-2 text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-md font-medium transition-colors"
								>
									Cancel
								</button>
								<button
									onClick={confirmDelete}
									className="px-4 py-2 text-white bg-red-600 hover:bg-red-700 rounded-md font-medium transition-colors"
								>
									Delete
								</button>
							</div>
						</div>
					</div>
				</div>
				);
			})()}

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
