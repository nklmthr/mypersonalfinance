import React, { useEffect, useState } from "react";
import { parse, format } from "date-fns";
import api from "../api"; // Adjust the import based on your project structure
export default function BalanceSheetPage() {
  const currentYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState(currentYear);
  const [months, setMonths] = useState([]);
  const [rowsByClassification, setRowsByClassification] = useState({});
  const [summaryByMonth, setSummaryByMonth] = useState({});
  const [loading, setLoading] = useState(false);

  const fetchBalanceSheet = () => {
    api
      .get(`/balance-sheet/yearly?year=${selectedYear}`, { withCredentials: true })
      .then((res) => {
        const responseData = res.data || [];
        const allMonthsSet = new Set();
        const classificationMap = {};
        const monthSummaries = {};

        responseData.forEach((monthBlock) => {
          const monthKey = Object.keys(monthBlock.summaryByMonth)[0]; // e.g., "01-Jul-2025"
          const parsedDate = parse(monthKey, "dd-MMM-yyyy", new Date());
          const monthLabel = format(parsedDate, "MMM yyyy"); // e.g., "Jul 2025"
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
          (a, b) => new Date("01 " + a) - new Date("01 " + b)
        );

        setMonths(sortedMonths);
        setRowsByClassification(classificationMap);
        setSummaryByMonth(monthSummaries);
      })
      .catch((err) => {
        if (err.response?.status === 401) {
          window.location.href = "/login"; // session check
        }
      });
  };

  useEffect(() => {
    fetchBalanceSheet();
  }, [selectedYear]);

  const formatCurrency = (val) =>
    `₹${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2 })}`;

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
      } else {
        alert("Failed to create snapshot.");
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
            {[...Array(6)].map((_, idx) => {
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
            className={`px-3 py-1 rounded text-white text-sm font-semibold ${
              loading ? "bg-gray-400" : "bg-blue-600 hover:bg-blue-700"
            }`}
          >
            {loading ? "Creating..." : "📸 Snapshot"}
          </button>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full border-collapse border text-sm text-left">
          <thead>
            <tr className="bg-gray-100 text-nowrap">
              <th className="border px-4 py-2">Classification</th>
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
                <td className="border px-4 py-2 font-medium bg-gray-50">{classification}</td>
                {months.map((month) => (
                  <td key={month} className="border px-4 py-2 text-right">
                    {balances[month] !== undefined ? formatCurrency(balances[month]) : "-"}
                  </td>
                ))}
              </tr>
            ))}
            <tr className="bg-blue-100 font-bold">
              <td className="border px-4 py-2">Summary</td>
              {months.map((month) => (
                <td key={month} className="border px-4 py-2 text-right">
                  {summaryByMonth[month] !== undefined ? formatCurrency(summaryByMonth[month]) : "-"}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
