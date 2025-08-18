(function(){
  function buildSidebar(active){
    function is(path){return active===path || window.location.pathname===path;}
    return `\n<div class="sidebar" id="sidebar">\n  <div class="sidebar-header">\n    <img src="/LogoAttijari.png" alt="Attijariwafa Bank" class="sidebar-logo">\n    <h3 class="sidebar-title">MT103 Converter</h3>\n  </div>\n  <nav class="sidebar-nav">\n    <div class="nav-item">\n      <a href="/dashboard" class="nav-link ${is('/dashboard')?'active':''}"><span class="nav-icon">📊</span> Dashboard</a>\n    </div>\n    <div class="nav-item">\n      <a href="/conversion" class="nav-link ${is('/conversion')?'active':''}"><span class="nav-icon">🔄</span> Conversion MT103 → PACS008</a>\n    </div>\n    <div class="nav-item">\n      <a href="/conversion-inverse" class="nav-link ${is('/conversion-inverse')?'active':''}"><span class="nav-icon">↩️</span> Conversion PACS008 → MT103</a>\n    </div>\n    <div class="nav-item">\n      <a href="/historique" class="nav-link ${is('/historique')?'active':''}"><span class="nav-icon">📋</span> Historique</a>\n    </div>\n  </nav>\n  <div class="logout-section">\n    <button class="logout-btn" onclick="window.location.href='/logout'">🚪 Déconnexion</button>\n  </div>\n</div>`;
  }
  window.injectSidebar=function(active){
    var container=document.getElementById('sidebarContainer');
    if(!container){
      container=document.createElement('div');
      container.id='sidebarContainer';
      document.body.prepend(container);
    }
    container.innerHTML=buildSidebar(active);
    // mobile toggle support
    var toggle=document.getElementById('menuToggle');
    if(toggle){
      toggle.addEventListener('click',()=>{
        document.getElementById('sidebar').classList.toggle('active');
      });
      document.addEventListener('click',e=>{
        if(window.innerWidth<=768){
          const sb=document.getElementById('sidebar');
          if(sb && !sb.contains(e.target) && !toggle.contains(e.target)) sb.classList.remove('active');
        }
      });
    }
  };
})();

