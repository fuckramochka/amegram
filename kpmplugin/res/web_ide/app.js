const SVGS = {
  folder: '<svg viewBox="0 0 24 24" width="16" height="16"><path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" fill="#f7b731"/></svg>',
  folderOpen: '<svg viewBox="0 0 24 24" width="16" height="16"><path d="M19 20H4a2 2 0 0 1-2-2V6c0-1.1.9-2 2-2h6l2 2h7a2 2 0 0 1 2 2v1" fill="#f7b731"/><path d="M4 9h18l-2.6 10.2A2 2 0 0 1 17.5 21H3.5a1.5 1.5 0 0 1-1.5-1.7L4 9z" fill="#ffd438"/></svg>',
  python: '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="#3776ab" d="M11.9 2c-3.1 0-5 .6-5 2.5v2.2h5v.8H4.8C2.9 7.5 2 9.1 2 11.8c0 2.8 1.1 4.2 3 4.2h1.7V14c0-2 1.6-3.7 3.7-3.7h5.1c1.7 0 3-1.3 3-3V4.5C18.5 2.6 15 2 11.9 2zm-2.4 1.5c.6 0 1 .4 1 1s-.4 1-1 1-1-.4-1-1 .4-1 1-1z"/><path fill="#ffd438" d="M12.1 22c3.1 0 5-.6 5-2.5v-2.2h-5v-.8h7.1c1.9 0 2.8-1.6 2.8-4.3 0-2.8-1.1-4.2-3-4.2h-1.7V10c0 2-1.6 3.7-3.7 3.7H8.5c-1.7 0-3 1.3-3 3v2.8c0 1.9 3.5 2.5 6.6 2.5zm2.4-1.5c-.6 0-1-.4-1-1s.4-1 1-1 1 .4 1 1-.4 1-1 1z"/></svg>',
  js: '<svg viewBox="0 0 24 24" width="16" height="16"><rect width="24" height="24" rx="3" fill="#f7df1e"/><path d="M7 17.5c0 .8.4 1.5 1.2 1.5.8 0 1.3-.4 1.3-1.2V11h2v6.5c0 2.2-1.4 3.2-3.3 3.2-1.8 0-3.2-1-3.2-3.2h2zm7.5 3.1c-1.3 0-2.3-.6-2.8-1.6l1.7-1c.4.6.7.9 1.2.9.6 0 1-.3 1-.7 0-.5-.4-.7-1.3-1.1-1.3-.5-2.5-1.2-2.5-2.6 0-1.4 1.1-2.4 2.7-2.4 1.2 0 2 .5 2.5 1.4l-1.6 1c-.3-.5-.6-.7-1-.7-.5 0-.8.3-.8.6 0 .4.3.6 1.1 1 1.5.6 2.7 1.2 2.7 2.7 0 1.6-1.2 2.5-2.7 2.5z" fill="#000"/></svg>',
  css: '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="#264de4" d="M3 2l1.8 20.2L12 24l7.2-1.8L21 2H3z"/><path fill="#2965f1" d="M12 3.8v18.4l5.7-1.4L19.2 3.8H12z"/><path fill="#ebebeb" d="M12 8.3H7.5l.3 3.4H12v-3.4zm0 6.8l-2.6-.7-.2-2H7.2l.3 4 4.5 1.2v-2.5z"/><path fill="#ffffff" d="M12 8.3h4.5l-.4 5-4.1 1.1v2.5l5.7-1.6.8-9.4H12v2.4z"/></svg>',
  html: '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="#e34f26" d="M3 2l1.8 20.2L12 24l7.2-1.8L21 2H3z"/><path fill="#ef652a" d="M12 3.8v18.4l5.7-1.4L19.2 3.8H12z"/><path fill="#ebebeb" d="M12 8.3H7.5l.3 3.4H12V8.3zm0 6.8l-2.6-.7-.2-2H7.2l.3 4 4.5 1.2v-2.5z"/><path fill="#ffffff" d="M12 8.3h4.5l-.4 5-4.1 1.1v2.5l5.7-1.6.8-9.4H12v2.4z"/></svg>',
  json: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fbc02d" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1M16 3h1a2 2 0 0 1 2 2v5c0 1.1.9 2 2 2a2 2 0 0 1-2 2v5a2 2 0 0 1-2 2h-1"/></svg>',
  yaml: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#e06c75" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
  doc: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#61afef" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>',
  image: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#98c379" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>',
  audio: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#e06c75" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>',
  video: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#61afef" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"/><line x1="7" y1="2" x2="7" y2="22"/><line x1="17" y1="2" x2="17" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/><line x1="2" y1="7" x2="7" y2="7"/><line x1="2" y1="17" x2="7" y2="17"/><line x1="17" y1="17" x2="22" y2="17"/><line x1="17" y1="7" x2="22" y2="7"/></svg>',
  dex: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#98c379" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 10h16v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10z"/><path d="M7 10V7a5 5 0 0 1 10 0v3"/><circle cx="9" cy="14" r="1" fill="#98c379"/><circle cx="15" cy="14" r="1" fill="#98c379"/></svg>',
  so: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#d19a66" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
  pyc: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#c678dd" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>',
  binary: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#e5c07b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>',
  arrow: '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>'
};

