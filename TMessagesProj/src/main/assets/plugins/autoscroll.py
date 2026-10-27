from base_plugin import BasePlugin, MethodHook
from hook_utils import find_class
from android_utils import run_on_ui_thread, R
import ui.settings as _settings
import time

__id__ = "autoscroll_chat"
__name__ = "Автоскролл чата"
__version__ = "1.2.0"
__author__ = "Community"
__description__ = "Автоскролл с сохранением кинетической скорости свайпа на 60/120 FPS. Остановка в один тап."
__icon__ = "msg_recent"
__app_version__ = ">=12.5.0"
__sdk_version__ = ">=1.0.0"
__permissions__ = ["hooks", "ui", "settings"]


class AutoScrollPlugin(BasePlugin):

    def __init__(self):
        super().__init__()
        self._hooks = []
        self.active_chat_list = None
        self.is_scrolling = False
        self.target_velocity = 0.0  # пикселей в секунду
        self.subpixel_acc = 0.0
        self.last_frame_time = 0.0
        self._scroll_runnable = None

        # Остановка по тапу
        self.was_stopped_by_tap = False

        # Трекинг свайпов и скорости
        self.touch_start_y = 0.0
        self.touch_start_time = 0.0
        self.swipe_history = []
        self.last_swipe_dir = 0
        self.velocity_tracker = None
        self.vt_class = None

    def on_plugin_load(self):
        self.log("AutoScrollPlugin: загрузка движка VSYNC...")
        self._scroll_runnable = R(self._scroll_step)
        try:
            self.vt_class = find_class("android.view.VelocityTracker")
        except Exception:
            self.vt_class = None
        self._install_hooks()

    def on_plugin_unload(self):
        self.log("AutoScrollPlugin: выгрузка...")
        self.stop_auto_scroll()
        self.active_chat_list = None
        self._scroll_runnable = None
        if self._hooks:
            for unhook in self._hooks:
                try:
                    self.unhook_method(unhook)
                except Exception as e:
                    self.log(f"AutoScrollPlugin: ошибка unhook: {e}")
            self._hooks.clear()

    def _install_hooks(self):
        ChatActivityClass = find_class("org.telegram.ui.ChatActivity")
        RecyclerListViewClass = find_class("org.telegram.ui.Components.RecyclerListView")

        if not ChatActivityClass or not RecyclerListViewClass:
            self.log("AutoScrollPlugin: не удалось найти классы Telegram")
            return

        plugin = self

        # 1. Привязка к активному chatListView
        class ChatCreateViewHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    chat_activity = param.thisObject
                    chat_list = getattr(chat_activity, "chatListView", None)
                    plugin.active_chat_list = chat_list
                except Exception as e:
                    plugin.log(f"ChatCreateViewHook error: {e}")

        # 2. Очистка при выходе из чата
        class ChatDestroyHook(MethodHook):
            def before_hooked_method(self, param):
                plugin.stop_auto_scroll()
                plugin.active_chat_list = None

        # 3. Перехват жестов и измерение кинетической скорости
        class TouchHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    list_view = param.thisObject
                    if plugin.active_chat_list is None or list_view is not plugin.active_chat_list:
                        return

                    if not plugin.get_setting("enabled", True):
                        return

                    event = param.args[0]
                    if event is None:
                        return

                    action = event.getActionMasked()

                    # --- МГНОВЕННАЯ ОСТАНОВКА ПО ТАПУ ---
                    if plugin.is_scrolling:
                        if action == 0:  # MotionEvent.ACTION_DOWN
                            plugin.stop_auto_scroll()
                            plugin.was_stopped_by_tap = True
                            param.setResult(True)  # Поглощаем тап
                            return

                    # Глушим остаточные события отменяющего тапа (MOVE/UP)
                    if plugin.was_stopped_by_tap:
                        if action == 1 or action == 3:  # UP / CANCEL
                            plugin.was_stopped_by_tap = False
                        param.setResult(True)
                        return

                    # --- ИЗМЕРЕНИЕ РЕАЛЬНОЙ СКОРОСТИ СВАЙПА ---
                    if action == 0:  # ACTION_DOWN
                        plugin.touch_start_y = event.getRawY()
                        plugin.touch_start_time = time.perf_counter()
                        if plugin.vt_class is not None:
                            try:
                                plugin.velocity_tracker = plugin.vt_class.obtain()
                                plugin.velocity_tracker.addMovement(event)
                            except Exception:
                                plugin.velocity_tracker = None

                    elif action == 2:  # ACTION_MOVE
                        if plugin.velocity_tracker is not None:
                            try:
                                plugin.velocity_tracker.addMovement(event)
                            except Exception:
                                pass

                    elif action == 1:  # ACTION_UP
                        now = time.perf_counter()
                        dt = now - plugin.touch_start_time
                        dy = event.getRawY() - plugin.touch_start_y

                        # Снимаем физическую скорость с VelocityTracker
                        vy = 0.0
                        if plugin.velocity_tracker is not None:
                            try:
                                plugin.velocity_tracker.addMovement(event)
                                plugin.velocity_tracker.computeCurrentVelocity(1000)
                                vy = float(plugin.velocity_tracker.getYVelocity())
                            except Exception:
                                pass
                            try:
                                plugin.velocity_tracker.recycle()
                            except Exception:
                                pass
                            plugin.velocity_tracker = None

                        # Фолбэк на ручной расчет (пикселей в секунду), если VelocityTracker вернул 0
                        if abs(vy) < 80.0 and dt > 0.02:
                            vy = float(dy / dt)

                        # Фильтр полноценного свайпа: расстояние > 70px и время < 0.6 сек
                        if abs(dy) > 70 and 0.03 < dt < 0.6:
                            # В Android: при свайпе пальцем вверх vy < 0, список листается вниз (+скорость)
                            swipe_speed = -vy
                            plugin.on_manual_swipe(swipe_speed)

                    elif action == 3:  # ACTION_CANCEL
                        if plugin.velocity_tracker is not None:
                            try:
                                plugin.velocity_tracker.recycle()
                            except Exception:
                                pass
                            plugin.velocity_tracker = None

                except Exception as e:
                    plugin.log(f"TouchHook error: {e}")

        try:
            unhooks = self.hook_all_methods(ChatActivityClass, "createView", ChatCreateViewHook())
            if unhooks:
                self._hooks.extend(unhooks)
        except Exception as e:
            self.log(f"Hook createView error: {e}")

        try:
            unhooks = self.hook_all_methods(ChatActivityClass, "onFragmentDestroy", ChatDestroyHook())
            if unhooks:
                self._hooks.extend(unhooks)
        except Exception as e:
            self.log(f"Hook onFragmentDestroy error: {e}")

        try:
            unhooks = self.hook_all_methods(RecyclerListViewClass, "dispatchTouchEvent", TouchHook())
            if unhooks:
                self._hooks.extend(unhooks)
        except Exception as e:
            self.log(f"Hook dispatchTouchEvent error: {e}")

        self.log(f"AutoScrollPlugin: захукано методов: {len(self._hooks)}")

    def on_manual_swipe(self, swipe_speed):
        now = time.perf_counter()
        dir_sign = 1 if swipe_speed > 0 else -1

        # Сброс серии при смене направления
        if self.last_swipe_dir != 0 and self.last_swipe_dir != dir_sign:
            self.swipe_history.clear()
        self.last_swipe_dir = dir_sign

        # Окно для 3 свайпов — до 2.2 секунды
        self.swipe_history = [s for s in self.swipe_history if now - s[0] <= 2.2]
        self.swipe_history.append((now, swipe_speed))

        required_swipes = self._get_target_swipes()
        if len(self.swipe_history) >= required_swipes:
            # Берем скорость последнего решающего свайпа
            last_speed = self.swipe_history[-1][1]
            self.swipe_history.clear()
            self.start_auto_scroll(last_speed)

    def start_auto_scroll(self, raw_swipe_speed):
        if self.is_scrolling or self.active_chat_list is None:
            return

        # Настраиваемый режим скорости
        speed_mode = self.get_setting("speed_mode", 0)
        if speed_mode == 0:     # Сохранять реальную скорость жеста (1.0x)
            final_speed = raw_swipe_speed * 1.0
        elif speed_mode == 1:   # Ускоренный (1.4x)
            final_speed = raw_swipe_speed * 1.4
        elif speed_mode == 2:   # Замедленный для чтения (0.65x)
            final_speed = raw_swipe_speed * 0.65
        elif speed_mode == 3:   # Фиксированная: Быстрая (8 000 px/с)
            direction = 1.0 if raw_swipe_speed > 0 else -1.0
            final_speed = direction * 8000.0
        elif speed_mode == 4:   # Фиксированная: Турбо (16 000 px/с)
            direction = 1.0 if raw_swipe_speed > 0 else -1.0
            final_speed = direction * 16000.0
        elif speed_mode == 5:   # Фиксированная: Максимум (28 000 px/с)
            direction = 1.0 if raw_swipe_speed > 0 else -1.0
            final_speed = direction * 28000.0
        else:
            final_speed = raw_swipe_speed

        # Границы безопасности: минимум 2 500 px/с (плавный ход), максимум 35 000 px/с
        dir_sign = 1.0 if final_speed > 0 else -1.0
        clamped_speed = dir_sign * min(max(abs(final_speed), 2500.0), 35000.0)

        self.target_velocity = clamped_speed
        self.subpixel_acc = 0.0
        self.last_frame_time = time.perf_counter()
        self.is_scrolling = True

        self.log(f"AutoScroll: старт на скорости {int(self.target_velocity)} px/сек")

        # Запуск первого кадра
        if self._scroll_runnable is not None:
            try:
                self.active_chat_list.postOnAnimation(self._scroll_runnable)
            except Exception:
                run_on_ui_thread(self._scroll_step, 16)

    def stop_auto_scroll(self):
        if not self.is_scrolling:
            return
        self.is_scrolling = False
        if self.active_chat_list is not None and self._scroll_runnable is not None:
            try:
                self.active_chat_list.removeCallbacks(self._scroll_runnable)
            except Exception:
                pass
        self.log("AutoScroll: остановлен")

    def _scroll_step(self):
        if not self.is_scrolling or self.active_chat_list is None:
            return

        now = time.perf_counter()
        dt = now - self.last_frame_time
        self.last_frame_time = now

        # Защита от фризов UI (если кадр задержался дольше 50мс)
        if dt > 0.05:
            dt = 0.016

        direction = 1 if self.target_velocity > 0 else -1
        try:
            if not self.active_chat_list.canScrollVertically(direction):
                self.stop_auto_scroll()
                return

            # Точный расчет шага с сохранением долей пикселей (sub-pixel drift)
            exact_delta = self.target_velocity * dt
            self.subpixel_acc += exact_delta
            step = int(self.subpixel_acc)
            self.subpixel_acc -= step

            if step != 0:
                self.active_chat_list.scrollBy(0, step)
        except Exception as e:
            self.log(f"AutoScroll error: {e}")
            self.stop_auto_scroll()
            return

        # Синхронизация с VSYNC экрана (на 60, 90 или 120 Гц)
        if self.is_scrolling and self.active_chat_list is not None and self._scroll_runnable is not None:
            try:
                self.active_chat_list.postOnAnimation(self._scroll_runnable)
            except Exception:
                run_on_ui_thread(self._scroll_step, 16)

    def _get_target_swipes(self):
        idx = self.get_setting("swipes_count", 1)
        try:
            return [2, 3, 4][int(idx)]
        except Exception:
            return 3

    def create_settings(self):
        return [
            _settings.Header(text="Автоскролл чата (VSYNC 120Hz)"),
            _settings.Switch(
                key="enabled",
                text="Включить автоскролл",
                default=True,
                subtext="Непрерывный автоскролл после серии быстрых свайпов"
            ),
            _settings.Selector(
                key="speed_mode",
                text="Режим скорости",
                default=0,
                items=[
                    "Сохранять скорость свайпа (1.0x)",
                    "Ускоренный свайп (1.4x)",
                    "Замедленный свайп (0.65x)",
                    "Фиксированная: Быстрая (8 000 px/с)",
                    "Фиксированная: Турбо (16 000 px/с)",
                    "Фиксированная: Максимум (28 000 px/с)"
                ],
                subtext="Скорость подхватывает физику вашего пальца или фиксирована"
            ),
            _settings.Selector(
                key="swipes_count",
                text="Количество свайпов для запуска",
                default=1,
                items=["2 свайпа подряд", "3 свайпа подряд", "4 свайпа подряд"],
                subtext="Сколько быстрых свайпов в одну сторону активируют скролл"
            ),
            _settings.Divider(
                text="Остановка: просто коснитесь экрана в любой точке. Тап мгновенно поглощается и не откроет сообщение."
            )
        ]
