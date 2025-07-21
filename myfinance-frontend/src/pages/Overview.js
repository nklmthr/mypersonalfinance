import React from "react";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts";

const dummyTrendData = [
	{ month: "Mar", income: 50000, expense: 32000 },
	{ month: "Apr", income: 52000, expense: 35000 },
	{ month: "May", income: 48000, expense: 42000 },
	{ month: "Jun", income: 53000, expense: 37000 },
	{ month: "Jul", income: 55000, expense: 39000 }
];

const dummyCategoryData = [
	{ name: "Food", value: 12000 },
	{ name: "Transport", value: 4000 },
	{ name: "Shopping", value: 6000 },
	{ name: "Rent", value: 18000 },
	{ name: "Utilities", value: 3000 }
];

const COLORS = ["#0088FE", "#00C49F", "#FFBB28", "#FF8042", "#8884d8"];

export default function OverviewPage() {
	return (
		<div className="p-4 space-y-6">
			<h2 className="text-2xl font-bold">📊 Financial Overview</h2>

			{/* Net Worth Summary */}
			<div className="grid grid-cols-1 md:grid-cols-3 gap-4">
				<div className="bg-white shadow rounded p-4">
					<div className="text-gray-600 text-sm">Total Assets</div>
					<div className="text-2xl font-semibold text-green-600">₹1,20,000</div>
				</div>
				<div className="bg-white shadow rounded p-4">
					<div className="text-gray-600 text-sm">Total Liabilities</div>
					<div className="text-2xl font-semibold text-red-600">₹40,000</div>
				</div>
				<div className="bg-white shadow rounded p-4">
					<div className="text-gray-600 text-sm">Net Worth</div>
					<div className="text-2xl font-semibold">₹80,000</div>
				</div>
			</div>

			{/* Income vs Expense Chart */}
			<div className="bg-white shadow rounded p-4">
				<div className="text-lg font-semibold mb-2">📈 Income vs Expense</div>
				<ResponsiveContainer width="100%" height={250}>
					<LineChart data={dummyTrendData}>
						<XAxis dataKey="month" />
						<YAxis />
						<Tooltip />
						<Line type="monotone" dataKey="income" stroke="#4ade80" strokeWidth={2} />
						<Line type="monotone" dataKey="expense" stroke="#f87171" strokeWidth={2} />
					</LineChart>
				</ResponsiveContainer>
			</div>

			{/* Category-wise Breakdown */}
			<div className="grid grid-cols-1 md:grid-cols-2 gap-4">
				<div className="bg-white shadow rounded p-4">
					<div className="text-lg font-semibold mb-2">📂 Expense Breakdown</div>
					<ResponsiveContainer width="100%" height={250}>
						<PieChart>
						<Pie
						  data={dummyCategoryData}
						  dataKey="value"
						  nameKey="name"
						  cx="50%"
						  cy="50%"
						  outerRadius={80}
						  label={({ name }) => name} // ← Custom label function
						>
						  {dummyCategoryData.map((entry, index) => (
						    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
						  ))}
						</Pie>
							<Tooltip />
						</PieChart>
					</ResponsiveContainer>
				</div>

				{/* Recent Transactions */}
				<div className="bg-white shadow rounded p-4">
					<div className="text-lg font-semibold mb-2">🧾 Recent Transactions</div>
					<ul className="text-sm space-y-1">
						<li className="flex justify-between">
							<span>Swiggy</span>
							<span className="text-red-600">- ₹450</span>
						</li>
						<li className="flex justify-between">
							<span>Salary</span>
							<span className="text-green-600">+ ₹50,000</span>
						</li>
						<li className="flex justify-between">
							<span>Petrol</span>
							<span className="text-red-600">- ₹1,200</span>
						</li>
						<li className="flex justify-between">
							<span>Electricity</span>
							<span className="text-red-600">- ₹900</span>
						</li>
					</ul>
				</div>
			</div>

			
		</div>
	);
}