function getFileSvg(kind) {
  if (kind === 'python') return SVGS.python;
  if (kind === 'javascript') return SVGS.js;
  if (kind === 'css') return SVGS.css;
  if (kind === 'html') return SVGS.html;
  if (kind === 'json') return SVGS.json;
  if (kind === 'yaml' || kind === 'ini') return SVGS.yaml;
  if (kind === 'markdown' || kind === 'text' || kind === 'bash' || kind === 'sql') return SVGS.doc;
  if (kind === 'image') return SVGS.image;
  if (kind === 'audio') return SVGS.audio;
  if (kind === 'video') return SVGS.video;
  if (kind === 'dex' || kind === 'java' || kind === 'class') return SVGS.dex;
  if (kind === 'so' || kind === 'c') return SVGS.so;
  if (kind === 'pyc') return SVGS.pyc;
  return SVGS.binary;
}

function decodeBase64Utf8(base64Str) {
  try {
    const binaryStr = atob(base64Str);
    const bytes = new Uint8Array(binaryStr.length);
    for (let i = 0; i < binaryStr.length; i++) {
      bytes[i] = binaryStr.charCodeAt(i);
    }
    return new TextDecoder('utf-8').decode(bytes);
  } catch (e) {
    console.error("decodeBase64Utf8 error", e);
    return "{}";
  }
}

let FILES = {};
try {
  FILES = JSON.parse(decodeBase64Utf8("__FILES_B64__"));
} catch (e) {
  console.error("Failed to parse FILES", e);
}

let STRINGS = {
  files: "Файлы",
  files_lower: "файлов",
  click_to_copy: "Нажмите, чтобы скопировать код",
  search_placeholder: "Поиск файлов...",
  code_copied: "Код скопирован!",
  path: "Путь",
  size: "Размер",
  type: "Тип",
  bytes: "байт",
  audio_file: "Аудио файл",
  video_file: "Видео файл",
  binary_resource: "Бинарный ресурс",
  hex_dump: "HEX Дамп (первые 64 байта)",
  audio_not_supported: "Ваш браузер не поддерживает аудио.",
  video_not_supported: "Ваш браузер не поддерживает видео.",
  file_too_large: "Файл слишком большой для встроенного превью"
};
try {
  const injectedStrings = __STRINGS_JSON__;
  if (injectedStrings && typeof injectedStrings === 'object') {
    STRINGS = Object.assign(STRINGS, injectedStrings);
  }
} catch (e) {}

let currentFile = __DEFAULT_FILE_JSON__;

