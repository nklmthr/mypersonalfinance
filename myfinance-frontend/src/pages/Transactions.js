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
import TransactionSplit from "./transactions/components/TransactionSplit";
import TransactionComparisonModal from "./transactions/components/TransactionComparisonModal";
import TransactionDetailsModal from "./transactions/components/TransactionDetailsModal";
import { buildTree, flattenCategories } from "./transactions/utils/utils";


// ---- main ----
export default function Transactions() {
	const navigate = useNavigate();
	const { showModal } = useErrorModal();
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
	const [deleteConfirmation, setDeleteConfirmation] = useState(null);
	const [searchParams, setSearchParams] = useSearchParams();
	const [filterMonth, setFilterMonth] = useState(
		searchParams.get("month") || dayjs().format("YYYY-MM")
	);
	const [filterDate, setFilterDate] = useState(
		searchParams.get("date") || ""
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
const [availableServices, setAvailableServices] = useState([]);
const [selectedServices, setSelectedServices] = useState([]);

// Unified date filter: choose Month or Date (default Month unless URL has date)
const [filterMode, setFilterMode] = useState(searchParams.get("date") ? "date" : "month");

// (moved to top-level above)

	// ESC key handler for modals
	useEffect(() => {
		const handleEscKey = (e) => {
			if (e.key === "Escape") {
				if (modalContent) {
					setModalContent(null);
				} else if (deleteConfirmation) {
					setDeleteConfirmation(null);
				}
			}
		};
		
		if (modalContent || deleteConfirmation) {
			document.addEventListener("keydown", handleEscKey);
			return () => document.removeEventListener("keydown", handleEscKey);
		}
	}, [modalContent, deleteConfirmation]);

	NProgress.configure({ showSpinner: false });

// Load available data extraction services and select all by default
useEffect(() => {
    (async () => {
        try {
            const res = await api.get('/data-extraction/configurations');
            const list = res.data?.configurations || [];
            setAvailableServices(list);
            setSelectedServices(list);
        } catch (err) {
            console.error('Failed to load extraction services:', err);
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

            if (filterMode === 'month' && filterMonth) {
                params.set('month', filterMonth);
            }
            if (filterMode === 'date' && filterDate) {
                params.set('date', filterDate);
            }

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
    }, [page, pageSize, filterMode, filterMonth, filterDate, filterAccount, filterType, filterCategory, debouncedSearch]);

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

	const deleteTx = async (id) => {
		setDeleteConfirmation(id);
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

	const currentPageSum = useMemo(() => {
		return (transactions || []).reduce((sum, tx) => {
			const amount = typeof tx.amount === "number" ? tx.amount : 0;
			// CREDIT adds to sum, DEBIT subtracts from sum
			const multiplier = tx.type === "CREDIT" ? 1 : -1;
			let total = sum + (amount * multiplier);
			
			// Include child amounts if expanded
			if (expandedParents[tx.id] && Array.isArray(tx.children)) {
				tx.children.forEach((child) => {
					if (typeof child === "object" && child !== null) {
						const childAmount = typeof child.amount === "number" ? child.amount : 0;
						const childMultiplier = child.type === "CREDIT" ? 1 : -1;
						total += (childAmount * childMultiplier);
					}
				});
			}
			
			return total;
		}, 0);
	}, [transactions, expandedParents]);

	// Helper function to create professional transaction comparison modal (with GPT data)
	const createComparisonModal = (tx) => {
		return <TransactionComparisonModal tx={tx} />;
	};

	// Helper function to create simple transaction details modal (without GPT data)
	const createDetailsModal = (tx) => {
		return <TransactionDetailsModal tx={tx} />;
	};

	const handleTransferLinkClick = async (linkedTransferId, e) => {
		e.stopPropagation(); // Prevent event bubbling
		
		// First, try to find the linked transaction in the current page's transactions
		let linkedTx = transactions.find(tx => tx.id === linkedTransferId);
		
		// If not found on current page, fetch it from API
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
		
		// Open the appropriate modal with the linked transaction
		if (linkedTx) {
			const hasGpt = typeof linkedTx.gptDescription === 'string' && linkedTx.gptDescription.trim().length > 0;
			setModalContent({
				title: "Linked Transfer Transaction",
				content: hasGpt ? createComparisonModal(linkedTx) : createDetailsModal(linkedTx),
			});
		}
	};

	const renderRow = (tx, isChild = false, index = 0) => {
		const baseColor = isChild
			? "bg-gray-50 border-dashed"
			: index % 2 === 0
				? "bg-blue-50"
				: "bg-blue-100";
		const hasGpt = typeof tx.gptDescription === 'string' && tx.gptDescription.trim().length > 0;
		const isDescTrimmed = Boolean((tx.gptDescription || tx.description) && (tx.gptDescription || tx.description) !== tx.shortDescription);
		return (
			<div
				key={tx.id}
				className={`grid grid-cols-1 sm:grid-cols-[24px_3fr_2fr_2fr_1fr_2fr] gap-2 py-2 px-3 rounded border items-center text-sm ${baseColor} border-gray-200`}
			>
				<div className="text-xs sm:col-span-1 hidden sm:block">
					{!isChild && tx.children?.length > 0 && (
						<button
							onClick={() => toggleExpand(tx.id)}
							className="text-gray-600 hover:text-black"
						>
							{expandedParents[tx.id] ? "▼" : "▶"}
						</button>
					)}
				</div>

				<div className={`flex flex-col ${isChild ? 'pl-3' : ''}`}>
					<div className="flex items-center gap-1 truncate font-medium text-gray-800">
						{!isChild && tx.children?.length > 0 && (
							<button
								title="Toggle children"
								className="text-gray-600 hover:text-black sm:hidden"
								onClick={() => toggleExpand(tx.id)}
							>
								{expandedParents[tx.id] ? "▼" : "▶"}
							</button>
						)}
						<span 
							className="truncate cursor-pointer hover:text-blue-600" 
							title={hasGpt ? "✨ Click to view AI analysis comparison" : "Click to view transaction details"}
							onClick={() =>
								setModalContent({
									title: hasGpt ? "Transaction Analysis & Comparison" : "Transaction Details",
									content: hasGpt ? createComparisonModal(tx) : createDetailsModal(tx),
								})
							}
						>
							{tx.shortDescription}
						</span>
						{hasGpt && (
							<button
								title="✨ AI analysis available - Click description to compare"
								className="text-blue-700 px-1 ml-1 cursor-pointer"
								onClick={() =>
									setModalContent({
										title: "Transaction Analysis & Comparison",
										content: createComparisonModal(tx),
									})
								}
							>
								✨
							</button>
						)}
						{isDescTrimmed && !hasGpt && (
							<button
								title="View full description and details"
								className="text-gray-700 px-1 cursor-pointer"
								onClick={() =>
									setModalContent({
										title: "Transaction Details",
										content: createDetailsModal(tx),
									})
								}
							>
								🔍
							</button>
						)}
					</div>
					<div 
						className="text-xs text-gray-500 break-words cursor-pointer hover:text-blue-600" 
						title={hasGpt ? "✨ Click to view AI analysis comparison" : "Click to view transaction details"}
						onClick={() =>
							setModalContent({
								title: hasGpt ? "Transaction Analysis & Comparison" : "Transaction Details",
								content: hasGpt ? createComparisonModal(tx) : createDetailsModal(tx),
							})
						}
					>
						{tx.shortExplanation}
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
					{(() => {
						// Check if this transaction has a linkedTransferId (points to another)
						const linkedId = tx.linkedTransferId;
						
						// Also check if another transaction on this page references this one (reverse link)
						// This handles the case where Transaction B has linkedTransferId pointing to Transaction A
						const reverseLinkedTx = transactions.find(otherTx => 
							otherTx.id !== tx.id && otherTx.linkedTransferId === tx.id
						);
						
						// Show link if either direction exists
						const hasLink = linkedId || reverseLinkedTx;
						const linkTargetId = linkedId || (reverseLinkedTx ? reverseLinkedTx.id : null);
						
						if (hasLink && linkTargetId) {
							return (
								<button
									onClick={(e) => {
										e.stopPropagation();
										handleTransferLinkClick(linkTargetId, e);
									}}
									className="ml-2 text-xs bg-purple-100 text-purple-700 rounded px-2 py-0.5 inline-flex items-center gap-1 hover:bg-purple-200 cursor-pointer transition-colors"
									title={`Click to view linked transfer transaction: ${linkTargetId}`}
									type="button"
								>
									🔗 Transfer
								</button>
							);
						}
						return null;
					})()}
					{tx.gptAmount && tx.gptAmount !== tx.amount && (
						<div className="text-xs text-blue-600 mt-1">
							🤖 GPT: {tx.currency || "₹"}{(typeof tx.gptAmount === "number" ? tx.gptAmount : parseFloat(tx.gptAmount) || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
							{tx.gptType && tx.gptType !== tx.type && ` (${tx.gptType})`}
						</div>
					)}
					<br />
					<span className="text-xs text-gray-500">{tx.account?.name}</span>
				</div>

				<div className="order-3 sm:order-none">
					<SearchSelect
						options={flattened.map(c => ({ id: c.id, name: c.name }))}
						value={tx.category?.id || ""}
						onChange={(val) => {
							saveTx(
								{
									...tx,
									categoryId: val,
									accountId: tx.account?.id,
									parentId: tx.parent?.id,
								},
								"put",
								`/transactions/${tx.id}`
							);
						}}
						placeholder="Category"
					/>
				</div>

				<div className="hidden sm:block text-xs sm:text-sm text-gray-500 self-start sm:self-center order-4 sm:order-none">
					{dayjs(tx.date).format("ddd, DD MMM YY HH:mm")}
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
							})
						}
						title="Edit transaction details (amount, description, category, etc.)"
					>
						Update
					</button>
				</div>

				{/* Mobile footer: date + actions */}
				<div className="sm:hidden flex items-center justify-between mt-2 text-xs">
					<div className="text-gray-500">{dayjs(tx.date).format("ddd, DD MMM YY HH:mm")}</div>
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
                    <div className="grid grid-cols-2 md:grid-cols-5 gap-2 items-center">
                    <select
                        value={filterMode}
                        onChange={(e) => {
                            const val = e.target.value;
                            setFilterMode(val);
                            if (val === 'month') {
                                updateUrlParams({ date: '' });
                            } else {
                                updateUrlParams({ month: '' });
                            }
                        }}
                        className="border px-2 py-1 rounded text-sm w-full"
                        title="Choose Month or Date filter"
                    >
                        <option value="month">By Month</option>
                        <option value="date">By Date</option>
                    </select>
                    {filterMode === 'month' ? (
                        <input
                            type="month"
                            value={filterMonth}
                            onChange={(e) => { setFilterMonth(e.target.value); updateUrlParams({ month: e.target.value, date: '' }); }}
                            className="border px-2 py-1 rounded text-sm w-full"
                        />
                    ) : (
                        <input
                            type="date"
                            value={filterDate}
                            onChange={(e) => { setFilterDate(e.target.value); updateUrlParams({ date: e.target.value, month: '' }); }}
                            className="border px-2 py-1 rounded text-sm w-full"
                        />
                    )}
                        <SearchSelect
                            options={[{ id: '', name: 'All Accounts' }, ...accounts.map(a => ({ id: a.id, name: a.name }))]}
                            value={filterAccount}
                            onChange={(val) => { setFilterAccount(val); updateUrlParams({ accountId: val }); }}
                            placeholder="Account"
                        />

                    <select
                        value={filterType}
                        onChange={(e) => {setFilterType(e.target.value); updateUrlParams({ type: e.target.value });}}
                        className="border px-2 py-1 rounded text-sm w-full"
                    >
                        <option value="ALL">All Types</option>
                        <option value="CREDIT">Credit</option>
                        <option value="DEBIT">Debit</option>
                    </select>

                    <input
                        value={search}
                        onChange={(e) => {setSearch(e.target.value); updateUrlParams({ search: e.target.value }); setPage(0);}}
                        placeholder="Search"
                        className="border px-2 py-1 rounded text-sm w-full"
                    />
                    </div>
                    <div className="flex gap-2 items-center">
                        <SearchSelect
                            options={[{ id: '', name: 'All Categories' }, ...flattened.map(c => ({ id: c.id, name: c.name }))]}
                            value={filterCategory}
                            onChange={(val) => { setFilterCategory(val); updateUrlParams({ categoryId: val }); }}
                            placeholder="Category"
                        />
                        <div className="flex gap-1 ml-auto">
                            <button
                                onClick={() => {
                                    setFilterAccount('');
                                    setFilterCategory('');
                                    setFilterType('ALL');
                                    setSearch('');
                                    setPage(0);
                                    updateUrlParams({ 
                                        accountId: '', 
                                        categoryId: '', 
                                        type: 'ALL', 
                                        search: '' 
                                    });
                                }}
                                className="bg-orange-500 text-white px-2 py-1 rounded text-xs hover:bg-orange-600 whitespace-nowrap"
                                title="Reset filters (keeps month/date selection, clears account, category, type, and search)"
                            >
                                Reset
                            </button>
                            <button
                                onClick={() => {
                                    setFilterMonth('');
                                    setFilterDate('');
                                    setFilterMode('month');
                                    setFilterAccount('');
                                    setFilterCategory('');
                                    setFilterType('ALL');
                                    setSearch('');
                                    setPage(0);
                                    updateUrlParams({ 
                                        month: '', 
                                        date: '', 
                                        accountId: '', 
                                        categoryId: '', 
                                        type: 'ALL', 
                                        search: '' 
                                    });
                                }}
                                className="bg-red-500 text-white px-2 py-1 rounded text-xs hover:bg-red-600 whitespace-nowrap"
                                title="Clear all filters including date selections (resets everything to default)"
                            >
                                Clear All
                            </button>
                        </div>
                    </div>
                </div>

                {/* Section 3: Actions (Add + Export) */}
				<div className="w-full bg-white border border-blue-200 rounded-md p-2 shadow-sm grid grid-cols-4 sm:flex sm:flex-wrap sm:items-center gap-2">
                    <button
                        onClick={() =>
                            setEditTx({
                                id: null,
                                description: '',
                                explanation: '',
                                amount: 0,
                                date: dayjs().format('YYYY-MM-DDTHH:mm'),
                                type: 'DEBIT',
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
                                accountId: filterAccount,
                                type: filterType,
                                search: debouncedSearch,
                            });
                            if (filterCategory) params.append('categoryId', filterCategory);
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
                        className="bg-blue-500 text-white px-2 py-1 text-xs sm:px-2 sm:py-1 sm:text-sm rounded hover:bg-blue-600 w-full sm:w-auto"
                        title="Export filtered transactions to CSV file (comma-separated values, opens in Excel/Sheets)"
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
                                search: debouncedSearch,
                            });
                            if (filterCategory) params.append('categoryId', filterCategory);
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
                        className="bg-blue-500 text-white px-2 py-1 text-xs sm:px-2 sm:py-1 sm:text-sm rounded hover:bg-blue-600 w-full sm:w-auto"
                        title="Export filtered transactions to Excel file (.xlsx format, preserves formatting)"
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
                                search: debouncedSearch,
                            });
                            if (filterCategory) params.append('categoryId', filterCategory);
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
                            autoTable(doc, {
                                head: [headers],
                                body: rows,
                                styles: { fontSize: 8, cellWidth: 'wrap' },
                                columnStyles: { 0: { cellWidth: 20 }, 1: { cellWidth: 70 }, 2: { cellWidth: 25, halign: 'right' }, 3: { cellWidth: 15 }, 4: { cellWidth: 30 } },
                                tableWidth: 'wrap',
                                margin: { top: 20 },
                            });
                            doc.save('transactions.pdf');
                        }}
                        className="bg-blue-500 text-white px-2 py-1 text-xs sm:px-2 sm:py-1 sm:text-sm rounded hover:bg-blue-600 w-full sm:w-auto"
                        title="Export filtered transactions to PDF file (printable/shareable document format)"
                    >
                        PDF
                    </button>
					<div className="col-span-4 sm:ml-auto text-right text-xs sm:text-sm">
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

			{modalContent && (
				<div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50 p-2 sm:p-4 overflow-y-auto">
					<div className={`bg-white rounded-lg shadow-lg w-full p-4 my-2 sm:my-4 ${
						modalContent.title === "Transaction Details" 
							? "max-w-[95vw] sm:max-w-lg md:max-w-2xl" 
							: "max-w-[95vw] sm:max-w-xl md:max-w-3xl lg:max-w-4xl"
					}`}>
						<h2 className="text-base sm:text-lg font-semibold mb-3">{modalContent.title}</h2>
						<div className="text-xs sm:text-sm text-gray-700 mb-3 max-h-[70vh] sm:max-h-[75vh] overflow-y-auto overflow-x-hidden">
							{modalContent.content}
						</div>
						<div className="flex justify-end">
							<button
								onClick={() => setModalContent(null)}
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
