import React from "react";

export default function TransactionDetailsModal({ tx }) {
	return (
		<div className="w-full">
			{/* Header */}
			<div className="bg-gradient-to-r from-gray-50 to-blue-50 p-2 rounded-lg border border-gray-200 mb-2">
				<div className="flex justify-between items-center">
					<div>
						<span className="text-xs text-gray-600 uppercase tracking-wide block">Transaction Date</span>
						<div className="font-semibold text-gray-800 text-sm">
							{new Date(tx.date).toLocaleString('en-IN', { 
								year: 'numeric', 
								month: 'short', 
								day: 'numeric', 
								hour: '2-digit', 
								minute: '2-digit' 
							})}
						</div>
					</div>
				</div>
			</div>

			{/* Transaction Details - Compact Grid Layout */}
			<div className="border border-gray-300 rounded-lg p-3 bg-gray-50">
				{/* Key Information Grid */}
				<div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
					{/* Amount */}
					<div>
						<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">💰 Amount</div>
						<div className={`text-base font-bold ${tx.type === "DEBIT" ? "text-red-600" : "text-green-600"}`}>
							{tx.currency || "₹"}
							{(typeof tx.amount === "number" ? tx.amount : 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
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

					{/* Currency */}
					<div>
						<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">💱 Currency</div>
						{tx.currency ? (
							<span className="inline-flex items-center px-2 py-1 bg-gray-100 text-gray-800 rounded-md font-semibold text-xs">
								{tx.currency}
							</span>
						) : (
							<span className="text-gray-400 italic text-xs">INR (default)</span>
						)}
					</div>

					{/* Account */}
					<div>
						<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">🏦 Account</div>
						<div className="space-y-0.5">
							<div className="font-medium text-gray-800 text-sm">{tx.account?.name}</div>
							{tx.account?.institution?.description && (
								<div className="text-xs text-gray-600 truncate" title={tx.account.institution.description}>
									{tx.account.institution.description}
								</div>
							)}
							{tx.account?.accountType?.name && (
								<div className="text-xs text-gray-500">{tx.account.accountType.name}</div>
							)}
						</div>
					</div>
				</div>

				{/* Category - Full Width */}
				{tx.category && (
					<div className="mb-3">
						<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">📁 Category</div>
						<div className="font-medium text-gray-800 text-sm">{tx.category.name}</div>
						{tx.category.parent?.name && (
							<div className="text-xs text-gray-500">Parent: {tx.category.parent.name}</div>
						)}
					</div>
				)}

				{/* Description - Full Width with proper word wrapping */}
				<div className="mb-3">
					<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">📝 Description</div>
					<div className="bg-white p-2 rounded border border-gray-300 min-w-0">
						<p className="text-sm whitespace-pre-wrap break-words overflow-wrap-anywhere">
							{tx.description || <span className="text-gray-400 italic">Not available</span>}
						</p>
					</div>
				</div>

				{/* Explanation - Full Width with proper word wrapping */}
				{tx.explanation && (
					<div className="mb-3">
						<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">💭 Explanation</div>
						<div className="bg-white p-2 rounded border border-gray-300 min-w-0">
							<p className="text-sm whitespace-pre-wrap break-words overflow-wrap-anywhere">
								{tx.explanation}
							</p>
						</div>
					</div>
				)}

				{/* Additional Info Grid */}
				{(tx.linkedTransferId || tx.parentId || (tx.children && tx.children.length > 0)) && (
					<div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-gray-300">
						{/* Linked Transfer */}
						{tx.linkedTransferId && (
							<div>
								<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">🔗 Linked Transfer</div>
								<div className="text-xs text-gray-700 break-all">
									<span className="font-mono">{tx.linkedTransferId}</span>
								</div>
							</div>
						)}

						{/* Parent Transaction */}
						{tx.parentId && (
							<div>
								<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">📋 Parent</div>
								<div className="text-xs text-gray-700 font-mono break-all">{tx.parentId}</div>
							</div>
						)}

						{/* Child Transactions */}
						{tx.children && tx.children.length > 0 && (
							<div>
								<div className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-1">📋 Split</div>
								<div className="text-xs text-gray-700">
									{tx.children.length} transaction{tx.children.length !== 1 ? 's' : ''}
								</div>
							</div>
						)}
					</div>
				)}
			</div>
		</div>
	);
}

