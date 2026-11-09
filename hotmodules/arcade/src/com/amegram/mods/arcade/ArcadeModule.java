package com.amegram.mods.arcade;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;

public class ArcadeModule implements HotModule {

    private static final String DINO_HTML = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no'>"
            + "<style>*{margin:0;padding:0;box-sizing:border-box;user-select:none;-webkit-user-select:none;}"
            + "html,body{width:100%;height:100%;background:#202124;overflow:hidden;font-family:monospace;touch-action:none;}"
            + "canvas{display:block;width:100%;height:100%;}#ui{position:absolute;top:24px;right:24px;color:#9aa0a6;font-size:20px;font-weight:bold;letter-spacing:2px;}"
            + "#hint{position:absolute;bottom:28px;width:100%;text-align:center;color:#5f6368;font-size:15px;}</style></head><body>"
            + "<div id='ui'>HI <span id='hi'>00000</span> &nbsp; <span id='score'>00000</span></div><canvas id='c'></canvas>"
            + "<div id='hint'>Торкніться екрана, щоб стрибнути</div><script>"
            + "var canvas=document.getElementById('c'),ctx=canvas.getContext('2d'),scoreEl=document.getElementById('score'),hiEl=document.getElementById('hi'),hintEl=document.getElementById('hint');"
            + "var W,H,groundY;function resize(){W=canvas.width=window.innerWidth;H=canvas.height=window.innerHeight;groundY=H*0.72;}window.addEventListener('resize',resize);resize();"
            + "var hi=parseInt(localStorage.getItem('dino_hi')||'0',10);hiEl.textContent=String(hi).padStart(5,'0');"
            + "var state='start',score=0,speed=6,frame=0,dino={x:50,y:0,w:44,h:48,vy:0,jump:-14,grounded:true},obstacles=[],nextObs=90;"
            + "function resetGame(){score=0;speed=6;frame=0;obstacles=[];nextObs=80;dino.y=groundY-dino.h;dino.vy=0;dino.grounded=true;state='play';hintEl.style.display='none';}"
            + "function tap(){if(state==='start'||state==='over'){resetGame();return;}if(dino.grounded){dino.vy=dino.jump;dino.grounded=false;}}"
            + "window.addEventListener('pointerdown',tap);window.addEventListener('keydown',function(e){if(e.code==='Space'||e.code==='ArrowUp')tap();});"
            + "function drawDino(x,y,w,h,leg){ctx.fillStyle='#e8eaed';ctx.fillRect(x+12,y,24,20);ctx.fillRect(x+28,y+4,12,10);ctx.fillStyle='#202124';ctx.fillRect(x+20,y+4,4,4);"
            + "ctx.fillStyle='#e8eaed';ctx.fillRect(x+6,y+16,24,20);ctx.fillRect(x,y+20,8,12);if(dino.grounded){if(leg){ctx.fillRect(x+8,y+36,6,12);ctx.fillRect(x+20,y+36,6,8);}else{ctx.fillRect(x+8,y+36,6,8);ctx.fillRect(x+20,y+36,6,12);}}else{ctx.fillRect(x+10,y+36,6,10);ctx.fillRect(x+18,y+36,6,10);}}"
            + "function drawCactus(x,y,w,h){ctx.fillStyle='#5bb974';ctx.fillRect(x+w*0.35,y,w*0.3,h);ctx.fillRect(x,y+h*0.25,w,h*0.2);ctx.fillRect(x,y+h*0.25,w*0.25,h*0.45);ctx.fillRect(x+w*0.75,y+h*0.15,w*0.25,h*0.45);}"
            + "function loop(){ctx.fillStyle='#202124';ctx.fillRect(0,0,W,H);ctx.strokeStyle='#5f6368';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(0,groundY);ctx.lineTo(W,groundY);ctx.stroke();"
            + "if(state==='play'){frame++;score+=0.15;speed=6+Math.min(8,score*0.005);scoreEl.textContent=String(Math.floor(score)).padStart(5,'0');dino.vy+=0.8;dino.y+=dino.vy;"
            + "if(dino.y>=groundY-dino.h){dino.y=groundY-dino.h;dino.vy=0;dino.grounded=true;}nextObs--;if(nextObs<=0){var h=34+Math.random()*24,w=22+Math.random()*12;obstacles.push({x:W+20,y:groundY-h,w:w,h:h});nextObs=60+Math.random()*70-Math.min(30,speed*2);}"
            + "for(var i=obstacles.length-1;i>=0;i--){var o=obstacles[i];o.x-=speed;drawCactus(o.x,o.y,o.w,o.h);if(dino.x+8<o.x+o.w&&dino.x+dino.w-8>o.x&&dino.y+6<o.y+o.h&&dino.y+dino.h>o.y){state='over';hintEl.textContent='ГРА ЗАКІНЧЕНА · Натисніть для повтору';hintEl.style.display='block';if(score>hi){hi=Math.floor(score);localStorage.setItem('dino_hi',hi);hiEl.textContent=String(hi).padStart(5,'0');}}if(o.x+o.w<-20)obstacles.splice(i,1);}}"
            + "else if(state==='start'){dino.y=groundY-dino.h;}drawDino(dino.x,dino.y,dino.w,dino.h,Math.floor(frame/6)%2===0);requestAnimationFrame(loop);}requestAnimationFrame(loop);</script></body></html>";

