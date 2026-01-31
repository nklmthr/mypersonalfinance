import React, { useEffect, useState } from "react";
import { parse, format } from "date-fns";
import api from "../auth/api";
import { useNavigate } from "react-router-dom";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

export default function BalanceSheetPage() {
	const currentYear = new Date().getFullYear();
	const [selectedYear, setSelectedYear] = useState(currentYear);
	const [months, setMonths] = useState([]);
	const [rowsByClassification, setRowsByClassification] = useState({});
	const [summaryByMonth, setSummaryByMonth] = useState({});
	const [loading, setLoading] = useState(false);
	const navigate = useNavigate();
	NProgress.configure({ showSpinner: false });
	const fetchBalanceSheet = () => {
		setLoading(true);
		NProgress.start();
		api
			.get(`/balance-sheet/year/${selectedYear}`, { withCredentials: true })
			.then((res) => {
				const responseData = res.data || [];
				const allMonthsSet = new Set();
				const classificationMap = {};
				const monthSummaries = {};

				responseData.forEach((monthBlock) => {
					const monthKey = Object.keys(monthBlock.summaryByMonth)[0];
					const parsedDate = parse(monthKey, "dd-MMM-yyyy", new Date());
					const monthLabel = format(parsedDate, "MMM yyyy");
					allMonthsSet.add(monthLabel);
					monthSummaries[monthLabel] = monthBlock.summaryByMonth[monthKey];

					monthBlock.rows.forEach((row) => {
						const classification = row.classification;
						if (!classificationMap[classification]) {
							classificationMap[classification] = {};
						}
						classificationMap[classification][monthLabel] = row.balancesByMonth[monthKey];
					});
				});

				const sortedMonths = Array.from(allMonthsSet).sort(
					(a, b) => new Date("01 " + b) - new Date("01 " + a)
				);

				// Filter out months that have no meaningful data
				// A month is considered empty if it has no classification data AND summary is 0, undefined, or null
				const filteredMonths = sortedMonths.filter((month) => {
					// Check if any classification has a value for this month
					const hasClassificationData = Object.values(classificationMap).some(
						(balances) => balances[month] !== undefined && balances[month] !== null
					);
					// Check if summary has a non-zero value for this month
					const hasMeaningfulSummary = monthSummaries[month] !== undefined && 
						monthSummaries[month] !== null && 
						monthSummaries[month] !== 0;
					
					return hasClassificationData || hasMeaningfulSummary;
				});

				setMonths(filteredMonths);
				setRowsByClassification(classificationMap);
				setSummaryByMonth(monthSummaries);
			})
			.catch((err) => {
				if (err.response?.status === 401) {
					window.location.href = "/login";
				}
			})
			.finally(() => {
				NProgress.done();
				setLoading(false);
			});
	};


	useEffect(() => {
		fetchBalanceSheet();
	}, [selectedYear]);

	const formatCurrency = (val) => {
		if (val === undefined || val === null || isNaN(val)) return "-";
		return new Intl.NumberFormat('en-IN', {
			style: 'currency',
			currency: 'INR',
			minimumFractionDigits: 2,
			maximumFractionDigits: 2
		}).format(val);
	};

	const handleSnapshot = async () => {
		setLoading(true);
		const today = new Date().toISOString().split("T")[0];
		try {
			await api.post(`/accounts/snapshot?date=${today}`, {}, { withCredentials: true });
			alert("Snapshot created successfully!");
			fetchBalanceSheet();
		} catch (error) {
			if (error.response && error.response.status === 409) {
				alert("❗ Snapshot already exists in the last 2 weeks.");
			} else if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");   // 👈 force redirect to login
			} else {
				console.error("Failed to fetch user profile:", err);
			}
		} finally {
			setLoading(false);
		}
	};

	return (
		<div className="max-w-6xl mx-auto p-4">
			<div className="flex flex-wrap items-center justify-between gap-2 mb-6">
				<h2 className="text-xl font-bold">📉 Balance Sheet - Yearly View</h2>
				<div className="flex items-center gap-2">
					<select
						className="border rounded px-2 py-1 text-sm"
						value={selectedYear}
						onChange={(e) => setSelectedYear(Number(e.target.value))}
					>
						{[...Array(10)].map((_, idx) => {
							const year = currentYear - idx;
							return (
								<option key={year} value={year}>
									{year}
								</option>
							);
						})}
					</select>
					<button
						onClick={handleSnapshot}
						disabled={loading}
						className={`px-3 py-1 rounded text-white text-sm font-semibold ${loading ? "bg-gray-400" : "bg-blue-600 hover:bg-blue-700"
							}`}
					>
						{loading ? "Creating..." : "📸 Snapshot"}
					</button>
				</div>
			</div>

			<div className="overflow-x-auto relative">
				<table className="min-w-full border-collapse border text-sm text-left">
					<thead>
						<tr className="bg-gray-100 text-nowrap">
							<th className="border px-4 py-2 sticky left-0 bg-white z-10">Classification</th>
							{months.map((month) => (
								<th key={month} className="border px-4 py-2 text-right min-w-[100px]">
									{month}
								</th>
							))}
						</tr>
					</thead>
					<tbody>
						{Object.entries(rowsByClassification).map(([classification, balances]) => (
							<tr key={classification}>
								<td className="border px-4 py-2 font-medium sticky left-0 bg-gray-50 z-10 bg-white">{classification}</td>
								{months.map((month) => (
									<td key={month} className="border px-4 py-2 text-right">
										{balances[month] !== undefined ? formatCurrency(balances[month]) : "-"}
									</td>
								))}
							</tr>
						))}
						<tr className="bg-blue-100 font-bold">
							<td className="border px-4 py-2 font-medium sticky left-0 bg-gray-50 z-10">Summary</td>
							{months.map((month) => (
								<td key={month} className="border px-4 py-2 text-right">
									{summaryByMonth[month] !== undefined ? formatCurrency(summaryByMonth[month]) : "-"}
								</td>
							))}
						</tr>
					</tbody>
				</table>
			</div>
			{loading && (
				<div className="fixed inset-0 bg-white bg-opacity-40 z-50 flex items-center justify-center">
					<div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-500 h-10 w-10 animate-spin"></div>
				</div>
			)}
		</div>
	);
}
