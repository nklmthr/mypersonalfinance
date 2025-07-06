import React from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import DashboardLayout from "./layouts/DashboardLayout";
import AccountTypes from "./pages/AccountTypes";
import Institutions from "./pages/Institutions";
import Categories from "./pages/Categories";
import Transactions from "./pages/Transactions";
import CategorySpends from "./pages/CategorySpends";
import BalanceSheet from "./pages/BalanceSheet";

import Accounts from "./pages/Accounts"; // ✅ Import the Accounts component

export default function App() {
	return (
		<Router>
			<Routes>
				<Route
					path="/"
					element={
						<DashboardLayout pageTitle="🏠 Home">
							<h1 className="text-2xl font-semibold">Welcome to myFinance</h1>
						</DashboardLayout>
					}
				/>
				<Route
				  path="/balance-sheet"
				  element={
				    <DashboardLayout pageTitle="📊 Balance Sheet">
				      <BalanceSheet />
				    </DashboardLayout>
				  }
				/>
				<Route
					path="/account-types"
					element={
						<DashboardLayout pageTitle="📒 Account Types">
							<AccountTypes />
						</DashboardLayout>
					}
				/>
				<Route
					path="/institutions"
					element={
						<DashboardLayout pageTitle="🏦 Institutions">
							<Institutions />
						</DashboardLayout>
					}
				/>
				<Route
					path="/accounts"
					element={
						<DashboardLayout pageTitle="💼 Accounts">
							<Accounts />
						</DashboardLayout>
					}
				/>
				<Route
					path="/categories" // ✅ New route
					element={
						<DashboardLayout pageTitle="📂 Categories">
							<Categories />
						</DashboardLayout>
					}
				/>
				<Route
				  path="/category-spends"
				  element={
				    <DashboardLayout pageTitle="📊 Category Spends">
				      <CategorySpends />
				    </DashboardLayout>
				  }
				/>
				<Route
				  path="/transactions"
				  element={
				    <DashboardLayout pageTitle="💸 Transactions">
				      <Transactions />
				    </DashboardLayout>
				  }
				/>
				<Route
					path="/reports"
					element={
						<DashboardLayout pageTitle="📊 Reports">
							<h1 className="text-2xl">Reports coming soon</h1>
						</DashboardLayout>
					}
				/>
			</Routes>
		</Router>
	);
}
