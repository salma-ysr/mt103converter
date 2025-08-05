//pour résumer les choses dans l'historique
function extraireChamp(texte, tag) {
    const regex = new RegExp(tag + '(.*)', 'i');
    const match = texte.match(regex);
    if (!match) return '---';
    const ligne = match[1].trim().split('\n')[0];
    return tag === ':32A:' && ligne.length >= 9
        ? ligne.slice(6, 9) + ' ' + ligne.slice(9)
        : ligne;
}

//pour bien afficher le xml dans l'historique
function escapeHtml(unsafe) {
    if (!unsafe) return 'Erreurs de validation MT103';
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}


document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('convertForm');
    const resultSection = document.getElementById('resultSection');
    const errorSection = document.getElementById('errorSection');
    const errorContent = document.getElementById('errorContent');
    const xmlResult = document.getElementById('xmlResult');
    const downloadBtn = document.getElementById('downloadBtn');
    const closeErrors = document.getElementById('closeErrors');

    // Gestionnaire pour fermer la zone d'erreur
    closeErrors.addEventListener('click', function() {
        errorSection.style.display = 'none';
    });

    form.addEventListener('submit', function(e) {
        e.preventDefault();

        // Réinitialiser l'affichage
        resultSection.style.display = 'none';
        errorSection.style.display = 'none';
        errorContent.innerHTML = '';
        xmlResult.value = '';

        const mt103 = document.getElementById('mt103').value;

        // Validation côté client basique
        if (!mt103.trim()) {
            showError('Le message MT103 ne peut pas être vide.');
            return;
        }

        fetch('/api/convert', {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain' },
            credentials: 'include',  // Inclure les cookies de session
            body: mt103
        })
        .then(async response => {
            const data = await response.json();

            if (data.success) {
                // Conversion réussie - afficher le XML
                xmlResult.value = data.xmlContent;
                resultSection.style.display = 'block';
                // Faire défiler vers le résultat
                resultSection.scrollIntoView({ behavior: 'smooth' });
            } else {
                // Erreur de conversion - afficher dans la zone d'erreur dédiée
                showError(data.errorMessage);
            }
        })
        .catch(error => {
            showError('Impossible de se connecter au serveur. Veuillez réessayer.');
        });
    });

    function showError(errorMessage) {
        // Traiter les erreurs multiples
        const errors = errorMessage.split('\n').filter(line => line.trim());

        errorContent.innerHTML = errors.map(error => {
            // Nettoyer le message d'erreur (enlever les • au début)
            const cleanError = error.replace(/^[•\s]+/, '').trim();
            return `<div class="error-item">${cleanError}</div>`;
        }).join('');

        errorSection.style.display = 'block';
        // Faire défiler vers la zone d'erreur
        errorSection.scrollIntoView({ behavior: 'smooth' });
    }

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
    // Permettre la redirection naturelle vers /historique
    // Suppression de e.preventDefault() pour permettre la navigation
    window.location.href = '/historique';
});


closeHistory.addEventListener('click', function() {
    historySection.style.display = 'none';
});
const clearHistory = document.getElementById('clearHistory');

clearHistory.addEventListener('click', function() {
    if (confirm("Effacer l'historique?")) {
        fetch('/api/history', {
            method: 'DELETE',
            credentials: 'include'  // Inclure les cookies de session
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

// Fonction pour télécharger les fichiers depuis l'historique
function downloadFileFromHistory(msgId, fileType) {
    const endpoint = `/api/history/${msgId}/download/${fileType}`;

    fetch(endpoint, {
        credentials: 'include'  // Inclure les cookies de session
    })
        .then(response => {
            if (!response.ok) {
                throw new Error(`Erreur ${response.status}: ${response.statusText}`);
            }
            return response.blob();
        })
        .then(blob => {
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;

            // Définir le nom de fichier selon le type
            if (fileType === 'mt103') {
                a.download = `MT103_${msgId}.txt`;
            } else if (fileType === 'xml') {
                a.download = `pacs008_${msgId}.xml`;
            }

            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        })
        .catch(error => {
            console.error('Erreur lors du téléchargement:', error);
            alert(`Erreur lors du téléchargement du fichier ${fileType.toUpperCase()}: ${error.message}`);
        });
}

// Fonction pour déconnexion OAuth2 complète (Spring + Keycloak)
function logout() {
    if (confirm('Êtes-vous sûr de vouloir vous déconnecter ?')) {
        // Déconnexion complète avec Keycloak
        // Spring Security gère automatiquement la déconnexion Keycloak avec RP-Initiated Logout
        window.location.href = '/logout';
    }
}
