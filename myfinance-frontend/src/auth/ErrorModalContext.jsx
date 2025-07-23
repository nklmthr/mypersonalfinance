import React, { createContext, useState, useContext } from 'react';
import { useEffect } from 'react';
import { registerModalTrigger } from './modalHandler';
const ErrorModalContext = createContext();

export const useErrorModal = () => useContext(ErrorModalContext);

export const ErrorModalProvider = ({ children }) => {
	const [isOpen, setIsOpen] = useState(false);
	const [message, setMessage] = useState('');

	const showModal = (msg) => {
		setMessage(msg);
		setIsOpen(true);
	};

	const hideModal = () => {
		setIsOpen(false);
		setMessage('');
	};

	useEffect(() => {
		registerModalTrigger(showModal);
	}, []);

	return (
		<ErrorModalContext.Provider value={{ isOpen, message, showModal, hideModal }}>
			{children}
			{isOpen && (
				<div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
					<div className="bg-white p-6 rounded shadow-md max-w-sm text-center">
						<h2 className="text-lg font-semibold text-red-600 mb-2">Server Error</h2>
						<p className="text-gray-700">{message}</p>
						<button
							onClick={hideModal}
							className="mt-4 bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700"
						>
							Close
						</button>
					</div>
				</div>
			)}
		</ErrorModalContext.Provider>
	);
};
