// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

/** Builds bounded, exact-origin credential scripts. No persistent JavaScript bridge is exposed. */
final class CredentialAutofill {
    private static final String FIELD_HELPERS =
            "function roots(){var o=[document],q=[document],n=0,r,e,i;while(q.length&&n++<64){r=q.shift();" +
            "try{e=r.querySelectorAll('*');for(i=0;i<e.length&&o.length<64;i++){if(e[i].shadowRoot){o.push(e[i].shadowRoot);q.push(e[i].shadowRoot);}" +
            "if(e[i].tagName==='IFRAME'&&e[i].contentDocument){o.push(e[i].contentDocument);q.push(e[i].contentDocument);}}}catch(x){}}return o;}" +
            "function visible(e){if(!e||e.disabled||e.readOnly||String(e.type||'').toLowerCase()==='hidden')return false;" +
            "try{return e.getClientRects().length>0||e.offsetParent!==null;}catch(x){return true;}}" +
            "function ac(e){return String(e&&e.autocomplete||'').toLowerCase();}" +
            "function acHas(e,t){return (' '+ac(e).replace(/\\s+/g,' ')+' ').indexOf(' '+t+' ')>=0;}" +
            "function hint(e){return String((e&&e.name||'')+' '+(e&&e.id||'')+' '+(e&&e.placeholder||'')+' '+(e&&e.getAttribute&&e.getAttribute('aria-label')||'')).toLowerCase();}" +
            "function badHint(h){return /(?:search|query|captcha|otp|one.?time|verification|verify|new|confirm|repeat|验证码|校验码|newsletter|subscribe|subscription|订阅|promo|coupon)/i.test(h);}" +
            "function loginContext(){var p=String(location&&location.pathname||''),h=String(location&&location.hostname||'').toLowerCase().split('.')[0];return /(?:login|logon|sign.?in|auth|account|session|passport|credential)/i.test(p)||/^(?:login|logon|signin|auth|account|accounts|passport)$/.test(h);}" +
            "function fields(){var R=roots(),p=[],u=[],i,j,a,e,t,h;for(i=0;i<R.length;i++){try{a=R[i].querySelectorAll('input');}catch(x){continue;}" +
            "for(j=0;j<a.length;j++){e=a[j];if(!visible(e))continue;t=String(e.type||'text').toLowerCase();h=hint(e);" +
            "if(t==='password'){if(!acHas(e,'new-password')&&!acHas(e,'one-time-code')&&!/(?:new|confirm|repeat|otp|one.?time|verification|verify|验证码|校验码)/i.test(h))p.push(e);}" +
            "else if(t==='email'||t==='text'||t==='tel'||!t){if(!acHas(e,'one-time-code')&&!badHint(h))u.push(e);}}}return{p:p,u:u};}" +
            "function password(F){var i;for(i=0;i<F.p.length;i++)if(acHas(F.p[i],'current-password'))return F.p[i];return F.p.length===1?F.p[0]:null;}" +
            "function username(F,p){var i,e,form=p&&p.form,scoped=[],strong=null;for(i=0;i<F.u.length;i++){e=F.u[i];if(form&&e.form!==form)continue;scoped.push(e);}" +
            "for(i=0;i<scoped.length;i++){e=scoped[i];if(acHas(e,'username'))return e;}" +
            "for(i=0;i<scoped.length;i++){e=scoped[i];if(/(?:user|login|account|phone|mobile|用户名|账号|手机)/i.test(hint(e))){strong=e;break;}}" +
            "if(strong)return strong;for(i=0;i<scoped.length;i++){e=scoped[i];if(String(e.type||'').toLowerCase()==='email'&&scoped.length===1&&loginContext())return e;}return null;}";

    static String detectScript() {
        return "(function(){" + FIELD_HELPERS +
                "var F=fields(),p=password(F),u=username(F,p),H=hint(u),uh=u&&(acHas(u,'username')||/(?:user|login|account|phone|mobile|用户名|账号|手机)/i.test(H)||" +
                "((String(u.type||'').toLowerCase()==='email'||/(?:email|邮箱)/i.test(H))&&loginContext()));" +
                "return JSON.stringify({login:!!p,username:!!u,usernameOnly:!p&&!!uh,passwords:F.p.length});})();";
    }

    static String fillScript(String username, String password) {
        return fillScript(username, password, false);
    }

    static String fillScript(String username, String password, boolean overwrite) {
        return "(function(){" + FIELD_HELPERS +
                "function setv(e,v){if(!e||(!" + overwrite + "&&String(e.value||'').length&&String(e.value)!==String(v)))return false;try{var p=e,d;while(p&&!d){p=Object.getPrototypeOf(p);d=p&&Object.getOwnPropertyDescriptor(p,'value');}" +
                "if(d&&d.set)d.set.call(e,v);else e.value=v;var E=typeof InputEvent==='function'?InputEvent:Event;e.dispatchEvent(new E('input',{bubbles:true,inputType:'insertText',data:null}));" +
                "e.dispatchEvent(new Event('change',{bubbles:true}));return true;}catch(x){try{e.value=v;return true;}catch(y){return false;}}}" +
                "var F=fields(),p=password(F),u=p?username(F,p):null,a=p?setv(u," + jsQuote(username) + "):false,b=p?setv(p," + jsQuote(password) + "):false;" +
                "if(p)try{p.focus({preventScroll:true});}catch(x){try{p.focus();}catch(y){}}return JSON.stringify({filled:!!b,username:!!a});})();";
    }

    /** Reads the current login fields for the explicit save dialog only. */
    static String captureFieldsScript() {
        return "(function(){" + FIELD_HELPERS +
                "var F=fields(),p=password(F),u=p?username(F,p):null;return JSON.stringify([u&&u.value||'',p&&p.value||'']);})();";
    }

    /** Compatibility output for legacy test/build callers; page credential capture is disabled. */
    static String captureScript(String ignoredToken) {
        return "(function(){})();";
    }

    private static String jsQuote(String value) {
        String input = value == null ? "" : value;
        StringBuilder out = new StringBuilder(input.length() + 2).append('"');
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                String hex = Integer.toHexString(c);
                out.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                out.append(hex);
            } else out.append(c);
        }
        return out.append('"').toString();
    }

    private CredentialAutofill() {}
}
