// ---- helpers: tree + flatten ----
export function buildTree(categories) {
	const map = {};
	const roots = [];

	(categories || []).forEach((cat) => {
		map[cat.id] = { ...cat, children: [] };
	});

	(categories || []).forEach((cat) => {
		if (cat.parentId) {
			map[cat.parentId]?.children.push(map[cat.id]);
		} else {
			roots.push(map[cat.id]);
		}
	});

	return roots;
}

export function flattenCategories(categories, prefix = "") {
	let flat = [];
	const sorted = [...(categories || [])].sort((a, b) => {
		const childDiff = (a?.children?.length || 0) - (b?.children?.length || 0);
		if (childDiff !== 0) return childDiff;
		return (a?.name || "").localeCompare(b?.name || "", undefined, { sensitivity: "base" });
	});
	for (const c of sorted) {
		flat.push({ id: c.id, name: prefix + c.name });
		if (c.children?.length > 0) {
			flat = flat.concat(flattenCategories(c.children, prefix + "— "));
		}
	}
	return flat;
}

