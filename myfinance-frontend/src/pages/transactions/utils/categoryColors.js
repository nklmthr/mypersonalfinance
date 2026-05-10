// Stable color assignment for categories.
// Same input key (typically the category UUID, fall back to name) always
// produces the same palette entry, so users can visually identify a
// category by colour. Renaming a category keeps its colour as long as
// the underlying ID does not change.
//
// All Tailwind class names below are written as complete string literals
// so Tailwind's JIT scanner can detect them.

const PALETTE = [
	{ bg: "bg-blue-100",     text: "text-blue-800",     border: "border-blue-400",     dot: "bg-blue-500"     },
	{ bg: "bg-green-100",    text: "text-green-800",    border: "border-green-400",    dot: "bg-green-500"    },
	{ bg: "bg-purple-100",   text: "text-purple-800",   border: "border-purple-400",   dot: "bg-purple-500"   },
	{ bg: "bg-pink-100",     text: "text-pink-800",     border: "border-pink-400",     dot: "bg-pink-500"     },
	{ bg: "bg-yellow-100",   text: "text-yellow-800",   border: "border-yellow-500",   dot: "bg-yellow-500"   },
	{ bg: "bg-indigo-100",   text: "text-indigo-800",   border: "border-indigo-400",   dot: "bg-indigo-500"   },
	{ bg: "bg-red-100",      text: "text-red-800",      border: "border-red-400",      dot: "bg-red-500"      },
	{ bg: "bg-teal-100",     text: "text-teal-800",     border: "border-teal-400",     dot: "bg-teal-500"     },
	{ bg: "bg-orange-100",   text: "text-orange-800",   border: "border-orange-400",   dot: "bg-orange-500"   },
	{ bg: "bg-cyan-100",     text: "text-cyan-800",     border: "border-cyan-400",     dot: "bg-cyan-500"     },
	{ bg: "bg-lime-100",     text: "text-lime-800",     border: "border-lime-500",     dot: "bg-lime-500"     },
	{ bg: "bg-fuchsia-100",  text: "text-fuchsia-800",  border: "border-fuchsia-400",  dot: "bg-fuchsia-500"  },
	{ bg: "bg-emerald-100",  text: "text-emerald-800",  border: "border-emerald-400",  dot: "bg-emerald-500"  },
	{ bg: "bg-amber-100",    text: "text-amber-800",    border: "border-amber-500",    dot: "bg-amber-500"    },
	{ bg: "bg-rose-100",     text: "text-rose-800",     border: "border-rose-400",     dot: "bg-rose-500"     },
	{ bg: "bg-sky-100",      text: "text-sky-800",      border: "border-sky-400",      dot: "bg-sky-500"      },
	{ bg: "bg-violet-100",   text: "text-violet-800",   border: "border-violet-400",   dot: "bg-violet-500"   },
	{ bg: "bg-stone-200",    text: "text-stone-800",    border: "border-stone-400",    dot: "bg-stone-500"    },
];

const FALLBACK = {
	bg: "bg-gray-100",
	text: "text-gray-700",
	border: "border-gray-300",
	dot: "bg-gray-400",
};

// djb2 hash variant — deterministic, well-distributed for short strings.
function hashString(s) {
	let h = 5381;
	for (let i = 0; i < s.length; i++) {
		h = ((h * 33) ^ s.charCodeAt(i)) >>> 0;
	}
	return h;
}

/**
 * Pick a palette entry for a category.
 * @param {string|null|undefined} key Stable identifier (prefer category id, fall back to name).
 * @returns {{bg: string, text: string, border: string, dot: string}}
 */
export function getCategoryColor(key) {
	if (!key) return FALLBACK;
	const idx = hashString(String(key)) % PALETTE.length;
	return PALETTE[idx];
}
