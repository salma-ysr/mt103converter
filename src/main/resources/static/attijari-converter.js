document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('convertForm');
    const resultSection = document.getElementById('resultSection');
    const errorSection = document.getElementById('errorSection');
    const xmlResult = document.getElementById('xmlResult');
    const downloadBtn = document.getElementById('downloadBtn');

    form.addEventListener('submit', function(e) {
        e.preventDefault();
        resultSection.style.display = 'none';
        errorSection.style.display = 'none';
        errorSection.innerHTML = '';
        xmlResult.value = '';

        const mt103 = document.getElementById('mt103').value;
        fetch('/api/convert', {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain' },
            body: mt103
        })
        .then(async response => {
            const data = await response.json();

            if (data.success) {
                // Conversion réussie - afficher le XML
                xmlResult.value = data.xmlContent;
                resultSection.style.display = 'block';
            } else {
                // Erreur de conversion - afficher dans la zone d'erreur
                errorSection.innerHTML = `
                    <div class="error-header">
                        <h3>❌ Erreurs de validation MT103</h3>
                    </div>
                    <div class="error-content">
                        ${data.errorMessage.split('\n').map(line => `<div class="error-item">${line}</div>`).join('')}
                    </div>
                `;
                errorSection.style.display = 'block';
            }
        })
        .catch(error => {
            errorSection.innerHTML = `
                <div class="error-header">
                    <h3>❌ Erreur de connexion</h3>
                </div>
                <div class="error-content">
                    <div class="error-item">Impossible de se connecter au serveur. Veuillez réessayer.</div>
                </div>
            `;
            errorSection.style.display = 'block';
        });
    });

    downloadBtn.addEventListener('click', function() {
        const blob = new Blob([xmlResult.value], { type: 'application/xml' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'pacs008.xml';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    });

    const fileInput = document.getElementById('mt103File');
    fileInput.addEventListener('change', function(e) {
        const file = e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = function(evt) {
            document.getElementById('mt103').value = evt.target.result;
        };
        reader.readAsText(file);
    });
});
const historyToggle = document.getElementById('historyToggle');
const closeHistory = document.getElementById('closeHistory');
const historySection = document.getElementById('historySection');
const historyList = document.getElementById('historyList');

historyToggle.addEventListener('click', function(e) {
    e.preventDefault();
    fetch('/api/history')
        .then(res => res.json())
        .then(data => {
            historyList.innerHTML = '';
            if (data.length === 0) {
                historyList.innerHTML = '<li>Historique vide.</li>';
            } else {
                data.reverse().forEach(msg => {
                    const li = document.createElement('li');
                    li.style.marginBottom = '1rem';

                    const date = new Date(msg.createdAt);
                    const options = {
                        weekday: 'short', year: 'numeric', month: 'long', day: 'numeric',
                        hour: '2-digit', minute: '2-digit'
                    };
                    const formattedDate = date.toLocaleString('fr-FR', options);

                    li.innerHTML = `
                            <div style="background:#333; padding:1rem; border-radius:6px; border:1px solid #ff9800;">
                            <div style="color:##ff9800;font-size:0.9rem;margin-bottom:0.5rem;">${formattedDate}</div>
                            <pre style="white-space:pre-wrap;color:#ccc;">${msg.rawContent}</pre>

                            <button class="attj-btn" type="button" style="margin-top:0.5rem;" onclick="reuseMessage(\`${msg.rawContent.replace(/`/g, '\\`')}\`)">Réutiliser</button>
                        </div>
                    `;
                    historyList.appendChild(li);
                });

            }
            historySection.style.display = 'block';
        })
        .catch(() => {
            historyList.innerHTML = '<li>Erreur de chargement de l’historique.</li>';
            historySection.style.display = 'block';
        });
});

window.reuseMessage = function(content) {
    document.getElementById('mt103').value = content;
    window.scrollTo({ top: 0, behavior: 'smooth' });
    historySection.style.display = 'none';
};

closeHistory.addEventListener('click', function() {
    historySection.style.display = 'none';
});
const clearHistory = document.getElementById('clearHistory');

clearHistory.addEventListener('click', function() {
    if (confirm("Effacer l'historique?")) {
        fetch('/api/history', {
            method: 'DELETE'
        })
        .then(res => {
            if (res.ok) {
                historyList.innerHTML = '<li>Historique vidé.</li>';
            } else {
                historyList.innerHTML = '<li>Erreur lors de la suppression.</li>';
            }
        })
        .catch(() => {
            historyList.innerHTML = '<li>Erreur réseau ou serveur.</li>';
        });
    }
});
