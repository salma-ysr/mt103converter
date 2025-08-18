// Attijari MT103 Converter - JavaScript principal (version modernisée)
// Fonctions utilitaires pour l'historique
function extraireChamp(texte, tag) {
    const regex = new RegExp(tag + '(.*)', 'i');
    const match = texte.match(regex);
    if (!match) return '---';
    const ligne = match[1].trim().split('\n')[0];
    return tag === ':32A:' && ligne.length >= 9
        ? ligne.slice(6, 9) + ' ' + ligne.slice(9)
        : ligne;
}

function escapeHtml(unsafe) {
    if (!unsafe) return 'Erreurs de validation MT103';
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}

// Variables globales
let selectedFile = null;
let fileContent = null;

// Initialisation de la page de conversion
document.addEventListener('DOMContentLoaded', function() {
    // Vérifier si on est sur la page de conversion
    if (!document.getElementById('fileInput')) {
        return; // Pas sur la page de conversion
    }

    // Éléments DOM
    const fileInput = document.getElementById('fileInput');
    const uploadArea = document.getElementById('uploadArea');
    const fileInfo = document.getElementById('fileInfo');
    const filePreview = document.getElementById('filePreview');
    const closePreview = document.getElementById('closePreview');
    const uploadProgress = document.getElementById('uploadProgress');
    const convertBtn = document.getElementById('convertBtn');
    const loadingOverlay = document.getElementById('loadingOverlay');

    // Si sidebar unifiée présente, ne pas dupliquer les gestionnaires de menu
    if (!window.injectSidebar) {
        const menuToggle = document.getElementById('menuToggle');
        const sidebar = document.getElementById('sidebar');
        if (menuToggle && sidebar) {
            menuToggle.addEventListener('click', function() {
                sidebar.classList.toggle('active');
            });
        }

        // Fermer sidebar sur mobile
        document.addEventListener('click', function(event) {
            if (window.innerWidth <= 768 && sidebar && menuToggle) {
                if (!sidebar.contains(event.target) && !menuToggle.contains(event.target)) {
                    sidebar.classList.remove('active');
                }
            }
        });
    }

    // Gestionnaire pour fermer l'aperçu
    if (closePreview) {
        closePreview.addEventListener('click', function() {
            hideFilePreview();
        });
    }

    // Gestionnaire de changement de fichier
    if (fileInput) {
        fileInput.addEventListener('change', function(e) {
            const file = e.target.files[0];
            if (file) {
                handleFileSelection(file);
            }
        });
    }

    // Gestionnaires drag & drop modernisés
    if (uploadArea) {
        uploadArea.addEventListener('dragover', function(e) {
            e.preventDefault();
            uploadArea.classList.add('dragover');
        });

        uploadArea.addEventListener('dragleave', function(e) {
            e.preventDefault();
            uploadArea.classList.remove('dragover');
        });

        uploadArea.addEventListener('drop', function(e) {
            e.preventDefault();
            uploadArea.classList.remove('dragover');
            const file = e.dataTransfer.files[0];
            if (file) {
                handleFileSelection(file);
            }
        });

        // Clic sur la zone d'upload
        uploadArea.addEventListener('click', function() {
            fileInput.click();
        });
    }

    // Gestionnaire du bouton de conversion
    if (convertBtn) {
        convertBtn.addEventListener('click', function() {
            const activeTab = document.querySelector('.tab-btn.active');
            const tabType = activeTab ? activeTab.getAttribute('data-tab') : 'file';

            if (tabType === 'file' && selectedFile && fileContent) {
                convertFile();
            } else if (tabType === 'text') {
                convertTextContent();
            }
        });
    }

    // Gestionnaire du nouveau bouton de conversion pour l'onglet texte
    const convertBtnText = document.getElementById('convertBtnText');
    if (convertBtnText) {
        convertBtnText.addEventListener('click', function() {
            convertTextContent();
        });
    }

    // Gestion des onglets
    initializeTabs();

    // Gestion des boutons d'action du prévisualisation
    const validateBtn = document.getElementById('validateBtn');
    const editBtn = document.getElementById('editBtn');

    if (validateBtn) {
        validateBtn.addEventListener('click', function() {
            validateMT103Content();
        });
    }

    if (editBtn) {
        editBtn.addEventListener('click', function() {
            switchToTextTab();
        });
    }

    // Gestion du textarea pour l'onglet texte
    const mt103TextInput = document.getElementById('mt103TextInput');
    if (mt103TextInput) {
        mt103TextInput.addEventListener('input', function() {
            updateTextStats();
            updateConvertButton();
        });

        mt103TextInput.addEventListener('paste', function() {
            setTimeout(() => {
                updateTextStats();
                updateConvertButton();
            }, 10);
        });
    }
});

