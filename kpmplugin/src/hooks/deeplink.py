from ..methods import log, log_error
from hook_utils import find_class
from client_utils import run_on_queue
import weakref


def install_deeplink_hook(pl):
    try:
        from android.net import Uri
        _pl_ref = weakref.ref(pl)

        class DeeplinkHook:
            def before_hooked_method(self, param):
                try:
                    _pl = _pl_ref()
                    if not _pl:
                        return
                    if len(param.args) < 1:
                        return
                    intent = param.args[0]
                    if not intent or intent.getAction() != "android.intent.action.VIEW":
                        return
                    data = intent.getData()
                    if not data:
                        return
                    url = str(data)
                    log(f"[KPM] DeeplinkHook: intent url={url}")
                    if "kpm_install" in url:
                        uri = Uri.parse(url)
                        pid = uri.getQueryParameter("plugin") or uri.getQueryParameter("id") or uri.getQueryParameter("p")
                        version = uri.getQueryParameter("version") or uri.getQueryParameter("v")
                        if not pid and "=" in url:
                            try:
                                import urllib.parse
                                parsed = urllib.parse.urlparse(url)
                                qs = urllib.parse.parse_qs(parsed.query)
                                pid = (qs.get("plugin") or qs.get("id") or qs.get("p") or [None])[0]
                                if not version:
                                    version = (qs.get("version") or qs.get("v") or [None])[0]
                            except Exception:
                                pass
                        if pid:
                            pid = str(pid).strip()
                            version = str(version).strip() if version else None
                            log(f"[KPM] DeeplinkHook: dispatching deep link install for {pid}, version={version}")
                            run_on_queue(lambda: _pl._handle_deep_link_install(pid, version))
                            param.setResult(None)
                    elif url.startswith("tg://kpm_list"):
                        run_on_queue(lambda: _pl.refresh_store(force=True, md3_anim=False))
                        param.setResult(None)
                    elif url.startswith("tg://kpm=faq") or url.startswith("tg://kpm_faq"):
                        from ..views import open_faq
                        tab = Uri.parse(url).getQueryParameter("tab")
                        open_faq(_pl, int(tab) - 1 if tab and tab.isdigit() else 0)
                        param.setResult(None)
                    elif url.startswith("tg://musor_drop"):
                        run_on_queue(_play_musor_drop_video)
                        param.setResult(None)
                except Exception as e:
                    log_error(e, context="DeeplinkHook")

        LaunchActivity = find_class("org.telegram.ui.LaunchActivity")
        if LaunchActivity:
            pl._track_hooks(pl.hook_all_methods(LaunchActivity, "handleIntent", DeeplinkHook()))
            log("[KPM] deeplink hook installed")
        else:
            log("[KPM] LaunchActivity not found for deeplink hook")
    except Exception as e:
        log_error(e, context="install_deeplink_hook")


