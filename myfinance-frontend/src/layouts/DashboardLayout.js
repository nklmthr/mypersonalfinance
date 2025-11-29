import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import {
  HomeIcon,
  ChartBarIcon,
  BanknotesIcon,
  BuildingLibraryIcon,
  FolderIcon,
  CreditCardIcon,
  Squares2X2Icon,
  UserCircleIcon,
  Bars3Icon,
  XMarkIcon,
} from "@heroicons/react/24/outline";

export default function DashboardLayout({ children }) {
  const [sidebarOpen, setSidebarOpen] = useState(null);

  const handleNavClick = () => {
    if (window.innerWidth < 768) setSidebarOpen(false);
  };

  return (
    <div className="flex h-screen bg-blue-50 text-gray-800 relative">
      {/* Sidebar */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-black opacity-30 z-10 md:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside
        className={`fixed md:relative z-20 top-0 left-0 h-full w-64 border-r shadow-lg bg-blue-100 transform transition-transform duration-300 ease-in-out ${
          sidebarOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"
        }`}
      >
        <div className="p-4 text-2xl font-bold text-blue-700 tracking-tight">💰 MyFinance</div>

        <nav className="flex flex-col p-2 space-y-2 text-sm">
          <Link to="/" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
            <HomeIcon className="h-5 w-5" />
            Overview
          </Link>
          <Link to="/category-spends" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
            <ChartBarIcon className="h-5 w-5" />
            Category Spends
          </Link>
          <Link to="/balance-sheet" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
            <BanknotesIcon className="h-5 w-5" />
            Balance Sheet
          </Link>
          <Link to="/transactions" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
            <CreditCardIcon className="h-5 w-5" />
            Transactions
          </Link>
		  <Link to="/prediction-rules" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
		    <ChartBarIcon className="h-5 w-5" />
		    Prediction Rules
		  </Link>
		  <Link to="/uploaded-statements" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
		    <FolderIcon className="h-5 w-5" />
		    Uploaded Statements
		  </Link>
          <Link to="/accounts" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
            <Squares2X2Icon className="h-5 w-5" />
            Accounts
          </Link>
          <Link to="/categories" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
            <FolderIcon className="h-5 w-5" />
            Categories
          </Link>
          <Link to="/institutions" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
            <BuildingLibraryIcon className="h-5 w-5" />
            Institutions
          </Link>
          <Link to="/account-types" onClick={handleNavClick} className="flex items-center gap-2 hover:text-blue-500">
            <Squares2X2Icon className="h-5 w-5" />
            Account Types
          </Link>
        </nav>
      </aside>

      {/* Main */}
      <div className="flex-1 flex flex-col relative z-10">
        {/* Header */}
        <header className="flex justify-between items-center px-4 py-3 shadow-sm border-b bg-blue-100 md:px-6">
          <button className="md:hidden text-gray-600" onClick={() => setSidebarOpen(!sidebarOpen)}>
            {sidebarOpen ? <XMarkIcon className="h-6 w-6" /> : <Bars3Icon className="h-6 w-6" />}
          </button>

          <div className="ml-auto flex items-center space-x-4">
            <Link to="/profile" className="flex items-center gap-1 text-sm hover:text-blue-600">
              <UserCircleIcon className="h-5 w-5" />
              Profile
            </Link>
            <Link to="/logout" className="text-sm font-medium text-red-600 hover:underline">
              Logout
            </Link>
          </div>
        </header>

        {/* Page content */}
		<main className="flex-1 overflow-auto px-4 py-4 md:px-6 bg-blue-50">
		  {children}
		</main>

        {/* Footer */}
        <footer className="text-center text-xs py-2 text-gray-500 bg-blue-100 border-t">
          © 2025 MyFinance by Nikhil Mathur
        </footer>
      </div>
    </div>
  );
}
