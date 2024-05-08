const fileInput = document.getElementById('fileInput');

const pdfText = document.getElementById('pdfText');
const annotationsText = document.getElementById('annotationsText');
const errorMessage = document.getElementById('error')

fileInput.addEventListener('change', function(e) {
    var file = e.target.files[0];
    if (!file)
        return;

    process(file)
});

function process(file) {
    const formData = new FormData();
    formData.append("file", file);

    var endpoint = "http://localhost:8080/frontend";
    fetch(endpoint, {
        method: "POST",
        body: formData
    })
    .then(response => {
            if(response.ok) {
                return response.json(); // Assuming the response is JSON
            } else {
                throw new Error('Failed to fetch');
            }
        })
    .then(data => {
        if(data.text) {
            pdfText.innerHTML = data.text;
        } else {
            pdfText.innerHTML = ""; // Clear the container if no text is received
        }

        if(data.annotations) {
            annotationsText.innerHTML = data.annotations;
        } else {
            annotationsText.innerHTML = ""; // Clear the container if no annotations are received
        }

        errorMessage.innerHTML = "";
    })
    .catch(error => {
        errorMessage.innerHTML = "An error occurred: " + error.message;
        errorMessage.style.color = "red";
    });
}