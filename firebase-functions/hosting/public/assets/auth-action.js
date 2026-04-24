import { initializeApp } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-app.js";
import {
  getAuth,
  verifyPasswordResetCode,
  confirmPasswordReset,
} from "https://www.gstatic.com/firebasejs/11.6.1/firebase-auth.js";

const firebaseConfig = {
  apiKey: "AIzaSyAJYErqRpCvwCBoLwpiqUO9SV-bPPn3Ob0",
  authDomain: "asociacion-ciguena-188da.firebaseapp.com",
  projectId: "asociacion-ciguena-188da",
  storageBucket: "asociacion-ciguena-188da.firebasestorage.app",
};

const params = new URLSearchParams(window.location.search);
const actionCode = params.get("oobCode");
const mode = params.get("mode");

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);

const title = document.getElementById("title");
const subtitle = document.getElementById("subtitle");
const banner = document.getElementById("banner");
const loadingState = document.getElementById("loadingState");
const resetForm = document.getElementById("resetForm");
const successState = document.getElementById("successState");
const errorState = document.getElementById("errorState");
const errorCopy = document.getElementById("errorCopy");
const accountEmail = document.getElementById("accountEmail");
const passwordInput = document.getElementById("password");
const confirmPasswordInput = document.getElementById("confirmPassword");
const formError = document.getElementById("formError");
const submitButton = document.getElementById("submitButton");
const requirementsList = document.getElementById("requirementsList");
const togglePasswordButton = document.getElementById("togglePassword");
const toggleConfirmPasswordButton = document.getElementById("toggleConfirmPassword");

function setHidden(element, hidden) {
  element.classList.toggle("hidden", hidden);
}

function showBanner(type, message) {
  banner.textContent = message;
  banner.className = `banner ${type}`;
}

function clearBanner() {
  banner.textContent = "";
  banner.className = "banner hidden";
}

function showForm(email) {
  title.textContent = "Crear tu contraseña";
  subtitle.textContent = "Elige una contraseña segura para acceder a tu cuenta.";
  accountEmail.textContent = email;
  setHidden(loadingState, true);
  setHidden(errorState, true);
  setHidden(successState, true);
  setHidden(resetForm, false);
}

function showExpired(message) {
  title.textContent = "Enlace no disponible";
  subtitle.textContent = "Necesitas solicitar un nuevo correo para continuar.";
  errorCopy.textContent = message;
  setHidden(loadingState, true);
  setHidden(resetForm, true);
  setHidden(successState, true);
  setHidden(errorState, false);
}

function showSuccess() {
  title.textContent = "Acceso configurado";
  subtitle.textContent = "La contraseña se ha guardado correctamente.";
  showBanner("success", "Contraseña actualizada correctamente.");
  setHidden(loadingState, true);
  setHidden(resetForm, true);
  setHidden(errorState, true);
  setHidden(successState, false);
}

function showFieldError(message) {
  formError.textContent = message;
  setHidden(formError, false);
}

function clearFieldError() {
  formError.textContent = "";
  setHidden(formError, true);
}

function getPasswordRules(password) {
  return {
    length: password.length >= 10,
    upper: /[A-ZÁÉÍÓÚÜÑ]/.test(password),
    lower: /[a-záéíóúüñ]/.test(password),
    number: /\d/.test(password),
  };
}

function updateRequirementIndicators() {
  const rules = getPasswordRules(passwordInput.value);
  requirementsList.querySelectorAll("li").forEach((item) => {
    const rule = item.dataset.rule;
    item.classList.toggle("valid", Boolean(rules[rule]));
  });
}

function validatePassword() {
  const password = passwordInput.value;
  const confirmation = confirmPasswordInput.value;
  const rules = getPasswordRules(password);

  if (Object.values(rules).some((rule) => !rule)) {
    return "La contraseña debe cumplir todos los requisitos indicados.";
  }

  if (password !== confirmation) {
    return "Las contraseñas no coinciden.";
  }

  return "";
}

function togglePasswordVisibility(button, input) {
  const visible = input.type === "text";
  input.type = visible ? "password" : "text";
  button.textContent = visible ? "Mostrar" : "Ocultar";
}

async function bootstrap() {
  if (mode !== "resetPassword" || !actionCode) {
    showExpired("El enlace recibido no corresponde a una acción de restablecimiento de contraseña válida.");
    return;
  }

  try {
    const email = await verifyPasswordResetCode(auth, actionCode);
    clearBanner();
    showForm(email);
    passwordInput.focus();
  } catch (error) {
    console.error("Error validando el código de acción:", error);
    showExpired("El enlace es inválido o ha caducado. Solicita uno nuevo al equipo administrador.");
  }
}

passwordInput.addEventListener("input", () => {
  updateRequirementIndicators();
  clearFieldError();
});

confirmPasswordInput.addEventListener("input", clearFieldError);
togglePasswordButton.addEventListener("click", () => togglePasswordVisibility(togglePasswordButton, passwordInput));
toggleConfirmPasswordButton.addEventListener("click", () => togglePasswordVisibility(toggleConfirmPasswordButton, confirmPasswordInput));

resetForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearFieldError();
  clearBanner();
  updateRequirementIndicators();

  const validationMessage = validatePassword();
  if (validationMessage) {
    showFieldError(validationMessage);
    return;
  }

  submitButton.disabled = true;
  submitButton.textContent = "Guardando…";

  try {
    await confirmPasswordReset(auth, actionCode, passwordInput.value);
    showSuccess();
  } catch (error) {
    console.error("Error confirmando el cambio de contraseña:", error);
    showBanner("error", "No se ha podido guardar la contraseña. Es posible que el enlace haya caducado.");
    showFieldError("No se pudo completar la operación. Solicita un nuevo enlace e inténtalo de nuevo.");
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "Guardar contraseña";
  }
});

updateRequirementIndicators();
bootstrap();
