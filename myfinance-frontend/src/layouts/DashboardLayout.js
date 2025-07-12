import React from "react";
import { Link } from "react-router-dom";

export default function DashboardLayout({ children }) {
	return (
		<div className="flex h-screen bg-gray-50 text-gray-800">
			{/* Sidebar */}
			<aside className="w-64 bg-white border-r shadow">
				<div className="p-4 text-xl font-bold text-blue-600">💰 MyFinance</div>
				<nav className="flex flex-col p-2 space-y-2 text-sm">
					<Link to="/" className="hover:text-blue-500">🏠 Overview</Link>
					<Link to="/category-spends" className="hover:text-blue-500">📊 Category Spends</Link>
					<Link to="/balance-sheet" className="hover:text-blue-500">📊 Balance Sheet</Link>
					<Link to="/transactions" className="hover:text-blue-500">💸 Transactions</Link>
					<Link to="/accounts" className="hover:text-blue-500">💼 Accounts</Link>
					<Link to="/categories" className="hover:text-blue-500">📂 Categoriess</Link>
					<Link to="/institutions" className="hover:text-blue-500">🏦 Institutions</Link>
					<Link to="/account-types" className="hover:text-blue-500">📒 Account Types</Link>
				</nav>
			</aside>

			{/* Main Area */}
			<div className="flex-1 flex flex-col">
				{/* Header */}
				<header className="flex justify-between items-center px-6 py-3 bg-white border-b shadow-sm">
					<div className="ml-auto flex items-center space-x-4">
						<button className="text-sm hover:text-blue-600">👤 Profile</button>
						<button className="text-sm hover:text-blue-600">🔐 Login</button>
					</div>
				</header>


				{/* Content */}
				<main className="flex-1 overflow-y-auto px-6 py-4 bg-gray-50">
					{children}
				</main>

				{/* Footer */}
				<footer className="text-center text-xs py-2 text-gray-500">
					© 2025 MyFinance by Nikhil Mathur
				</footer>
			</div>
		</div>
	);
}
