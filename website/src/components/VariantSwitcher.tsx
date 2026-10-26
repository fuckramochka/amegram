import React from 'react';
import { SiteVariant } from '../types';

interface VariantSwitcherProps {
  currentVariant: SiteVariant;
  onChange: (variant: SiteVariant) => void;
  onDeployNotice?: () => void;
}

export const VariantSwitcher: React.FC<VariantSwitcherProps> = ({
  currentVariant,
  onChange,
  onDeployNotice,
}) => {
  const variants: { id: SiteVariant; label: string; tag: string }[] = [
    {
      id: 'nothing-stark' as any,
      label: 'Nothing OS (Stark)',
      tag: 'DOT-MATRIX MONO',
    },
    {
      id: 'apple-pro' as any,
      label: 'Apple Pro',
      tag: 'MATTE TITANIUM',
    },
    {
      id: 'mono-utility' as any,
      label: 'Pure Utility',
      tag: 'BARE-BONES',
    },
  ];

  return (
    <aside aria-label="Панель вибору стилю" className="sticky top-0 z-50 w-full bg-black/90 backdrop-blur-md border-b border-zinc-800 px-4 py-2.5 font-mono text-xs">
      <div className="max-w-6xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-zinc-400 text-[11px]">
          <span className="w-1.5 h-1.5 rounded-full bg-white inline-block" />
          <span className="text-white font-bold uppercase tracking-wider">
            МІНІМАЛІЗМ:
          </span>
          <span className="text-zinc-500">
            Apple & Nothing OS естетика
          </span>
        </div>

        <div className="flex items-center bg-zinc-950 p-1 border border-zinc-800 w-full sm:w-auto overflow-x-auto">
          {variants.map((v) => {
            const isActive = currentVariant === v.id;
            return (
              <button
                key={v.id}
                onClick={() => onChange(v.id)}
                className={`px-3 py-1 text-[11px] font-bold tracking-wider uppercase transition-colors whitespace-nowrap cursor-pointer ${
                  isActive
                    ? 'bg-white text-black'
                    : 'text-zinc-400 hover:text-white hover:bg-zinc-900'
                }`}
              >
                {v.label}
              </button>
            );
          })}
        </div>

        {onDeployNotice && (
          <button
            onClick={onDeployNotice}
            className="hidden sm:inline-block text-[11px] text-zinc-400 hover:text-white underline cursor-pointer"
          >
            [Як запушити обраний?]
          </button>
        )}
      </div>
    </aside>
  );
};
