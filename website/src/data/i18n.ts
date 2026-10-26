export type Language = 'uk' | 'en' | 'ru';

export interface Translation {
  nav: {
    features: string;
    badges: string;
    download: string;
    github: string;
    getApk: string;
  };
  hero: {
    tag: string;
    titleLine1: string;
    titleLine2: string;
    description: string;
    downloadButton: string;
    githubButton: string;
    tagFree: string;
    tagNoAds: string;
    tagAndroid: string;
  };
  featuresSection: {
    kicker: string;
    heading: string;
    subheading: string;
  };
  features: {
    title: string;
    subtitle: string;
    desc: string;
  }[];
  badgesSection: {
    kicker: string;
    heading: string;
    countNote: string;
    verifiedNote: string;
    chooseLabel: string;
  };
  downloadSection: {
    heading: string;
    description: string;
    button: string;
    note: string;
  };
  modal: {
    kicker: string;
    title: string;
    desc: string;
    step1: string;
    step2: string;
    step3: string;
    downloadBtn: string;
    allReleases: string;
    footerNote: string;
  };
  footer: {
    community: string;
    github: string;
    license: string;
    modules: string;
  };
}

export const TRANSLATIONS: Record<Language, Translation> = {
  uk: {
    nav: {
      features: 'Можливості',
      badges: 'Відзнаки ໒꒱',
      download: 'Завантажити',
      github: 'GitHub ↗',
      getApk: 'Отримати APK',
    },
    hero: {
      tag: 'Telegram, у якому все для людей',
      titleLine1: 'Нічого зайвого.',
      titleLine2: 'Тільки ваші правила.',
      description: 'Швидкий та зручний клієнт із другим дном від сторонніх очей, скачуванням з TikTok без логотипів, живими бейджиками та повною відсутністю реклами.',
      downloadButton: 'Завантажити APK',
      githubButton: 'Релізи на GitHub ↗',
      tagFree: 'Безкоштовно назавжди',
      tagNoAds: 'Без реклами та трекерів',
      tagAndroid: 'Для Android 8.0+',
    },
    featuresSection: {
      kicker: 'Прості речі, яких так не вистачало',
      heading: 'Усе зрозуміло з першої секунди.',
      subheading: 'Жодних складних інструкцій — кожна функція просто працює і робить щоденне спілкування приємнішим.',
    },
    features: [
      {
        title: 'Друге дно (Фейковий PIN)',
        subtitle: 'Повний спокій, якщо вас змушують показати чати.',
        desc: 'Вводите ваш звичайний код — відкриваються справжні особисті чати. Вводите спеціальний фейковий код — відкривається абсолютно чистий, порожній Telegram без жодних підозр. Ніхто не дізнається, що у вас є друге дно.',
      },
      {
        title: 'TikTok без водяних знаків',
        subtitle: 'Відео та музика зберігаються миттєво.',
        desc: 'Вам скинули посилання на TikTok чи Reels? Додаток сам збереже чисте HD-відео без набридливих водяних знаків або збереже з нього аудіотрек у високій якості.',
      },
      {
        title: 'Ексклюзивні бейджики ໒꒱',
        subtitle: 'Красиві піксельні відзнаки у вашому профілі.',
        desc: 'Сяючі піксельні сердечка, крильця та німби біля вашого імені в чатах та на сторінці профілю. Кожну відзнаку перевірено та офіційно видано засновником спільноти.',
      },
      {
        title: 'Режим невидимки та видалені смс',
        subtitle: 'Читайте непомітно і не втрачайте стерті повідомлення.',
        desc: 'Співрозмовник видалив повідомлення «для всіх»? Ви все одно бачитимете його копію у своєму чаті. А ще можна переглядати історії та читати діалоги без позначки про прочитання.',
      },
      {
        title: 'Зручні модулі в один дотик',
        subtitle: 'Вмикайте нові фішки прямо в налаштуваннях.',
        desc: 'Потрібна нова функція? Просто відкрийте розділ модулів та натисніть тумблер. Без перевстановлення додатка, без складних скриптів і без рут-прав.',
      },
      {
        title: 'Швидкий, плавний і не гріє телефон',
        subtitle: 'Працює на 120 кадрах без зависань.',
        desc: 'Додаток оптимізовано під сучасні смартфони та новий Android 15. Він відкриває великі канали без підвисань та економно витрачає заряд акумулятора.',
      },
    ],
    badgesSection: {
      kicker: 'Ідентичність',
      heading: 'Відзнаки спільноти Miyumi ໒꒱',
      countNote: 'верифікованих бейджів у системі',
      verifiedNote: 'Відзнаки не можна намалювати самостійно — вони офіційно прив’язуються до вашого акаунту через хмару.',
      chooseLabel: 'Натисніть на відзнаку, щоб переглянути:',
    },
    downloadSection: {
      heading: 'Спробуйте Yumigram зараз.',
      description: 'Безпечна офіційна збірка APK безпосередньо з репозиторію. Усе готово до встановлення.',
      button: 'Завантажити офіційний APK',
      note: 'Працює на будь-якому Android 8.0+ · Відкритий вихідний код GPL-3.0',
    },
    modal: {
      kicker: 'Встановлення застосунку',
      title: 'Як встановити Yumigram APK',
      desc: 'Три прості кроки для запуску на телефоні:',
      step1: 'Завантажте інсталяційний файл за кнопкою нижче.',
      step2: 'Якщо телефон запитає — надайте дозвіл на встановлення з браузера.',
      step3: 'Відкрийте Yumigram та увійдіть за своїм номером телефону.',
      downloadBtn: 'Завантажити APK прямо зараз',
      allReleases: 'Всі версії на GitHub Releases ↗',
      footerNote: 'Безпечно. Ніколи не передавайте код входу з SMS третім особам.',
    },
    footer: {
      community: 'Yumigram ໒꒱ · Спільнота Miyumi · 2026',
      github: 'GitHub Репозиторій',
      license: 'Ліцензія GPL-3.0',
      modules: 'Модулі YuiModules',
    },
  },

  en: {
    nav: {
      features: 'Features',
      badges: 'Badges ໒꒱',
      download: 'Download',
      github: 'GitHub ↗',
      getApk: 'Get APK',
    },
    hero: {
      tag: 'Telegram, made friendly and private',
      titleLine1: 'Pure simplicity.',
      titleLine2: 'Your own rules.',
      description: 'A fast, elegant Android client with a secret second space (fake PIN), watermark-free TikTok downloads, live glowing badges, and zero advertisements.',
      downloadButton: 'Download APK',
      githubButton: 'Releases on GitHub ↗',
      tagFree: 'Free Forever',
      tagNoAds: 'No Ads or Trackers',
      tagAndroid: 'For Android 8.0+',
    },
    featuresSection: {
      kicker: 'Simple things you always wanted',
      heading: 'Effortless from the first tap.',
      subheading: 'No complicated technical manuals — every feature is designed to just work and make your daily messaging better.',
    },
    features: [
      {
        title: 'Secret Second Space (Fake PIN)',
        subtitle: 'Peace of mind if someone forces you to unlock your phone.',
        desc: 'Entering your real PIN opens your actual private chats. Entering a secret decoy PIN opens a totally clean, innocent Telegram. Nobody will ever suspect you have a second space.',
      },
      {
        title: 'No-Watermark TikTok Downloads',
        subtitle: 'Save videos and audio with a single tap.',
        desc: 'Did someone send a link to TikTok or Reels? Yumigram instantly downloads the crystal clear video without any annoying watermarks or extracts the soundtrack in high quality.',
      },
      {
        title: 'Exclusive ໒꒱ Badges',
        subtitle: 'Glowing pixel badges next to your name.',
        desc: 'Lovely pixel wings, halos, and hearts in your profile and conversations. Each badge is cryptographically verified and officially granted by the community founder.',
      },
      {
        title: 'Ghost Mode & Anti-Delete',
        subtitle: 'Read invisibly and keep deleted messages.',
        desc: 'If someone deletes a message "for everyone", it stays visible in your chat. You can also view stories and read incoming messages without revealing that you have seen them.',
      },
      {
        title: 'One-Tap Module Store',
        subtitle: 'Enable extra capabilities in seconds.',
        desc: 'Want a new feature? Simply turn on the toggle in the settings menu without reinstalling the app, running terminal commands, or requiring root access.',
      },
      {
        title: 'Fast, Smooth & Battery Saver',
        subtitle: 'Flawless 120 FPS without draining battery.',
        desc: 'Fully optimized for modern Android 15 phones. Heavy channels open smoothly and instantaneously without lag or device heating.',
      },
    ],
    badgesSection: {
      kicker: 'Identity',
      heading: 'Miyumi ໒꒱ Community Badges',
      countNote: 'verified badges in registry',
      verifiedNote: 'Badges cannot be self-assigned — they are officially granted and bound to your account in the cloud.',
      chooseLabel: 'Click any badge to inspect:',
    },
    downloadSection: {
      heading: 'Get Yumigram today.',
      description: 'Clean official APK build straight from the open repository. Ready to install.',
      button: 'Download Official APK',
      note: 'Compatible with Android 8.0+ · Open Source GPL-3.0 License',
    },
    modal: {
      kicker: 'Installation Guide',
      title: 'How to install Yumigram APK',
      desc: 'Three straightforward steps:',
      step1: 'Download the APK file using the button below.',
      step2: 'If prompted by Android, allow installation from this browser.',
      step3: 'Open Yumigram and log in with your Telegram phone number.',
      downloadBtn: 'Download APK Now',
      allReleases: 'All Releases on GitHub ↗',
      footerNote: 'Safe & secure. Never share your SMS login code with anyone.',
    },
    footer: {
      community: 'Yumigram ໒꒱ · Miyumi Community · 2026',
      github: 'GitHub Repository',
      license: 'GPL-3.0 License',
      modules: 'YuiModules',
    },
  },

  ru: {
    nav: {
      features: 'Возможности',
      badges: 'Бейджики ໒꒱',
      download: 'Скачать',
      github: 'GitHub ↗',
      getApk: 'Скачать APK',
    },
    hero: {
      tag: 'Telegram, сделанный по-человечески',
      titleLine1: 'Ничего лишнего.',
      titleLine2: 'Только ваши правила.',
      description: 'Быстрый и удобный клиент с фейковым PIN от чужих глаз, скачиванием TikTok без водяных знаков, живыми бейджами и полным отсутствием рекламы.',
      downloadButton: 'Скачать APK',
      githubButton: 'Релизы на GitHub ↗',
      tagFree: 'Бесплатно навсегда',
      tagNoAds: 'Без рекламы и трекеров',
      tagAndroid: 'Для Android 8.0+',
    },
    featuresSection: {
      kicker: 'Простые вещи, которых так не хватало',
      heading: 'Всё понятно с первой секунды.',
      subheading: 'Никаких сложных инструкций и заумных терминов — каждая функция создана для удобного и спокойного общения.',
    },
    features: [
      {
        title: 'Второе дно (Фейковый PIN)',
        subtitle: 'Спокойствие, если вас заставляют разблокировать чаты.',
        desc: 'Вводите обычный PIN — открываются ваши настоящие диалоги. Вводите секретный фейковый PIN — открывается абсолютно чистый Telegram. Никто никогда не догадается, что есть тайные чаты.',
      },
      {
        title: 'TikTok без водяных знаков',
        subtitle: 'Видео и треки сохраняются в один клик.',
        desc: 'Скинули ссылку на TikTok или Reels? Приложение автоматически скачает чистое видео без логотипов или достанет музыку в высоком качестве для плеера.',
      },
      {
        title: 'Эксклюзивные бейджики ໒꒱',
        subtitle: 'Светящиеся пиксельные значки в профиле.',
        desc: 'Красивые пиксельные сердечки, крылышки и нимбы рядом с вашим именем в чатах и профиле. Каждый бейдж проверен и официально выдан создателем сообщества.',
      },
      {
        title: 'Режим невидимки и удалённые смс',
        subtitle: 'Читайте незаметно и не теряйте стёртые сообщения.',
        desc: 'Собеседник удалил сообщение для всех? Вы всё равно продолжите видеть его в чате. Также можно читать сообщения и смотреть истории без отметки о прочтении.',
      },
      {
        title: 'Удобные модули в один клик',
        subtitle: 'Включайте нужные фишки прямо в настройках.',
        desc: 'Нужна новая функция? Просто откройте меню модулей и включите тумблер. Без переустановки приложения, без рута и сложных манипуляций.',
      },
      {
        title: 'Плавный, быстрый и не греет телефон',
        subtitle: 'Работает на 120 кадрах без подлагиваний.',
        desc: 'Клиент оптимизирован под современные смартфоны и Android 15. Он моментально открывает любые большие каналы и бережно расходует заряд аккумулятора.',
      },
    ],
    badgesSection: {
      kicker: 'Идентичность',
      heading: 'Бейджики сообщества Miyumi ໒꒱',
      countNote: 'верифицированных бейджей в системе',
      verifiedNote: 'Бейджики нельзя подделать локально — они официально привязываются к вашему аккаунту через облако.',
      chooseLabel: 'Нажмите на отметку, чтобы посмотреть:',
    },
    downloadSection: {
      heading: 'Попробуйте Yumigram прямо сейчас.',
      description: 'Официальная и чистая сборка APK напрямую из репозитория. Всё готово к установке.',
      button: 'Скачать официальный APK',
      note: 'Работает на Android 8.0+ · Открытый исходный код GPL-3.0',
    },
    modal: {
      kicker: 'Установка приложения',
      title: 'Как установить Yumigram APK',
      desc: 'Три простых шага:',
      step1: 'Скачайте установочный файл по кнопке ниже.',
      step2: 'Если телефон спросит — разрешите установку из браузера в настройках.',
      step3: 'Откройте Yumigram и войдите по своему номеру телефона.',
      downloadBtn: 'Скачать APK прямо сейчас',
      allReleases: 'Все релизы на GitHub ↗',
      footerNote: 'Безопасно. Никогда не передавайте код входа из SMS третьим лицам.',
    },
    footer: {
      community: 'Yumigram ໒꒱ · Сообщество Miyumi · 2026',
      github: 'Репозиторий GitHub',
      license: 'Лицензия GPL-3.0',
      modules: 'Модули YuiModules',
    },
  },
};
