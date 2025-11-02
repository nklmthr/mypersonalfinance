import React from "react";

export default function FetchToolbar({
    availableServices,
    selectedServices,
    setSelectedServices,
    refreshing,
    triggerDataExtraction,
    currentTotal,
}) {
    // Function to determine color based on amount in lakhs
    // Negative amounts (expenses) → red, Positive amounts (income/savings) → green
    const getColorForAmount = (amount) => {
        const lakhs = amount / 100000;
        
        // Very negative (< -3L): dark red
        if (lakhs < -3) {
            return { bg: 'bg-red-200', text: 'text-red-950', border: 'border-red-500' };
        }
        // Moderately negative (-3L to -2L): red
        else if (lakhs < -2) {
            return { bg: 'bg-red-100', text: 'text-red-900', border: 'border-red-400' };
        }
        // Slightly negative (-2L to -1L): orange
        else if (lakhs < -1) {
            return { bg: 'bg-orange-100', text: 'text-orange-900', border: 'border-orange-300' };
        }
        // Near zero (-1L to 1L): yellow
        else if (lakhs < 1) {
            return { bg: 'bg-yellow-100', text: 'text-yellow-900', border: 'border-yellow-300' };
        }
        // Slightly positive (1L to 2L): light green
        else if (lakhs < 2) {
            return { bg: 'bg-green-100', text: 'text-green-800', border: 'border-green-300' };
        }
        // Moderately positive (2L to 3L): green
        else if (lakhs < 3) {
            return { bg: 'bg-green-200', text: 'text-green-900', border: 'border-green-400' };
        }
        // Very positive (≥ 3L): dark green
        else {
            return { bg: 'bg-green-300', text: 'text-green-950', border: 'border-green-500' };
        }
    };

    const colors = getColorForAmount(currentTotal);

    return (
        <div className="w-full bg-white border border-blue-200 rounded-md p-3 shadow-sm">
            <div className="flex flex-wrap items-center gap-2 justify-between">
                <div className="flex items-center gap-2">
                    <select
                        value={selectedServices.length === availableServices.length ? 'ALL' : (selectedServices[0] || 'ALL')}
                        onChange={(e) => {
                            const val = e.target.value;
                            if (val === 'ALL') {
                                setSelectedServices(availableServices);
                            } else {
                                setSelectedServices([val]);
                            }
                        }}
                        className="border px-3 py-2 rounded text-sm min-w-[260px] bg-blue-50"
                        title="Select a data extraction service or All"
                    >
                        <option value="ALL">All Services</option>
                        {(availableServices || []).map((svc) => (
                            <option key={svc} value={svc}>{svc}</option>
                        ))}
                    </select>
                    <button
                        onClick={() => triggerDataExtraction(selectedServices)}
                        disabled={refreshing}
                        className={`${refreshing 
                            ? 'bg-gray-400 cursor-not-allowed' 
                            : 'bg-purple-600 hover:bg-purple-700'
                        } text-white px-4 py-2 rounded text-sm shadow`}
                        title="Fetch new transactions from bank/email services (NOT for page refresh - transactions auto-load on filter change)"
                    >
                        Fetch
                    </button>
                </div>
                <div
                    className={`${colors.bg} ${colors.text} ${colors.border} border-2 px-4 py-2 rounded text-sm shadow-md font-semibold`}
                    title={`Total transactions amount: ₹${currentTotal.toLocaleString('en-IN', {
                        minimumFractionDigits: 2,
                    })}`}
                >
                    Total: ₹{currentTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                </div>
            </div>
        </div>
    );
}

