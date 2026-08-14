package com.xinyv.median;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Locale;

/** Dependency-free reader overlay, page diagnostics and on-device text-to-speech. */
final class PageAssistant {
    interface Callback<T> { void onResult(T value, Exception error); }

    private final Context context;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean shutdown;
    private long speechGeneration;
    private long pendingGeneration;
    private String pendingSpeech;
    private Callback<Boolean> pendingCallback;

    PageAssistant(Context context) { this.context = context.getApplicationContext(); }

    void toggleReader(WebView view, boolean dark, final Callback<String> callback) {
        if (view == null) { callback.onResult(null, new IllegalArgumentException("没有活动页面")); return; }
        String css = "#__median_reader{position:fixed;inset:0;z-index:2147483647;overflow:auto;padding:28px 7vw 80px;background:" +
                (dark ? "#17191c;color:#e8eaed" : "#faf8f3;color:#202124") +
                ";font:18px/1.78 system-ui,sans-serif}#__median_reader article{max-width:760px;margin:auto}#__median_reader h1{font-size:2em;line-height:1.25}#__median_reader img,#__median_reader video{max-width:100%;height:auto}#__median_reader a{color:" +
                (dark ? "#8ab4f8" : "#185abc") + ";text-decoration:none}#__median_reader pre{overflow:auto;padding:12px;background:rgba(127,127,127,.12)}";
        String js = "(function(){var old=document.getElementById('__median_reader');if(old){old.remove();document.documentElement.style.overflow='';return 'off';}" +
                "var c=document.querySelector('article,[role=main],main'),best=c,score=c?(c.innerText||'').length:0;" +
                "if(score<400){Array.from(document.querySelectorAll('section,div')).slice(0,700).forEach(function(e){var t=(e.innerText||'').length,p=e.querySelectorAll('p').length,l=e.querySelectorAll('a').length,s=t+p*120-l*24;if(t>300&&s>score){score=s;best=e;}});}" +
                "if(!best||score<300)return 'none';var n=best.cloneNode(true);n.querySelectorAll('script,style,noscript,iframe,form,button,input,textarea,select,nav,aside,[aria-hidden=true]').forEach(function(e){e.remove();});" +
                "n.querySelectorAll('*').forEach(function(e){Array.from(e.attributes).forEach(function(a){if(a.name.indexOf('on')===0||a.name==='style'||a.name==='id')e.removeAttribute(a.name);});});" +
                "var box=document.createElement('div');box.id='__median_reader';var a=document.createElement('article'),h=document.createElement('h1');h.textContent=document.title;a.appendChild(h);a.appendChild(n);box.appendChild(a);" +
                "var st=document.createElement('style');st.textContent=" + JSONObject.quote(css) + ";box.appendChild(st);document.documentElement.appendChild(box);document.documentElement.style.overflow='hidden';return 'on';})();";
        view.evaluateJavascript(js, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                try { callback.onResult(String.valueOf(new JSONTokener(value).nextValue()), null); }
                catch (Exception e) { callback.onResult(null, e); }
            }
        });
    }

    void pageInfo(WebView view, final Callback<JSONObject> callback) {
        if (view == null) { callback.onResult(null, new IllegalArgumentException("没有活动页面")); return; }
        String js = "(function(){var t=(document.body&&document.body.innerText||'').trim();return JSON.stringify({title:document.title,url:location.href,lang:document.documentElement.lang||'',charset:document.characterSet||'',characters:t.length,words:(t.match(/[A-Za-z0-9]+|[㐀-鿿]/g)||[]).length,links:document.links.length,images:document.images.length,forms:document.forms.length,scripts:document.scripts.length});})();";
        view.evaluateJavascript(js, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                try { callback.onResult(new JSONObject(decode(value)), null); }
                catch (Exception e) { callback.onResult(null, e); }
            }
        });
    }

    void speak(WebView view, final Callback<Boolean> callback) {
        if (view == null) { callback.onResult(Boolean.FALSE, new IllegalArgumentException("没有活动页面")); return; }
        final long requestGeneration = ++speechGeneration;
        String js = "(function(){var r=document.getElementById('__median_reader'),e=r||document.querySelector('article,[role=main],main')||document.body,t=(e&&e.innerText||'').replace(/\\s+/g,' ').trim();return t.slice(0,60000);})();";
        view.evaluateJavascript(js, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                if (shutdown || requestGeneration != speechGeneration) return;
                try {
                    String text = decode(value);
                    if (text.length() < 2) throw new IllegalStateException("页面没有可朗读文字");
                    startSpeech(text, callback, requestGeneration);
                } catch (Exception e) { callback.onResult(Boolean.FALSE, e); }
            }
        });
    }

    boolean isSpeaking() { return tts != null && tts.isSpeaking(); }

    void stop() {
        speechGeneration++;
        pendingGeneration = 0L;
        pendingSpeech = null;
        pendingCallback = null;
        if (tts != null) tts.stop();
    }

    void shutdown() {
        shutdown = true;
        stop();
        if (tts != null) tts.shutdown();
        tts = null;
        ttsReady = false;
    }

    private void startSpeech(String text, Callback<Boolean> callback, long generation) {
        if (shutdown || generation != speechGeneration) return;
        pendingSpeech = text;
        pendingCallback = callback;
        pendingGeneration = generation;
        if (ttsReady && tts != null) { speakPending(); return; }
        if (tts != null) return;
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                if (shutdown || tts == null) return;
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    Locale locale = Locale.getDefault();
                    if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(locale);
                    speakPending();
                } else {
                    Callback<Boolean> result = pendingCallback;
                    pendingCallback = null;
                    pendingSpeech = null;
                    pendingGeneration = 0L;
                    if (result != null) result.onResult(Boolean.FALSE, new IllegalStateException("系统朗读引擎不可用"));
                }
            }
        });
    }

    private void speakPending() {
        if (shutdown || pendingGeneration == 0L || pendingGeneration != speechGeneration) {
            pendingSpeech = null;
            pendingCallback = null;
            pendingGeneration = 0L;
            return;
        }
        String text = pendingSpeech;
        Callback<Boolean> result = pendingCallback;
        pendingSpeech = null;
        pendingCallback = null;
        pendingGeneration = 0L;
        if (text == null || tts == null) return;
        int limit = Math.max(500, TextToSpeech.getMaxSpeechInputLength() - 80);
        int start = 0;
        int sequence = 0;
        int status = TextToSpeech.SUCCESS;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + limit);
            if (end < text.length()) {
                int floor = start + limit / 2;
                for (int i = end; i > floor; i--) {
                    char c = text.charAt(i - 1);
                    if (Character.isWhitespace(c) || c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                        end = i;
                        break;
                    }
                }
            }
            String chunk = text.substring(start, end).trim();
            if (chunk.length() > 0) {
                int queued = tts.speak(chunk, sequence == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                        new Bundle(), "median-page-" + sequence);
                if (queued != TextToSpeech.SUCCESS) { status = queued; break; }
                sequence++;
            }
            start = end;
        }
        if (result != null) result.onResult(Boolean.valueOf(status == TextToSpeech.SUCCESS),
                status == TextToSpeech.SUCCESS ? null : new IllegalStateException("无法开始朗读"));
    }

    private static String decode(String value) throws Exception {
        Object decoded = new JSONTokener(value == null ? "null" : value).nextValue();
        return decoded == null || decoded == JSONObject.NULL ? "" : String.valueOf(decoded);
    }
}
