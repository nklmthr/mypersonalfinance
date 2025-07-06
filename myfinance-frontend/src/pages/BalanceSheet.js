import React, { useEffect, useState } from "react";
import axios from "axios";

export default function BalanceSheetPage() {
  const [months, setMonths] = useState([]);
  const [rowsByClassification, setRowsByClassification] = useState({});
  const [summaryByMonth, setSummaryByMonth] = useState({});
  const [loading, setLoading] = useState(false);

  const fetchBalanceSheet = () => {
    axios.get("/api/balance-sheet/last-six-months").then((res) => {
      const responseData = res.data || [];
      const allMonths = [];
      const classificationMap = {};
      const monthSummaries = {};

      responseData.forEach((monthBlock) => {
        const month = Object.keys(monthBlock.summaryByMonth)[0];
        allMonths.push(month);
        monthSummaries[month] = monthBlock.summaryByMonth[month];

        monthBlock.rows.forEach((row) => {
          const classification = row.classification;
          if (!classificationMap[classification]) {
            classificationMap[classification] = {};
          }
          classificationMap[classification][month] = row.balancesByMonth[month];
        });
      });

      setMonths(allMonths);
      setRowsByClassification(classificationMap);
      setSummaryByMonth(monthSummaries);
    });
  };

  useEffect(() => {
    fetchBalanceSheet();
  }, []);

  const formatCurrency = (val) =>
    `₹${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2 })}`;

  const handleSnapshot = async () => {
    setLoading(true);
    const today = new Date().toISOString().split("T")[0];
    try {
      await axios.post(`/api/accounts/snapshot?date=${today}`);
      alert("Snapshot created successfully!");
      fetchBalanceSheet();
    } catch (error) {
      console.error(error);
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
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold">📉 Balance Sheet - Last 6 Months</h2>
        <button
          onClick={handleSnapshot}
          disabled={loading}
          className={`px-4 py-2 rounded text-white font-semibold ${
            loading ? "bg-gray-400" : "bg-blue-600 hover:bg-blue-700"
          }`}
        >
          {loading ? "Creating..." : "📸 Create Snapshot"}
        </button>
      </div>

      <table className="min-w-full border-collapse border text-sm text-left">
        <thead>
          <tr className="bg-gray-100">
            <th className="border px-4 py-2">Classification</th>
            {months.map((month) => (
              <th key={month} className="border px-4 py-2 text-right">
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
  );
}
