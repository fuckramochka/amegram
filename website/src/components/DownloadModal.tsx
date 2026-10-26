import React from 'react';
import { Language, TRANSLATIONS } from '../data/i18n';
import { ReleaseInfo } from '../types';

interface DownloadModalProps {
  isOpen: boolean;
  onClose: () => void;
  release: ReleaseInfo | null;
  lang: Language;
}

export const DownloadModal: React.FC<DownloadModalProps> = ({
  isOpen,
  onClose,
  release,
  lang,
}) => {
  if (!isOpen) return null;

  const t = TRANSLATIONS[lang].modal;
  const downloadUrl = release?.apkUrl || 'https://github.com/fuckramochka/amegram/releases/latest';

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/90 backdrop-blur-md animate-fade-in font-sans"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div 
        className="w-full max-w-lg bg-[#0E0E10] border border-white/20 rounded-3xl p-6 sm:p-8 relative text-white space-y-6 shadow-2xl"
        role="dialog"
        aria-modal="true"
      >
        <button
          onClick={onClose}
          className="absolute top-5 right-5 text-zinc-400 hover:text-white p-2 text-sm rounded-full bg-white/[0.05] border border-white/10 cursor-pointer"
          aria-label="Close"
        >
          ✕
        </button>

        <div className="space-y-1.5 border-b border-white/[0.08] pb-4">
          <span className="text-[10px] text-zinc-400 uppercase tracking-widest block font-bold font-mono">
            {t.kicker}
          </span>
          <h3 className="text-2xl font-bold tracking-tight text-white">
            {t.title}
          </h3>
          <p className="text-xs text-zinc-400">
            {t.desc}
          </p>
        </div>

        {/* 3 Simple Steps */}
        <div className="space-y-2.5 text-xs">
          <div className="p-3.5 rounded-2xl border border-white/[0.08] bg-white/[0.02] flex items-start gap-3.5">
            <span className="w-6 h-6 rounded-full bg-white text-black font-bold text-xs flex items-center justify-center flex-shrink-0">
              1
            </span>
            <span className="text-zinc-300 leading-relaxed font-normal pt-0.5">
              {t.step1}
            </span>
          </div>
          <div className="p-3.5 rounded-2xl border border-white/[0.08] bg-white/[0.02] flex items-start gap-3.5">
            <span className="w-6 h-6 rounded-full bg-white text-black font-bold text-xs flex items-center justify-center flex-shrink-0">
              2
            </span>
            <span className="text-zinc-300 leading-relaxed font-normal pt-0.5">
              {t.step2}
            </span>
          </div>
          <div className="p-3.5 rounded-2xl border border-white/[0.08] bg-white/[0.02] flex items-start gap-3.5">
            <span className="w-6 h-6 rounded-full bg-white text-black font-bold text-xs flex items-center justify-center flex-shrink-0">
              3
            </span>
            <span className="text-zinc-300 leading-relaxed font-normal pt-0.5">
              {t.step3}
            </span>
          </div>
        </div>

        {/* Action */}
        <div className="space-y-3 pt-1">
          <a
            href={downloadUrl}
            download
            className="w-full py-4 rounded-full text-center text-xs font-bold tracking-tight text-black bg-white hover:bg-zinc-200 block transition-colors cursor-pointer shadow-lg shadow-white/5"
          >
            {t.downloadBtn} {release?.tag ? `(${release.tag}${release.size ? ` · ${release.size}` : ''})` : ''}
          </a>

          <a
            href="https://github.com/fuckramochka/amegram/releases"
            target="_blank"
            rel="noreferrer"
            className="w-full py-3 rounded-full text-center text-xs text-zinc-300 hover:text-white border border-white/15 hover:border-white/30 block transition-colors"
          >
            {t.allReleases}
          </a>
        </div>

        <p className="text-[11px] text-zinc-500 text-center leading-relaxed">
          {t.footerNote}
        </p>
      </div>
    </div>
  );
};
