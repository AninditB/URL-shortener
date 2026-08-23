const authView = document.getElementById('auth-view');
const dashboardView = document.getElementById('dashboard-view');
const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');
const showLoginBtn = document.getElementById('show-login');
const showRegisterBtn = document.getElementById('show-register');
const userEmailSpan = document.getElementById('user-email');
const logoutBtn = document.getElementById('logout-btn');
const toast = document.getElementById('toast');
const toastMessage = document.getElementById('toast-message');
const toastCloseBtn = document.getElementById('toast-close');
const createForm = document.getElementById('create-form');
const createResult = document.getElementById('create-result');
const createResultUrl = document.getElementById('create-result-url');
const copyBtn = document.getElementById('copy-btn');
const openBtn = document.getElementById('open-btn');

function showError(message) {
  toastMessage.textContent = message;
  toast.classList.remove('hidden');
}

function hideError() {
  toast.classList.add('hidden');
}

function showDashboard(email) {
  authView.classList.add('hidden');
  dashboardView.classList.remove('hidden');
  userEmailSpan.textContent = email;
}

function showAuth() {
  dashboardView.classList.add('hidden');
  authView.classList.remove('hidden');
}

toastCloseBtn.addEventListener('click', hideError);

showLoginBtn.addEventListener('click', () => {
  showLoginBtn.classList.add('active');
  showRegisterBtn.classList.remove('active');
  loginForm.classList.remove('hidden');
  registerForm.classList.add('hidden');
});

showRegisterBtn.addEventListener('click', () => {
  showRegisterBtn.classList.add('active');
  showLoginBtn.classList.remove('active');
  registerForm.classList.remove('hidden');
  loginForm.classList.add('hidden');
});

loginForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  hideError();
  const email = document.getElementById('login-email').value;
  const password = document.getElementById('login-password').value;
  try {
    const { token } = await login(email, password);
    setSession(token, email);
    showDashboard(email);
  } catch (err) {
    showError(err.message);
  }
});

registerForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  hideError();
  const email = document.getElementById('register-email').value;
  const password = document.getElementById('register-password').value;
  try {
    await register(email, password);
    const { token } = await login(email, password);
    setSession(token, email);
    showDashboard(email);
  } catch (err) {
    showError(err.message);
  }
});

logoutBtn.addEventListener('click', () => {
  clearSession();
  showAuth();
});

// Reused across repeated submits of the same form state, so a rapid
// double-click hits the server's idempotency cache instead of creating two
// rows; regenerated only after a successful create.
let createIdempotencyKey = crypto.randomUUID();

createForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  hideError();

  const originalUrl = document.getElementById('create-original-url').value;
  const customAlias = document.getElementById('create-alias').value;
  const expiresInput = document.getElementById('create-expires').value;
  const expiresAt = expiresInput ? new Date(expiresInput).toISOString() : null;

  try {
    const response = await createUrl({ originalUrl, customAlias, expiresAt }, createIdempotencyKey);
    createResultUrl.textContent = response.shortUrl;
    openBtn.href = response.shortUrl;
    createResult.classList.remove('hidden');
    createIdempotencyKey = crypto.randomUUID();
  } catch (err) {
    showError(err.message);
  }
});

copyBtn.addEventListener('click', () => {
  navigator.clipboard.writeText(createResultUrl.textContent);
});

const storedToken = getToken();
const storedEmail = getStoredEmail();
if (storedToken && storedEmail) {
  showDashboard(storedEmail);
}
