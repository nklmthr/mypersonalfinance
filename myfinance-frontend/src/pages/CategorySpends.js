import React, { useState, useEffect } from "react";
import dayjs from "dayjs";
import api from "../auth/api";
import { useNavigate, Link } from "react-router-dom";

export default function CategorySpendSummary() {
	const [data, setData] = useState([]);
	const EXPANDED_STORAGE_KEY = "category-spends:expanded";
	const [expanded, setExpanded] = useState(() => {
		if (typeof window === "undefined") return new Set();
		try {
			const stored = window.localStorage.getItem(EXPANDED_STORAGE_KEY);
			return stored ? new Set(JSON.parse(stored)) : new Set();
		} catch (error) {
			console.warn("Failed to restore category spends expanded state", error);
			return new Set();
		}
	});

	const navigate = useNavigate();
	const last6Months = [...Array(6)].map((_, i) =>
		dayjs().subtract(i, "month").format("YYYY-MM")
	);

	const persistExpanded = (expandedSet) => {
		if (typeof window === "undefined") return;
		try {
			window.localStorage.setItem(
				EXPANDED_STORAGE_KEY,
				JSON.stringify([...expandedSet])
			);
		} catch (error) {
			console.warn("Failed to persist category spends expanded state", error);
		}
	};

	const sortTreeByChildCountAsc = (nodes = []) => {
		if (!Array.isArray(nodes)) return [];
		return [...nodes]
			.map((node) => ({
				...node,
				children: sortTreeByChildCountAsc(node.children || []),
			}))
			.sort((a, b) => (a.children?.length || 0) - (b.children?.length || 0));
	};

	const collectIds = (nodes = []) => {
		const result = new Set();
		const stack = [...nodes];
		while (stack.length) {
			const current = stack.pop();
			if (!current?.id || result.has(current.id)) continue;
			result.add(current.id);
			if (Array.isArray(current.children)) {
				stack.push(...current.children);
			}
		}
		return result;
	};

	const toggle = (id) => {
		setExpanded((prev) => {
			const next = new Set(prev);
			next.has(id) ? next.delete(id) : next.add(id);
			persistExpanded(next);
			return next;
		});
	};

	const isExpanded = (id) => expanded.has(id);

	const formatINR = (amount) =>
		new Intl.NumberFormat("en-IN", {
			style: "currency",
			currency: "INR",
			maximumFractionDigits: 0,
		}).format(amount);

	const getAmountForMonth = (monthlySpends, month) => {
		const entry = monthlySpends?.find((m) => m.month === month);
		return entry ? entry.amount : 0;
	};

	useEffect(() => {
		api.get("/category-spends")
			.then((res) => {
				const responseData = Array.isArray(res.data) ? res.data : [];
				const sorted = sortTreeByChildCountAsc(responseData);
				setData(sorted);
				setExpanded((prev) => {
					const validIds = collectIds(sorted);
					const filtered = new Set([...prev].filter((id) => validIds.has(id)));
					persistExpanded(filtered);
					return filtered;
				});
			})
			.catch((err) => {
				if (err.response?.status === 401) {
					localStorage.removeItem("authToken");
					navigate("/");
				} else {
					console.error("Failed to fetch user profile:", err);
				}
			});
	}, []);

	const renderRows = (items = [], depth = 0) => {
		return items.flatMap((item) => {
			const hasChildren = item.children?.length > 0;
			const expandedState = isExpanded(item.id);

			const total = last6Months.reduce(
				(sum, month) => sum + getAmountForMonth(item.monthlySpends, month),
				0
			);

			return [
				<tr key={item.id} className="border-b">
					<td
						className="py-1 px-2 sticky left-0 border-r z-10"
						style={{ background: '#fff', minWidth: '200px' }}
					>
						<div style={{ paddingLeft: depth * 20 }} className="flex items-center gap-1">
							{hasChildren ? (
								<button onClick={() => toggle(item.id)} className="text-sm text-blue-500">
									{expandedState ? "▾" : "▸"}
								</button>
							) : (
								<span className="w-4" />
							)}
							{item.name}
						</div>
					</td>

					{last6Months.map((month) => (
						<td key={month} className="text-right px-2 py-1">
							<Link
								to={`/transactions?month=${month}&categoryId=${item.id}`}
								className="text-blue-600 hover:underline"
							>
								{formatINR(getAmountForMonth(item.monthlySpends, month))}
							</Link>
						</td>
					))}
					<td className="text-right px-2 py-1 font-bold">
						{formatINR(total)}
					</td>
				</tr>,
				expandedState && hasChildren ? renderRows(item.children, depth + 1) : null,
			];
		});
	};

	return (
		<div className="w-full overflow-x-auto">
			<table className="table-fixed border border-gray-300 mt-4 text-sm min-w-[900px]">
				<thead className="bg-gray-100">
					<tr>
						<th
							className="text-left py-2 px-2 border-r sticky left-0 z-20"
							style={{ minWidth: '200px', background: '#fff' }}
						>
							Category
						</th>
						{last6Months.map((month) => (
							<th key={month} className="text-right py-2 px-2 whitespace-nowrap">
								{dayjs(month).format("MMM YY")}
							</th>
						))}
						<th className="text-right py-2 px-2">Total</th>
					</tr>
				</thead>
				<tbody>
					{renderRows(data)}
				</tbody>
			</table>
		</div>
	);
}