// Fonction pour gérer la sélection de fichier (modernisée)
function handleFileSelection(file) {
    console.log('Handling file selection:', file.name);

    // Vérifications de base
    if (!file) {
        console.error('No file provided');
        return;
    }

    if (file.size > 5 * 1024 * 1024) { // 5MB
        showError('Le fichier est trop volumineux (max: 5MB)');
        return;
    }

    const allowedTypes = ['.txt', '.mt103'];
    const fileExtension = '.' + file.name.split('.').pop().toLowerCase();
    if (!allowedTypes.includes(fileExtension)) {
        showError('Format de fichier non supporté. Utilisez .txt ou .mt103');
        return;
    }

    selectedFile = file;
    console.log('File selected:', selectedFile.name);

    // Afficher la progression
    showUploadProgress();

    // Lire le fichier
    const reader = new FileReader();
    reader.onload = function(e) {
        console.log('File loaded successfully');
        fileContent = e.target.result;
        hideUploadProgress();
        showFilePreview(file, fileContent);
        enableConvertButton();
        showFileInfo();
    };

    reader.onerror = function(error) {
        console.error('Error reading file:', error);
        hideUploadProgress();
        showError('Erreur lors de la lecture du fichier');
    };

    reader.readAsText(file, 'UTF-8');
}

// Afficher les informations de fichier
function showFileInfo() {
    const fileInfo = document.getElementById('fileInfo');
    const convertBtn = document.getElementById('convertBtn');

    if (fileInfo) {
        fileInfo.classList.add('show');
        fileInfo.style.display = 'block';
    }

    // Afficher le bouton de conversion quand les infos du fichier sont affichées
    if (convertBtn) {
        convertBtn.style.display = 'inline-block';
        convertBtn.disabled = false;
        convertBtn.classList.add('btn-appear');

        // Retirer la classe d'animation après l'animation
        setTimeout(() => {
            convertBtn.classList.remove('btn-appear');
        }, 300);
    }
}

// Masquer les informations de fichier
function hideFileInfo() {
    const fileInfo = document.getElementById('fileInfo');
    if (fileInfo) {
        fileInfo.classList.remove('show');
        fileInfo.style.display = 'none';
    }
}

// Afficher la progression de chargement
function showUploadProgress() {
    const uploadProgress = document.getElementById('uploadProgress');
    if (uploadProgress) {
        uploadProgress.style.display = 'flex';
        // Animation de la barre de progression
        const progressFill = uploadProgress.querySelector('.progress-fill');
        if (progressFill) {
            progressFill.style.width = '0%';
            setTimeout(() => {
                progressFill.style.width = '100%';
            }, 100);
        }
    }
}

// Masquer la progression de chargement
function hideUploadProgress() {
    const uploadProgress = document.getElementById('uploadProgress');
    if (uploadProgress) {
        setTimeout(() => {
            uploadProgress.style.display = 'none';
        }, 500);
    }
}

