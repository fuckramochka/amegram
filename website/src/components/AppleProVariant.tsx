import React, { useState } from 'react';
import { BADGES, BadgePixelArt } from '../data/badges';
import { Language, TRANSLATIONS } from '../data/i18n';
import { ReleaseInfo, SupabaseStats } from '../types';

interface VariantProps {
  release: ReleaseInfo | null;
  stats: SupabaseStats | null;
  onOpenDownloadModal: () => void;
  lang: Language;
  onLanguageChange: (lang: Language) => void;
}

const languages: { id: Language; label: string; title: string }[] = [
  { id: 'en', label: 'EN', title: 'English' },
  { id: 'uk', label: 'UA', title: 'Українська' },
  { id: 'ru', label: 'RU', title: 'Русский' },
];

export const AppleProVariant: React.FC<VariantProps> = ({
  release,
  stats,
  onOpenDownloadModal,
  lang,
  onLanguageChange,
}) => {
  const [activeBadge, setActiveBadge] = useState(BADGES[1]); // Bright Angel
  const t = TRANSLATIONS[lang];

  return (
    <div className="min-h-screen bg-[#070708] text-white font-sans selection:bg-white selection:text-black">
      {/* Apple Minimal Floating Header */}
      <header className="sticky top-0 z-40 backdrop-blur-2xl bg-[#070708]/85 border-b border-white/[0.08] px-4 sm:px-10 h-16 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <span className="font-extrabold text-base tracking-tight text-white">Yumigram</span>
          <span className="text-[10px] px-2 py-0.5 rounded-full bg-white/[0.08] text-zinc-300 font-mono">
            ໒꒱ Miyumi
          </span>
        </div>

        {/* Center Nav */}
        <nav className="hidden lg:flex items-center gap-8 text-xs text-zinc-400 font-medium">
          <a href="#features" className="hover:text-white transition-colors">{t.nav.features}</a>
          <a href="#badges" className="hover:text-white transition-colors">{t.nav.badges}</a>
          <a href="#download" className="hover:text-white transition-colors">{t.nav.download}</a>
          <a href="https://github.com/fuckramochka/amegram" target="_blank" rel="noreferrer" className="text-zinc-400 hover:text-white">
            {t.nav.github}
          </a>
        </nav>

        {/* Right: Language switch + Download button */}
        <div className="flex items-center gap-2.5">
          {/* Apple-style Segmented Language Switcher */}
          <div className="flex items-center bg-white/[0.08] border border-white/10 rounded-full p-0.5 text-xs font-semibold">
            {languages.map((l) => (
              <button
                key={l.id}
                onClick={() => onLanguageChange(l.id)}
                className={`px-2.5 sm:px-3 py-1 rounded-full uppercase transition-all cursor-pointer text-[11px] font-bold ${
                  lang === l.id
                    ? 'bg-white text-black shadow-sm'
                    : 'text-zinc-400 hover:text-white'
                }`}
                title={l.title}
                aria-label={l.title}
              >
                {l.label}
              </button>
            ))}
          </div>

          <button
            onClick={onOpenDownloadModal}
            className="px-3.5 sm:px-4 py-1.5 rounded-full bg-white hover:bg-zinc-200 text-black text-xs font-bold tracking-tight transition-all cursor-pointer whitespace-nowrap"
          >
            <span className="sm:hidden">APK</span>
            <span className="hidden sm:inline">{t.nav.getApk}</span>
          </button>
        </div>
      </header>

      {/* Hero Section */}
      <section className="max-w-4xl mx-auto px-6 sm:px-8 pt-20 sm:pt-32 pb-20 text-center space-y-6">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/[0.04] border border-white/10 text-xs text-zinc-400 font-medium">
          <span className="w-1.5 h-1.5 rounded-full bg-white inline-block" />
          <span>{t.hero.tag}</span>
        </div>

        <h1 className="text-5xl sm:text-7xl font-extrabold tracking-tight text-white leading-tight">
          {t.hero.titleLine1}<br />
          <span className="text-zinc-400">{t.hero.titleLine2}</span>
        </h1>

        <p className="text-base sm:text-lg text-zinc-300 max-w-xl mx-auto leading-relaxed font-normal">
          {t.hero.description}
        </p>

        <div className="pt-4 flex flex-wrap items-center justify-center gap-3">
          <button
            onClick={onOpenDownloadModal}
            className="px-8 py-3.5 rounded-full bg-white hover:bg-zinc-200 text-black font-bold text-xs tracking-tight transition-all cursor-pointer shadow-lg shadow-white/5"
          >
            {t.hero.downloadButton} {release?.tag ? `(${release.tag})` : ''}
          </button>

          <a
            href="https://github.com/fuckramochka/amegram/releases"
            target="_blank"
            rel="noreferrer"
            className="px-6 py-3.5 rounded-full border border-white/20 hover:border-white/50 text-white font-medium text-xs transition-all"
          >
            {t.hero.githubButton}
          </a>
        </div>

        <div className="pt-6 text-xs text-zinc-500 flex flex-wrap items-center justify-center gap-5">
          <span>{t.hero.tagFree}</span>
          <span>·</span>
          <span>{t.hero.tagNoAds}</span>
          <span>·</span>
          <span>{t.hero.tagAndroid}</span>
        </div>
      </section>

      {/* Linear Typography Features (Explained simply for real humans) */}
      <section className="max-w-4xl mx-auto px-6 sm:px-8 py-20 border-t border-white/[0.08] space-y-12" id="features">
        <div className="text-left space-y-2">
          <span className="text-xs uppercase tracking-widest text-zinc-500 font-semibold">
            {t.featuresSection.kicker}
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
            {t.featuresSection.heading}
          </h2>
          <p className="text-xs sm:text-sm text-zinc-400 max-w-xl leading-relaxed">
            {t.featuresSection.subheading}
          </p>
        </div>

        <div className="divide-y divide-white/[0.08]">
          {t.features.map((f, i) => (
            <div key={i} className="py-9 grid md:grid-cols-12 gap-5 items-baseline">
              <div className="md:col-span-5">
                <h3 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
                  {f.title}
                </h3>
                <span className="text-xs text-zinc-400 block mt-1 leading-normal">
                  {f.subtitle}
                </span>
              </div>
              <div className="md:col-span-7">
                <p className="text-sm text-zinc-300 leading-relaxed font-normal">
                  {f.desc}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Badges Section */}
      <section className="max-w-4xl mx-auto px-6 sm:px-8 py-20 border-t border-white/[0.08] space-y-10" id="badges">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
          <div>
            <span className="text-xs uppercase tracking-widest text-zinc-500 font-semibold">
              {t.badgesSection.kicker}
            </span>
            <h2 className="text-3xl font-extrabold text-white tracking-tight mt-1">
              {t.badgesSection.heading}
            </h2>
          </div>
          <span className="text-xs text-zinc-400 font-mono">
            {stats ? stats.verifiedBadges : '1,240+'} {t.badgesSection.countNote}
          </span>
        </div>

        <p className="text-xs text-zinc-400">
          {t.badgesSection.chooseLabel}
        </p>

        {/* Badges Minimal Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          {BADGES.map((b) => {
            const isSelected = activeBadge.id === b.id;
            return (
              <button
                key={b.id}
                onClick={() => setActiveBadge(b)}
                className={`p-4 rounded-2xl border text-center transition-all cursor-pointer ${
                  isSelected
                    ? 'border-white bg-white/[0.08] text-white shadow-md'
                    : 'border-white/[0.06] hover:border-white/20 bg-transparent text-zinc-400'
                }`}
              >
                <div className="h-12 flex items-center justify-center">
                  <BadgePixelArt id={b.id} size={36} />
                </div>
                <div className="mt-2 text-xs font-semibold truncate">
                  {b.names[lang]}
                </div>
                <span className="text-[10px] text-zinc-500 font-mono block mt-0.5">
                  {b.tags[lang]}
                </span>
              </button>
            );
          })}
        </div>

        {/* Selected Badge lore */}
        <div className="p-6 rounded-2xl bg-white/[0.03] border border-white/[0.08] flex flex-col sm:flex-row items-center sm:items-start gap-5 text-center sm:text-left">
          <div className="flex-shrink-0 p-2 rounded-xl bg-white/[0.04]">
            <BadgePixelArt id={activeBadge.id} size={54} />
          </div>
          <div className="space-y-1">
            <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2">
              <h4 className="text-base font-bold text-white">{activeBadge.names[lang]}</h4>
              <span className="text-xs text-zinc-500 font-mono">[{activeBadge.tags[lang]}]</span>
            </div>
            <p className="text-xs sm:text-sm text-zinc-300 leading-relaxed font-normal">
              {activeBadge.lores[lang]}
            </p>
          </div>
        </div>

        <p className="text-[11px] text-zinc-500 text-center sm:text-left">
          {t.badgesSection.verifiedNote}
        </p>
      </section>

      {/* Direct Download Card */}
      <section className="max-w-4xl mx-auto px-6 sm:px-8 py-20 border-t border-white/[0.08]" id="download">
        <div className="p-8 sm:p-14 rounded-3xl bg-white/[0.03] border border-white/[0.08] text-center space-y-6">
          <h2 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
            {t.downloadSection.heading}
          </h2>
          <p className="text-xs sm:text-sm text-zinc-400 max-w-md mx-auto leading-relaxed font-normal">
            {t.downloadSection.description}
          </p>
          <div className="pt-2 flex justify-center">
            <button
              onClick={onOpenDownloadModal}
              className="px-8 py-4 rounded-full bg-white hover:bg-zinc-200 text-black font-bold text-xs tracking-tight transition-all cursor-pointer shadow-lg shadow-white/5"
            >
              {t.downloadSection.button} {release?.tag ? `(${release.tag})` : ''}
            </button>
          </div>
          <p className="text-[11px] text-zinc-500">
            {t.downloadSection.note}
          </p>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-white/[0.08] px-6 sm:px-12 py-10 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-zinc-500">
        <div>{t.footer.community}</div>
        <div className="flex items-center gap-6">
          <a href="https://github.com/fuckramochka/amegram" className="hover:text-white transition-colors">{t.footer.github}</a>
          <a href="https://github.com/fuckramochka/amegram/blob/main/LICENSE" className="hover:text-white transition-colors">{t.footer.license}</a>
          <a href="https://github.com/fuckramochka/yuimodules" className="hover:text-white transition-colors">{t.footer.modules}</a>
        </div>
      </footer>
    </div>
  );
};
