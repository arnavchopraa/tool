function handleFormSubmission(event) {
    event.preventDefault();

    const login = document.getElementById('login-username').value;
    const password = document.getElementById('login-password').value;

    var endpoint = 'http://localhost:8080/auth/login';

    fetch(endpoint, {
        method: "POST",
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            username: login,
            password: password
        })
    }).then(response => {
        console.log(response)
        // Check if the response is successful (status code 200)
        if (response.ok) {
            // Parse the JSON response
            localStorage.setItem('username', login);
            window.location.href = "../Dashboard/Dashboard.html";
        } else {
            // If the response is not successful, throw an error
            throw new Error('Invalid login credentials. Please try again.');
        }
    })
    .catch(error => {
        // If there is an error with the request, display an error message
        alert(error.message);
    });
}
