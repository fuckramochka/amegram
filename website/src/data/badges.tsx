import React from 'react';
import { Language } from './i18n';

export interface BadgeInfo {
  id: string;
  code: string;
  names: Record<Language, string>;
  tags: Record<Language, string>;
  lores: Record<Language, string>;
  bloomColor: string;
}

export const BADGES: BadgeInfo[] = [
  {
    id: 'original',
    code: '01',
    names: {
      uk: 'Класичний стиль',
      en: 'Original Heart',
      ru: 'Классический стиль',
    },
    tags: {
      uk: 'перший канон',
      en: 'first canon',
      ru: 'первый канон',
    },
    lores: {
      uk: 'Класичне піксельне серце з крильцями. Перша відзнака, з якої розпочалася історія спільноти.',
      en: 'Classic pixel heart with wings. The very first badge that started the community.',
      ru: 'Классическое пиксельное сердце с крыльями. Первая отметка, с которой началась история.',
    },
    bloomColor: 'rgba(20, 200, 249, 0.35)',
  },
  {
    id: 'angel',
    code: '02',
    names: {
      uk: 'Світлий ангел',
      en: 'Bright Angel',
      ru: 'Светлый ангел',
    },
    tags: {
      uk: 'золотий німб',
      en: 'golden halo',
      ru: 'золотой нимб',
    },
    lores: {
      uk: 'Білі пір’їнки та сяючий золотистий німб. Символ доброти, спокою та гармонії в чатах.',
      en: 'Soft white feathers and a glowing halo. Symbol of kindness, peace, and good vibes.',
      ru: 'Белые крылья и светящийся золотой нимб. Символ доброты, спокойствия и уюта в чатах.',
    },
    bloomColor: 'rgba(254, 137, 217, 0.40)',
  },
  {
    id: 'dark',
    code: '03',
    names: {
      uk: 'Нічний обсидіан',
      en: 'Night Obsidian',
      ru: 'Ночной обсидиан',
    },
    tags: {
      uk: 'фіолетовий вайб',
      en: 'midnight violet',
      ru: 'фиолетовый вайб',
    },
    lores: {
      uk: 'Темно-фіолетові крила з акуратними шпильками. Для тих, хто любить затишні нічні переписки.',
      en: 'Deep purple wings with cyber clips. For those who enjoy late-night conversations.',
      ru: 'Тёмно-фиолетовые крылья с заколками. Для тех, кто любит ночные разговоры в тишине.',
    },
    bloomColor: 'rgba(142, 107, 255, 0.40)',
  },
  {
    id: 'glitch',
    code: '04',
    names: {
      uk: 'Кібер-глітч',
      en: 'Cyber Glitch',
      ru: 'Кибер-глитч',
    },
    tags: {
      uk: 'rgb ефект',
      en: 'rgb chromatic',
      ru: 'rgb эффект',
    },
    lores: {
      uk: 'Цифрове мерехтіння кольорів та атмосфера ретро-екранів. Для справжніх поціновувачів естетики збоїв.',
      en: 'Chromatic color shift and retro CRT vibes. For glitch art and digital aesthetic fans.',
      ru: 'Цифровое мерцание цветов и ретро-экраны. Для любителей глитч-эстетики и киберпанка.',
    },
    bloomColor: 'rgba(255, 0, 127, 0.40)',
  },
  {
    id: 'pink',
    code: '05',
    names: {
      uk: 'Рожевий стрімер',
      en: 'Pink Streamer',
      ru: 'Розовый стример',
    },
    tags: {
      uk: 'яскрава енергія',
      en: 'streamer energy',
      ru: 'яркая энергия',
    },
    lores: {
      uk: 'Неоново-рожеве сердечко з подвійними шевронами. Знак щирих емоцій та живої енергії.',
      en: 'Neon pink heart with wing chevrons. A sign of bright emotions and playful energy.',
      ru: 'Неоново-розовое сердечко с крылышками. Знак ярких эмоций и дружеской энергии.',
    },
    bloomColor: 'rgba(254, 137, 217, 0.40)',
  },
  {
    id: 'cyan',
    code: '06',
    names: {
      uk: 'Крижаний циан',
      en: 'Icy Cyan',
      ru: 'Ледяной циан',
    },
    tags: {
      uk: 'кришталеве сяйво',
      en: 'ice crystals',
      ru: 'кристальное сияние',
    },
    lores: {
      uk: 'Холодний блакитний колір з білими акцентами. Символ чистоти, швидкості та точності.',
      en: 'Crisp electric cyan with clean white highlights. Symbol of clarity and speed.',
      ru: 'Холодный голубой цвет с белыми акцентами. Символ чистоты, скорости и свежести.',
    },
    bloomColor: 'rgba(20, 200, 249, 0.35)',
  },
  {
    id: 'devil',
    code: '07',
    names: {
      uk: 'Зухвалий чортик',
      en: 'Rebel Devil',
      ru: 'Дерзкий чёртик',
    },
    tags: {
      uk: 'відзнака засновника',
      en: 'founder badge',
      ru: 'знак создателя',
    },
    lores: {
      uk: 'Гострі червоні ріжки та крила кажана. Особиста відзнака засновника проєкту та сміливого характеру.',
      en: 'Sharp red horns and bat wings. Official personal badge of the founder and bold character.',
      ru: 'Острые красные рожки и крылышки летучей мыши. Личный знак создателя и бунтарского характера.',
    },
    bloomColor: 'rgba(255, 0, 85, 0.40)',
  },
  {
    id: 'rainbow',
    code: '08',
    names: {
      uk: 'Веселковий спектр',
      en: 'Rainbow Spectrum',
      ru: 'Радужный спектр',
    },
    tags: {
      uk: 'святковий настрій',
      en: 'all colors',
      ru: 'все цвета',
    },
    lores: {
      uk: 'П’ятиколірні пір’їнки та тепле сяйво. Відзнака творчості, радості та різноманіття думок.',
      en: 'Five vibrant color layers and warm glow. A badge of creativity, celebration, and joy.',
      ru: 'Пятицветные крылышки и тёплое сияние. Знак творчества, радости и хорошего настроения.',
    },
    bloomColor: 'rgba(255, 230, 109, 0.35)',
  },
  {
    id: 'outline',
    code: '09',
    names: {
      uk: 'Чистий контур',
      en: 'Minimal Outline',
      ru: 'Чистый контур',
    },
    tags: {
      uk: '1-бітний мінімал',
      en: '1-bit wireframe',
      ru: '1-битный минимал',
    },
    lores: {
      uk: 'Тонкий прозорий контур без зайвої заливки. Стриманий стиль для любителів суворого мінімалізму.',
      en: 'Thin wireframe contour without heavy fills. Understated style for pure minimalists.',
      ru: 'Тонкий прозрачный контур без заливки. Сдержанный стиль для любителей строгого минимализма.',
    },
    bloomColor: 'rgba(20, 200, 249, 0.25)',
  },
  {
    id: 'premium',
    code: '10',
    names: {
      uk: 'Золота корона',
      en: 'Royal Crown',
      ru: 'Золотая корона',
    },
    tags: {
      uk: 'особливий статус',
      en: 'special status',
      ru: 'особый статус',
    },
    lores: {
      uk: 'Золота корона та янтарні крила. Відзнака особливого внеску в розвиток проєкту Yumigram.',
      en: 'Golden crown with radiant amber wings. Badge of high achievement and special contribution.',
      ru: 'Золотая корона и янтарные крылья. Знак особого вклада в развитие проекта Yumigram.',
    },
    bloomColor: 'rgba(255, 215, 0, 0.40)',
  },
];