    private static final String GAME_2048_HTML = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no'>"
            + "<style>*{margin:0;padding:0;box-sizing:border-box;user-select:none;-webkit-user-select:none;}"
            + "html,body{width:100%;height:100%;background:#faf8ef;font-family:'Clear Sans',sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;overflow:hidden;touch-action:none;}"
            + "#top{width:340px;display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;}.title{font-size:48px;font-weight:bold;color:#776e65;}"
            + ".scores{display:flex;gap:8px;}.box{background:#bbada0;color:#fff;border-radius:6px;padding:6px 14px;text-align:center;min-width:70px;}.box-lbl{font-size:11px;text-transform:uppercase;color:#eee4da;}"
            + ".box-val{font-size:20px;font-weight:bold;}#grid{width:340px;height:340px;background:#bbada0;border-radius:8px;display:grid;grid-template-columns:repeat(4,1fr);grid-gap:10px;padding:10px;}"
            + ".cell{width:100%;height:100%;background:rgba(238,228,218,0.35);border-radius:6px;display:flex;align-items:center;justify-content:center;font-size:28px;font-weight:bold;color:#776e65;}"
            + ".c-2{background:#eee4da;}.c-4{background:#ede0c8;}.c-8{background:#f2b179;color:#f9f6f2;}.c-16{background:#f59563;color:#f9f6f2;}.c-32{background:#f67c5f;color:#f9f6f2;}.c-64{background:#f65e3b;color:#f9f6f2;}"
            + ".c-128{background:#edcf72;color:#f9f6f2;font-size:24px;}.c-256{background:#edcc61;color:#f9f6f2;font-size:24px;}.c-512{background:#edc850;color:#f9f6f2;font-size:24px;}.c-1024{background:#edc53f;color:#f9f6f2;font-size:20px;}.c-2048{background:#edc22e;color:#f9f6f2;font-size:20px;}"
            + "#btn{margin-top:14px;background:#8f7a66;color:#f9f6f2;font-size:16px;font-weight:bold;padding:10px 24px;border-radius:6px;cursor:pointer;}</style></head><body>"
            + "<div id='top'><div class='title'>2048</div><div class='scores'><div class='box'><div class='box-lbl'>Рахунок</div><div class='box-val' id='score'>0</div></div>"
            + "<div class='box'><div class='box-lbl'>Кращий</div><div class='box-val' id='best'>0</div></div></div></div><div id='grid'></div>"
            + "<div id='btn' onclick='init()'>Нова гра</div><script>"
            + "var board,score=0,best=parseInt(localStorage.getItem('2048_best')||'0',10);document.getElementById('best').textContent=best;var grid=document.getElementById('grid');"
            + "function init(){board=[[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]];score=0;updateScore(0);addTile();addTile();render();}"
            + "function updateScore(add){score+=add;document.getElementById('score').textContent=score;if(score>best){best=score;localStorage.setItem('2048_best',best);document.getElementById('best').textContent=best;}}"
            + "function addTile(){var empty=[];for(var r=0;r<4;r++)for(var c=0;c<4;c++)if(!board[r][c])empty.push({r:r,c:c});if(!empty.length)return;var spot=empty[Math.floor(Math.random()*empty.length)];board[spot.r][spot.c]=Math.random()<0.9?2:4;}"
            + "function render(){grid.innerHTML='';for(var r=0;r<4;r++)for(var c=0;c<4;c++){var val=board[r][c],div=document.createElement('div');div.className='cell '+(val?'c-'+val:'');div.textContent=val||'';grid.appendChild(div);}}"
            + "function slide(row){var arr=row.filter(function(v){return v;});for(var i=0;i<arr.length-1;i++){if(arr[i]===arr[i+1]){arr[i]*=2;updateScore(arr[i]);arr.splice(i+1,1);}}while(arr.length<4)arr.push(0);return arr;}"
            + "function move(dir){var changed=false;if(dir===0||dir===1){for(var r=0;r<4;r++){var row=board[r].slice();if(dir===1)row.reverse();var next=slide(row);if(dir===1)next.reverse();for(var c=0;c<4;c++){if(board[r][c]!==next[c])changed=true;board[r][c]=next[c];}}}"
            + "else{for(var c=0;c<4;c++){var col=[board[0][c],board[1][c],board[2][c],board[3][c]];if(dir===3)col.reverse();var next=slide(col);if(dir===3)next.reverse();for(var r=0;r<4;r++){if(board[r][c]!==next[r])changed=true;board[r][c]=next[r];}}}if(changed){addTile();render();}}"
            + "var sx,sy;window.addEventListener('touchstart',function(e){sx=e.touches[0].clientX;sy=e.touches[0].clientY;},false);"
            + "window.addEventListener('touchend',function(e){if(!sx||!sy)return;var dx=e.changedTouches[0].clientX-sx,dy=e.changedTouches[0].clientY-sy;if(Math.abs(dx)>Math.abs(dy)){if(Math.abs(dx)>30)move(dx>0?1:0);}else{if(Math.abs(dy)>30)move(dy>0?3:2);}sx=sy=null;},false);"
            + "init();</script></body></html>";

