import React, { useEffect, useState, useRef } from "react";
import api from "../../../auth/api";
import dayjs from "dayjs";
import { getCurrencySymbol } from "../utils/currency";

const ACCEPTED_TYPES = "image/jpeg,image/png,image/gif,image/webp,application/pdf";
const MAX_SIZE_BYTES = 10 * 1024 * 1024;

const formatBytes = (bytes) => {
	if (bytes === 0) return "0 B";
	if (!bytes) return "";
	const k = 1024;
	const units = ["B", "KB", "MB", "GB"];
	const i = Math.floor(Math.log(bytes) / Math.log(k));
	return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${units[i]}`;
};

export default function TransactionAttachments({ transaction, onClose, onCountChange }) {
	const [attachments, setAttachments] = useState([]);
	const [loading, setLoading] = useState(false);
	const [uploading, setUploading] = useState(false);
	const [error, setError] = useState(null);
	const [thumbUrls, setThumbUrls] = useState({});
	const [preview, setPreview] = useState(null); // { att, url, type }
	const [previewLoading, setPreviewLoading] = useState(false);
	const fileInputRef = useRef(null);

	const fetchAttachments = async () => {
		setLoading(true);
		setError(null);
		try {
			const res = await api.get(`/transactions/${transaction.id}/attachments`);
			const list = Array.isArray(res.data) ? res.data : [];
			setAttachments(list);
			if (typeof onCountChange === "function") {
				onCountChange(transaction.id, list.length);
			}
		} catch (err) {
			console.error("Failed to fetch attachments", err);
			setError(err.response?.data?.error || "Failed to load attachments");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		if (transaction?.id) {
			fetchAttachments();
		}
	}, [transaction?.id]);

	useEffect(() => {
		let cancelled = false;
		const urls = {};
		(async () => {
			for (const att of attachments) {
				if (!att.hasThumbnail || thumbUrls[att.id]) continue;
				try {
					const res = await api.get(`/attachments/${att.id}/thumbnail`, {
						responseType: "blob",
					});
					if (cancelled) return;
					const url = URL.createObjectURL(res.data);
					urls[att.id] = url;
					setThumbUrls((prev) => ({ ...prev, [att.id]: url }));
				} catch (err) {
					console.warn("Failed to load thumbnail for", att.id, err);
				}
			}
		})();
		return () => {
			cancelled = true;
			Object.values(urls).forEach((u) => URL.revokeObjectURL(u));
		};
	}, [attachments]);

	useEffect(() => {
		return () => {
			Object.values(thumbUrls).forEach((u) => URL.revokeObjectURL(u));
		};
	}, []);

	useEffect(() => {
		if (!preview) return undefined;
		const handler = (e) => {
			if (e.key === "Escape") {
				closePreview();
			}
		};
		document.addEventListener("keydown", handler);
		return () => {
			document.removeEventListener("keydown", handler);
		};
	}, [preview]);

	useEffect(() => {
		return () => {
			if (preview?.url) {
				URL.revokeObjectURL(preview.url);
			}
		};
	}, [preview]);

	const extensionFor = (mimeType) => {
		if (!mimeType) return "bin";
		if (mimeType === "image/jpeg") return "jpg";
		if (mimeType === "image/png") return "png";
		if (mimeType === "image/gif") return "gif";
		if (mimeType === "image/webp") return "webp";
		if (mimeType === "image/bmp") return "bmp";
		if (mimeType === "application/pdf") return "pdf";
		return mimeType.split("/")[1] || "bin";
	};

	// Re-encode an image blob as a (smaller) JPEG via a canvas. Used to shrink
	// pasted/dropped images that browsers re-encode as PNG when copied from a
	// website (a 600 KB JPEG photo typically expands to ~10 MB PNG on copy).
	// Returns the original blob unchanged if anything goes wrong.
	const recompressImage = (blob, { maxDim = 3200, quality = 0.85 } = {}) =>
		new Promise((resolve) => {
			if (!blob || !blob.type?.startsWith("image/") || blob.type === "image/gif") {
				resolve(blob);
				return;
			}
			const url = URL.createObjectURL(blob);
			const img = new Image();
			img.onload = () => {
				try {
					let { width, height } = img;
					const longEdge = Math.max(width, height);
					if (longEdge > maxDim) {
						const scale = maxDim / longEdge;
						width = Math.round(width * scale);
						height = Math.round(height * scale);
					}
					const canvas = document.createElement("canvas");
					canvas.width = width;
					canvas.height = height;
					const ctx = canvas.getContext("2d");
					ctx.drawImage(img, 0, 0, width, height);
					canvas.toBlob(
						(out) => {
							URL.revokeObjectURL(url);
							resolve(out || blob);
						},
						"image/jpeg",
						quality
					);
				} catch (err) {
					URL.revokeObjectURL(url);
					console.warn("Image recompress failed, using original", err);
					resolve(blob);
				}
			};
			img.onerror = () => {
				URL.revokeObjectURL(url);
				console.warn("Image recompress: failed to load source");
				resolve(blob);
			};
			img.src = url;
		});

	// Build a File suitable for upload. If the source is a large image (typically
	// produced by paste-from-webpage where browsers hand us a bloated PNG bitmap),
	// re-encode it as JPEG to fit within the upload limit while staying high quality.
	const prepareForUpload = async (sourceBlob, baseName) => {
		if (!sourceBlob) return null;
		const isImage = sourceBlob.type?.startsWith("image/");
		const tooBigForLossless = isImage && sourceBlob.size > 1.5 * 1024 * 1024;
		if (tooBigForLossless) {
			const jpeg = await recompressImage(sourceBlob);
			if (jpeg && jpeg !== sourceBlob && jpeg.size < sourceBlob.size) {
				const name = (baseName || "image").replace(/\.[^.]+$/, "") + ".jpg";
				return new File([jpeg], name, { type: "image/jpeg" });
			}
		}
		// Otherwise wrap the original blob as a File (preserving name & type).
		const ext = extensionFor(sourceBlob.type);
		const name = baseName || `upload.${ext}`;
		return new File([sourceBlob], name, { type: sourceBlob.type });
	};

	const uploadFile = async (file) => {
		if (!file) return;
		setError(null);
		const isAcceptedType =
			file.type?.startsWith("image/") || file.type === "application/pdf";
		if (!isAcceptedType) {
			setError("Only image files (JPEG/PNG/GIF/WEBP) and PDFs are allowed.");
			if (fileInputRef.current) fileInputRef.current.value = "";
			return;
		}
		if (file.size > MAX_SIZE_BYTES) {
			setError(`File too large. Max size is ${formatBytes(MAX_SIZE_BYTES)}.`);
			if (fileInputRef.current) fileInputRef.current.value = "";
			return;
		}

		const formData = new FormData();
		formData.append("file", file, file.name);

		setUploading(true);
		try {
			await api.post(
				`/transactions/${transaction.id}/attachments`,
				formData,
				{ headers: { "Content-Type": "multipart/form-data" } }
			);
			await fetchAttachments();
		} catch (err) {
			console.error("Upload failed", err);
			setError(err.response?.data?.error || "Failed to upload attachment");
		} finally {
			setUploading(false);
			if (fileInputRef.current) fileInputRef.current.value = "";
		}
	};

	const handleFileSelected = async (e) => {
		const file = e.target.files?.[0];
		if (!file) return;
		const prepared = await prepareForUpload(file, file.name);
		await uploadFile(prepared);
	};

	// Listen for paste events while the modal is open. If the clipboard contains
	// an image (e.g. screenshot copied to clipboard via Cmd+Shift+Ctrl+4 on macOS
	// or Print Screen on Windows, or an image copied from a webpage), upload it
	// directly so the user doesn't have to save the image to disk first.
	useEffect(() => {
		const handlePaste = async (e) => {
			if (uploading) return;
			const items = e.clipboardData?.items;
			if (!items || items.length === 0) return;

			for (const item of items) {
				if (item.kind === "file") {
					const blob = item.getAsFile();
					if (blob) {
						e.preventDefault();
						const ts = new Date().toISOString().replace(/[:.]/g, "-");
						const ext = extensionFor(blob.type);
						const baseName = blob.name && blob.name !== "image.png"
							? blob.name
							: `pasted-${ts}.${ext}`;
						const prepared = await prepareForUpload(blob, baseName);
						await uploadFile(prepared);
						return;
					}
				}
			}
		};

		document.addEventListener("paste", handlePaste);
		return () => document.removeEventListener("paste", handlePaste);
		// eslint-disable-next-line
	}, [uploading, transaction?.id]);

	// Drag-and-drop into the dialog (bonus convenience).
	const [dragActive, setDragActive] = useState(false);
	const handleDragOver = (e) => {
		e.preventDefault();
		e.stopPropagation();
		if (!dragActive) setDragActive(true);
	};
	const handleDragLeave = (e) => {
		e.preventDefault();
		e.stopPropagation();
		setDragActive(false);
	};
	const handleDrop = async (e) => {
		e.preventDefault();
		e.stopPropagation();
		setDragActive(false);
		const file = e.dataTransfer?.files?.[0];
		if (file) {
			const prepared = await prepareForUpload(file, file.name);
			await uploadFile(prepared);
		}
	};

	const inferContentType = (att, blobType) => {
		const explicit = att.contentType || blobType || "";
		if (explicit && explicit !== "application/octet-stream") return explicit;
		const name = (att.fileName || "").toLowerCase();
		if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
		if (name.endsWith(".png")) return "image/png";
		if (name.endsWith(".gif")) return "image/gif";
		if (name.endsWith(".webp")) return "image/webp";
		if (name.endsWith(".bmp")) return "image/bmp";
		if (name.endsWith(".pdf")) return "application/pdf";
		return "application/octet-stream";
	};

	const openPreview = async (att) => {
		setError(null);
		setPreviewLoading(true);
		try {
			const res = await api.get(`/attachments/${att.id}`, { responseType: "blob" });
			const type = inferContentType(att, res.data.type);
			const blob = new Blob([res.data], { type });
			const url = URL.createObjectURL(blob);
			setPreview({ att, url, type });
		} catch (err) {
			console.error("Failed to load attachment", err);
			setError("Failed to load attachment for preview");
		} finally {
			setPreviewLoading(false);
		}
	};

	const closePreview = () => {
		if (preview?.url) {
			URL.revokeObjectURL(preview.url);
		}
		setPreview(null);
	};

	const openPreviewInNewTab = () => {
		if (!preview) return;
		const a = document.createElement("a");
		a.href = preview.url;
		a.target = "_blank";
		a.rel = "noopener noreferrer";
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
	};

	const downloadFile = async (att) => {
		try {
			const res = await api.get(`/attachments/${att.id}?download=true`, { responseType: "blob" });
			const blob = new Blob([res.data], {
				type: att.contentType || res.data.type || "application/octet-stream",
			});
			const url = URL.createObjectURL(blob);
			const a = document.createElement("a");
			a.href = url;
			a.download = att.fileName || "attachment";
			document.body.appendChild(a);
			a.click();
			document.body.removeChild(a);
			setTimeout(() => URL.revokeObjectURL(url), 60_000);
		} catch (err) {
			console.error("Failed to download attachment", err);
			setError("Failed to download attachment");
		}
	};

	const deleteAttachment = async (att) => {
		if (!window.confirm(`Delete attachment "${att.fileName}"? This cannot be undone.`)) {
			return;
		}
		try {
			await api.delete(`/attachments/${att.id}`);
			if (thumbUrls[att.id]) {
				URL.revokeObjectURL(thumbUrls[att.id]);
				setThumbUrls((prev) => {
					const next = { ...prev };
					delete next[att.id];
					return next;
				});
			}
			await fetchAttachments();
		} catch (err) {
			console.error("Failed to delete attachment", err);
			setError(err.response?.data?.error || "Failed to delete attachment");
		}
	};

	const renderThumbnail = (att) => {
		const url = thumbUrls[att.id];
		if (url) {
			return (
				<img
					src={url}
					alt={att.fileName}
					className="w-full h-full object-cover"
				/>
			);
		}
		const isPdf = att.contentType === "application/pdf";
		return (
			<div className="w-full h-full flex items-center justify-center bg-gray-100 text-gray-500 text-xs">
				{isPdf ? "PDF" : (att.contentType || "FILE").split("/")[0].toUpperCase()}
			</div>
		);
	};

	return (
		<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-2 sm:p-4 overflow-y-auto">
			<div className="bg-white rounded-lg shadow-xl w-full max-w-[95vw] sm:max-w-2xl my-2 sm:my-4">
				<div className="flex justify-between items-start p-4 border-b border-gray-200">
					<div className="min-w-0 pr-2">
						<h2 className="text-base sm:text-lg font-semibold text-gray-800">
							Attachments
						</h2>
						<p className="text-xs sm:text-sm text-gray-600 mt-1 truncate">
							<strong>Transaction:</strong>{" "}
							{transaction.shortDescription || transaction.description}
						</p>
						<p className="text-xs text-gray-500">
							{getCurrencySymbol(transaction.currency)}
							{(typeof transaction.amount === "number" ? transaction.amount : 0).toLocaleString("en-IN", {
								minimumFractionDigits: 2,
							})}
							{" • "}
							{transaction.date ? dayjs(transaction.date).format("DD MMM YYYY") : ""}
						</p>
					</div>
					<button
						onClick={onClose}
						className="text-gray-500 hover:text-gray-700 text-2xl leading-none"
						title="Close"
					>
						×
					</button>
				</div>

				<div className="p-4">
					<div
						className={`mb-4 p-3 border border-dashed rounded transition-colors ${
							dragActive
								? "border-blue-500 bg-blue-100"
								: "border-blue-300 bg-blue-50"
						}`}
						onDragOver={handleDragOver}
						onDragEnter={handleDragOver}
						onDragLeave={handleDragLeave}
						onDrop={handleDrop}
					>
						<label className="block text-sm font-medium text-gray-700 mb-2">
							Upload receipt (image or PDF, max 10 MB)
						</label>
						<input
							ref={fileInputRef}
							type="file"
							accept={ACCEPTED_TYPES}
							onChange={handleFileSelected}
							disabled={uploading}
							className="block w-full text-sm text-gray-700 file:mr-3 file:py-1.5 file:px-3 file:rounded file:border-0 file:text-sm file:font-medium file:bg-blue-600 file:text-white hover:file:bg-blue-700 disabled:opacity-50"
						/>
						<p className="text-xs text-gray-600 mt-2">
							Tip: you can also <strong>paste</strong> an image (⌘V / Ctrl+V) or <strong>drag &amp; drop</strong> a file here.
						</p>
						{uploading && (
							<p className="text-xs text-blue-700 mt-2 flex items-center gap-2">
								<span className="inline-block w-3 h-3 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></span>
								Uploading...
							</p>
						)}
					</div>

					{error && (
						<div className="mb-3 px-3 py-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded">
							{error}
						</div>
					)}

					<div className="max-h-[55vh] overflow-y-auto">
						{loading ? (
							<div className="text-sm text-gray-500 text-center py-6">Loading attachments...</div>
						) : attachments.length === 0 ? (
							<div className="text-sm text-gray-500 italic text-center py-6">
								No attachments yet. Upload a receipt above.
							</div>
						) : (
							<ul className="space-y-2">
								{attachments.map((att) => (
									<li
										key={att.id}
										className="flex items-center gap-3 border border-gray-200 rounded p-2 hover:bg-gray-50"
									>
										<button
											type="button"
											onClick={() => openPreview(att)}
											className="flex-shrink-0 w-16 h-16 border border-gray-200 rounded overflow-hidden focus:outline-none focus:ring-2 focus:ring-blue-400"
											title="Click to preview"
										>
											{renderThumbnail(att)}
										</button>
										<div className="flex-1 min-w-0">
											<button
												type="button"
												onClick={() => openPreview(att)}
												className="text-sm font-medium text-blue-700 hover:underline truncate block w-full text-left"
												title={att.fileName}
											>
												{att.fileName}
											</button>
											<div className="text-xs text-gray-500 mt-0.5">
												{att.contentType} • {formatBytes(att.size)}
												{att.date && ` • ${dayjs(att.date).format("DD MMM YYYY h:mm A")}`}
											</div>
										</div>
										<div className="flex flex-col sm:flex-row gap-1 sm:gap-2 text-xs">
											<button
												onClick={() => openPreview(att)}
												className="px-2 py-1 bg-blue-100 text-blue-700 rounded hover:bg-blue-200"
												title="Preview in modal"
											>
												View
											</button>
											<button
												onClick={() => downloadFile(att)}
												className="px-2 py-1 bg-gray-100 text-gray-700 rounded hover:bg-gray-200"
												title="Download file"
											>
												Download
											</button>
											<button
												onClick={() => deleteAttachment(att)}
												className="px-2 py-1 bg-red-100 text-red-700 rounded hover:bg-red-200"
												title="Delete attachment"
											>
												Delete
											</button>
										</div>
									</li>
								))}
							</ul>
						)}
					</div>
				</div>

				<div className="flex justify-end p-3 border-t border-gray-200">
					<button
						onClick={onClose}
						className="text-blue-600 hover:underline text-sm px-3 py-1.5 bg-blue-50 rounded hover:bg-blue-100 transition-colors"
					>
						Close
					</button>
				</div>
			</div>

			{previewLoading && (
				<div className="fixed inset-0 z-[60] bg-black bg-opacity-30 flex items-center justify-center pointer-events-none">
					<div className="bg-white px-4 py-2 rounded shadow text-sm text-gray-700">Loading preview…</div>
				</div>
			)}

			{preview && (
				<div
					className="fixed inset-0 z-[70] bg-black bg-opacity-80 flex items-center justify-center p-2 sm:p-4"
					onClick={closePreview}
				>
					<div
						className="bg-white rounded-lg shadow-2xl w-full max-w-[95vw] max-h-[95vh] flex flex-col"
						onClick={(e) => e.stopPropagation()}
					>
						<div className="flex items-center justify-between p-3 border-b border-gray-200 gap-2">
							<div className="min-w-0 pr-2">
								<div className="text-sm font-semibold text-gray-800 truncate" title={preview.att.fileName}>
									{preview.att.fileName}
								</div>
								<div className="text-xs text-gray-500">
									{preview.type} • {formatBytes(preview.att.size)}
								</div>
							</div>
							<div className="flex items-center gap-2">
								<button
									onClick={openPreviewInNewTab}
									className="text-xs px-2 py-1 bg-blue-100 text-blue-700 rounded hover:bg-blue-200"
									title="Open in a new browser tab"
								>
									Open in new tab
								</button>
								<button
									onClick={() => downloadFile(preview.att)}
									className="text-xs px-2 py-1 bg-gray-100 text-gray-700 rounded hover:bg-gray-200"
									title="Download original file"
								>
									Download
								</button>
								<button
									onClick={closePreview}
									className="text-gray-500 hover:text-gray-700 text-2xl leading-none px-2"
									title="Close preview"
								>
									×
								</button>
							</div>
						</div>

						<div className="flex-1 overflow-auto bg-gray-100 flex items-center justify-center min-h-[60vh]">
							{preview.type.startsWith("image/") ? (
								<img
									src={preview.url}
									alt={preview.att.fileName}
									className="max-w-full max-h-[85vh] object-contain"
								/>
							) : preview.type === "application/pdf" ? (
								<iframe
									title={preview.att.fileName}
									src={preview.url}
									className="w-full h-[85vh] bg-white"
								/>
							) : (
								<div className="p-6 text-center text-sm text-gray-700">
									<p className="mb-3">Preview not available for this file type ({preview.type}).</p>
									<button
										onClick={() => downloadFile(preview.att)}
										className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
									>
										Download
									</button>
								</div>
							)}
						</div>
					</div>
				</div>
			)}
		</div>
	);
}
