(() => {
  const TOKEN_KEY = 'checklist-token';
  const COOKIE_NAME = 'checklist_id_token';

  function parseFragment() {
    const hash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : '';
    return new URLSearchParams(hash);
  }

  function setCookie(name, value, maxAgeSeconds) {
    document.cookie = `${name}=${encodeURIComponent(value)}; path=/; max-age=${maxAgeSeconds}; SameSite=Lax`;
  }

  function storeToken(idToken) {
    localStorage.setItem(TOKEN_KEY, idToken);
    setCookie(COOKIE_NAME, idToken, 3600);
  }

  function captureFromRedirect() {
    const params = parseFragment();
    const idToken = params.get('id_token');
    if (!idToken) return false;
    storeToken(idToken);
    window.history.replaceState({}, document.title, window.location.pathname);
    return true;
  }

  window.ChecklistAuth = {
    captureFromRedirect,
    storeToken,
    cookieName: COOKIE_NAME,
    tokenKey: TOKEN_KEY
  };

  if (window.location.pathname.endsWith('/auth/callback')) {
    if (captureFromRedirect()) {
      window.location.replace('/');
    }
  }
})();