function escapeHtml(text) {
  return (text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/* =======================================================================
   DEDICATED MULTI-LANGUAGE TOKENIZER & SYNTAX HIGHLIGHTER
   Single-pass raw text scanning to guarantee zero HTML tag collision bugs
   ======================================================================= */

const TOKEN_SPECS = {
  python: [
    { type: 'comment', regex: /#.*/ },
    { type: 'str', regex: /"""[\s\S]*?"""|'''[\s\S]*?'''|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/ },
    { type: 'dec', regex: /@[a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*/ },
    { type: 'kw', regex: /\b(?:def|class|import|from|as|return|if|elif|else|for|while|try|except|finally|with|in|is|not|and|or|lambda|yield|raise|break|continue|pass|global|nonlocal|async|await)\b/ },
    { type: 'bool', regex: /\b(?:True|False|None|self|cls)\b/ },
    { type: 'fn', regex: /\b[a-zA-Z_]\w*(?=\s*\()/ },
    { type: 'num', regex: /\b(?:0[xX][0-9a-fA-F]+|0[bB][01]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b/ }
  ],
  javascript: [
    { type: 'comment', regex: /\/\/.*|\/\*[\s\S]*?\*\// },
    { type: 'str', regex: /`[\s\S]*?`|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/ },
    { type: 'dec', regex: /@[a-zA-Z_$][\w$]*/ },
    { type: 'kw', regex: /\b(?:const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|default|try|catch|finally|throw|new|typeof|instanceof|class|extends|super|this|import|export|from|as|async|await|yield|in|of|void|delete|debugger)\b/ },
    { type: 'bool', regex: /\b(?:true|false|null|undefined|NaN|Infinity)\b/ },
    { type: 'fn', regex: /\b[a-zA-Z_$][\w$]*(?=\s*\()/ },
    { type: 'num', regex: /\b(?:0[xX][0-9a-fA-F]+|0[bB][01]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b/ }
  ],
  css: [
    { type: 'comment', regex: /\/\*[\s\S]*?\*\// },
    { type: 'str', regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|url\([^)]+\)/ },
    { type: 'dec', regex: /@[a-zA-Z_-]+/ },
    { type: 'key', regex: /--?[a-zA-Z0-9_-]+(?=\s*:)/ },
    { type: 'fn', regex: /:[a-zA-Z-]+(?:\([^)]*\))?/ },
    { type: 'num', regex: /#[0-9a-fA-F]{3,8}\b|\b\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|s|ms|deg|fr|pt)?\b/ },
    { type: 'kw', regex: /\b(?:!important|inherit|initial|unset|revert|auto|none|block|flex|grid|inline|absolute|relative|fixed|sticky)\b/ }
  ],
  yaml: [
    { type: 'comment', regex: /#.*/ },
    { type: 'str', regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/ },
    { type: 'key', regex: /^[ \t]*(?:-[ \t]+)?([a-zA-Z0-9_\.-]+)(?=\s*:)/m },
    { type: 'bool', regex: /\b(?:true|false|yes|no|null|on|off|TRUE|FALSE|True|False)\b/ },
    { type: 'num', regex: /\b\d+(?:\.\d+)?\b/ }
  ],
  json: [
    { type: 'key', regex: /"(?:\\.|[^"\\])*"(?=\s*:)/ },
    { type: 'str', regex: /"(?:\\.|[^"\\])*"/ },
    { type: 'bool', regex: /\b(?:true|false|null)\b/ },
    { type: 'num', regex: /-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b/ }
  ],
  ini: [
    { type: 'comment', regex: /[#;].*/ },
    { type: 'dec', regex: /^\s*\[.*?\]/m },
    { type: 'key', regex: /^\s*([a-zA-Z0-9_\.-]+)(?=\s*=)/m },
    { type: 'str', regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/ },
    { type: 'bool', regex: /\b(?:true|false|yes|no|on|off)\b/i },
    { type: 'num', regex: /\b\d+\b/ }
  ],
  markdown: [
    { type: 'fn', regex: /^#{1,6}\s+.*$/m },
    { type: 'str', regex: /`[^`\n]+`/ },
    { type: 'kw', regex: /^\s*(?:[-*+]|\d+\.|\>)\s+/m },
    { type: 'bool', regex: /\*\*.*?\*\*|__.*?__/ },
    { type: 'key', regex: /\[.*?\]\(.*?\)/ }
  ],
  bash: [
    { type: 'comment', regex: /#.*/ },
    { type: 'str', regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/ },
    { type: 'dec', regex: /\$(?:[a-zA-Z_]\w*|\{[^}]+\})/ },
    { type: 'kw', regex: /\b(?:if|then|else|elif|fi|case|esac|for|while|until|do|done|in|function|select|time|echo|exit|cd|export|source|local|return|read|shift|eval|exec|set|unset)\b/ },
    { type: 'num', regex: /\b\d+\b/ }
  ],
  sql: [
    { type: 'comment', regex: /--.*|\/\*[\s\S]*?\*\// },
    { type: 'str', regex: /'(?:''|[^'])*'/ },
    { type: 'kw', regex: /\b(?:SELECT|FROM|WHERE|INSERT|INTO|UPDATE|DELETE|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|ON|GROUP|BY|ORDER|HAVING|LIMIT|OFFSET|CREATE|TABLE|DROP|ALTER|ADD|COLUMN|PRIMARY|KEY|FOREIGN|REFERENCES|NOT|NULL|DEFAULT|UNION|ALL|AS|DISTINCT|CASE|WHEN|THEN|ELSE|END|AND|OR|IN|LIKE|IS|EXISTS|BETWEEN|VALUES|SET|INDEX|VIEW|TRIGGER|TRANSACTION|COMMIT|ROLLBACK|ASC|DESC)\b/i },
    { type: 'fn', regex: /\b[a-zA-Z_]\w*(?=\s*\()/ },
    { type: 'num', regex: /\b\d+(?:\.\d+)?\b/ }
  ],
  html: [
    { type: 'comment', regex: /<!--[\s\S]*?-->/ },
    { type: 'str', regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/ },
    { type: 'kw', regex: /<\/?[\w:-]+/ },
    { type: 'key', regex: /\b[\w:-]+(?=\s*=)/ }
  ],
  c: [
    { type: 'comment', regex: /\/\/.*|\/\*[\s\S]*?\*\// },
    { type: 'str', regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/ },
    { type: 'dec', regex: /^\s*#\s*[a-zA-Z_]\w*/m },
    { type: 'kw', regex: /\b(?:int|char|void|float|double|long|short|unsigned|signed|const|static|volatile|struct|union|enum|typedef|auto|register|extern|inline|return|if|else|for|while|do|switch|case|default|break|continue|goto|sizeof|class|namespace|public|private|protected|template|typename|virtual|override|explicit|operator|new|delete|try|catch|throw|constexpr|nullptr|bool|true|false|uint8_t|uint16_t|uint32_t|uint64_t|int8_t|int16_t|int32_t|int64_t|size_t|ssize_t|JNIEXPORT|JNICALL|JavaVM|JNIEnv|jobject|jclass|jstring|jarray|jboolean|jbyte|jchar|jshort|jint|jlong|jfloat|jdouble)\b/ },
    { type: 'bool', regex: /\b(?:true|false|NULL|nullptr)\b/ },
    { type: 'fn', regex: /\b[a-zA-Z_]\w*(?=\s*\()/ },
    { type: 'num', regex: /\b(?:0[xX][0-9a-fA-F]+|0[bB][01]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?(?:[uUlLfF]+)?)\b/ }
  ],
  java: [
    { type: 'comment', regex: /\/\/.*|\/\*[\s\S]*?\*\// },
    { type: 'str', regex: /"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/ },
    { type: 'dec', regex: /@[a-zA-Z_]\w*/ },
    { type: 'kw', regex: /\b(?:package|import|public|private|protected|class|interface|enum|record|extends|implements|static|final|abstract|native|transient|volatile|synchronized|strictfp|return|if|else|for|while|do|switch|case|default|break|continue|try|catch|finally|throw|throws|new|instanceof|this|super|void|boolean|byte|char|short|int|long|float|double|var|val|fun|override|open|data|sealed)\b/ },
    { type: 'bool', regex: /\b(?:true|false|null)\b/ },
    { type: 'fn', regex: /\b[a-zA-Z_]\w*(?=\s*\()/ },
    { type: 'num', regex: /\b(?:0[xX][0-9a-fA-F]+|0[bB][01]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?(?:[lLfFdD]+)?)\b/ }
  ]
};

function highlightSyntax(rawCode, lang) {
  if (!rawCode) return '';
  const spec = TOKEN_SPECS[lang];
  if (!spec) {
    return escapeHtml(rawCode);
  }

  // Build unified master regex with capturing groups
  const groupRules = spec.map(r => `(${r.regex.source})`);
  const masterRegex = new RegExp(groupRules.join('|'), 'gm');

  let result = '';
  let lastIndex = 0;
  let match;

  while ((match = masterRegex.exec(rawCode)) !== null) {
    const matchIndex = match.index;
    const matchStr = match[0];

    // Append unformatted text before the match
    if (matchIndex > lastIndex) {
      result += escapeHtml(rawCode.substring(lastIndex, matchIndex));
    }

    // Determine which token group matched
    let matchedType = 'text';
    for (let i = 0; i < spec.length; i++) {
      if (match[i + 1] !== undefined) {
        matchedType = spec[i].type;
        break;
      }
    }

    if (matchedType !== 'text') {
      result += '<span class="tok-' + matchedType + '">' + escapeHtml(matchStr) + '</span>';
    } else {
      result += escapeHtml(matchStr);
    }

    lastIndex = masterRegex.lastIndex;
  }

  if (lastIndex < rawCode.length) {
    result += escapeHtml(rawCode.substring(lastIndex));
  }

  return result;
}

/* =======================================================================
   FILE EXPLORER & TREE BUILDER
   ======================================================================= */

function buildTree(filterText) {
  const root = document.getElementById('tree-root');
  if (!root) return;
  root.innerHTML = '';
  const filter = (filterText || '').toLowerCase().trim();

  const tree = { isDir: true, children: {} };

  for (const [path, file] of Object.entries(FILES)) {
    if (filter && !path.toLowerCase().includes(filter)) continue;
    const parts = path.split('/');
    let curr = tree;
    for (let i = 0; i < parts.length; i++) {
      const isFile = (i === parts.length - 1);
      const part = parts[i];
      if (isFile) {
        curr.children[part] = { isDir: false, name: part, path: path, file: file };
      } else {
        if (!curr.children[part]) {
          curr.children[part] = { isDir: true, name: part, children: {} };
        }
        curr = curr.children[part];
      }
    }
  }

  function renderNode(node, container) {
    const sorted = Object.entries(node.children).sort((a, b) => {
      if (a[1].isDir && !b[1].isDir) return -1;
      if (!a[1].isDir && b[1].isDir) return 1;
      return a[0].localeCompare(b[0]);
    });

    for (const [name, item] of sorted) {
      if (item.isDir) {
        const group = document.createElement('div');
        group.className = 'folder-group';
        const head = document.createElement('div');
        head.className = 'folder-header';
        head.innerHTML = '<span class="arrow">' + SVGS.arrow + '</span> <span class="svg-icon">' + SVGS.folderOpen + '</span> <span>' + escapeHtml(name) + '</span>';
        const children = document.createElement('div');
        children.className = 'folder-children';

        head.onclick = () => {
          const isCollapsed = head.classList.toggle('collapsed');
          children.classList.toggle('collapsed');
          const iconEl = head.querySelector('.svg-icon');
          if (iconEl) {
            iconEl.innerHTML = isCollapsed ? SVGS.folder : SVGS.folderOpen;
          }
        };

        renderNode(item, children);
        group.appendChild(head);
        group.appendChild(children);
        container.appendChild(group);
      } else {
        const row = document.createElement('div');
        row.className = 'tree-item' + (item.path === currentFile ? ' active' : '');
        row.innerHTML = '<span class="svg-icon">' + getFileSvg(item.file.kind) + '</span> <span class="name">' + escapeHtml(name) + '</span> <span class="size">' + item.file.size_str + '</span>';
        row.onclick = () => {
          openFile(item.path);
          toggleDrawer(false);
        };
        container.appendChild(row);
      }
    }
  }

  renderNode(tree, root);
}

function openFile(path) {
  const file = FILES[path];
  if (!file) return;
  currentFile = path;

  const curIcon = document.getElementById('cur-icon');
  const curPath = document.getElementById('cur-path');
  if (curIcon) curIcon.innerHTML = getFileSvg(file.kind);
  if (curPath) curPath.innerHTML = '<b>' + escapeHtml(file.name) + '</b> <span style="opacity:0.6;font-size:11px">(' + file.size_str + ')</span>';

  const codeContainer = document.getElementById('code-container');
  const assetContainer = document.getElementById('asset-container');
  const btnCopy = document.getElementById('btn-copy');

  if (file.type === 'text') {
    if (codeContainer) codeContainer.style.display = 'flex';
    if (assetContainer) assetContainer.style.display = 'none';
    if (btnCopy) btnCopy.style.display = 'inline-flex';

    const linesCount = file.lines || 1;
    let gutterHtml = '';
    for (let i = 1; i <= linesCount; i++) {
      gutterHtml += i + '<br>';
    }
    const gutterEl = document.getElementById('gutter');
    const codeViewEl = document.getElementById('code-view');
    const editorWrapEl = document.getElementById('editor-wrap');

    if (gutterEl) gutterEl.innerHTML = gutterHtml;
    if (codeViewEl) codeViewEl.innerHTML = highlightSyntax(file.content, file.kind);
    if (editorWrapEl) {
      editorWrapEl.scrollTop = 0;
      editorWrapEl.scrollLeft = 0;
    }
  } else if (file.type === 'image') {
    if (codeContainer) codeContainer.style.display = 'none';
    if (assetContainer) {
      assetContainer.style.display = 'flex';
      assetContainer.innerHTML =
        '<div class="img-preview-box">' +
          '<img src="' + file.data_uri + '" alt="' + escapeHtml(file.name) + '">' +
        '</div>' +
        '<div class="asset-card">' +
          '<div class="asset-card-title"><span class="svg-icon">' + SVGS.image + '</span> ' + escapeHtml(file.name) + '</div>' +
          '<div style="font-size:12.5px;color:var(--text-muted)">' +
            escapeHtml(STRINGS.path) + ': <b>' + escapeHtml(file.path) + '</b><br>' +
            escapeHtml(STRINGS.size) + ': <b>' + file.size_str + '</b> (' + file.size.toLocaleString() + ' ' + escapeHtml(STRINGS.bytes) + ')<br>' +
            'CRC-32: <b>' + file.crc + '</b>' +
          '</div>' +
        '</div>';
    }
    if (btnCopy) btnCopy.style.display = 'none';
  } else if (file.type === 'audio') {
    if (codeContainer) codeContainer.style.display = 'none';
    if (assetContainer) {
      assetContainer.style.display = 'flex';
      assetContainer.innerHTML =
        '<div class="media-preview-box">' +
          '<div class="audio-disc-icon">' + SVGS.audio + '</div>' +
          '<div class="media-title">' + escapeHtml(file.name) + '</div>' +
          '<audio controls preload="metadata" style="width:100%;margin-top:14px;">' +
            '<source src="' + file.data_uri + '">' +
            escapeHtml(STRINGS.audio_not_supported) +
          '</audio>' +
        '</div>' +
        '<div class="asset-card">' +
          '<div class="asset-card-title"><span class="svg-icon">' + SVGS.audio + '</span> ' + escapeHtml(file.name) + '</div>' +
          '<div style="font-size:12.5px;color:var(--text-muted)">' +
            escapeHtml(STRINGS.path) + ': <b>' + escapeHtml(file.path) + '</b><br>' +
            escapeHtml(STRINGS.type) + ': <b>' + escapeHtml(STRINGS.audio_file) + '</b><br>' +
            escapeHtml(STRINGS.size) + ': <b>' + file.size_str + '</b> (' + file.size.toLocaleString() + ' ' + escapeHtml(STRINGS.bytes) + ')<br>' +
            'CRC-32: <b>' + file.crc + '</b>' +
          '</div>' +
        '</div>';
    }
    if (btnCopy) btnCopy.style.display = 'none';
  } else if (file.type === 'video') {
    if (codeContainer) codeContainer.style.display = 'none';
    if (assetContainer) {
      assetContainer.style.display = 'flex';
      assetContainer.innerHTML =
        '<div class="media-preview-box video-box">' +
          '<video controls playsinline preload="metadata" style="max-width:100%;max-height:48vh;border-radius:8px;background:#000;">' +
            '<source src="' + file.data_uri + '">' +
            escapeHtml(STRINGS.video_not_supported) +
          '</video>' +
        '</div>' +
        '<div class="asset-card">' +
          '<div class="asset-card-title"><span class="svg-icon">' + SVGS.video + '</span> ' + escapeHtml(file.name) + '</div>' +
          '<div style="font-size:12.5px;color:var(--text-muted)">' +
            escapeHtml(STRINGS.path) + ': <b>' + escapeHtml(file.path) + '</b><br>' +
            escapeHtml(STRINGS.type) + ': <b>' + escapeHtml(STRINGS.video_file) + '</b><br>' +
            escapeHtml(STRINGS.size) + ': <b>' + file.size_str + '</b> (' + file.size.toLocaleString() + ' ' + escapeHtml(STRINGS.bytes) + ')<br>' +
            'CRC-32: <b>' + file.crc + '</b>' +
          '</div>' +
        '</div>';
    }
    if (btnCopy) btnCopy.style.display = 'none';
  } else if (file.type === 'large_asset') {
    if (codeContainer) codeContainer.style.display = 'none';
    if (assetContainer) {
      assetContainer.style.display = 'flex';
      assetContainer.innerHTML =
        '<div class="asset-card">' +
          '<div class="asset-card-title"><span class="svg-icon">' + getFileSvg(file.kind) + '</span> ' + escapeHtml(file.name) + '</div>' +
          '<div style="font-size:12.5px;color:var(--text-muted)">' +
            escapeHtml(STRINGS.path) + ': <b>' + escapeHtml(file.path) + '</b><br>' +
            escapeHtml(STRINGS.size) + ': <b>' + file.size_str + '</b> (' + file.size.toLocaleString() + ' ' + escapeHtml(STRINGS.bytes) + ')<br>' +
            'CRC-32: <b>' + file.crc + '</b><br>' +
            '<div style="color:var(--accent);margin-top:8px;font-size:12px;">ℹ️ ' + escapeHtml(file.note || STRINGS.file_too_large) + '</div>' +
          '</div>' +
        '</div>';
    }
    if (btnCopy) btnCopy.style.display = 'none';
  } else {
    if (codeContainer) codeContainer.style.display = 'none';
    if (assetContainer) {
      assetContainer.style.display = 'flex';
      let hexHtml = '';
      if (file.hex_preview) {
        hexHtml = '<div style="font-size:11.5px;color:var(--text-muted);margin-top:4px;">' + escapeHtml(STRINGS.hex_dump) + ':</div>' +
                  '<div class="hex-box">' + escapeHtml(file.hex_preview) + '</div>';
      }

      assetContainer.innerHTML =
        '<div class="asset-card">' +
          '<div class="asset-card-title"><span class="svg-icon">' + getFileSvg(file.kind) + '</span> ' + escapeHtml(file.name) + '</div>' +
          '<div style="font-size:12.5px;color:var(--text-muted)">' +
            escapeHtml(STRINGS.path) + ': <b>' + escapeHtml(file.path) + '</b><br>' +
            escapeHtml(STRINGS.type) + ': <b>' + escapeHtml(STRINGS.binary_resource) + '</b><br>' +
            escapeHtml(STRINGS.size) + ': <b>' + file.size_str + '</b> (' + file.size.toLocaleString() + ' ' + escapeHtml(STRINGS.bytes) + ')<br>' +
            'CRC-32: <b>' + file.crc + '</b>' +
          '</div>' +
          hexHtml +
        '</div>';
    }
    if (btnCopy) btnCopy.style.display = 'none';
  }

  const searchInput = document.getElementById('search-input');
  buildTree(searchInput ? searchInput.value : '');
}

function toggleDrawer(forceState) {
  const drawer = document.getElementById('drawer');
  const backdrop = document.getElementById('backdrop');
  if (!drawer || !backdrop) return;
  const isOpen = drawer.classList.contains('open');
  const target = (typeof forceState === 'boolean') ? forceState : !isOpen;

  if (target) {
    drawer.classList.add('open');
    backdrop.classList.add('show');
  } else {
    drawer.classList.remove('open');
    backdrop.classList.remove('show');
  }
}

function filterTree(val) {
  buildTree(val);
}

function showToast(msg) {
  const t = document.getElementById('toast');
  if (!t) return;
  t.textContent = msg || STRINGS.code_copied || 'Код скопирован!';
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 1800);
}

function copyCurrentCode() {
  const file = FILES[currentFile];
  if (!file || !file.content) return;

  function doSuccess() {
    showToast(STRINGS.code_copied || 'Код скопирован!');
  }

  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(file.content)
      .then(doSuccess)
      .catch(() => fallbackCopy(file.content));
  } else {
    fallbackCopy(file.content);
  }
}

function fallbackCopy(text) {
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    ta.style.top = '-9999px';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    if (ok) {
      showToast(STRINGS.code_copied || 'Код скопирован!');
      return;
    }
  } catch (e) {}
  showToast(STRINGS.code_copied || 'Код скопирован!');
}

// Initial Load
buildTree();
if (currentFile) {
  openFile(currentFile);
}
