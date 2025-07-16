import React from "react";
import { Link } from "react-router-dom";
import {
	HomeIcon,
	ChartBarIcon,
	BanknotesIcon,
	BuildingLibraryIcon,
	FolderIcon,
	CreditCardIcon,
	Squares2X2Icon,
	ArrowRightOnRectangleIcon,
	UserCircleIcon,
} from "@heroicons/react/24/outline";

export default function DashboardLayout({ children }) {
	const backendUrl = process.env.REACT_APP_BACKEND_URL;
	const handleConnectGmail = async () => {
		try {
			const res = await axios.get(
				window.location.origin + `/oauth/authorize?redirectOrigin`,
				{ withCredentials: true }
			);
			window.location.href = res.data.authUrl; // Redirect the browser to Google
		} catch (err) {
			alert("Failed to get Gmail authorization URL");
			console.error(err);
		}
	};
	return (
		<div className="flex h-screen overflow-hidden bg-gradient-to-br from-gray-100 via-white to-gray-200 text-gray-800 relative">
			{/* Global background texture */}
			<div
				className="absolute inset-0 z-0 opacity-10 bg-cover bg-no-repeat bg-center"
				style={{
					backgroundImage: "url('https://www.transparenttextures.com/patterns/purty-wood.png')",
				}}
			/>

			{/* Sidebar */}
			<aside
				className="relative z-10 w-64 border-r shadow-lg bg-white/90 bg-repeat backdrop-blur-sm"
				style={{
					backgroundImage: "url('https://www.transparenttextures.com/patterns/white-wall-3.png')",
				}}
			>
				<div className="p-4 text-2xl font-bold text-blue-600 tracking-tight">
					💰 MyFinance
				</div>
				<nav className="flex flex-col p-2 space-y-2 text-sm">
					<Link to="/" className="flex items-center gap-2 hover:text-blue-500">
						<HomeIcon className="h-5 w-5" />
						Overview
					</Link>
					<Link to="/category-spends" className="flex items-center gap-2 hover:text-blue-500">
						<ChartBarIcon className="h-5 w-5" />
						Category Spends
					</Link>
					<Link to="/balance-sheet" className="flex items-center gap-2 hover:text-blue-500">
						<BanknotesIcon className="h-5 w-5" />
						Balance Sheet
					</Link>
					<Link to="/transactions" className="flex items-center gap-2 hover:text-blue-500">
						<CreditCardIcon className="h-5 w-5" />
						Transactions
					</Link>
					<Link to="/accounts" className="flex items-center gap-2 hover:text-blue-500">
						<Squares2X2Icon className="h-5 w-5" />
						Accounts
					</Link>
					<Link to="/categories" className="flex items-center gap-2 hover:text-blue-500">
						<FolderIcon className="h-5 w-5" />
						Categories
					</Link>
					<Link to="/institutions" className="flex items-center gap-2 hover:text-blue-500">
						<BuildingLibraryIcon className="h-5 w-5" />
						Institutions
					</Link>
					<Link to="/account-types" className="flex items-center gap-2 hover:text-blue-500">
						<Squares2X2Icon className="h-5 w-5" />
						Account Types
					</Link>
				</nav>
			</aside>

			{/* Main Content */}
			<div className="flex-1 flex flex-col relative z-10">
				{/* Header */}
				<header
					className="flex justify-between items-center px-6 py-3 shadow-sm border-b bg-white/90 backdrop-blur-sm bg-repeat"
					style={{
						backgroundImage: "url('https://www.transparenttextures.com/patterns/wood-pattern.png')",
					}}
				>
					<div className="ml-auto flex items-center space-x-4">
						<button className="flex items-center gap-1 text-sm hover:text-blue-600">
							<UserCircleIcon className="h-5 w-5" />
							Profile
						</button>
						<button
							className="bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700 transition"
							onClick={() => {
								window.location.href = `${backendUrl}/oauth/authorize`;
							}}
						>
							Connect Gmail
						</button>
						<Link to="/logout" className="text-sm font-medium text-red-600 hover:underline ml-auto">
							Logout
						</Link>
					</div>
				</header>

				{/* Content */}
				<main className="flex-1 overflow-y-auto px-6 py-4 relative z-10">
					{children}
				</main>

				{/* Footer */}
				<footer
					className="text-center text-xs py-2 text-gray-500 bg-white/70 backdrop-blur-sm border-t bg-repeat"
					style={{
						backgroundImage: "url('https://www.transparenttextures.com/patterns/paper-fibers.png')",
					}}
				>
					© 2025 MyFinance by Nikhil Mathur
				</footer>
			</div>
		</div>
	);
}
