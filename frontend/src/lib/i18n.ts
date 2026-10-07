/**
 * The words the app is shown in (V68).
 *
 * <p>Keyed by the English text itself, so English needs no dictionary and anything not yet translated
 * simply stays English — a missing entry can never show a key like "nav.payroll" to a customer.
 * `{name}` placeholders are filled from the values passed to `t`.
 *
 * <p>Must offer the same languages as the backend's auth/Preferences.LANGUAGES.
 */

export const LANGUAGES = [
  { code: "en", label: "English" },
  { code: "hi", label: "हिन्दी (Hindi)" },
  { code: "es", label: "Español (Spanish)" },
  { code: "fr", label: "Français (French)" },
  { code: "de", label: "Deutsch (German)" },
] as const;

export type Language = (typeof LANGUAGES)[number]["code"];
export const DEFAULT_LANGUAGE: Language = "en";

type Dictionary = Record<string, string>;

// Columns: hi, es, fr, de. One row per English phrase keeps the four in step.
const ROWS: [string, string, string, string, string][] = [
  // ---- navigation
  ["Platform", "प्लेटफ़ॉर्म", "Plataforma", "Plateforme", "Plattform"],
  ["Companies", "कंपनियाँ", "Empresas", "Entreprises", "Unternehmen"],
  ["Requests", "अनुरोध", "Solicitudes", "Demandes", "Anfragen"],
  ["Agencies", "एजेंसियाँ", "Agencias", "Agences", "Agenturen"],
  ["Pricing", "मूल्य", "Precios", "Tarifs", "Preise"],
  ["Plans & features", "प्लान और सुविधाएँ", "Planes y funciones", "Offres et fonctionnalités", "Pläne & Funktionen"],
  ["My companies", "मेरी कंपनियाँ", "Mis empresas", "Mes entreprises", "Meine Unternehmen"],
  ["Dashboard", "डैशबोर्ड", "Panel", "Tableau de bord", "Dashboard"],
  ["Announcements", "घोषणाएँ", "Anuncios", "Annonces", "Ankündigungen"],
  ["Company documents", "कंपनी दस्तावेज़", "Documentos de la empresa", "Documents de l'entreprise", "Unternehmensdokumente"],
  ["Insights", "इनसाइट्स", "Análisis", "Analyses", "Auswertungen"],
  ["Me", "मैं", "Yo", "Moi", "Ich"],
  ["Overview", "सारांश", "Resumen", "Vue d'ensemble", "Übersicht"],
  ["Attendance", "उपस्थिति", "Asistencia", "Présence", "Anwesenheit"],
  ["Time off", "छुट्टी", "Ausencias", "Congés", "Abwesenheit"],
  ["My team", "मेरी टीम", "Mi equipo", "Mon équipe", "Mein Team"],
  ["Expenses", "खर्च", "Gastos", "Notes de frais", "Spesen"],
  ["Performance", "प्रदर्शन", "Desempeño", "Performance", "Leistung"],
  ["Org chart", "संगठन चार्ट", "Organigrama", "Organigramme", "Organigramm"],
  ["Finance", "वित्त", "Finanzas", "Finances", "Finanzen"],
  ["My pay", "मेरा वेतन", "Mi nómina", "Ma paie", "Mein Gehalt"],
  ["My finances", "मेरा वित्त", "Mis finanzas", "Mes finances", "Meine Finanzen"],
  ["Tax declaration", "कर घोषणा", "Declaración de impuestos", "Déclaration fiscale", "Steuererklärung"],
  ["Manage tax", "कर प्रबंधन", "Gestionar impuestos", "Gérer la fiscalité", "Steuern verwalten"],
  ["My goals", "मेरे लक्ष्य", "Mis objetivos", "Mes objectifs", "Meine Ziele"],
  ["My review", "मेरी समीक्षा", "Mi evaluación", "Mon évaluation", "Meine Beurteilung"],
  ["Review cycles", "समीक्षा चक्र", "Ciclos de evaluación", "Cycles d'évaluation", "Beurteilungszyklen"],
  ["Inbox", "इनबॉक्स", "Bandeja de entrada", "Boîte de réception", "Posteingang"],
  ["Helpdesk", "हेल्पडेस्क", "Soporte", "Assistance", "Helpdesk"],
  ["Regularizations", "नियमितीकरण", "Regularizaciones", "Régularisations", "Korrekturen"],
  ["Leave approvals", "छुट्टी स्वीकृतियाँ", "Aprobación de ausencias", "Validation des congés", "Urlaubsfreigaben"],
  ["Exits", "निकास", "Salidas", "Départs", "Austritte"],
  ["People", "कर्मचारी", "Personas", "Personnel", "Mitarbeitende"],
  ["Directory", "निर्देशिका", "Directorio", "Annuaire", "Verzeichnis"],
  ["Designations", "पदनाम", "Cargos", "Postes", "Positionen"],
  ["Leave policy", "छुट्टी नीति", "Política de ausencias", "Politique de congés", "Urlaubsrichtlinie"],
  ["Holidays", "अवकाश", "Festivos", "Jours fériés", "Feiertage"],
  ["Recruitment", "भर्ती", "Reclutamiento", "Recrutement", "Recruiting"],
  ["Shifts", "शिफ़्ट", "Turnos", "Plannings", "Schichten"],
  ["Payroll", "पेरोल", "Nómina", "Paie", "Lohnabrechnung"],
  ["Salaries", "वेतन", "Salarios", "Salaires", "Gehälter"],
  ["Payroll run", "पेरोल रन", "Ejecutar nómina", "Lancer la paie", "Abrechnungslauf"],
  ["Payslip template", "वेतन पर्ची टेम्पलेट", "Plantilla de recibo", "Modèle de bulletin", "Lohnzettel-Vorlage"],
  ["Statutory", "वैधानिक", "Obligaciones legales", "Obligations légales", "Gesetzliche Abgaben"],
  ["Returns", "रिटर्न", "Declaraciones", "Déclarations", "Meldungen"],
  ["Documents", "दस्तावेज़", "Documentos", "Documents", "Dokumente"],
  ["Issued", "जारी किए गए", "Emitidos", "Émis", "Ausgestellt"],
  ["Generate", "बनाएँ", "Generar", "Générer", "Erstellen"],
  ["Templates", "टेम्पलेट", "Plantillas", "Modèles", "Vorlagen"],
  ["Letterpad", "लेटरपैड", "Membrete", "Papier à en-tête", "Briefpapier"],
  ["Members", "सदस्य", "Miembros", "Membres", "Mitglieder"],
  ["People & invites", "लोग और आमंत्रण", "Personas e invitaciones", "Personnes et invitations", "Personen & Einladungen"],
  ["Roles & permissions", "भूमिकाएँ और अनुमतियाँ", "Roles y permisos", "Rôles et autorisations", "Rollen & Berechtigungen"],
  ["Subscription", "सदस्यता", "Suscripción", "Abonnement", "Abonnement"],
  ["Settings", "सेटिंग्स", "Configuración", "Paramètres", "Einstellungen"],

  // ---- profile menu
  ["Account settings", "खाता सेटिंग्स", "Configuración de la cuenta", "Paramètres du compte", "Kontoeinstellungen"],
  ["Language & region", "भाषा और क्षेत्र", "Idioma y región", "Langue et région", "Sprache & Region"],
  ["Change password", "पासवर्ड बदलें", "Cambiar contraseña", "Changer le mot de passe", "Passwort ändern"],
  ["Log out", "लॉग आउट", "Cerrar sesión", "Se déconnecter", "Abmelden"],
  ["Open profile menu", "प्रोफ़ाइल मेनू खोलें", "Abrir menú de perfil", "Ouvrir le menu du profil", "Profilmenü öffnen"],

  // ---- account page
  ["Your sign-in details, and how Orbit looks for you.", "आपके साइन-इन विवरण, और Orbit आपको कैसा दिखे।", "Tus datos de acceso y cómo ves Orbit.", "Vos identifiants, et l'affichage d'Orbit pour vous.", "Ihre Anmeldedaten und wie Orbit für Sie aussieht."],
  ["Welcome to Orbit! You signed in with a temporary password. Choose your own to continue.", "Orbit में आपका स्वागत है! आपने अस्थायी पासवर्ड से साइन इन किया है। आगे बढ़ने के लिए अपना पासवर्ड चुनें।", "¡Te damos la bienvenida a Orbit! Iniciaste sesión con una contraseña temporal. Elige la tuya para continuar.", "Bienvenue sur Orbit ! Vous vous êtes connecté avec un mot de passe temporaire. Choisissez le vôtre pour continuer.", "Willkommen bei Orbit! Sie haben sich mit einem temporären Passwort angemeldet. Wählen Sie ein eigenes, um fortzufahren."],
  ["Signed in as", "साइन इन हैं", "Sesión iniciada como", "Connecté en tant que", "Angemeldet als"],
  ["Name", "नाम", "Nombre", "Nom", "Name"],
  ["Email", "ईमेल", "Correo electrónico", "E-mail", "E-Mail"],
  ["Company", "कंपनी", "Empresa", "Entreprise", "Unternehmen"],
  ["Role", "भूमिका", "Rol", "Rôle", "Rolle"],
  ["You'll stay signed in here; every other device is signed out.", "आप यहाँ साइन इन रहेंगे; बाकी सभी डिवाइस से साइन आउट हो जाएगा।", "Seguirás con la sesión iniciada aquí; se cerrará en los demás dispositivos.", "Vous restez connecté ici ; tous les autres appareils sont déconnectés.", "Hier bleiben Sie angemeldet; alle anderen Geräte werden abgemeldet."],
  ["Password changed.", "पासवर्ड बदल दिया गया।", "Contraseña cambiada.", "Mot de passe modifié.", "Passwort geändert."],
  ["Temporary password (from your welcome email)", "अस्थायी पासवर्ड (आपके स्वागत ईमेल से)", "Contraseña temporal (de tu correo de bienvenida)", "Mot de passe temporaire (de votre e-mail de bienvenue)", "Temporäres Passwort (aus Ihrer Willkommens-E-Mail)"],
  ["Current password", "मौजूदा पासवर्ड", "Contraseña actual", "Mot de passe actuel", "Aktuelles Passwort"],
  ["New password", "नया पासवर्ड", "Nueva contraseña", "Nouveau mot de passe", "Neues Passwort"],
  ["Confirm new password", "नए पासवर्ड की पुष्टि करें", "Confirma la nueva contraseña", "Confirmez le nouveau mot de passe", "Neues Passwort bestätigen"],
  ["At least 10 characters", "कम से कम 10 अक्षर", "Al menos 10 caracteres", "Au moins 10 caractères", "Mindestens 10 Zeichen"],
  ["A letter", "एक अक्षर", "Una letra", "Une lettre", "Ein Buchstabe"],
  ["A number", "एक अंक", "Un número", "Un chiffre", "Eine Ziffer"],
  ["Set my password", "मेरा पासवर्ड सेट करें", "Establecer mi contraseña", "Définir mon mot de passe", "Mein Passwort festlegen"],
  ["The two new passwords don't match.", "दोनों नए पासवर्ड मेल नहीं खाते।", "Las dos contraseñas nuevas no coinciden.", "Les deux nouveaux mots de passe ne correspondent pas.", "Die beiden neuen Passwörter stimmen nicht überein."],
  ["Couldn't change your password", "आपका पासवर्ड नहीं बदला जा सका", "No se pudo cambiar tu contraseña", "Impossible de changer votre mot de passe", "Ihr Passwort konnte nicht geändert werden"],
  ["Choose the language, timezone and date style Orbit uses for you. Only your own account changes.", "Orbit आपके लिए कौन-सी भाषा, समय क्षेत्र और तारीख़ का प्रारूप इस्तेमाल करे, चुनें। केवल आपका खाता बदलेगा।", "Elige el idioma, la zona horaria y el formato de fecha que Orbit usa contigo. Solo cambia tu cuenta.", "Choisissez la langue, le fuseau horaire et le format de date qu'Orbit utilise pour vous. Seul votre compte change.", "Wählen Sie Sprache, Zeitzone und Datumsformat, die Orbit für Sie verwendet. Nur Ihr eigenes Konto ändert sich."],
  ["Language", "भाषा", "Idioma", "Langue", "Sprache"],
  ["Timezone", "समय क्षेत्र", "Zona horaria", "Fuseau horaire", "Zeitzone"],
  ["Date format", "तारीख़ का प्रारूप", "Formato de fecha", "Format de date", "Datumsformat"],
  ["Time format", "समय का प्रारूप", "Formato de hora", "Format de l'heure", "Zeitformat"],
  ["Company default ({zone})", "कंपनी डिफ़ॉल्ट ({zone})", "Predeterminada de la empresa ({zone})", "Par défaut de l'entreprise ({zone})", "Unternehmensstandard ({zone})"],
  ["Same as the language ({example})", "भाषा के अनुसार ({example})", "Según el idioma ({example})", "Selon la langue ({example})", "Wie die Sprache ({example})"],
  ["12-hour ({example})", "12-घंटे ({example})", "12 horas ({example})", "12 heures ({example})", "12-Stunden ({example})"],
  ["24-hour ({example})", "24-घंटे ({example})", "24 horas ({example})", "24 heures ({example})", "24-Stunden ({example})"],
  ["Your timezone also sets the clock your attendance is recorded in.", "आपका समय क्षेत्र यह भी तय करता है कि आपकी उपस्थिति किस घड़ी से दर्ज हो।", "Tu zona horaria también fija el reloj con el que se registra tu asistencia.", "Votre fuseau horaire détermine aussi l'heure d'enregistrement de votre présence.", "Ihre Zeitzone bestimmt auch die Uhrzeit, mit der Ihre Anwesenheit erfasst wird."],
  ["Common timezones", "सामान्य समय क्षेत्र", "Zonas horarias comunes", "Fuseaux horaires courants", "Häufige Zeitzonen"],
  ["All timezones", "सभी समय क्षेत्र", "Todas las zonas horarias", "Tous les fuseaux horaires", "Alle Zeitzonen"],
  ["Preview", "पूर्वावलोकन", "Vista previa", "Aperçu", "Vorschau"],
  ["Save preferences", "प्राथमिकताएँ सहेजें", "Guardar preferencias", "Enregistrer les préférences", "Einstellungen speichern"],
  ["Preferences saved.", "प्राथमिकताएँ सहेज ली गईं।", "Preferencias guardadas.", "Préférences enregistrées.", "Einstellungen gespeichert."],
  ["Couldn't save your preferences", "आपकी प्राथमिकताएँ सहेजी नहीं जा सकीं", "No se pudieron guardar tus preferencias", "Impossible d'enregistrer vos préférences", "Ihre Einstellungen konnten nicht gespeichert werden"],
  ["Menus and account screens are translated; other screens are being translated and show English until then.", "मेनू और खाता स्क्रीन अनुवादित हैं; बाकी स्क्रीन का अनुवाद जारी है और तब तक वे अंग्रेज़ी में दिखेंगी।", "Los menús y la cuenta ya están traducidos; las demás pantallas se están traduciendo y mientras tanto se muestran en inglés.", "Les menus et l'écran du compte sont traduits ; les autres écrans sont en cours de traduction et restent en anglais d'ici là.", "Menüs und Kontoseiten sind übersetzt; die übrigen Seiten werden noch übersetzt und erscheinen bis dahin auf Englisch."],
];

const COLUMN: Record<Exclude<Language, "en">, number> = { hi: 1, es: 2, fr: 3, de: 4 };

const DICTIONARIES: Partial<Record<Language, Dictionary>> = Object.fromEntries(
  (Object.keys(COLUMN) as Exclude<Language, "en">[]).map((lang) => [
    lang,
    Object.fromEntries(ROWS.map((row) => [row[0], row[COLUMN[lang]]])),
  ]),
);

export function isLanguage(code: string | null | undefined): code is Language {
  return LANGUAGES.some((l) => l.code === code);
}

/** Translate `text` into `language`, falling back to the English it was written in. */
export function translate(language: string | null | undefined, text: string, values?: Record<string, string | number>): string {
  const dictionary = isLanguage(language) ? DICTIONARIES[language] : undefined;
  const out = dictionary?.[text] ?? text;
  return values ? out.replace(/\{(\w+)\}/g, (whole, key: string) => (key in values ? String(values[key]) : whole)) : out;
}
