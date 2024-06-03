const sessionEmail = localStorage.getItem('username')

document.addEventListener('DOMContentLoaded', function () {
    getFiles();
})

function getFiles() {
    var endpoint =`http://localhost:8080/submissions/coordinator/${sessionEmail}`;

    fetch(endpoint, {
        method: "GET",
        headers: {
            'Content-Type': 'application/json',
        }
    }).then(response => {
        // Check if the response is successful (status code 200)
        if (response.ok) {
            // Parse the JSON response
            return response.json();
        } else {
            // If the response is not successful, throw an error
            throw new Error('Failed to fetch user');
        }
    })
        .then(submissions => {
            const table = document.getElementById('table-content');

            let index = 0

            submissions.forEach(sub => {
                const line = document.createElement('div')
                const one=  document.createElement('p')
                const two = document.createElement('p')
                const three =document.createElement('p')
                const four = document.createElement('p')

                line.className = 'table-line'
                one.className = 'table-cell'
                const node1 = document.createTextNode(sub.fileName);
                one.appendChild(node1);
                two.className = 'table-cell'
                let node2;
                console.log(sub.lastEdited)
                if(sub.lastEdited == null)
                    node2 = document.createTextNode('Never')
                else
                    node2 = document.createTextNode(sub.lastEdited)
                two.appendChild(node2);
                three.className = 'table-cell'
                const node3 = document.createTextNode('No');
                three.appendChild(node3);
                four.className = 'table-cell'
                const node4 = document.createTextNode('No');
                four.appendChild(node4);

                line.appendChild(one)
                line.appendChild(two)
                line.appendChild(three)
                line.appendChild(four)

                line.addEventListener('click', function() {
                    localStorage.setItem('file', sub.id)
                    window.location.href = "../Home/Home.html"
                })


                table.appendChild(line)
            })

        })
        .catch(error => {
            // Handle any errors that occur during the fetch
            console.log(error)
        });
}