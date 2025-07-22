import React from "react";
import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";
import DashboardLayout from "./layouts/DashboardLayout";
import AccountTypes from "./pages/AccountTypes";
import Institutions from "./pages/Institutions";
import Categories from "./pages/Categories";
import Transactions from "./pages/Transactions";
import CategorySpends from "./pages/CategorySpends";
import BalanceSheet from "./pages/BalanceSheet";
import Accounts from "./pages/Accounts";
import Login from "./pages/Login";
import Logout from "./pages/Logout";
import RequireAuth from "./auth/RequireAuth";
import Signup from "./pages/Signup";
import Overview from "./pages/Overview";
import Profile from "./pages/Profile";
export default function App() {
	return (
		<Router>
			<Routes>
				{/* Public Route */}
				<Route path="/login" element={<Login />} />
				<Route path="/logout" element={<Logout />} />
				<Route path="/signup" element={<Signup />} />
				{/* Protected Routes */}
				<Route
					path="/"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="🏠 Home">
								<Overview />
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/profile"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="🏠 Home">
								{<Profile />}
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/balance-sheet"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="📊 Balance Sheet">
								<BalanceSheet />
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/account-types"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="📒 Account Types">
								<AccountTypes />
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/institutions"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="🏦 Institutions">
								<Institutions />
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/accounts"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="💼 Accounts">
								<Accounts />
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/categories"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="📂 Categories">
								<Categories />
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/category-spends"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="📊 Category Spends">
								<CategorySpends />
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/transactions"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="💸 Transactions">
								<Transactions />
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				<Route
					path="/reports"
					element={
						<RequireAuth>
							<DashboardLayout pageTitle="📊 Reports">
								<h1 className="text-2xl">Reports coming soon</h1>
							</DashboardLayout>
						</RequireAuth>
					}
				/>
				{/* Fallback */}
				<Route path="*" element={<Navigate to="/" />} />
			</Routes>
		</Router>
	);
}
