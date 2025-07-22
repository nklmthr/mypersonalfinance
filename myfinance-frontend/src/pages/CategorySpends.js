import React, { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import api from "./../auth/api";
import {
  ChartBarIcon,
  ArrowPathIcon,
  ChevronRightIcon,
  ChevronDownIcon,
} from "@heroicons/react/24/outline";

const months = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"
];

export default function CategorySpendSummary() {
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1);
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear());
  const [spendData, setSpendData] = useState(null);
  const [expanded, setExpanded] = useState({});
  const formattedMonth = `${selectedYear}-${String(selectedMonth).padStart(2, '0')}`;
  const [loading, setLoading] = useState(false);
  NProgress.configure({ showSpinner: false });
  const fetchData = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const res = await api.get("/category-spends", {
        params: { month: selectedMonth, year: selectedYear },
      });
      setSpendData(res.data);
    } catch (err) {
      console.error("Fetch error:", err);
    } finally {
      NProgress.done();
      setLoading(false);
    }
  };


  useEffect(() => {
    fetchData();
  }, []);

  const toggleExpand = (id) => {
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  };
  const formatCurrency = (val) => {
    if (!Number.isFinite(val)) return "₹0.00";
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Math.abs(val));
  };


  const renderCategoryTree = (node, level = 0) => {
    if (!node) return null;

    const indent = level * 16;
    const hasChildren = node.children && node.children.length > 0;
    const isExpanded = expanded[node.categoryId];
    const isNegative = node.amount < 0;
	const isZero = node.amount === 0;
    return (
      <div key={node.categoryId}>
        <div
          style={{ paddingLeft: indent }}
          className="flex items-center py-1 cursor-pointer group transition-colors"
          onClick={() => hasChildren && toggleExpand(node.categoryId)}
        >
          {hasChildren && (
            <span className="mr-2 text-gray-400 group-hover:text-gray-600">
              {isExpanded ? (
                <ChevronDownIcon className="h-4 w-4 inline-block" />
              ) : (
                <ChevronRightIcon className="h-4 w-4 inline-block" />
              )}
            </span>
          )}

          <motion.a
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
            href={`/transactions?categoryId=${node.categoryId}&month=${formattedMonth}`}
            className={`ml-1 text-sm font-medium hover:underline ${
              isZero? "text-blue-600": isNegative ? "text-red-600" : "text-green-600"
            }`}
            onClick={(e) => e.stopPropagation()}
          >
            {node.categoryName}: {formatCurrency(node.amount)}
          </motion.a>
        </div>

        <AnimatePresence>
          {isExpanded && node.children && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.3 }}
            >
              {node.children.map((child) =>
                renderCategoryTree(child, level + 3)
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    );
  };

  return (
    <div className="max-w-4xl mx-auto px-4">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <ChartBarIcon className="h-6 w-6 text-indigo-600" />
        <h2 className="text-2xl font-semibold text-gray-800">
          Category Spend Summary
        </h2>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4 items-center mb-6">
        <select
          className="border rounded px-3 py-2 shadow-sm focus:outline-none focus:ring focus:ring-indigo-300"
          value={selectedMonth}
          onChange={(e) => setSelectedMonth(Number(e.target.value))}
        >
          {months.map((m, i) => (
            <option key={i} value={i + 1}>{m}</option>
          ))}
        </select>
        <input
          type="number"
          className="border rounded px-3 py-2 w-24 shadow-sm focus:outline-none focus:ring focus:ring-indigo-300"
          value={selectedYear}
          onChange={(e) => setSelectedYear(Number(e.target.value))}
        />
        <button
          className="flex items-center gap-1 bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700 transition"
          onClick={fetchData}
        >
          <ArrowPathIcon className="h-4 w-4" />
          Refresh
        </button>
      </div>

      {/* Card with Results */}
      <div className="bg-white rounded-xl shadow-lg border border-gray-100 p-6 min-h-[200px]">
        {spendData ? (
          spendData.map((node) => renderCategoryTree(node))
        ) : (
          <p className="text-gray-500 text-sm">Loading...</p>
        )}
      </div>
	  {loading && (
	    <div className="fixed inset-0 bg-white bg-opacity-50 z-50 flex items-center justify-center">
	      <div className="loader ease-linear rounded-full border-4 border-t-4 border-indigo-500 h-10 w-10 animate-spin"></div>
	    </div>
	  )}
    </div>
  );
}