    private static final String FLAPPY_HTML = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no'>"
            + "<style>*{margin:0;padding:0;box-sizing:border-box;user-select:none;-webkit-user-select:none;}"
            + "html,body{width:100%;height:100%;background:#4ec0ca;overflow:hidden;font-family:sans-serif;touch-action:none;}"
            + "canvas{display:block;width:100%;height:100%;}#ui{position:absolute;top:30px;width:100%;text-align:center;color:#fff;font-size:42px;font-weight:900;text-shadow:2px 2px 4px rgba(0,0,0,0.5);}"
            + "#msg{position:absolute;top:45%;width:100%;text-align:center;color:#fff;font-size:22px;font-weight:bold;text-shadow:1px 1px 3px rgba(0,0,0,0.6);}</style></head><body>"
            + "<div id='ui'>0</div><div id='msg'>Торкніться, щоб летіти</div><canvas id='c'></canvas><script>"
            + "var canvas=document.getElementById('c'),ctx=canvas.getContext('2d'),ui=document.getElementById('ui'),msg=document.getElementById('msg');"
            + "var W,H;function resize(){W=canvas.width=window.innerWidth;H=canvas.height=window.innerHeight;}window.addEventListener('resize',resize);resize();"
            + "var state='start',score=0,best=parseInt(localStorage.getItem('flappy_best')||'0',10),bird={x:70,y:0,r:16,vy:0,jump:-7.5,grav:0.38},pipes=[],timer=0;"
            + "function reset(){bird.y=H*0.45;bird.vy=0;pipes=[];score=0;timer=0;ui.textContent='0';state='play';msg.style.display='none';}"
            + "function tap(){if(state==='start'||state==='over'){reset();return;}bird.vy=bird.jump;}window.addEventListener('pointerdown',tap);"
            + "function loop(){ctx.fillStyle='#4ec0ca';ctx.fillRect(0,0,W,H);ctx.fillStyle='#cbf1f4';ctx.fillRect(0,H-120,W,80);ctx.fillStyle='#ded895';ctx.fillRect(0,H-40,W,40);ctx.fillStyle='#73bf2e';ctx.fillRect(0,H-40,W,8);"
            + "if(state==='play'){bird.vy+=bird.grav;bird.y+=bird.vy;timer++;if(timer%95===0){var gap=140,topH=50+Math.random()*(H-40-gap-100);pipes.push({x:W+20,top:topH,bot:topH+gap,w:52,scored:false});}"
            + "for(var i=pipes.length-1;i>=0;i--){var p=pipes[i];p.x-=3.2;ctx.fillStyle='#73bf2e';ctx.strokeStyle='#558a22';ctx.lineWidth=3;ctx.fillRect(p.x,0,p.w,p.top);ctx.strokeRect(p.x,0,p.w,p.top);ctx.fillRect(p.x,p.bot,p.w,H-40-p.bot);ctx.strokeRect(p.x,p.bot,p.w,H-40-p.bot);"
            + "if(bird.x+bird.r>p.x&&bird.x-bird.r<p.x+p.w){if(bird.y-bird.r<p.top||bird.y+bird.r>p.bot){gameOver();}}if(!p.scored&&p.x+p.w<bird.x){p.scored=true;score++;ui.textContent=score;}if(p.x<-60)pipes.splice(i,1);}"
            + "if(bird.y+bird.r>=H-40||bird.y-bird.r<=0){gameOver();}}ctx.save();ctx.translate(bird.x,bird.y);var angle=Math.min(Math.PI/4,Math.max(-Math.PI/4,bird.vy*0.08));ctx.rotate(angle);"
            + "ctx.fillStyle='#f7d02c';ctx.beginPath();ctx.arc(0,0,bird.r,0,Math.PI*2);ctx.fill();ctx.stroke();ctx.fillStyle='#fff';ctx.beginPath();ctx.arc(6,-6,5,0,Math.PI*2);ctx.fill();ctx.fillStyle='#000';ctx.beginPath();ctx.arc(8,-6,2,0,Math.PI*2);ctx.fill();ctx.fillStyle='#f85b2b';ctx.beginPath();ctx.arc(10,3,6,0,Math.PI*2);ctx.fill();ctx.restore();requestAnimationFrame(loop);}"
            + "function gameOver(){state='over';if(score>best){best=score;localStorage.setItem('flappy_best',best);}msg.innerHTML='ГРА ЗАКІНЧЕНА<br><span style=\"font-size:16px;\">Кращий: '+best+' · Торкніться для рестарту</span>';msg.style.display='block';}"
            + "reset();state='start';msg.style.display='block';requestAnimationFrame(loop);</script></body></html>";