// Afficher l'aperçu du fichier
function showFilePreview(file, content) {
    const filePreview = document.getElementById('filePreview');
    const previewFileName = document.getElementById('previewFileName');
    const previewFileSize = document.getElementById('previewFileSize');
    const previewFileType = document.getElementById('previewFileType');
    const previewFileDate = document.getElementById('previewFileDate');
    const previewContent = document.getElementById('previewContent');
    const previewLines = document.getElementById('previewLines');
    const previewChars = document.getElementById('previewChars');
    const previewFormat = document.getElementById('previewFormat');

    if (!filePreview) return;

    // Mettre à jour les informations du fichier
    if (previewFileName) previewFileName.textContent = file.name;
    if (previewFileSize) previewFileSize.textContent = formatFileSize(file.size);
    if (previewFileType) previewFileType.textContent = file.type || 'text/plain';
    if (previewFileDate) previewFileDate.textContent = new Date(file.lastModified).toLocaleDateString();

    // Mettre à jour le contenu (limité pour l'aperçu)
    if (previewContent) {
        const maxPreviewLength = 1000;
        const displayContent = content.length > maxPreviewLength
            ? content.substring(0, maxPreviewLength) + '\n\n... (contenu tronqué pour l\'aperçu)'
            : content;
        previewContent.textContent = displayContent;
    }

    // Mettre à jour les statistiques
    const lines = content.split('\n').length;
    const chars = content.length;
    const format = detectMT103Format(content);

    if (previewLines) previewLines.textContent = lines;
    if (previewChars) previewChars.textContent = chars;
    if (previewFormat) previewFormat.textContent = format;

    // Afficher la section d'aperçu
    filePreview.style.display = 'block';

    // Mettre à jour le statut
    updateFileStatus(content);
}

// Masquer l'aperçu du fichier
function hideFilePreview() {
    const filePreview = document.getElementById('filePreview');
    if (filePreview) {
        filePreview.style.display = 'none';
    }
}

// Formater la taille du fichier
function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Détecter le format MT103
function detectMT103Format(content) {
    if (content.includes('{1:') && content.includes('{4:')) {
        return 'MT103 Standard';
    } else if (content.includes(':20:') && content.includes(':32A:')) {
        return 'MT103 Simplifié';
    } else {
        return 'Format non reconnu';
    }
}

// Mettre à jour le statut du fichier
function updateFileStatus(content) {
    const statusIndicator = document.querySelector('.status-indicator');
    const statusText = document.querySelector('.status-text');

    if (!statusIndicator || !statusText) return;

    const format = detectMT103Format(content);

    if (format.includes('Standard') || format.includes('Simplifié')) {
        statusIndicator.className = 'status-indicator status-ready';
        statusText.textContent = 'Prêt';
        statusText.style.color = '#4caf50';
    } else {
        statusIndicator.className = 'status-indicator status-warning';
        statusText.textContent = 'Format suspect';
        statusText.style.color = '#ff9800';
    }
}

// Valider le contenu MT103
function validateMT103Content() {
    if (!fileContent) return;

    const format = detectMT103Format(fileContent);
    const hasRequiredFields = fileContent.includes(':20:') &&
                              fileContent.includes(':32A:') &&
                              fileContent.includes(':50K:') &&
                              fileContent.includes(':59:');

    let message = `Format détecté: ${format}\n`;
    message += hasRequiredFields ? '✓ Champs obligatoires présents' : '⚠️ Champs obligatoires manquants';

    alert(message);
}

// Basculer vers l'onglet texte
function switchToTextTab() {
    const textTab = document.querySelector('[data-tab="text"]');
    const mt103TextInput = document.getElementById('mt103TextInput');

    if (textTab) {
        textTab.click();
    }

    if (mt103TextInput && fileContent) {
        mt103TextInput.value = fileContent;
        updateTextStats();
    }
}

// Activer le bouton de conversion
function enableConvertButton() {
    const convertBtn = document.getElementById('convertBtn');
    if (convertBtn) {
        convertBtn.disabled = false;
    }
}

