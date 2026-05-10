// Maps a stored currency code to a display symbol.
// Older transactions have currency=null (default fell back to ₹), newer ones
// store the literal "INR" — this helper makes both render identically and
// lays the groundwork for future multi-currency support (USD/EUR/etc).
const SYMBOLS = {
	INR: "₹",
	USD: "$",
	EUR: "€",
	GBP: "£",
	JPY: "¥",
	AUD: "A$",
	CAD: "C$",
	SGD: "S$",
};

export function getCurrencySymbol(currency) {
	if (!currency) return "₹";
	const code = String(currency).trim().toUpperCase();
	return SYMBOLS[code] || currency;
}

// Returns a Tailwind text-size class scaled by the absolute amount, so a
// glance at the column conveys magnitude (₹25 looks small, ₹10,000 looks big).
export function getAmountSizeClass(amount) {
	const abs = Math.abs(typeof amount === "number" ? amount : parseFloat(amount) || 0);
	if (abs >= 100000) return "text-xl";
	if (abs >= 10000) return "text-lg";
	if (abs >= 1000) return "text-base";
	return "text-sm";
}
