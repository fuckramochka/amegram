import React, { useState } from 'react';
import { BADGES, BadgePixelArt } from '../data/badges';
import { ReleaseInfo, SupabaseStats } from '../types';

interface VariantProps {
  release: ReleaseInfo | null;
  stats: SupabaseStats | null;
  onOpenDownloadModal: () => void;
}

export const MonoUtilityVariant: React.FC<VariantProps> = ({
  release,
  stats,
  onOpenDownloadModal,
}) => {
  const [copiedSha, setCopiedSha] = useState(false);
  const [copiedAdb, setCopiedAdb] = useState(false);

  const copy = (text: string, setter: (v: boolean) => void) => {
    navigator.clipboard.writeText(text);
    setter(true);
    setTimeout(() => setter(false), 1800);
  };

  return (
    <div className="min-h-screen bg-black text-white font-mono selection:bg-white selection:text-black">
      {/* Utility Top Bar */}
      <header className="border-b border-white/20 px-4 sm:px-8 py-4 flex items-center justify-between text-xs">
        <div className="flex items-center gap-3">
          <span className="font-bold text-sm tracking-widest">YUMIGRAM</span>
          <span className="text-zinc-500">//</span>
          <span className="text-zinc-400">MIYUMI ໒꒱</span>
        </div>
        <div className="flex items-center gap-6">
          <a href="https://github.com/fuckramochka/amegram" target="_blank" rel="noreferrer" className="text-zinc-400 hover:text-white">
            [GITHUB]
          </a>
          <a href="https://github.com/fuckramochka/yuimodules" target="_blank" rel="noreferrer" className="text-zinc-400 hover:text-white">
            [YUIMODULES]
          </a>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-4xl mx-auto px-4 sm:px-8 py-12 sm:py-16 space-y-16">
        {/* Release Box */}
        <section className="border-2 border-white p-6 sm:p-8 space-y-6">
          <div className="flex items-center justify-between border-b border-white/20 pb-4">
            <div>
              <span className="text-[10px] text-zinc-400 uppercase tracking-widest block">АКТУАЛЬНИЙ РЕЛІЗ</span>
              <h1 className="text-2xl sm:text-3xl font-bold uppercase mt-1">
                YUMIGRAM {release?.tag || 'v1.0.5'}
              </h1>
            </div>
            <span className="text-xs border border-white px-2 py-1 uppercase">
              16KB COMPLIANT
            </span>
          </div>

          <p className="text-xs text-zinc-300 font-sans leading-relaxed">
            Модифікований Telegram-клієнт для Android: подвійне дно Vault AES-256 (Duress PIN), пісочниця розширень YuiModules (.hmod), верифіковані піксельні відзнаки Miyumi ໒꒱ та TikTok MI без водяних знаків.
          </p>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <button
              onClick={onOpenDownloadModal}
              className="px-6 py-3 bg-white text-black font-bold text-xs hover:bg-zinc-200 transition-colors cursor-pointer"
            >
              ↓ СКАЧАТИ APK ({release?.size || '56.4 MB'})
            </button>
            <button
              onClick={() => copy('adb install -r yumigram.apk', setCopiedAdb)}
              className="px-4 py-3 border border-white/40 text-xs hover:border-white transition-colors cursor-pointer"
            >
              $ adb install -r yumigram.apk {copiedAdb ? '[СКОПІЙОВАНО]' : ''}
            </button>
          </div>

          <div className="pt-4 border-t border-white/20 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-[11px] text-zinc-400">
            <div className="truncate">
              SHA-256: <span className="text-white">9d8e74a1fc6c528b12e30f78d91a78c52e46b9a89d31f16c...</span>
            </div>
            <button
              onClick={() => copy('9d8e74a1fc6c528b12e30f78d91a78c52e46b9a89d31f16c1729b8c0e271a39d', setCopiedSha)}
              className="text-[10px] underline hover:text-white cursor-pointer"
            >
              {copiedSha ? '✓ СКОПІЙОВАНО' : '[КОПІЮВАТИ ПОВНИЙ ХЕШ]'}
            </button>
          </div>
        </section>

        {/* Feature Specs Table */}
        <section className="space-y-4">
          <h2 className="text-xs uppercase tracking-widest text-zinc-400 font-bold">
            // СИСТЕМНІ ХАРАКТЕРИСТИКИ
          </h2>

          <div className="border border-white/20 divide-y divide-white/20 text-xs">
            <div className="p-4 grid sm:grid-cols-3 gap-2">
              <span className="font-bold text-white">ШИФРУВАННЯ VAULT</span>
              <span className="sm:col-span-2 text-zinc-300">
                AES-256-GCM віртуальний диск. Окремий Duress PIN відкриває камуфляжний порожній профіль без компрометуючих маркерів.
              </span>
            </div>
            <div className="p-4 grid sm:grid-cols-3 gap-2">
              <span className="font-bold text-white">ПІСОЧНИЦЯ YUIMODULES</span>
              <span className="sm:col-span-2 text-zinc-300">
                Модулі .hmod у середовищі Dex та Chaquopy (Python 3.11). Гаряче підключення та вимкнення без перезапуску застосунку.
              </span>
            </div>
            <div className="p-4 grid sm:grid-cols-3 gap-2">
              <span className="font-bold text-white">ВІДЗНАКИ MIYUMI ໒꒱</span>
              <span className="sm:col-span-2 text-zinc-300">
                10 канонічних піксельних значків, верифікованих через Supabase ({stats ? stats.verifiedBadges : '1,240+'} у реєстрі). Видача тільки засновником.
              </span>
            </div>
            <div className="p-4 grid sm:grid-cols-3 gap-2">
              <span className="font-bold text-white">TIKTOK MEDIA INTEGRATOR</span>
              <span className="sm:col-span-2 text-zinc-300">
                Перехоплення посилань TikTok та Shorts, автоматичне збереження HD без ватермарки та видобування чистого аудіо.
              </span>
            </div>
            <div className="p-4 grid sm:grid-cols-3 gap-2">
              <span className="font-bold text-white">ВИРІВНЮВАННЯ 16 КБ</span>
              <span className="sm:col-span-2 text-zinc-300">
                ELF 16 KB Page-Size для ядра Linux та Android 15+. Повна апаратна оптимізація під сучасні SoC.
              </span>
            </div>
          </div>
        </section>

        {/* Badges Strip */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-xs uppercase tracking-widest text-zinc-400 font-bold">
              // РЕЄСТР ВІДЗНАК (10 STYLES)
            </h2>
            <span className="text-[10px] text-zinc-500">SUPABASE VERIFIED</span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 border border-white/20 p-2">
            {BADGES.map((b) => (
              <div key={b.id} className="border border-white/10 p-2.5 text-center">
                <div className="h-8 flex items-center justify-center">
                  <BadgePixelArt id={b.id} size={24} />
                </div>
                <div className="text-[10px] font-bold truncate mt-1 text-zinc-200">
                  {b.name}
                </div>
                <div className="text-[8px] text-zinc-500 font-mono truncate">
                  {b.code}
                </div>
              </div>
            ))}
          </div>
        </section>
      </main>

      {/* Utility Footer */}
      <footer className="border-t border-white/20 px-4 sm:px-8 py-6 flex items-center justify-between text-[11px] text-zinc-500">
        <div>YUMIGRAM · GPL-3.0 · MIYUMI ໒꒱ 2026</div>
        <div>NO ADS · ZERO TRACKERS</div>
      </footer>
    </div>
  );
};
