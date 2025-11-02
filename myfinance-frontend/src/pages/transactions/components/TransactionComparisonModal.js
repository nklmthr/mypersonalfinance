import React from "react";

export default function TransactionComparisonModal({ tx }) {
	const hasGptData = tx.gptDescription || tx.gptAmount || tx.gptExplanation || tx.gptType || tx.gptAccount;
	
	return (
		<div>
			{/* Header */}
			<div className="bg-gradient-to-r from-blue-50 to-indigo-50 p-2 sm:p-3 rounded-lg border border-blue-200 mb-2 sm:mb-3">
				<div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 text-xs sm:text-sm">
					<div>
						<span className="text-xs text-gray-600 uppercase tracking-wide">Transaction Date</span>
						<div className="font-semibold text-gray-800 text-xs sm:text-sm">{new Date(tx.date).toLocaleString('en-IN', { 
							year: 'numeric', 
							month: 'short', 
							day: 'numeric', 
							hour: '2-digit', 
							minute: '2-digit' 
						})}</div>
					</div>
					{hasGptData && (
						<div className="text-right text-xs sm:text-sm">
							<span className="text-gray-600">See account comparison below ↓</span>
						</div>
					)}
				</div>
			</div>

			{/* Comparison Table - Only show if GPT data is available */}
			{hasGptData ? (
				<div className="border border-gray-300 rounded-lg overflow-x-auto -mx-1 sm:mx-0">
					<table className="w-full text-xs sm:text-sm" style={{ tableLayout: 'fixed' }}>
						<colgroup>
							<col style={{ width: '20%' }} />
							<col style={{ width: '40%' }} />
							<col style={{ width: '40%' }} />
						</colgroup>
						<thead className="bg-gradient-to-r from-gray-100 to-blue-50 border-b-2 border-gray-300">
							<tr>
								<th className="px-2 sm:px-3 py-1.5 sm:py-2 text-left font-bold text-gray-700">Attribute</th>
								<th className="px-2 sm:px-3 py-1.5 sm:py-2 text-left font-bold text-gray-700 border-l border-gray-300">📊 Extracted</th>
								<th className="px-2 sm:px-3 py-1.5 sm:py-2 text-left font-bold text-blue-700 border-l-2 border-blue-300">🤖 AI Analyzed</th>
							</tr>
						</thead>
						<tbody className="divide-y divide-gray-200">
							{/* Amount */}
							<tr className="hover:bg-gray-50">
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 font-semibold text-gray-700 align-top">💰 Amount</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l border-gray-200 align-top">
									<span className={`text-sm sm:text-lg font-bold ${tx.type === "DEBIT" ? "text-red-600" : "text-green-600"}`}>
										₹{(typeof tx.amount === "number" ? tx.amount : 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
									</span>
								</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l-2 border-blue-200 align-top">
									{tx.gptAmount ? (
										<span className={`text-sm sm:text-lg font-bold ${tx.gptType === "DEBIT" ? "text-red-600" : "text-green-600"}`}>
											{tx.currency || "INR"}{(typeof tx.gptAmount === "number" ? tx.gptAmount : parseFloat(tx.gptAmount) || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
										</span>
									) : (
										<span className="text-gray-400 italic text-xs sm:text-sm">Not analyzed</span>
									)}
								</td>
							</tr>

							{/* Type */}
							<tr className="hover:bg-gray-50">
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 font-semibold text-gray-700 align-top">🔄 Type</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l border-gray-200 align-top">
									<span className={`inline-flex items-center px-1.5 sm:px-2 py-0.5 sm:py-1 rounded-full text-xs font-semibold ${
										tx.type === "DEBIT" ? "bg-red-100 text-red-800" : "bg-green-100 text-green-800"
									}`}>
										{tx.type}
									</span>
								</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l-2 border-blue-200 align-top">
									{tx.gptType ? (
										<span className={`inline-flex items-center px-1.5 sm:px-2 py-0.5 sm:py-1 rounded-full text-xs font-semibold ${
											tx.gptType === "DEBIT" ? "bg-red-100 text-red-800" : "bg-green-100 text-green-800"
										}`}>
											{tx.gptType}
										</span>
									) : (
										<span className="text-gray-400 italic text-xs sm:text-sm">Not analyzed</span>
									)}
								</td>
							</tr>

							{/* Account */}
							<tr className="hover:bg-gray-50">
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 font-semibold text-gray-700 align-top">🏦 Account</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l border-gray-200 align-top">
									<div className="space-y-0.5">
										<div className="font-medium text-gray-800 text-xs sm:text-sm">{tx.account?.name}</div>
										<div className="text-xs text-gray-600">{tx.account?.institution?.description}</div>
										<div className="text-xs text-gray-500">{tx.account?.accountType?.name}</div>
									</div>
								</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l-2 border-blue-200 align-top">
									{tx.gptAccount ? (
										<div className="space-y-0.5">
											<div className="font-medium text-gray-800 text-xs sm:text-sm">{tx.gptAccount.name}</div>
											<div className="text-xs text-gray-600">{tx.gptAccount.institution?.description}</div>
											<div className="text-xs text-gray-500">{tx.gptAccount.accountType?.name}</div>
										</div>
									) : (
										<span className="text-gray-400 italic text-xs sm:text-sm">Not analyzed</span>
									)}
								</td>
							</tr>

							{/* Description */}
							<tr className="hover:bg-gray-50">
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 font-semibold text-gray-700 align-top">📝 Description</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l border-gray-200 align-top">
									<div className="max-h-20 sm:max-h-24 overflow-y-auto bg-gray-50 p-1.5 sm:p-2 rounded border border-gray-300 w-full">
										<p className="text-xs sm:text-sm whitespace-pre-wrap break-words">
											{tx.description || <span className="text-gray-400 italic">Not available</span>}
										</p>
									</div>
								</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l-2 border-blue-200 align-top">
									<div className="max-h-20 sm:max-h-24 overflow-y-auto bg-blue-50 p-1.5 sm:p-2 rounded border border-blue-300 w-full">
										<p className="text-xs sm:text-sm whitespace-pre-wrap break-words">
											{tx.gptDescription || <span className="text-gray-400 italic">Not analyzed</span>}
										</p>
									</div>
								</td>
							</tr>

							{/* Explanation */}
							<tr className="hover:bg-gray-50">
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 font-semibold text-gray-700 align-top">💭 Explanation</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l border-gray-200 align-top">
									<div className="max-h-20 sm:max-h-24 overflow-y-auto bg-gray-50 p-1.5 sm:p-2 rounded border border-gray-300 w-full">
										<p className="text-xs sm:text-sm whitespace-pre-wrap break-words">
											{tx.explanation || <span className="text-gray-400 italic">Not available</span>}
										</p>
									</div>
								</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l-2 border-blue-200 align-top">
									<div className="max-h-20 sm:max-h-24 overflow-y-auto bg-blue-50 p-1.5 sm:p-2 rounded border border-blue-300 w-full">
										<p className="text-xs sm:text-sm whitespace-pre-wrap break-words">
											{tx.gptExplanation || <span className="text-gray-400 italic">Not analyzed</span>}
										</p>
									</div>
								</td>
							</tr>

							{/* Currency */}
							<tr className="hover:bg-gray-50">
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 font-semibold text-gray-700 align-top">💱 Currency</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l border-gray-200 align-top">
									{tx.currency ? (
										<span className="inline-flex items-center px-2 sm:px-3 py-0.5 sm:py-1 bg-gray-100 text-gray-800 rounded-md font-semibold text-xs sm:text-sm">
											{tx.currency}
										</span>
									) : (
										<span className="text-gray-400 italic text-xs sm:text-sm">INR (default)</span>
									)}
								</td>
								<td className="px-2 sm:px-3 py-1.5 sm:py-2 border-l-2 border-blue-200 align-top">
									{tx.gptCurrency ? (
										<span className="inline-flex items-center px-2 sm:px-3 py-0.5 sm:py-1 bg-blue-100 text-blue-800 rounded-md font-semibold text-xs sm:text-sm">
											{tx.gptCurrency}
										</span>
									) : (
										<span className="text-gray-400 italic text-xs sm:text-sm">Not analyzed</span>
									)}
								</td>
							</tr>
						</tbody>
					</table>
				</div>
			) : (
				/* Simple Details View - When no GPT data is available */
				<div className="border border-gray-300 rounded-lg p-3 sm:p-4 bg-gray-50">
					<div className="space-y-3">
						{/* Amount */}
						<div>
							<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">💰 Amount</div>
							<div className={`text-lg font-bold ${tx.type === "DEBIT" ? "text-red-600" : "text-green-600"}`}>
								₹{(typeof tx.amount === "number" ? tx.amount : 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
							</div>
						</div>

						{/* Type */}
						<div>
							<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">🔄 Type</div>
							<span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-semibold ${
								tx.type === "DEBIT" ? "bg-red-100 text-red-800" : "bg-green-100 text-green-800"
							}`}>
								{tx.type}
							</span>
						</div>

						{/* Account */}
						<div>
							<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">🏦 Account</div>
							<div className="space-y-0.5">
								<div className="font-medium text-gray-800 text-sm">{tx.account?.name}</div>
								<div className="text-xs text-gray-600">{tx.account?.institution?.description}</div>
								<div className="text-xs text-gray-500">{tx.account?.accountType?.name}</div>
							</div>
						</div>

						{/* Description */}
						<div>
							<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">📝 Description</div>
							<div className="bg-white p-2 rounded border border-gray-300">
								<p className="text-sm whitespace-pre-wrap break-words">
									{tx.description || <span className="text-gray-400 italic">Not available</span>}
								</p>
							</div>
						</div>

						{/* Explanation */}
						<div>
							<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">💭 Explanation</div>
							<div className="bg-white p-2 rounded border border-gray-300">
								<p className="text-sm whitespace-pre-wrap break-words">
									{tx.explanation || <span className="text-gray-400 italic">Not available</span>}
								</p>
							</div>
						</div>

						{/* Currency */}
						<div>
							<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">💱 Currency</div>
							{tx.currency ? (
								<span className="inline-flex items-center px-3 py-1 bg-gray-100 text-gray-800 rounded-md font-semibold text-sm">
									{tx.currency}
								</span>
							) : (
								<span className="text-gray-400 italic text-sm">INR (default)</span>
							)}
						</div>
					</div>
				</div>
			)}

			{/* Info Footer */}
			<div className="mt-2 sm:mt-3 text-center">
				<p className="text-xs text-gray-600">
					{hasGptData 
						? "✨ AI analysis available for this transaction" 
						: "ℹ️ No AI analysis performed"}
				</p>
			</div>
		</div>
	);
}

