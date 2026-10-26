import React, { useState } from 'react';
import { BADGES, BadgePixelArt } from '../data/badges';
import { ReleaseInfo, SupabaseStats } from '../types';

interface VariantProps {
  release: ReleaseInfo | null;
  stats: SupabaseStats | null;
  onOpenDownloadModal: () => void;
}

export const NothingStarkVariant: React.FC<VariantProps> = ({
  release,
  stats,
  onOpenDownloadModal,
}) => {
  const [selectedBadge, setSelectedBadge] = useState(BADGES[6]); // DEVIL
  const [copiedSha, setCopiedSha] = useState(false);

  const copySha = () => {
    navigator.clipboard.writeText('9d8e74a1fc6c528b12e30f78d91a78c52e46b9a89d31f16c1729b8c0e271a39d');
    setCopiedSha(true);
    setTimeout(() => setCopiedSha(false), 1800);
  };

  const specs = [
    {
      index: '01',
      label: 'VAULT AES-256',
      spec: 'AES-256-GCM / DURESS PIN',
      desc: 'Камуфляжне подвійне дно. Введення тривожного коду відкриває порожній нейтральний простір.',
    },
    {
      index: '02',
      label: 'YUIMODULES',
      spec: '.HMOD / CHAQUOPY 3.11',
      desc: 'Ізольована пісочниця розширень. Завантаження скриптів Python та Java без руту та перезапуску.',
    },
    {
      index: '03',
      label: 'MIYUMI BADGES',
      spec: 'SUPABASE REGISTRY / ໒꒱',
      desc: '10 канонічних піксельних відзнак. Хмарна верифікація та прив’язка до Telegram ID.',
    },
    {
      index: '04',
      label: 'MEDIA ENGINE',
      spec: 'TIKTOK MI / HI-FI FLAC',
      desc: 'Миттєве завантаження TikTok/Shorts без водяних знаків. Фоновий плеєр з караоке-текстами.',
    },
    {
      index: '05',
      label: 'P2P SYNC',
      spec: 'CLOUDFLARE QUICK TUNNEL',
      desc: 'Віддалене керування модулями та конфігураціями без відкритих портів та публічних IP.',
    },
    {
      index: '06',
      label: 'KERNEL COMPLIANCE',
      spec: '16 KB ELF / ANDROID 15+',
      desc: 'Повна сумісність із сторінками пам’яті 16 КБ ядра Linux. Стабільні 120 FPS без просідань.',
    },
  ];

  return (
    <div className="min-h-screen bg-black text-white font-mono selection:bg-white selection:text-black">
      {/* Top Stark Statusbar */}
      <div className="border-b border-zinc-900 px-4 sm:px-8 py-3 flex items-center justify-between text-[11px] text-zinc-500">
        <div className="flex items-center gap-3">
          <span className="w-1.5 h-1.5 rounded-full bg-red-600 inline-block animate-pulse" />
          <span className="text-zinc-300 font-bold tracking-widest uppercase">
            YUMIGRAM (1)
          </span>
          <span className="hidden sm:inline text-zinc-600">//</span>
          <span className="hidden sm:inline text-zinc-400">BY MIYUMI ໒꒱</span>
        </div>
        <div className="flex items-center gap-4 text-zinc-400 font-mono text-[10px]">
          <span>[REL: {release?.tag || 'v1.0.5'}]</span>
          <span>[ARCH: 16KB-ELF]</span>
          <span>[VERIFIED: {stats ? stats.verifiedBadges : '1,240+'}]</span>
        </div>
      </div>

      {/* Main Container */}
      <main className="max-w-6xl mx-auto px-4 sm:px-8 py-16 sm:py-24 space-y-24">
        {/* Hero Section */}
        <section className="space-y-8">
          <div className="inline-block px-2.5 py-1 text-[10px] tracking-widest uppercase border border-zinc-800 text-zinc-400 bg-zinc-950">
            SYSTEM VERSION: 1.0.5 / STABLE RELEASE
          </div>

          <h1 className="text-5xl sm:text-7xl lg:text-8xl font-black tracking-tighter leading-none uppercase text-white font-sans">
            YUMIGRAM.
          </h1>

          <p className="text-lg sm:text-xl text-zinc-400 max-w-2xl font-sans font-normal leading-relaxed">
            Модифікований клієнт Telegram для Android. Створений для повної приватності, модульності без рестарту та високої продуктивності.
          </p>

          {/* Action Row */}
          <div className="flex flex-wrap items-center gap-4 pt-4">
            <button
              onClick={onOpenDownloadModal}
              className="px-8 py-4 bg-white hover:bg-zinc-200 text-black font-bold text-xs tracking-wider uppercase transition-colors flex items-center gap-3 cursor-pointer"
            >
              <span>Завантажити APK</span>
              <span className="text-[10px] bg-black/15 px-1.5 py-0.5 rounded font-mono">
                {release?.size || '56.4 MB'}
              </span>
            </button>

            <a
              href="https://github.com/fuckramochka/amegram"
              target="_blank"
              rel="noreferrer"
              className="px-6 py-4 border border-zinc-800 hover:border-zinc-500 text-zinc-300 hover:text-white text-xs tracking-wider uppercase transition-colors"
            >
              Вихідний код (GitHub) ↗
            </a>
          </div>

          {/* Quick Monospace Bar */}
          <div className="pt-4 flex flex-wrap items-center gap-6 text-[11px] text-zinc-500 border-t border-zinc-900">
            <div>
              <span className="text-zinc-600">ЛІЦЕНЗІЯ:</span> <span className="text-zinc-300">GPL-3.0</span>
            </div>
            <div>
              <span className="text-zinc-600">ОС:</span> <span className="text-zinc-300">Android 8.0+ (arm64-v8a)</span>
            </div>
            <div>
              <span className="text-zinc-600">ТРЕКЕРИ:</span> <span className="text-white">0% (ВИДАЛЕНО)</span>
            </div>
          </div>
        </section>

        {/* Technical Specs Matrix - Nothing OS Grid */}
        <section className="space-y-6">
          <div className="flex items-center justify-between border-b border-zinc-900 pb-3">
            <span className="text-xs text-zinc-500 uppercase tracking-widest font-bold">
              // СПЕЦИФІКАЦІЯ СИСТЕМИ
            </span>
            <span className="text-[10px] text-zinc-600">
              6 CORE MODULES
            </span>
          </div>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-px bg-zinc-900 border border-zinc-900">
            {specs.map((s) => (
              <div
                key={s.index}
                className="bg-black p-6 space-y-4 hover:bg-zinc-950 transition-colors"
              >
                <div className="flex items-center justify-between text-[10px]">
                  <span className="text-red-500 font-bold font-mono">
                    ({s.index})
                  </span>
                  <span className="text-zinc-600 font-mono tracking-wider">
                    {s.spec}
                  </span>
                </div>
                <h3 className="text-base font-bold text-white tracking-tight uppercase font-sans">
                  {s.label}
                </h3>
                <p className="text-xs text-zinc-400 font-sans leading-relaxed">
                  {s.desc}
                </p>
              </div>
            ))}
          </div>
        </section>

        {/* Minimal Monochromatic Badge Registry */}
        <section className="space-y-6">
          <div className="flex items-center justify-between border-b border-zinc-900 pb-3">
            <span className="text-xs text-zinc-500 uppercase tracking-widest font-bold">
              // ВІДЗНАКИ СПІЛЬНОТИ MIYUMI ໒꒱
            </span>
            <span className="text-[10px] text-zinc-500">
              SUPABASE CLOUD SYNC · {stats ? stats.verifiedBadges : '1,240+'} ВЕРИФІКОВАНО
            </span>
          </div>

          {/* Badges 10-Item Strip */}
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
            {BADGES.map((b) => {
              const isSelected = selectedBadge.id === b.id;
              return (
                <button
                  key={b.id}
                  onClick={() => setSelectedBadge(b)}
                  className={`p-3 border text-left transition-all cursor-pointer ${
                    isSelected
                      ? 'border-white bg-zinc-950 text-white'
                      : 'border-zinc-900 hover:border-zinc-700 bg-black text-zinc-400'
                  }`}
                >
                  <div className="h-10 flex items-center justify-center">
                    <BadgePixelArt id={b.id} size={32} />
                  </div>
                  <div className="mt-2 text-[10px] font-bold truncate uppercase">
                    {b.name}
                  </div>
                  <div className="text-[9px] text-zinc-600 font-mono truncate">
                    {b.code}
                  </div>
                </button>
              );
            })}
          </div>

          {/* Selected Badge Detail Box */}
          <div className="border border-zinc-900 p-5 bg-zinc-950 flex flex-col sm:flex-row items-center gap-6">
            <div className="w-16 h-16 flex items-center justify-center border border-zinc-800 bg-black flex-shrink-0">
              <BadgePixelArt id={selectedBadge.id} size={48} />
            </div>
            <div className="space-y-1 text-center sm:text-left flex-1">
              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2 text-xs">
                <span className="text-white font-bold">{selectedBadge.name}</span>
                <span className="text-zinc-500 font-mono text-[10px]">[{selectedBadge.code}]</span>
                <span className="text-[10px] px-1.5 py-0.2 bg-zinc-900 border border-zinc-800 text-zinc-400 font-mono">
                  {selectedBadge.tag}
                </span>
              </div>
              <p className="text-xs text-zinc-400 font-sans leading-relaxed">
                {selectedBadge.lore}
              </p>
            </div>
            <div className="text-[10px] text-zinc-500 font-mono text-right flex-shrink-0">
              ВЕРИФІКАЦІЯ: <span className="text-zinc-300">ID 8011880648</span>
            </div>
          </div>
        </section>

        {/* Binary Download & Integrity */}
        <section className="border border-zinc-900 p-8 sm:p-12 space-y-6 bg-zinc-950/50">
          <div className="flex items-center gap-2 text-xs text-zinc-400 uppercase tracking-widest font-bold">
            <span className="w-2 h-2 rounded-full bg-white inline-block" />
            <span>ОФІЦІЙНИЙ РЕЛІЗ ДЛЯ ANDROID</span>
          </div>

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6">
            <div>
              <h2 className="text-3xl font-black text-white uppercase tracking-tight font-sans">
                YUMIGRAM {release?.tag || 'v1.0.5'}
              </h2>
              <p className="text-xs text-zinc-400 mt-1 font-sans">
                Прямий APK з підтримкою сторінок пам'яті 16 КБ. Без реклами та трекерів.
              </p>
            </div>

            <button
              onClick={onOpenDownloadModal}
              className="px-8 py-4 bg-white hover:bg-zinc-200 text-black font-bold text-xs tracking-wider uppercase transition-colors whitespace-nowrap cursor-pointer"
            >
              Завантажити APK ({release?.size || '56.4 MB'})
            </button>
          </div>

          {/* Checksum Row */}
          <div className="pt-4 border-t border-zinc-900 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-zinc-500">
            <div className="truncate">
              <span className="text-zinc-600 font-mono">SHA-256: </span>
              <span className="text-zinc-400 font-mono text-[11px] truncate">
                9d8e74a1fc6c528b12e30f78d91a78c52e46b9a89d31f16c1729b8c0e271a39d
              </span>
            </div>
            <button
              onClick={copySha}
              className="text-[10px] text-zinc-400 hover:text-white border border-zinc-800 hover:border-zinc-600 px-3 py-1 cursor-pointer whitespace-nowrap"
            >
              {copiedSha ? '✓ СКОПІЙОВАНО' : 'КОПІЮВАТИ ХЕШ'}
            </button>
          </div>
        </section>
      </main>

      {/* Stark Minimal Footer */}
      <footer className="border-t border-zinc-900 px-4 sm:px-8 py-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-[11px] text-zinc-500">
        <div>
          YUMIGRAM // СИСТЕМА MIYUMI ໒꒱ // 2026
        </div>
        <div className="flex items-center gap-6">
          <a href="https://github.com/fuckramochka/amegram" className="hover:text-white transition-colors">
            GITHUB
          </a>
          <a href="https://github.com/fuckramochka/amegram/blob/main/LICENSE" className="hover:text-white transition-colors">
            GPL-3.0
          </a>
          <a href="https://github.com/fuckramochka/yuimodules" className="hover:text-white transition-colors">
            YUIMODULES
          </a>
        </div>
      </footer>
    </div>
  );
};