def _play_musor_drop_video():
    try:
        from android_utils import run_on_ui_thread

        def play():
            try:
                from client_utils import get_last_fragment
                from android.widget import LinearLayout
                from android.view import TextureView, ViewGroup, Surface
                from android.graphics import Matrix
                from android.media import MediaPlayer
                from java import dynamic_proxy

                fragment = get_last_fragment()
                if not fragment:
                    log("[KPM] musor_drop: no fragment")
                    return
                activity = fragment.getParentActivity()
                if not activity:
                    log("[KPM] musor_drop: no activity")
                    return

                decor = activity.getWindow().getDecorView()

                from elyx import assets
                video_path = assets.musor_drop.path_str

                linear = LinearLayout(activity)
                linear.setOrientation(LinearLayout.VERTICAL)
                linear.setBackgroundColor(-16777216)

                state = {"pending": 0, "listeners": []}

                class _SurfaceListener(dynamic_proxy(TextureView.SurfaceTextureListener)):
                    def __init__(self, tv, muted, on_done):
                        super().__init__()
                        self.tv = tv
                        self.muted = muted
                        self.on_done = on_done
                        self.mp = None
                        self.surface = None
                        self.released = False

                    def onSurfaceTextureAvailable(self, surface, width, height):
                        try:
                            if self.released:
                                return
                            self.surface = Surface(surface)
                            mp = MediaPlayer()
                            self.mp = mp
                            mp.setDataSource(video_path)
                            mp.setSurface(self.surface)
                            mp.setOnPreparedListener(self._mk_prepared())
                            mp.setOnCompletionListener(self._mk_completion())
                            mp.setOnErrorListener(self._mk_error())
                            mp.prepareAsync()
                        except Exception as e:
                            log_error(e, context="musor_drop onSurfaceTextureAvailable")
                            self.on_done()

                    def onSurfaceTextureSizeChanged(self, surface, width, height):
                        pass

                    def onSurfaceTextureDestroyed(self, surface):
                        self.released = True
                        self.surface = None
                        self._release_mp()
                        return True

                    def onSurfaceTextureUpdated(self, surface):
                        pass

                    def _release_mp(self):
                        try:
                            if self.mp:
                                self.mp.reset()
                                self.mp.release()
                        except Exception:
                            pass
                        self.mp = None

                    def _apply_crop(self, mp):
                        try:
                            vw = mp.getVideoWidth()
                            vh = mp.getVideoHeight()
                            if vw <= 0 or vh <= 0:
                                return
                            w = self.tv.getWidth()
                            h = self.tv.getHeight()
                            if w <= 0 or h <= 0:
                                return
                            scale = max(float(w) / vw, float(h) / vh)
                            sw = float(vw) * scale
                            sh = float(vh) * scale
                            m = Matrix()
                            m.setScale(scale, scale)
                            m.postTranslate((w - sw) / 2.0, (h - sh) / 2.0)
                            self.tv.setTransform(m)
                        except Exception as e:
                            log_error(e, context="musor_drop _apply_crop")

                    def _mk_prepared(self):
                        class _P(dynamic_proxy(MediaPlayer.OnPreparedListener)):
                            def __init__(self, outer):
                                super().__init__()
                                self.outer = outer

                            def onPrepared(self, mp):
                                try:
                                    self.outer._apply_crop(mp)
                                    if self.outer.muted:
                                        mp.setVolume(0.0, 0.0)
                                    else:
                                        mp.setVolume(1.0, 1.0)
                                    mp.start()
                                except Exception as e:
                                    log_error(e, context="musor_drop onPrepared")
                                    self.outer.on_done()

                        return _P(self)

                    def _mk_completion(self):
                        class _C(dynamic_proxy(MediaPlayer.OnCompletionListener)):
                            def __init__(self, outer):
                                super().__init__()
                                self.outer = outer

                            def onCompletion(self, mp):
                                self.outer.on_done()

                        return _C(self)

                    def _mk_error(self):
                        class _E(dynamic_proxy(MediaPlayer.OnErrorListener)):
                            def __init__(self, outer):
                                super().__init__()
                                self.outer = outer

                            def onError(self, mp, what, extra):
                                self.outer.on_done()
                                return True

                        return _E(self)

                def _done():
                    state["pending"] -= 1
                    if state["pending"] <= 0:
                        try:
                            decor.removeView(linear)
                        except Exception:
                            pass

                for i in range(5):
                    state["pending"] += 1
                    tv = TextureView(activity)
                    lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                    )
                    lp.weight = 1
                    linear.addView(tv, lp)
                    listener = _SurfaceListener(tv, i != 2, _done)
                    tv.setSurfaceTextureListener(listener)
                    state["listeners"].append(listener)

                decor.addView(
                    linear,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            except Exception as e:
                log_error(e, context="musor_drop play")

        run_on_ui_thread(play)
    except Exception as e:
        log_error(e, context="_play_musor_drop_video")
