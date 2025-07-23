let modalTrigger;

export const registerModalTrigger = (fn) => {
  modalTrigger = fn;
};

export const showModal = (msg) => {
  if (modalTrigger) {
    modalTrigger(msg);
  } else {
    console.warn('Error modal not initialized yet');
  }
};