    private HotHost host;
    private Dialog currentDialog;
    private int lastState = -1;

    public ArcadeModule() {
    }

    @Override
    public String moduleId() {
        return "arcade";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            Class<?> nc = Class.forName("org.telegram.messenger.NotificationCenter");
            int didUpdateId = nc.getField("didUpdateConnectionState").getInt(null);
            host.subscribeNotification(didUpdateId, (id, account, args) -> {
                try {
                    Class<?> cm = Class.forName("org.telegram.tgnet.ConnectionsManager");
                    Object inst = cm.getMethod("getInstance", int.class).invoke(null, account);
                    int state = (int) cm.getMethod("getConnectionState").invoke(inst);
                    // 2 = ConnectionStateWaitingForNetwork
                    if (state == 2 && lastState != 2) {
                        lastState = 2;
                        if (host.getBool("offline_prompt", true)) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                host.toast(t("📡 Немає інтернету? Відкрийте «Офлайн Аркади» у налаштуваннях!",
                                             "📡 Нет интернета? Откройте «Офлайн Аркады» в настройках!",
                                             "📡 No connection? Open Offline Arcade in Settings!"));
                            });
                        }
                    } else if (state != 2) {
                        lastState = state;
                    }
                } catch (Throwable ignore) {
                }
            });
        } catch (Throwable t) {
            host.log("arcade onAttach: " + t.getMessage());
        }
    }

    @Override
    public void onDetach() {
        if (currentDialog != null) {
            try {
                currentDialog.dismiss();
            } catch (Throwable ignore) {
            }
            currentDialog = null;
        }
        host = null;
    }

    private static String t(String uk, String ru, String en) {
        try {
            Class<?> locale = Class.forName("app.miogram.bridge.MiogramLocale");
            Method get = locale.getMethod("get", String.class, String.class, String.class);
            Object value = get.invoke(null, uk, ru, en);
            if (value instanceof String) return (String) value;
        } catch (Throwable ignore) {
        }
        return uk != null ? uk : (en != null ? en : "");
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return t("Офлайн Аркади", "Офлайн Аркады", "Offline Arcade");
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        boolean prompt = host != null && host.getBool("offline_prompt", true);
        boolean keepScreen = host != null && host.getBool("keep_screen_on", true);

        rows.add(HotRow.header(t("Офлайн Ігри", "Офлайн Игры", "Offline Games")));
        rows.add(HotRow.switchRow("offline_prompt",
                t("Пропонувати гру без зв'язку", "Предлагать игру без связи", "Prompt games when offline"),
                t("Сповіщення, коли пропадає інтернет", "Уведомление при потере интернета", "Notify when connection is lost"),
                prompt));
        rows.add(HotRow.switchRow("keep_screen_on",
                t("Не гасити екран", "Не гасить экран", "Keep screen on"),
                "",
                keepScreen));

        rows.add(HotRow.header(t("Запуск ігор", "Запуск игр", "Launch Games")));
        rows.add(HotRow.button("play_dino", "🦖 Chrome Dino", t("Офлайн біг з перешкодами", "Офлайн раннер", "Offline runner")));
        rows.add(HotRow.button("play_2048", "🔢 2048", t("Класична головоломка", "Классическая головоломка", "Classic puzzle")));
        rows.add(HotRow.button("play_flappy", "🐦 Flappy Bird", t("Ретро аркада", "Ретро аркада", "Retro arcade")));
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("play_dino".equals(rowId)) {
            launchGame(DINO_HTML);
        } else if ("play_2048".equals(rowId)) {
            launchGame(GAME_2048_HTML);
        } else if ("play_flappy".equals(rowId)) {
            launchGame(FLAPPY_HTML);
        }
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.putBool(key, value);
        }
    }

    private void launchGame(final String html) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (currentDialog != null) {
                    try {
                        currentDialog.dismiss();
                    } catch (Throwable ignore) {
                    }
                    currentDialog = null;
                }

                Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
                Activity act = (Activity) laClass.getField("instance").get(null);
                if (act == null || act.isFinishing()) return;

                FrameLayout layout = new FrameLayout(act);
                layout.setBackgroundColor(Color.parseColor("#202124"));

                WebView webView = new WebView(act);
                webView.setVerticalScrollBarEnabled(false);
                webView.setHorizontalScrollBarEnabled(false);

                WebSettings s = webView.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setDatabaseEnabled(true);
                s.setUseWideViewPort(true);
                s.setLoadWithOverviewMode(true);

                // 100% OFFLINE execution
                webView.loadDataWithBaseURL("https://offline.arcade/", html, "text/html", "utf-8", null);
                layout.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                // Floating Close Button
                TextView btnClose = new TextView(act);
                btnClose.setText("✕");
                btnClose.setTextSize(20f);
                btnClose.setTextColor(Color.WHITE);
                btnClose.setGravity(Gravity.CENTER);
                btnClose.setBackgroundColor(Color.parseColor("#66000000"));
                btnClose.setPadding(35, 20, 35, 20);

                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.gravity = Gravity.TOP | Gravity.RIGHT;
                clp.setMargins(0, 35, 35, 0);
                layout.addView(btnClose, clp);

                final Dialog dialog = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                dialog.setContentView(layout);
                currentDialog = dialog;

                Window win = dialog.getWindow();
                if (win != null) {
                    win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    if (host != null && host.getBool("keep_screen_on", true)) {
                        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }

                btnClose.setOnClickListener(v -> {
                    try {
                        dialog.dismiss();
                    } catch (Throwable ignore) {
                    }
                });

                dialog.setOnDismissListener(d -> {
                    currentDialog = null;
                    try {
                        webView.stopLoading();
                        webView.destroy();
                    } catch (Throwable ignore) {
                    }
                });

                dialog.show();
            } catch (Throwable t) {
                if (host != null) {
                    host.log("launchGame failed: " + t.getMessage());
                }
            }
        });
    }
}
