import { useState } from 'react';

export interface BadgeInfo {
  id: string;
  code: string;
  name: string;
  tag: string;
  lore: string;
  bloomColor: string;
}

export const BADGES: BadgeInfo[] = [
  {
    id: 'original',
    code: '01 — ORIGINAL',
    name: 'Класичний стиль',
    tag: 'canonical arrow',
    lore: 'Класичне піксельне серце з крилами, антеною та м\'яким неоновим сяйвом. Канонічна відзнака, з якої розпочалася історія спільноти.',
    bloomColor: 'rgba(20, 200, 249, 0.28)',
  },
  {
    id: 'angel',
    code: '02 — ANGEL',
    name: 'Небесний ангел',
    tag: 'celestial halo',
    lore: 'Ангельські крила з ширяючим золотистим німбом, пастельними пір\'їнами та чистим серцем. Відзнака гармонії, світлих намірів та піднесення.',
    bloomColor: 'rgba(254, 137, 217, 0.35)',
  },
  {
    id: 'dark',
    code: '03 — DARK',
    name: 'Нічний обсидіан',
    tag: 'midnight violet',
    lore: 'Нічний обсидіан з оксамитовим фіолетовим краєм, кібер-шпильками та лавандовим серцем. Для поціновувачів таємничої меланхолії та нічного вайбу.',
    bloomColor: 'rgba(142, 107, 255, 0.35)',
  },
  {
    id: 'glitch',
    code: '04 — GLITCH',
    name: 'Кібер-глітч',
    tag: 'rgb chromatic',
    lore: 'Хроматичне розщеплення кольору з ефектом RGB-зсуву та мерехтливими сканлайнами. Для поціновувачів естетики CRT-екранів, ретро-касет та цифрових збоїв.',
    bloomColor: 'rgba(255, 0, 127, 0.32)',
  },
  {
    id: 'pink',
    code: '05 — PINK',
    name: 'Неоново-рожевий',
    tag: 'streamer heart',
    lore: 'Неоново-рожеве піксельне серце з подвійними шевронами та блискітками. Символ невичерпної енергії, цифрових емоцій та відданості спільноті.',
    bloomColor: 'rgba(254, 137, 217, 0.35)',
  },
  {
    id: 'cyan',
    code: '06 — CYAN',
    name: 'Кібер-блакитний',
    tag: 'cyberspace matrix',
    lore: 'Електричний блакитний стиль з антеною-візором, крижаним сяйвом та білими акцентами. Символізує швидкість, технологічність та цифровий простір.',
    bloomColor: 'rgba(20, 200, 249, 0.30)',
  },
  {
    id: 'devil',
    code: '07 — DEVIL',
    name: 'Зухвалий чортик',
    tag: 'chaos & charm',
    lore: 'Гострі ріжки та крила кажана з гарячим неоновим контуром. Відзнака бунтарського характеру, зухвалого шарму та свободи самовираження.',
    bloomColor: 'rgba(255, 0, 85, 0.30)',
  },
  {
    id: 'rainbow',
    code: '08 — RAINBOW',
    name: 'Призматичний спектр',
    tag: 'prismatic feathers',
    lore: 'П\'ятирівневий призматичний спектр із золотим контуром серця. Символ яскравих емоцій, творчого розмаїття та святкового настрою.',
    bloomColor: 'rgba(255, 230, 109, 0.30)',
  },
  {
    id: 'outline',
    code: '09 — OUTLINE',
    name: 'Кібер-вайрфрейм',
    tag: '1-bit wireframe',
    lore: 'Мінімалістичний 1-піксельний контур у стилі ретро-дисплеїв. Чиста цифрова естетика — точні лінії, прозора форма та жодних зайвих деталей.',
    bloomColor: 'rgba(20, 200, 249, 0.22)',
  },
  {
    id: 'premium',
    code: '10 — PREMIUM',
    name: 'Королівська корона',
    tag: 'royal ascension',
    lore: 'Королівська золота корона, сяючий німб та янтарні крила з нагрудними шевронами. Відзнака визнання найвищих досягнень та статусу в Miogram.',
    bloomColor: 'rgba(255, 215, 0, 0.35)',
  },
];

