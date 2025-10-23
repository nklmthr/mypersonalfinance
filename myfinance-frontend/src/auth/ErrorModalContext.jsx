import React, { createContext, useState, useContext } from 'react';
import { useEffect } from 'react';
import { registerModalTrigger } from './modalHandler';
const ErrorModalContext = createContext();

export const useErrorModal = () => useContext(ErrorModalContext);

export const ErrorModalProvider = ({ children }) => {
	const [isOpen, setIsOpen] = useState(false);
	const [message, setMessage] = useState('');
	const [modalType, setModalType] = useState('error'); // 'error', 'success', 'info', or 'confirm'
	const [onConfirmCallback, setOnConfirmCallback] = useState(null);
	const [onCancelCallback, setOnCancelCallback] = useState(null);

	const showModal = (msg, onConfirm = null, onCancel = null, type = null) => {
		setMessage(msg);
		// Determine modal type: explicit type, confirm if callbacks, or auto-detect from message
		if (type) {
			setModalType(type);
		} else if (onConfirm) {
			setModalType('confirm');
		} else {
			// Auto-detect: if message contains error/fail keywords, show as error, otherwise info
			const lowerMsg = msg.toLowerCase();
			if (lowerMsg.includes('error') || lowerMsg.includes('fail') || lowerMsg.includes('invalid')) {
				setModalType('error');
			} else if (lowerMsg.includes('success') || lowerMsg.includes('completed') || lowerMsg.includes('refreshed')) {
				setModalType('success');
			} else {
				setModalType('info');
			}
		}
		setOnConfirmCallback(() => onConfirm);
		setOnCancelCallback(() => onCancel);
		setIsOpen(true);
	};

	const hideModal = () => {
		setIsOpen(false);
		setMessage('');
		setModalType('error');
		setOnConfirmCallback(null);
		setOnCancelCallback(null);
	};

	const handleConfirm = () => {
		hideModal();
		if (onConfirmCallback) {
			onConfirmCallback();
		}
	};

	const handleCancel = () => {
		hideModal();
		if (onCancelCallback) {
			onCancelCallback();
		}
	};

	useEffect(() => {
		registerModalTrigger(showModal);
	}, []);

	const getModalConfig = () => {
		switch (modalType) {
			case 'success':
				return { 
					title: 'Success', 
					titleColor: 'text-green-600', 
					buttonColor: 'bg-green-600 hover:bg-green-700' 
				};
			case 'info':
				return { 
					title: 'Information', 
					titleColor: 'text-blue-600', 
					buttonColor: 'bg-blue-600 hover:bg-blue-700' 
				};
			case 'confirm':
				return { 
					title: 'Confirmation', 
					titleColor: 'text-blue-600', 
					buttonColor: 'bg-blue-600 hover:bg-blue-700' 
				};
			case 'error':
			default:
				return { 
					title: 'Error', 
					titleColor: 'text-red-600', 
					buttonColor: 'bg-red-600 hover:bg-red-700' 
				};
		}
	};

	const config = getModalConfig();

	return (
		<ErrorModalContext.Provider value={{ isOpen, message, showModal, hideModal }}>
			{children}
			{isOpen && (
				<div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
					<div className="bg-white p-6 rounded shadow-md max-w-md text-center">
						<h2 className={`text-lg font-semibold mb-2 ${config.titleColor}`}>
							{config.title}
						</h2>
						<p className="text-gray-700 whitespace-pre-line">{message}</p>
						<div className="mt-4 flex justify-center gap-2">
							{modalType === 'confirm' ? (
								<>
									<button
										onClick={handleCancel}
										className="bg-gray-500 text-white px-4 py-2 rounded hover:bg-gray-600"
									>
										Cancel
									</button>
									<button
										onClick={handleConfirm}
										className={`text-white px-4 py-2 rounded ${config.buttonColor}`}
									>
										Continue
									</button>
								</>
							) : (
								<button
									onClick={hideModal}
									className={`text-white px-4 py-2 rounded ${config.buttonColor}`}
								>
									OK
								</button>
							)}
						</div>
					</div>
				</div>
			)}
		</ErrorModalContext.Provider>
	);
};