// Afficher une erreur
function showError(message) {
    // Créer une notification d'erreur temporaire
    const errorDiv = document.createElement('div');
    errorDiv.className = 'error-notification';
    errorDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: #f44336;
        color: white;
        padding: 1rem;
        border-radius: 8px;
        z-index: 10000;
        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
    `;
    errorDiv.textContent = message;

    document.body.appendChild(errorDiv);

    setTimeout(() => {
        document.body.removeChild(errorDiv);
    }, 5000);
}

// Gestion des onglets
function initializeTabs() {
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.upload-tab-content');

    tabBtns.forEach(btn => {
        btn.addEventListener('click', function() {
            const tabType = this.getAttribute('data-tab');

            // Mettre à jour les boutons actifs
            tabBtns.forEach(b => b.classList.remove('active'));
            this.classList.add('active');

            // Mettre à jour le contenu actif
            tabContents.forEach(content => {
                content.classList.remove('active');
                if (content.id === tabType + 'Tab') {
                    content.classList.add('active');
                }
            });

            // Réinitialiser le bouton de conversion selon l'onglet
            updateConvertButton();
        });
    });
}

// Mettre à jour l'état du bouton de conversion
function updateConvertButton() {
    const convertBtn = document.getElementById('convertBtn');
    const activeTab = document.querySelector('.tab-btn.active');

    if (!convertBtn || !activeTab) return;

    const tabType = activeTab.getAttribute('data-tab');

    if (tabType === 'file') {
        convertBtn.disabled = !selectedFile || !fileContent;
    } else if (tabType === 'text') {
        const textInput = document.getElementById('mt103TextInput');
        convertBtn.disabled = !textInput || !textInput.value.trim();
    }
}

// Gestion du textarea pour l'onglet texte
document.addEventListener('DOMContentLoaded', function() {
    const mt103TextInput = document.getElementById('mt103TextInput');

    if (mt103TextInput) {
        mt103TextInput.addEventListener('input', function() {
            updateTextStats();
            updateConvertButton();
        });

        mt103TextInput.addEventListener('paste', function() {
            setTimeout(() => {
                updateTextStats();
                updateConvertButton();
            }, 10);
        });
    }
});

// Mettre à jour les statistiques du texte
function updateTextStats() {
    const textInput = document.getElementById('mt103TextInput');
    const charCount = document.getElementById('charCount');
    const lineCount = document.getElementById('lineCount');
    const textValidation = document.getElementById('textValidation');
    const convertBtnText = document.getElementById('convertBtnText'); // Nouveau bouton spécifique

    if (!textInput) return;

    const text = textInput.value;
    const chars = text.length;
    const lines = text.split('\n').length;

    if (charCount) charCount.textContent = chars;
    if (lineCount) lineCount.textContent = lines;

    // Validation et gestion de l'affichage du bouton de conversion
    if (textValidation && convertBtnText) {
        if (text.trim() === '') {
            textValidation.innerHTML = '';
            convertBtnText.style.display = 'none'; // Masquer le bouton si pas de texte
        } else {
            const format = detectMT103Format(text);
            const isValid = format.includes('Standard') || format.includes('Simplifié');

            if (isValid) {
                textValidation.innerHTML = '<span style="color: #4caf50; font-weight: 600;">✓ Format MT103 détecté</span>';

                // Afficher le bouton de conversion avec une animation
                convertBtnText.style.display = 'inline-block';
                convertBtnText.disabled = false;
                convertBtnText.classList.add('btn-appear');

                // Retirer la classe d'animation après l'animation
                setTimeout(() => {
                    convertBtnText.classList.remove('btn-appear');
                }, 300);
            } else {
                textValidation.innerHTML = '<span style="color: #ff9800; font-weight: 600;">⚠️ Format MT103 non reconnu</span>';
                convertBtnText.style.display = 'none'; // Masquer le bouton si format invalide
            }
        }
    }
}

// Fonctions utilitaires pour les boutons du textarea
function clearTextInput() {
    const textInput = document.getElementById('mt103TextInput');
    if (textInput) {
        textInput.value = '';
        updateTextStats();
        updateConvertButton();
    }
}

function pasteFromClipboard() {
    navigator.clipboard.readText().then(text => {
        const textInput = document.getElementById('mt103TextInput');
        if (textInput) {
            textInput.value = text;
            updateTextStats();
            updateConvertButton();
        }
    }).catch(err => {
        showError('Impossible d\'accéder au presse-papiers');
    });
}

// Fonction de conversion de fichier
function convertFile() {
    if (!selectedFile || !fileContent) {
        showError('Aucun fichier sélectionné');
        return;
    }

    const loadingOverlay = document.getElementById('loadingOverlay');
    if (loadingOverlay) {
        loadingOverlay.classList.add('show');
    }

    // Envoi du contenu texte directement (pas de FormData)
    fetch('/convert', {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
        },
        body: fileContent  // Envoyer le contenu du fichier directement
    })
    .then(response => response.json())
    .then(data => {
        if (loadingOverlay) {
            loadingOverlay.classList.remove('show');
        }
        displayResults(data);
    })
    .catch(error => {
        if (loadingOverlay) {
            loadingOverlay.classList.remove('show');
        }
        console.error('Erreur:', error);
        showError('Erreur lors de la conversion: ' + error.message);
    });
}

// Fonction de conversion de texte
function convertTextContent() {
    const textInput = document.getElementById('mt103TextInput');
    if (!textInput || !textInput.value.trim()) {
        showError('Aucun contenu à convertir');
        return;
    }

    const loadingOverlay = document.getElementById('loadingOverlay');
    if (loadingOverlay) {
        loadingOverlay.classList.add('show');
    }

    // Envoi du contenu texte vers le serveur (même endpoint que pour les fichiers)
    fetch('/convert', {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
        },
        body: textInput.value  // Envoyer le contenu texte directement
    })
    .then(response => response.json())
    .then(data => {
        if (loadingOverlay) {
            loadingOverlay.classList.remove('show');
        }
        displayResults(data);
    })
    .catch(error => {
        if (loadingOverlay) {
            loadingOverlay.classList.remove('show');
        }
        console.error('Erreur:', error);
        showError('Erreur lors de la conversion: ' + error.message);
    });
}

// Afficher les résultats de conversion
function displayResults(data) {
    const resultsSection = document.getElementById('resultsSection');
    const resultContent = document.getElementById('resultContent');

    if (!resultsSection || !resultContent) return;

    if (data.success) {
        resultContent.innerHTML = `
            <div class="result-success">
                <div class="result-title">
                    <span>✅</span>
                    Conversion réussie !
                </div>
                <p>Votre fichier MT103 a été converti avec succès au format PACS008.</p>
                <div class="download-section">
                    <button class="attj-btn" onclick="downloadXmlContent()">
                        📁 Télécharger XML
                    </button>
                </div>
            </div>
        `;

        // Stocker le contenu XML pour le téléchargement
        window.lastConvertedXml = data.xmlContent;
    } else {
        resultContent.innerHTML = `
            <div class="result-error">
                <div class="result-title">
                    <span>❌</span>
                    Erreur de conversion
                </div>
                <p>${escapeHtml(data.errorMessage || 'Une erreur inattendue s\'est produite')}</p>
            </div>
        `;
    }

    resultsSection.classList.add('show');
    resultsSection.scrollIntoView({ behavior: 'smooth' });
}

// Fonction de téléchargement
function downloadFile(fileName, type) {
    const link = document.createElement('a');
    link.href = `/download/${fileName}`;
    link.download = fileName;
    link.style.display = 'none';

    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    // Afficher une notification de succès
    showSuccess(`Tél��chargement du fichier ${type.toUpperCase()} démarré`);
}

// Afficher une notification de succès
function showSuccess(message) {
    const successDiv = document.createElement('div');
    successDiv.className = 'success-notification';
    successDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: #4caf50;
        color: white;
        padding: 1rem;
        border-radius: 8px;
        z-index: 10000;
        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
    `;
    successDiv.textContent = message;

    document.body.appendChild(successDiv);

    setTimeout(() => {
        if (document.body.contains(successDiv)) {
            document.body.removeChild(successDiv);
        }
    }, 3000);
}

// Fonction de téléchargement XML
function downloadXmlContent() {
    const xmlContent = window.lastConvertedXml;

    if (!xmlContent) {
        showError('Aucun contenu XML disponible');
        return;
    }

    const blob = new Blob([xmlContent], { type: 'application/xml' });
    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.download = 'converted_file.xml';
    link.style.display = 'none';

    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    URL.revokeObjectURL(url);

    showSuccess('Téléchargement du fichier XML démarré');
}
