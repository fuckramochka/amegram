import React, { useEffect, useState } from 'react';
import { AppleProVariant } from './components/AppleProVariant';
import { DownloadModal } from './components/DownloadModal';
import { Language, TRANSLATIONS } from './data/i18n';
import { ReleaseInfo, SupabaseStats } from './types';

const REPO = 'https://github.com/fuckramochka/amegram';
const SUPABASE_ANON = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRieHNuam9leWlxdnF0cmx1dnd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1NDI1MzEsImV4cCI6MjEwNDExODUzMX0.KJ0kvON1HXZu4MzlZjapSJEhEzWYlEqQoNEstWCgIjA';

export default function App() {
  const [lang, setLang] = useState<Language>(() => {
    if (typeof window !== 'undefined') {
      const urlLang = new URLSearchParams(window.location.search).get('lang') as Language;
      if (urlLang === 'en' || urlLang === 'uk' || urlLang === 'ru') {
        return urlLang;
      }
      const stored = localStorage.getItem('yumigram-lang') as Language;
      if (stored === 'en' || stored === 'uk' || stored === 'ru') {
        return stored;
      }
    }
    // Default primary language is English
    return 'en';
  });

  const [downloadModalOpen, setDownloadModalOpen] = useState(false);
  const [stats, setStats] = useState<SupabaseStats | null>(null);
  const [release, setRelease] = useState<ReleaseInfo | null>(null);

  const handleLanguageChange = (newLang: Language) => {
    setLang(newLang);
    localStorage.setItem('yumigram-lang', newLang);
    if (typeof document !== 'undefined') {
      document.documentElement.lang = newLang;
      const t = TRANSLATIONS[newLang];
      document.title = `Yumigram ໒꒱ — ${t.hero.titleLine1} ${t.hero.titleLine2}`;
    }
  };

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = lang;
      const t = TRANSLATIONS[lang];
      document.title = `Yumigram ໒꒱ — ${t.hero.titleLine1} ${t.hero.titleLine2}`;
    }
  }, [lang]);

  // Real-time Supabase Badge Statistics
  useEffect(() => {
    let live = true;
    fetch('https://dbxsnjoeyiqvqtrluvwu.supabase.co/rest/v1/miogram_badges?select=user_id', {
      headers: {
        apikey: SUPABASE_ANON,
        Authorization: `Bearer ${SUPABASE_ANON}`,
      },
    })
      .then((r) => r.json())
      .then((data) => {
        if (live && Array.isArray(data)) {
          setStats({
            verifiedBadges: data.length,
            activeUsers: data.length,
          });
        }
      })
      .catch(() => {});

    return () => {
      live = false;
    };
  }, []);

  // Fetch Latest Release from GitHub
  useEffect(() => {
    let live = true;
    fetch('https://api.github.com/repos/fuckramochka/amegram/releases/latest')
      .then((r) => r.json())
      .then((data) => {
        if (!live || !data) return;
        let apkUrl = data.html_url || `${REPO}/releases/latest`;
        let sizeStr: string | undefined;

        if (Array.isArray(data.assets)) {
          const apkAsset = data.assets.find(
            (a: any) => typeof a.name === 'string' && a.name.endsWith('.apk')
          );
          if (apkAsset) {
            apkUrl = apkAsset.browser_download_url || apkUrl;
            if (typeof apkAsset.size === 'number') {
              sizeStr = (apkAsset.size / (1024 * 1024)).toFixed(1) + ' MB';
            }
          }
        }

        setRelease({
          tag: data.tag_name || 'v1.0.5',
          name: data.name || 'Yumigram Core Release',
          apkUrl,
          size: sizeStr,
          publishedAt: data.published_at,
          changelog: data.body,
        });
      })
      .catch(() => {
        if (live) {
          setRelease({
            tag: 'v1.0.5',
            name: 'Yumigram 1.0.5',
            apkUrl: `${REPO}/releases/latest`,
            size: '56.4 MB',
          });
        }
      });

    return () => {
      live = false;
    };
  }, []);

  return (
    <div className="min-h-screen bg-[#070708] text-white">
      <main>
        <AppleProVariant
          release={release}
          stats={stats}
          onOpenDownloadModal={() => setDownloadModalOpen(true)}
          lang={lang}
          onLanguageChange={handleLanguageChange}
        />
      </main>

      <DownloadModal
        isOpen={downloadModalOpen}
        onClose={() => setDownloadModalOpen(false)}
        release={release}
        lang={lang}
      />
    </div>
  );
}