export function BadgePixelArt({ id, size = 64, className = '' }: { id: string; size?: number; className?: string }) {
  const renderHeart = (coreColor: string, contourColor: string, highlightColor?: string) => (
    <>
      <rect x="10" y="7" width="3" height="1" fill={coreColor} />
      <rect x="15" y="7" width="3" height="1" fill={coreColor} />
      <rect x="9" y="8" width="10" height="2" fill={coreColor} />
      <rect x="8" y="10" width="12" height="2" fill={coreColor} />
      <rect x="9" y="12" width="10" height="1" fill={coreColor} />
      <rect x="10" y="13" width="8" height="1" fill={coreColor} />
      <rect x="11" y="14" width="6" height="1" fill={coreColor} />
      <rect x="12" y="15" width="4" height="1" fill={coreColor} />
      <rect x="13" y="16" width="2" height="1" fill={coreColor} />
      <rect x="10" y="6" width="3" height="1" fill={contourColor} />
      <rect x="15" y="6" width="3" height="1" fill={contourColor} />
      <rect x="8" y="8" width="1" height="4" fill={contourColor} />
      <rect x="19" y="8" width="1" height="4" fill={contourColor} />
      {highlightColor && (
        <>
          <rect x="10" y="8" width="2" height="1" fill={highlightColor} />
          <rect x="10" y="9" width="1" height="1" fill={highlightColor} />
        </>
      )}
    </>
  );

  const renderWings = (fillColor: string, leftTip: string, rightTip: string) => (
    <>
      <rect x="4" y="5" width="5" height="1" fill={fillColor} />
      <rect x="3" y="6" width="6" height="1" fill={fillColor} />
      <rect x="1" y="7" width="8" height="3" fill={fillColor} />
      <rect x="2" y="10" width="6" height="1" fill={fillColor} />
      <rect x="4" y="11" width="3" height="2" fill={fillColor} />

      <rect x="19" y="5" width="5" height="1" fill={fillColor} />
      <rect x="19" y="6" width="6" height="1" fill={fillColor} />
      <rect x="19" y="7" width="8" height="3" fill={fillColor} />
      <rect x="20" y="10" width="6" height="1" fill={fillColor} />
      <rect x="21" y="11" width="3" height="2" fill={fillColor} />

      <rect x="1" y="8" width="2" height="2" fill={leftTip} />
      <rect x="4" y="12" width="3" height="1" fill={leftTip} />
      <rect x="25" y="8" width="2" height="2" fill={rightTip} />
      <rect x="21" y="12" width="3" height="1" fill={rightTip} />
    </>
  );

  const renderEyes = (eyeColor: string) => (
    <>
      <rect x="11" y="9.5" width="1.5" height="1.5" fill={eyeColor} />
      <rect x="15.5" y="9.5" width="1.5" height="1.5" fill={eyeColor} />
    </>
  );

  const renderCrossEyes = (eyeColor: string) => (
    <>
      <rect x="11.5" y="9.5" width="1" height="2" fill={eyeColor} />
      <rect x="10.5" y="10.5" width="3" height="1" fill={eyeColor} />
      <rect x="15.5" y="9.5" width="1" height="2" fill={eyeColor} />
      <rect x="14.5" y="10.5" width="3" height="1" fill={eyeColor} />
    </>
  );

  return (
    <svg
      viewBox="0 0 28 22"
      width={size}
      height={(size * 22) / 28}
      shapeRendering="crispEdges"
      className={`badge-pixel-art ${className}`}
      aria-hidden="true"
    >
      {id === 'original' && (
        <>
          <rect x="10" y="3" width="8" height="1" fill="#14C8F9" />
          <rect x="12" y="4" width="4" height="1" fill="#14C8F9" />
          {renderWings('#FCFEFF', '#14C8F9', '#FE89D9')}
          {renderHeart('#0F141C', '#14C8F9', '#FFFFFF')}
          {renderEyes('#FFFFFF')}
        </>
      )}

      {id === 'angel' && (
        <>
          <rect x="10" y="1" width="8" height="1" fill="#FFE66D" />
          <rect x="8" y="2" width="2" height="1" fill="#FFE66D" />
          <rect x="18" y="2" width="2" height="1" fill="#FFE66D" />
          <rect x="11" y="1.4" width="6" height="0.6" fill="#FFFFFF" />
          {renderWings('#FCFEFF', '#14C8F9', '#FE89D9')}
          {renderHeart('#F3BBE7', '#6ADCEA', '#FFFFFF')}
          {renderCrossEyes('#14C8F9')}
        </>
      )}

      {id === 'dark' && (
        <>
          <rect x="7" y="6" width="2" height="2" fill="#FE89D9" />
          <rect x="19" y="6" width="2" height="2" fill="#14C8F9" />
          {renderWings('#1C132B', '#8E6BFF', '#8E6BFF')}
          {renderHeart('#130A1F', '#8E6BFF', '#D7C5F1')}
          {renderEyes('#D7C5F1')}
        </>
      )}

      {id === 'glitch' && (
        <>
          <g opacity="0.4" transform="translate(-1.4, 0)">
            {renderWings('#FF0055', '#FF007F', '#FF007F')}
            {renderHeart('#330015', '#FF0055')}
          </g>
          <g opacity="0.4" transform="translate(1.4, 0)">
            {renderWings('#14C8F9', '#00F0FF', '#00F0FF')}
            {renderHeart('#002233', '#14C8F9')}
          </g>
          {renderWings('#FFFFFF', '#FF007F', '#14C8F9')}
          {renderHeart('#0D0B14', '#14C8F9', '#FFFFFF')}
          {renderEyes('#FF007F')}
          <rect x="4" y="7" width="20" height="0.6" fill="rgba(20,200,249,0.4)" />
          <rect x="4" y="11" width="20" height="0.6" fill="rgba(20,200,249,0.4)" />
          <rect x="4" y="15" width="20" height="0.6" fill="rgba(20,200,249,0.4)" />
        </>
      )}

      {id === 'pink' && (
        <>
          <rect x="10" y="3" width="8" height="1" fill="#FE89D9" />
          {renderWings('#FEECFA', '#FE89D9', '#FE89D9')}
          {renderHeart('#1B0F1C', '#FE89D9', '#FFFFFF')}
          <rect x="11" y="11" width="6" height="1" fill="#FE89D9" />
          <rect x="12" y="13" width="4" height="1" fill="#FE89D9" />
          {renderEyes('#FEECFA')}
        </>
      )}

      {id === 'cyan' && (
        <>
          <rect x="10" y="3" width="8" height="1" fill="#14C8F9" />
          {renderWings('#DAE8FF', '#14C8F9', '#14C8F9')}
          {renderHeart('#0A1822', '#14C8F9', '#DAE8FF')}
          {renderEyes('#FFFFFF')}
        </>
      )}

      {id === 'devil' && (
        <>
          <rect x="9" y="4" width="2" height="3" fill="#FF0055" />
          <rect x="8" y="3" width="2" height="2" fill="#FF0055" />
          <rect x="17" y="4" width="2" height="3" fill="#FF0055" />
          <rect x="18" y="3" width="2" height="2" fill="#FF0055" />
          {renderWings('#FF3377', '#B8003D', '#B8003D')}
          {renderHeart('#1C0A15', '#FF0055', '#FFB3C6')}
          {renderEyes('#FFB3C6')}
        </>
      )}

      {id === 'rainbow' && (
        <>
          <rect x="4" y="5" width="5" height="1.5" fill="#FF3377" />
          <rect x="19" y="5" width="5" height="1.5" fill="#FF3377" />
          <rect x="3" y="6.5" width="6" height="1.5" fill="#FF8800" />
          <rect x="19" y="6.5" width="6" height="1.5" fill="#FF8800" />
          <rect x="1" y="8" width="8" height="1.5" fill="#FFE66D" />
          <rect x="19" y="8" width="8" height="1.5" fill="#FFE66D" />
          <rect x="2" y="9.5" width="6" height="1.5" fill="#06D6A0" />
          <rect x="20" y="9.5" width="6" height="1.5" fill="#06D6A0" />
          <rect x="4" y="11" width="3" height="2" fill="#14C8F9" />
          <rect x="21" y="11" width="3" height="2" fill="#8E6BFF" />
          {renderHeart('#10141E', '#FFE66D', '#FFF5B8')}
          {renderEyes('#FFFFFF')}
        </>
      )}

      {id === 'outline' && (
        <g fill="none" stroke="#14C8F9" strokeWidth="1">
          <rect x="4.5" y="5.5" width="4.5" height="6.5" />
          <rect x="19" y="5.5" width="4.5" height="6.5" />
          <polygon points="14,16 9,11 11.5,8 14,10 16.5,8 19,11" />
          <rect x="11.5" y="9.5" width="1" height="1" fill="#14C8F9" stroke="none" />
          <rect x="15.5" y="9.5" width="1" height="1" fill="#14C8F9" stroke="none" />
        </g>
      )}

      {id === 'premium' && (
        <>
          <rect x="10" y="2" width="2" height="3" fill="#FFD700" />
          <rect x="13" y="1" width="2" height="4" fill="#FFD700" />
          <rect x="16" y="2" width="2" height="3" fill="#FFD700" />
          <rect x="10" y="5" width="8" height="1" fill="#FFD700" />
          {renderWings('#FFE066', '#CC8800', '#CC8800')}
          {renderHeart('#1B1408', '#FFD700', '#FFFDF0')}
          <rect x="10" y="11" width="8" height="1" fill="#FFD700" />
          {renderEyes('#FFF5B8')}
        </>
      )}
    </svg>
  );
}
