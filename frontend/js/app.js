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
const urlTableBody = document.getElementById('url-table-body');
const loadMoreBtn = document.getElementById('load-more-btn');

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
  resetList();
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
    resetList();
  } catch (err) {
    showError(err.message);
  }
});

copyBtn.addEventListener('click', () => {
  navigator.clipboard.writeText(createResultUrl.textContent);
});

let nextCursor = null;

function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value;
  return div.innerHTML;
}

function truncate(value, max) {
  return value.length > max ? `${value.slice(0, max)}…` : value;
}

function appendRow(item) {
  const tr = document.createElement('tr');
  tr.dataset.id = item.id;
  const shortUrl = `${API_BASE}/${item.shortCode}`;
  tr.innerHTML = `
    <td><a href="${shortUrl}" target="_blank" rel="noopener">${item.shortCode}</a></td>
    <td title="${escapeHtml(item.originalUrl)}">${escapeHtml(truncate(item.originalUrl, 40))}</td>
    <td>${item.status}</td>
    <td>${new Date(item.createdAt).toLocaleString()}</td>
    <td>${item.expiresAt ? new Date(item.expiresAt).toLocaleString() : '-'}</td>
    <td class="actions">
      <button type="button" class="analytics-btn">Analytics</button>
      <button type="button" class="disable-btn">Disable</button>
      <button type="button" class="delete-btn">Delete</button>
    </td>`;
  urlTableBody.appendChild(tr);
}

async function loadUrls(cursor) {
  try {
    const page = await listUrls(20, cursor);
    page.items.forEach(appendRow);
    nextCursor = page.nextCursor;
    loadMoreBtn.classList.toggle('hidden', !nextCursor);
  } catch (err) {
    showError(err.message);
  }
}

function resetList() {
  urlTableBody.innerHTML = '';
  nextCursor = null;
  loadUrls(null);
}

loadMoreBtn.addEventListener('click', () => loadUrls(nextCursor));

urlTableBody.addEventListener('click', async (event) => {
  const row = event.target.closest('tr');
  if (!row) {
    return;
  }
  const id = row.dataset.id;

  if (event.target.classList.contains('disable-btn')) {
    try {
      await disableUrl(id);
      row.children[2].textContent = 'DISABLED';
    } catch (err) {
      showError(err.message);
    }
  } else if (event.target.classList.contains('delete-btn')) {
    if (confirm('Delete this URL?')) {
      try {
        await deleteUrl(id);
        row.remove();
      } catch (err) {
        showError(err.message);
      }
    }
  }
});

const storedToken = getToken();
const storedEmail = getStoredEmail();
if (storedToken && storedEmail) {
  showDashboard(storedEmail);
}
