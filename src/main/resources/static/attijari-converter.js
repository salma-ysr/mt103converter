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
        errorSection.textContent = '';
        xmlResult.value = '';

        const mt103 = document.getElementById('mt103').value;
        fetch('/api/convert', {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain' },
            body: mt103
        })
        .then(async response => {
            const text = await response.text();
            if (response.ok && !text.includes('<error>')) {
                xmlResult.value = text;
                resultSection.style.display = 'block';
            } else {
                errorSection.innerHTML = '<b>Erreur :</b><br>' + text.replace(/</g, '&lt;').replace(/>/g, '&gt;');
                errorSection.style.display = 'block';
            }
        })
        .catch(() => {
            errorSection.textContent = 'Erreur réseau ou serveur.';
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
