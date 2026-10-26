import React from 'react';
import { SiteVariant } from '../types';

interface DeployModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentVariant: SiteVariant;
}

export const DeployModal: React.FC<DeployModalProps> = ({
  isOpen,
  onClose,
  currentVariant,
}) => {
  if (!isOpen) return null;

  const variantNames: Record<SiteVariant, string> = {
    'nothing-stark': 'Nothing OS (Stark Dot-Matrix)',
    'apple-pro': 'Apple Pro (Matte Titanium)',
    'mono-utility': 'Pure Utility (Bare-Bones)',
  };

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/90 backdrop-blur-md animate-fade-in font-mono"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div 
        className="w-full max-w-lg bg-black border border-white p-6 sm:p-8 relative text-white space-y-6"
        role="dialog"
        aria-modal="true"
      >
        <button
          onClick={onClose}
          className="absolute top-5 right-5 text-zinc-400 hover:text-white p-2 text-sm border border-zinc-800 hover:border-zinc-500 cursor-pointer"
          aria-label="Закрити"
        >
          [ESC]
        </button>

        <div className="space-y-2 border-b border-zinc-800 pb-4">
          <span className="text-[10px] text-zinc-400 uppercase tracking-widest block font-bold">
            // ПУШ НА GITHUB PAGES
          </span>
          <h3 className="text-2xl font-bold uppercase tracking-tight text-white font-sans">
            Пуш обраного дизайну
          </h3>
          <p className="text-xs text-zinc-400 font-sans">
            Активний стиль: <strong className="text-white">{variantNames[currentVariant]}</strong>
          </p>
        </div>

        <div className="space-y-3 text-xs">
          <div className="p-3 border border-zinc-800 bg-zinc-950 space-y-1">
            <span className="font-bold text-white block">1. Вибір та тест:</span>
            <p className="text-zinc-400 font-sans leading-relaxed">
              Усі 3 монохромні стилі (Nothing OS, Apple Pro, Pure Utility) повністю збудовані та перемикаються без затримок.
            </p>
          </div>

          <div className="p-3 border border-zinc-800 bg-zinc-950 space-y-1">
            <span className="font-bold text-white block">2. Фіксація єдиного варіанту:</span>
            <p className="text-zinc-400 font-sans leading-relaxed">
              Якщо ви обрали один стиль (наприклад, Nothing OS), напишіть — я приберу верхню панель перемикача і залишу тільки його.
            </p>
          </div>

          <div className="p-3 border border-zinc-800 bg-zinc-950 space-y-1">
            <span className="font-bold text-white block">3. Автоматичний білд:</span>
            <p className="text-zinc-400 font-sans leading-relaxed">
              При коміті та пуші в репозиторій робочий процес GitHub Actions автоматично опублікує сайт на GitHub Pages.
            </p>
          </div>
        </div>

        <div className="flex justify-end pt-2">
          <button
            onClick={onClose}
            className="px-6 py-3 bg-white text-black font-bold text-xs uppercase hover:bg-zinc-200 transition-colors cursor-pointer"
          >
            Зрозуміло
          </button>
        </div>
      </div>
    </div>
  );
};
