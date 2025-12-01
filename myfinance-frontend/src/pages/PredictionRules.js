import React, { useEffect, useState } from "react";
import api from "../auth/api";
import { useNavigate } from "react-router-dom";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import SearchSelect from "./transactions/components/SearchSelect";
import { buildTree, flattenCategories } from "./transactions/utils/utils";
import { useErrorModal } from "../auth/ErrorModalContext";

export default function PredictionRules() {
	const navigate = useNavigate();
	const { showModal } = useErrorModal();
	const [rules, setRules] = useState([]);
	const [categories, setCategories] = useState([]);
	const [loading, setLoading] = useState(false);
	const [editRule, setEditRule] = useState(null);
	const [deleteConfirmation, setDeleteConfirmation] = useState(null);
	
	// Initialize with current month and year
	const now = new Date();
	const [selectedMonth, setSelectedMonth] = useState(now.getMonth() + 1); // 1-12
	const [selectedYear, setSelectedYear] = useState(now.getFullYear());

	NProgress.configure({ showSpinner: false });

	const fetchData = async () => {
		setLoading(true);
		NProgress.start();
		try {
			const [rulesRes, catRes] = await Promise.all([
				api.get("/prediction-rules"),
				api.get("/categories"),
			]);
			setRules(rulesRes.data);
			setCategories(catRes.data);
		} catch (error) {
			if (error.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to fetch data:", error);
			}
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

	useEffect(() => {
		fetchData();
	}, []);

	const saveRule = async (rule) => {
		setLoading(true);
		NProgress.start();
		try {
			const payload = {
				categoryId: rule.categoryId,
				predictionType: rule.predictionType,
				enabled: rule.enabled,
				lookbackMonths: rule.lookbackMonths || 3,
				specificMonth: rule.specificMonth || null,
			};

			if (rule.id) {
				await api.put(`/prediction-rules/${rule.id}`, payload);
			} else {
				await api.post("/prediction-rules", payload);
			}
			await fetchData();
			setEditRule(null);
		} catch (err) {
			console.error("Failed to save prediction rule:", err);
			// Error is already handled by api.js interceptor (shows modal)
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

	const deleteRule = async (id) => {
		setDeleteConfirmation(null);
		setLoading(true);
		NProgress.start();
		try {
			await api.delete(`/prediction-rules/${id}`);
			await fetchData();
		} catch (err) {
			console.error("Failed to delete prediction rule:", err);
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

	const regeneratePredictions = async (id) => {
		setLoading(true);
		NProgress.start();
		try {
			// Format: yyyy-MM
			const targetMonth = `${selectedYear}-${String(selectedMonth).padStart(2, '0')}`;
			await api.post(`/prediction-rules/${id}/regenerate?targetMonth=${targetMonth}`);
			showModal(`Predictions regenerated for ${getMonthName(selectedMonth)} ${selectedYear}!`);
		} catch (err) {
			console.error("Failed to regenerate predictions:", err);
			// Error is already handled by api.js interceptor (shows modal)
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

	const generateAllPredictions = async () => {
		setLoading(true);
		NProgress.start();
		try {
			// Format: yyyy-MM
			const targetMonth = `${selectedYear}-${String(selectedMonth).padStart(2, '0')}`;
			await api.post(`/prediction-rules/generate-all?targetMonth=${targetMonth}`);
			showModal(`Predictions generated for ${getMonthName(selectedMonth)} ${selectedYear}!`);
		} catch (err) {
			console.error("Failed to generate predictions:", err);
			// Error is already handled by api.js interceptor (shows modal)
		} finally {
			NProgress.done();
			setLoading(false);
		}
	};

	// Build category tree and flatten for dropdown (same as Transactions page)
	const treeCategories = buildTree(categories);
	const rootHome = treeCategories.filter((cat) => cat?.name === "Home");
	const limitedTree = rootHome.length > 0 ? rootHome : treeCategories;
	const flattened = flattenCategories(limitedTree);

	return (
		<div className="space-y-4">
			<div className="flex items-center justify-between">
				<h1 className="text-2xl font-bold text-gray-800">Prediction Rules</h1>
				<div className="flex gap-2 items-center">
					{/* Month/Year Selection */}
					<div className="flex gap-2 items-center bg-gray-50 px-3 py-2 rounded border border-gray-200">
						<label className="text-sm font-medium text-gray-700">Generate for:</label>
						<select
							value={selectedMonth}
							onChange={(e) => setSelectedMonth(parseInt(e.target.value))}
							className="border border-gray-300 px-2 py-1 rounded text-sm"
						>
							{[...Array(12)].map((_, i) => (
								<option key={i + 1} value={i + 1}>
									{getMonthName(i + 1)}
								</option>
							))}
						</select>
						<select
							value={selectedYear}
							onChange={(e) => setSelectedYear(parseInt(e.target.value))}
							className="border border-gray-300 px-2 py-1 rounded text-sm"
						>
							{[...Array(5)].map((_, i) => {
								const year = new Date().getFullYear() + i - 1;
								return (
									<option key={year} value={year}>
										{year}
									</option>
								);
							})}
						</select>
					</div>
					<button
						onClick={generateAllPredictions}
						className="bg-purple-600 text-white px-4 py-2 rounded text-sm shadow hover:bg-purple-700"
					>
						Generate All Predictions
					</button>
					<button
						onClick={() =>
							setEditRule({
								id: null,
								categoryId: "",
								predictionType: "MONTHLY",
								enabled: true,
								lookbackMonths: 3,
								specificMonth: null,
							})
						}
						className="bg-green-500 text-white px-4 py-2 rounded text-sm shadow hover:bg-green-600"
					>
						Add Rule
					</button>
				</div>
			</div>

			<div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
				<div className="space-y-2">
					{rules.map((rule) => (
						<div
							key={rule.id}
							className="grid grid-cols-1 md:grid-cols-6 gap-4 py-3 px-4 rounded border border-gray-200 items-center text-sm bg-blue-50"
						>
							<div className="font-medium text-gray-800">{rule.categoryName}</div>
							<div className="text-gray-600">
								{rule.predictionType === "MONTHLY" ? "Monthly" : `Yearly (${getMonthName(rule.specificMonth)})`}
							</div>
							<div className="text-gray-600">Lookback: {rule.lookbackMonths} months</div>
							<div>
								<span
									className={`inline-block px-2 py-1 rounded text-xs ${
										rule.enabled
											? "bg-green-100 text-green-700"
											: "bg-gray-100 text-gray-600"
									}`}
								>
									{rule.enabled ? "Enabled" : "Disabled"}
								</span>
							</div>
							<div className="flex gap-2 md:col-span-2 justify-end">
								<button
									onClick={() => regeneratePredictions(rule.id)}
									className="text-purple-600 hover:underline"
									title="Regenerate predictions for current month"
								>
									Regenerate
								</button>
								<button
									onClick={() => setEditRule({ ...rule })}
									className="text-blue-600 hover:underline"
								>
									Edit
								</button>
								<button
									onClick={() => setDeleteConfirmation(rule.id)}
									className="text-red-600 hover:underline"
								>
									Delete
								</button>
							</div>
						</div>
					))}
					{rules.length === 0 && (
						<div className="text-center text-gray-500 py-8">
							No prediction rules configured. Click "Add Rule" to create one.
						</div>
					)}
				</div>
			</div>

			{/* Edit Modal */}
			{editRule && (
				<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
					<div className="bg-white rounded-lg shadow-xl max-w-lg w-full p-6">
						<h2 className="text-xl font-semibold text-gray-800 mb-4">
							{editRule.id ? "Edit Prediction Rule" : "Create Prediction Rule"}
						</h2>

						<div className="space-y-4">
							<div>
								<label className="block text-sm font-medium text-gray-700 mb-1">
									Category
								</label>
								<SearchSelect
									options={flattened.map((c) => ({ id: c.id, name: c.name }))}
									value={editRule.categoryId}
									onChange={(val) => setEditRule({ ...editRule, categoryId: val })}
									placeholder="Select Category"
									disabled={!!editRule.id}
								/>
							</div>

							<div>
								<label className="block text-sm font-medium text-gray-700 mb-1">
									Prediction Type
								</label>
								<select
									value={editRule.predictionType}
									onChange={(e) =>
										setEditRule({ ...editRule, predictionType: e.target.value })
									}
									className="w-full border px-3 py-2 rounded"
								>
									<option value="MONTHLY">Monthly</option>
									<option value="YEARLY">Yearly</option>
								</select>
							</div>

							{editRule.predictionType === "YEARLY" && (
								<div>
									<label className="block text-sm font-medium text-gray-700 mb-1">
										Specific Month (for yearly predictions)
									</label>
									<select
										value={editRule.specificMonth || ""}
										onChange={(e) =>
											setEditRule({
												...editRule,
												specificMonth: e.target.value ? parseInt(e.target.value) : null,
											})
										}
										className="w-full border px-3 py-2 rounded"
									>
										<option value="">Select Month</option>
										{[...Array(12)].map((_, i) => (
											<option key={i + 1} value={i + 1}>
												{getMonthName(i + 1)}
											</option>
										))}
									</select>
								</div>
							)}

							<div>
								<label className="block text-sm font-medium text-gray-700 mb-1">
									Lookback Months (for averaging)
								</label>
								<input
									type="number"
									min="1"
									max="24"
									value={editRule.lookbackMonths}
									onChange={(e) =>
										setEditRule({
											...editRule,
											lookbackMonths: parseInt(e.target.value),
										})
									}
									className="w-full border px-3 py-2 rounded"
								/>
							</div>

							<div className="flex items-center">
								<input
									type="checkbox"
									checked={editRule.enabled}
									onChange={(e) =>
										setEditRule({ ...editRule, enabled: e.target.checked })
									}
									className="mr-2"
								/>
								<label className="text-sm text-gray-700">Enabled</label>
							</div>
						</div>

						<div className="flex gap-2 justify-end mt-6">
							<button
								onClick={() => setEditRule(null)}
								className="px-4 py-2 text-sm border border-gray-300 rounded hover:bg-gray-50"
							>
								Cancel
							</button>
							<button
								onClick={() => saveRule(editRule)}
								className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
								disabled={!editRule.categoryId}
							>
								Save
							</button>
						</div>
					</div>
				</div>
			)}

			{/* Delete Confirmation Modal */}
			{deleteConfirmation && (
				<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
					<div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-6">
						<h2 className="text-xl font-semibold text-gray-900 mb-2">
							Delete Prediction Rule
						</h2>
						<p className="text-gray-600 mb-6">
							Are you sure you want to delete this prediction rule? All associated
							predicted transactions will also be deleted.
						</p>
						<div className="flex gap-3 justify-end">
							<button
								onClick={() => setDeleteConfirmation(null)}
								className="px-4 py-2 text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-md"
							>
								Cancel
							</button>
							<button
								onClick={() => deleteRule(deleteConfirmation)}
								className="px-4 py-2 text-white bg-red-600 hover:bg-red-700 rounded-md"
							>
								Delete
							</button>
						</div>
					</div>
				</div>
			)}

			{loading && (
				<div className="fixed inset-0 bg-white bg-opacity-40 z-50 flex items-center justify-center">
					<div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-500 h-10 w-10 animate-spin"></div>
				</div>
			)}
		</div>
	);
}

function getMonthName(month) {
	const months = [
		"January",
		"February",
		"March",
		"April",
		"May",
		"June",
		"July",
		"August",
		"September",
		"October",
		"November",
		"December",
	];
	return months[month - 1] || "";
}

