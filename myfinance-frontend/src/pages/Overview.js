import React, { useEffect, useState } from "react";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, BarChart, Bar, Legend, CartesianGrid } from "recharts";
import { parse, format } from "date-fns";
import dayjs from "dayjs";
import api from "../auth/api";
import { useNavigate } from "react-router-dom";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

const COLORS = ["#0088FE", "#00C49F", "#FFBB28", "#FF8042", "#8884d8", "#FF6B9D", "#C44569", "#1B9CFC"];

// Category-specific color mapping
const CATEGORY_COLORS = {
	"Earning": "#22c55e",      // Green
	"Expenses": "#f97316",     // Orange
	"Not Classified": "#eab308", // Yellow
	"Cash in Hand": "#3b82f6"  // Blue
};

export default function OverviewPage() {
	const [balanceSheetData, setBalanceSheetData] = useState([]);
	const [allBalanceSheetData, setAllBalanceSheetData] = useState([]);
	const [categoryData, setCategoryData] = useState([]);
	const [allCategoryData, setAllCategoryData] = useState([]);
	const [loading, setLoading] = useState(true);
	const [balanceTimePeriod, setBalanceTimePeriod] = useState('1year');
	const [categoryTimePeriod, setCategoryTimePeriod] = useState('1year');
	const [balanceYearsFetched, setBalanceYearsFetched] = useState(1);
	const [categoryMonthsFetched, setCategoryMonthsFetched] = useState(12);
	const navigate = useNavigate();
	const currentYear = new Date().getFullYear();

	NProgress.configure({ showSpinner: false });

	const formatCurrency = (val) => {
		if (val === undefined || val === null || isNaN(val)) return "-";
		return new Intl.NumberFormat('en-IN', {
			style: 'currency',
			currency: 'INR',
			minimumFractionDigits: 0,
			maximumFractionDigits: 0
		}).format(val);
	};

	const formatYAxisCurrency = (value) => {
		if (value === 0) return "₹0";
		const absValue = Math.abs(value);
		
		// For values >= 1 lakh (100,000), show in lakhs
		if (absValue >= 100000) {
			const lakhs = value / 100000;
			return `₹${lakhs.toFixed(1)}L`;
		}
		// For values >= 1000, show in thousands
		else if (absValue >= 1000) {
			const thousands = value / 1000;
			return `₹${thousands.toFixed(0)}K`;
		}
		// For smaller values, show as is
		return `₹${value}`;
	};

	const processCategoryData = (categoryResponseData, totalMonths) => {
		// Get all months for the specified period
		const allMonths = [];
		for (let i = totalMonths - 1; i >= 0; i--) {
			allMonths.push(dayjs().subtract(i, "month").format("YYYY-MM"));
		}

		// Filter for specific 4 categories: Earning, Expenses, Cash in Hand, Not Classified
		const targetCategories = ["Earning", "Expenses", "Cash in Hand", "Not Classified"];
		const selectedCategories = [];

		const findCategory = (categories, targetName) => {
			for (const category of categories) {
				if (category.name === targetName) {
					return category;
				}
				if (category.children && category.children.length > 0) {
					const found = findCategory(category.children, targetName);
					if (found) return found;
				}
			}
			return null;
		};

		targetCategories.forEach(targetName => {
			const category = findCategory(categoryResponseData, targetName);
			if (category) {
				const monthlyData = {};
				
				allMonths.forEach(month => {
					const entry = category.monthlySpends?.find(m => m.month === month);
					const amount = entry ? Math.abs(entry.amount) : 0;
					monthlyData[month] = amount;
				});

				selectedCategories.push({
					name: category.name,
					monthlyData,
					allMonths
				});
			}
		});

		setAllCategoryData({ categories: selectedCategories, allMonths });
	};

	// Initial data fetch - only 1 year
	useEffect(() => {
		const fetchInitialData = async () => {
			setLoading(true);
			NProgress.start();
			
			try {
				// Fetch Balance Sheet data for current year only
				const balanceChartData = [];
				try {
					const balanceRes = await api.get(`/balance-sheet/year/${currentYear}`, { withCredentials: true });
					const balanceResponseData = balanceRes.data || [];
					
					balanceResponseData.forEach((monthBlock) => {
						const monthKey = Object.keys(monthBlock.summaryByMonth)[0];
						const parsedDate = parse(monthKey, "dd-MMM-yyyy", new Date());
						const monthLabel = format(parsedDate, "MMM yy");
						const summary = monthBlock.summaryByMonth[monthKey];
						
						balanceChartData.push({
							month: monthLabel,
							balance: summary,
							date: parsedDate
						});
					});
				} catch (err) {
					console.warn(`Failed to fetch balance sheet for ${currentYear}:`, err);
				}

				// Sort by date
				balanceChartData.sort((a, b) => a.date - b.date);
				setAllBalanceSheetData(balanceChartData);
				setBalanceYearsFetched(1);

				// Fetch Category Spends data for 12 months
				const categoryRes = await api.get("/category-spends?months=12", { withCredentials: true });
				const categoryResponseData = Array.isArray(categoryRes.data) ? categoryRes.data : [];
				
				// Process category data
				processCategoryData(categoryResponseData, 12);
				setCategoryMonthsFetched(12);

			} catch (err) {
				if (err.response?.status === 401) {
					localStorage.removeItem("authToken");
					navigate("/login");
				} else {
					console.error("Failed to fetch initial data:", err);
				}
			} finally {
				NProgress.done();
				setLoading(false);
			}
		};

		fetchInitialData();
	}, [currentYear, navigate]);

	// Lazy load balance sheet data when user selects 5 or 10 years
	useEffect(() => {
		const fetchMoreBalanceData = async () => {
			let yearsNeeded = 1;
			if (balanceTimePeriod === '5years') yearsNeeded = 5;
			else if (balanceTimePeriod === '10years') yearsNeeded = 10;

			// If we already have the data, no need to fetch
			if (balanceYearsFetched >= yearsNeeded) return;

			setLoading(true);
			NProgress.start();

			try {
				const balanceChartData = [...allBalanceSheetData];
				
				// Fetch additional years
				for (let i = balanceYearsFetched; i < yearsNeeded; i++) {
					const year = currentYear - i;
					try {
						const balanceRes = await api.get(`/balance-sheet/year/${year}`, { withCredentials: true });
						const balanceResponseData = balanceRes.data || [];
						
						balanceResponseData.forEach((monthBlock) => {
							const monthKey = Object.keys(monthBlock.summaryByMonth)[0];
							const parsedDate = parse(monthKey, "dd-MMM-yyyy", new Date());
							const monthLabel = format(parsedDate, "MMM yy");
							const summary = monthBlock.summaryByMonth[monthKey];
							
							balanceChartData.push({
								month: monthLabel,
								balance: summary,
								date: parsedDate
							});
						});
					} catch (err) {
						console.warn(`Failed to fetch balance sheet for ${year}:`, err);
					}
				}

				// Sort by date
				balanceChartData.sort((a, b) => a.date - b.date);
				setAllBalanceSheetData(balanceChartData);
				setBalanceYearsFetched(yearsNeeded);
			} catch (err) {
				console.error("Failed to fetch additional balance data:", err);
			} finally {
				NProgress.done();
				setLoading(false);
			}
		};

		fetchMoreBalanceData();
	}, [balanceTimePeriod, balanceYearsFetched, allBalanceSheetData, currentYear]);

	// Lazy load category data when user selects 5 or 10 years
	useEffect(() => {
		const fetchMoreCategoryData = async () => {
			let monthsNeeded = 12;
			if (categoryTimePeriod === '6months') monthsNeeded = 6;
			else if (categoryTimePeriod === '1year') monthsNeeded = 12;
			else if (categoryTimePeriod === '5years') monthsNeeded = 60;
			else if (categoryTimePeriod === '10years') monthsNeeded = 120;

			// If we already have the data, no need to fetch
			if (categoryMonthsFetched >= monthsNeeded) return;

			setLoading(true);
			NProgress.start();

			try {
				const categoryRes = await api.get(`/category-spends?months=${monthsNeeded}`, { withCredentials: true });
				const categoryResponseData = Array.isArray(categoryRes.data) ? categoryRes.data : [];
				
				processCategoryData(categoryResponseData, monthsNeeded);
				setCategoryMonthsFetched(monthsNeeded);
			} catch (err) {
				if (err.response?.status === 401) {
					localStorage.removeItem("authToken");
					navigate("/login");
				} else {
					console.error("Failed to fetch additional category data:", err);
				}
			} finally {
				NProgress.done();
				setLoading(false);
			}
		};

		fetchMoreCategoryData();
	}, [categoryTimePeriod, categoryMonthsFetched, navigate]);

	// Filter balance sheet data based on selected time period
	useEffect(() => {
		if (allBalanceSheetData.length === 0) return;

		const now = new Date();
		let filteredData = [];

		switch (balanceTimePeriod) {
			case '6months':
				const sixMonthsAgo = new Date(now.getFullYear(), now.getMonth() - 6, 1);
				filteredData = allBalanceSheetData.filter(item => item.date >= sixMonthsAgo);
				break;
			case '1year':
				const oneYearAgo = new Date(now.getFullYear() - 1, now.getMonth(), 1);
				filteredData = allBalanceSheetData.filter(item => item.date >= oneYearAgo);
				break;
			case '5years':
				const fiveYearsAgo = new Date(now.getFullYear() - 5, now.getMonth(), 1);
				filteredData = allBalanceSheetData.filter(item => item.date >= fiveYearsAgo);
				break;
			case '10years':
				filteredData = [...allBalanceSheetData];
				break;
			default:
				filteredData = allBalanceSheetData.slice(-6);
		}

		setBalanceSheetData(filteredData);
	}, [balanceTimePeriod, allBalanceSheetData]);

	// Filter category data based on selected time period
	useEffect(() => {
		if (!allCategoryData.categories || allCategoryData.categories.length === 0) return;

		let monthsToShow = [];
		const now = dayjs();

		switch (categoryTimePeriod) {
			case '6months':
				monthsToShow = [...Array(6)].map((_, i) => 
					now.subtract(i, "month").format("YYYY-MM")
				).reverse();
				break;
			case '1year':
				monthsToShow = [...Array(12)].map((_, i) => 
					now.subtract(i, "month").format("YYYY-MM")
				).reverse();
				break;
			case '5years':
				monthsToShow = [...Array(60)].map((_, i) => 
					now.subtract(i, "month").format("YYYY-MM")
				).reverse();
				break;
			case '10years':
				monthsToShow = [...Array(120)].map((_, i) => 
					now.subtract(i, "month").format("YYYY-MM")
				).reverse();
				break;
			default:
				monthsToShow = [...Array(6)].map((_, i) => 
					now.subtract(i, "month").format("YYYY-MM")
				).reverse();
		}

		// Transform data for chart
		const categoryChartData = monthsToShow.map(month => {
			const dataPoint = {
				month: dayjs(month).format("MMM YY")
			};
			allCategoryData.categories.forEach(cat => {
				dataPoint[cat.name] = cat.monthlyData[month] || 0;
			});
			return dataPoint;
		});

		// Calculate totals for cards
		const categoriesWithTotals = allCategoryData.categories.map(cat => {
			const total = monthsToShow.reduce((sum, month) => 
				sum + (cat.monthlyData[month] || 0), 0
			);
			return {
				name: cat.name,
				total
			};
		});

		setCategoryData({ chartData: categoryChartData, categories: categoriesWithTotals });
	}, [categoryTimePeriod, allCategoryData]);

	const latestBalance = balanceSheetData.length > 0 ? balanceSheetData[balanceSheetData.length - 1].balance : 0;
	const previousBalance = balanceSheetData.length > 1 ? balanceSheetData[balanceSheetData.length - 2].balance : 0;
	const balanceChange = latestBalance - previousBalance;

	return (
		<div className="p-4 space-y-6">
			<h2 className="text-2xl font-bold">📊 Financial Overview</h2>

			{loading ? (
				<div className="flex items-center justify-center h-64">
					<div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-500 h-12 w-12 animate-spin"></div>
				</div>
			) : (
				<>
					{/* Net Worth Summary */}
					<div className="grid grid-cols-1 md:grid-cols-3 gap-4">
						<div className="bg-white shadow rounded p-4">
							<div className="text-gray-600 text-sm">Current Balance</div>
							<div className="text-2xl font-semibold text-blue-600">{formatCurrency(latestBalance)}</div>
						</div>
						<div className="bg-white shadow rounded p-4">
							<div className="text-gray-600 text-sm">Previous Month</div>
							<div className="text-2xl font-semibold text-gray-600">{formatCurrency(previousBalance)}</div>
						</div>
						<div className="bg-white shadow rounded p-4">
							<div className="text-gray-600 text-sm">Change</div>
							<div className={`text-2xl font-semibold ${balanceChange >= 0 ? 'text-green-600' : 'text-red-600'}`}>
								{balanceChange >= 0 ? '+' : ''}{formatCurrency(balanceChange)}
							</div>
						</div>
					</div>

					{/* Balance Sheet Summary Chart */}
					<div className="bg-white shadow rounded p-4">
						<div className="flex items-center justify-between mb-4">
							<div className="text-lg font-semibold">📈 Balance Sheet Summary</div>
							<select
								className="border rounded px-3 py-1 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
								value={balanceTimePeriod}
								onChange={(e) => setBalanceTimePeriod(e.target.value)}
							>
								<option value="6months">Last 6 Months</option>
								<option value="1year">Last 1 Year</option>
								<option value="5years">Last 5 Years</option>
								<option value="10years">Last 10 Years</option>
							</select>
						</div>
						<ResponsiveContainer width="100%" height={300}>
							<LineChart data={balanceSheetData}>
								<CartesianGrid strokeDasharray="3 3" />
								<XAxis dataKey="month" />
								<YAxis 
									tickFormatter={formatYAxisCurrency}
								/>
								<Tooltip 
									formatter={(value) => formatCurrency(value)}
									labelStyle={{ color: '#000' }}
								/>
								<Line 
									type="monotone" 
									dataKey="balance" 
									stroke="#0088FE" 
									strokeWidth={3}
									dot={{ fill: '#0088FE', r: 5 }}
									activeDot={{ r: 8 }}
								/>
							</LineChart>
						</ResponsiveContainer>
					</div>

					{/* Category Spending Chart */}
					{categoryData.chartData && categoryData.chartData.length > 0 && (
						<div className="bg-white shadow rounded p-4">
							<div className="flex items-center justify-between mb-4">
								<div className="text-lg font-semibold">📂 Category Spends</div>
								<select
									className="border rounded px-3 py-1 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
									value={categoryTimePeriod}
									onChange={(e) => setCategoryTimePeriod(e.target.value)}
								>
									<option value="6months">Last 6 Months</option>
									<option value="1year">Last 1 Year</option>
									<option value="5years">Last 5 Years</option>
									<option value="10years">Last 10 Years</option>
								</select>
							</div>
							<ResponsiveContainer width="100%" height={350}>
								<BarChart data={categoryData.chartData}>
									<CartesianGrid strokeDasharray="3 3" />
									<XAxis dataKey="month" />
									<YAxis 
										tickFormatter={formatYAxisCurrency}
									/>
									<Tooltip 
										formatter={(value) => formatCurrency(value)}
										labelStyle={{ color: '#000' }}
									/>
									<Legend />
									{categoryData.categories && categoryData.categories.map((cat) => (
										<Bar 
											key={cat.name} 
											dataKey={cat.name} 
											fill={CATEGORY_COLORS[cat.name] || COLORS[0]}
											radius={[4, 4, 0, 0]}
										/>
									))}
								</BarChart>
							</ResponsiveContainer>
						</div>
					)}

					{/* Category Summary Cards */}
					{categoryData.categories && categoryData.categories.length > 0 && (
						<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
							{categoryData.categories.map((cat) => (
								<div key={cat.name} className="bg-white shadow rounded p-4">
									<div className="flex items-center gap-2 mb-2">
										<div 
											className="w-4 h-4 rounded" 
											style={{ backgroundColor: CATEGORY_COLORS[cat.name] || COLORS[0] }}
										></div>
										<div className="text-gray-600 text-sm font-medium">{cat.name}</div>
									</div>
									<div className="text-xl font-semibold">{formatCurrency(cat.total)}</div>
									<div className="text-xs text-gray-500 mt-1">
										{categoryTimePeriod === '6months' && 'Last 6 months total'}
										{categoryTimePeriod === '1year' && 'Last 1 year total'}
										{categoryTimePeriod === '5years' && 'Last 5 years total'}
										{categoryTimePeriod === '10years' && 'Last 10 years total'}
									</div>
								</div>
							))}
						</div>
					)}
				</>
			)}
		</div>
	);
}
