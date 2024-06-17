function displaySavedPopUp(textContent) {
    const Toast = Swal.mixin({
      toast: true,
      position: "top-end",
      showConfirmButton: false,
      timer: 3000,
      timerProgressBar: true,
      didOpen: (toast) => {
        toast.onmouseenter = Swal.stopTimer;
        toast.onmouseleave = Swal.resumeTimer;
      }
    });

    Toast.fire({
      icon: "success",
      iconColor: '#588157',
      color: '#000',
      title: textContent,
      customClass: {
        title: 'popup-saved-title'
      }
    });
}

function displayErrorPasswordPopUp (textContent) {
    Swal.fire({
        title: 'ERROR!',
        text: textContent,
        icon: 'error',
        iconColor: '#bd3233',
        color: '#a6a6a6',
        customClass: {
            popup: 'popup-container',
            title: 'popup-title',
            confirmButton: 'popup-confirm-button'
        },
        buttonsStyling: false,
        showConfirmButton: true,
        confirmButtonText: 'OK',
        confirmButtonAriaLabel: 'OK button'
    });
}