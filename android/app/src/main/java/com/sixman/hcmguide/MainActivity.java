package com.sixman.hcmguide;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.Locale;

/**
 * 호치민 탐방 가이드 — 웹앱(Vercel)을 그대로 보여주는 안드로이드 앱.
 * 웹앱을 고치면 이 앱도 자동으로 새 내용이 보여요 (APK를 다시 설치할 필요 없음).
 */
public class MainActivity extends Activity {

    // 앱이 여는 주소. 주소를 바꾸면 이 줄과 아래 APP_HOSTS만 고치면 돼요.
    static final String START_URL = "https://hcm-guide-eight.vercel.app/";
    static final String[] APP_HOSTS = { "hcm-guide-eight.vercel.app", "hcmguide26.vercel.app" };
    static final int FILE_REQ = 1001;

    WebView web;
    ValueCallback<Uri[]> fileCb;
    TextToSpeech tts;
    volatile boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " HcmGuideApp/1");

        web.addJavascriptInterface(new Bridge(), "HcmApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                return openOutside(req.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(i, "사진 선택"), FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                int r = tts.setLanguage(new Locale("vi", "VN"));
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    /** 앱 주소가 아니면(전화, 그랩, 구글 지도, 번역 등) 바깥 앱으로 열어요. */
    boolean openOutside(Uri u) {
        String scheme = u.getScheme();
        String host = u.getHost();
        if (("https".equals(scheme) || "http".equals(scheme)) && host != null) {
            for (String a : APP_HOSTS) if (host.equalsIgnoreCase(a)) return false;
        }
        try {
            Intent i = "intent".equals(scheme)
                    ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, u);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQ && fileCb != null) {
            Uri[] out = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    out = new Uri[n];
                    for (int k = 0; k < n; k++) out[k] = data.getClipData().getItemAt(k).getUri();
                } else if (data.getData() != null) {
                    out = new Uri[] { data.getData() };
                }
            }
            fileCb.onReceiveValue(out);
            fileCb = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 뒤로 가기: 열린 창(장소 안내, 크게 보기 등)을 먼저 닫고, 그다음 홈으로, 마지막에 앱 종료. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.hcmBack && window.hcmBack()) ? '1' : '0'", v -> {
            if (v != null && v.contains("1")) return;
            if (web.canGoBack()) web.goBack();
            else finish();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** 웹앱에서 window.HcmApp 으로 부르는 기능들 */
    class Bridge {
        @JavascriptInterface
        public boolean hasVietnamese() { return ttsReady; }

        @JavascriptInterface
        public void speak(String text) {
            if (tts == null) return;
            tts.setSpeechRate(0.85f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hcm");
        }

        @JavascriptInterface
        public void copy(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", text));
            });
        }

        @JavascriptInterface
        public void open(String url) {
            runOnUiThread(() -> {
                Uri u = Uri.parse(url);
                if (!openOutside(u)) web.loadUrl(url);
            });
        }
    }
}