export function BadgePixelArt({ id, size = 64, className = '' }: { id: string; size?: number; className?: string }) {
  // Common Heart Shape
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

      {/* Contour */}
      <rect x="10" y="6" width="3" height="1" fill={contourColor} />
      <rect x="15" y="6" width="3" height="1" fill={contourColor} />
      <rect x="8" y="8" width="1" height="4" fill={contourColor} />
      <rect x="19" y="8" width="1" height="4" fill={contourColor} />

      {/* Highlight */}
      {highlightColor && (
        <>
          <rect x="10" y="8" width="2" height="1" fill={highlightColor} />
          <rect x="10" y="9" width="1" height="1" fill={highlightColor} />
        </>
      )}
    </>
  );

  // Common Wings
  const renderWings = (fillColor: string, leftTip: string, rightTip: string) => (
    <>
      {/* Left Wing */}
      <rect x="4" y="5" width="5" height="1" fill={fillColor} />
      <rect x="3" y="6" width="6" height="1" fill={fillColor} />
      <rect x="1" y="7" width="8" height="3" fill={fillColor} />
      <rect x="2" y="10" width="6" height="1" fill={fillColor} />
      <rect x="4" y="11" width="3" height="2" fill={fillColor} />

      {/* Right Wing */}
      <rect x="19" y="5" width="5" height="1" fill={fillColor} />
      <rect x="19" y="6" width="6" height="1" fill={fillColor} />
      <rect x="19" y="7" width="8" height="3" fill={fillColor} />
      <rect x="20" y="10" width="6" height="1" fill={fillColor} />
      <rect x="21" y="11" width="3" height="2" fill={fillColor} />

      {/* Fringe / Tips */}
      <rect x="1" y="8" width="2" height="2" fill={leftTip} />
      <rect x="4" y="12" width="3" height="1" fill={leftTip} />
      <rect x="25" y="8" width="2" height="2" fill={rightTip} />
      <rect x="21" y="12" width="3" height="1" fill={rightTip} />
    </>
  );

  // Eyes
  const renderEyes = (eyeColor: string) => (
    <>
      <rect x="11" y="9.5" width="1.5" height="1.5" fill={eyeColor} />
      <rect x="15.5" y="9.5" width="1.5" height="1.5" fill={eyeColor} />
    </>
  );

  // Cross Eyes
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
          {/* Floating Halo */}
          <rect x="10" y="1" width="8" height="1" fill="#FFE66D" />
          <rect x="8" y="2" width="2" height="1" fill="#FFE66D" />
          <rect x="18" y="2" width="2" height="1" fill="#FFE66D" />
          <rect x="11" y="1.4" width="6" height="0.6" fill="#FFFFFF" />
          {/* Pastel Wings: Left KAngel Cyan, Right KAngel Pink */}
          {renderWings('#FCFEFF', '#14C8F9', '#FE89D9')}
          {renderHeart('#F3BBE7', '#6ADCEA', '#FFFFFF')}
          {renderCrossEyes('#14C8F9')}
        </>
      )}

      {id === 'dark' && (
        <>
          {/* Ame Cyber Hairclips: Pink & Cyan */}
          <rect x="7" y="6" width="2" height="2" fill="#FE89D9" />
          <rect x="19" y="6" width="2" height="2" fill="#14C8F9" />
          {renderWings('#1C132B', '#8E6BFF', '#8E6BFF')}
          {renderHeart('#130A1F', '#8E6BFF', '#D7C5F1')}
          {renderEyes('#D7C5F1')}
        </>
      )}

      {id === 'glitch' && (
        <>
          {/* Sliced Chromatic Aberration */}
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
          {/* Scanlines */}
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
          {/* Horns */}
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
          {/* Rainbow 5-tier feathers */}
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
          {/* Crown */}
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

export default function BadgeShowcase() {
  const [selectedId, setSelectedId] = useState('angel');
  const activeBadge = BADGES.find(b => b.id === selectedId) || BADGES[0];

  return (
    <section className="badges-section container section" id="badges" aria-labelledby="badges-heading">
      <div className="catalog-window badges-window">
        <div className="window-bar">
          <span>✧ miogram_badges.exe</span>
          <span className="catalog-bar-note">10 pixel badges collection</span>
        </div>

        <div className="badges-content">
          <div className="catalog-heading">
            <div>
              <div className="section-kicker">КОЛЕКЦІЯ ПІКСЕЛЬНИХ ВІДЗНАК</div>
              <h2 id="badges-heading">
                Твій характер. <span>В одному піксельному серці.</span>
              </h2>
            </div>
            <p>
              Відкривай та обирай свій стиль у спільноті Miogram.<br />
              Хмарна синхронізація Supabase миттєво показує твій бейдж усім співрозмовникам.
            </p>
          </div>

          <div className="badge-showcase-layout">
            {/* Active Badge Hero Showcase */}
            <div
              className="badge-active-preview"
              style={{
                background: `radial-gradient(circle at 50% 40%, ${activeBadge.bloomColor} 0%, rgba(255,255,255,0.02) 75%)`,
              }}
            >
              <div className="badge-large-float">
                <span className="badge-sparkle s1">✦</span>
                <span className="badge-sparkle s2">✧</span>
                <span className="badge-sparkle s3">✦</span>
                <BadgePixelArt id={activeBadge.id} size={140} className="floating-badge-svg" />
              </div>

              <div className="badge-active-meta">
                <span className="badge-active-code">{activeBadge.code}</span>
                <h3>{activeBadge.name}</h3>
                <span className="badge-active-tag">{activeBadge.tag}</span>
                <p className="badge-active-lore">{activeBadge.lore}</p>
              </div>
            </div>

            {/* Badges Grid Selector */}
            <div className="badges-selector-grid" role="group" aria-label="Вибір бейджа">
              {BADGES.map(badge => {
                const isSelected = badge.id === selectedId;
                return (
                  <button
                    key={badge.id}
                    className={`badge-card-button ${isSelected ? 'selected' : ''}`}
                    onClick={() => setSelectedId(badge.id)}
                    aria-pressed={isSelected}
                  >
                    <div className="badge-card-icon">
                      <BadgePixelArt id={badge.id} size={38} />
                    </div>
                    <div className="badge-card-info">
                      <strong>{badge.code.split(' — ')[1]}</strong>
                      <small>{badge.name}</small>
                    </div>
                    {isSelected && <span className="badge-check-mark">✓</span>}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="badges-footer-note">
            <span className="status-dot" style={{ background: '#10b981', boxShadow: '0 0 8px #10b981' }} />
            <span>Усі 10 стилів доступні в Android-клієнті MioGram через Supabase Cloud Sync</span>
          </div>
        </div>
      </div>
    </section>
  );
}
