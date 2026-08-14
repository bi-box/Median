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
            "function hint(e){return String((e&&e.name||'')+' '+(e&&e.id||'')+' '+(e&&e.placeholder||'')+' '+(e&&e.getAttribute&&e.getAttribute('aria-label')||'')).toLowerCase();}" +
            "function fields(){var R=roots(),p=[],u=[],i,j,a,e,t,h;for(i=0;i<R.length;i++){try{a=R[i].querySelectorAll('input');}catch(x){continue;}" +
            "for(j=0;j<a.length;j++){e=a[j];if(!visible(e))continue;t=String(e.type||'text').toLowerCase();h=hint(e);" +
            "if(t==='password'){if(ac(e)!=='new-password'&&ac(e)!=='one-time-code'&&!/(?:new|confirm|repeat|otp|one.?time|验证码)/i.test(h))p.push(e);}" +
            "else if(t==='email'||t==='text'||t==='tel'||!t){if(ac(e)!=='one-time-code'&&!/(?:search|query|captcha|otp|one.?time|验证码)/i.test(h))u.push(e);}}}return{p:p,u:u};}" +
            "function password(F){var i;for(i=0;i<F.p.length;i++)if(ac(F.p[i])==='current-password')return F.p[i];return F.p.length===1?F.p[0]:null;}" +
            "function username(F,p){var i,e,best=null,form=p&&p.form;for(i=0;i<F.u.length;i++){e=F.u[i];if(form&&e.form!==form)continue;if(ac(e)==='username'||String(e.type).toLowerCase()==='email')return e;if(!best&&/(?:user|login|email|account|phone|mobile|用户名|邮箱|账号|手机)/i.test(hint(e)))best=e;}" +
            "if(best)return best;for(i=0;i<F.u.length;i++){e=F.u[i];if(!form||e.form===form)return e;}return null;}";

    static String detectScript() {
        return "(function(){" + FIELD_HELPERS +
                "var F=fields(),p=password(F),u=username(F,p),H=hint(u),P=String(location.hostname||'')+' '+String(location.pathname||''),uh=u&&(ac(u)==='username'||/(?:user|login|account|phone|mobile|用户名|账号|手机)/i.test(H)||" +
                "((String(u.type||'').toLowerCase()==='email'||/(?:email|邮箱)/i.test(H))&&/(?:login|logon|sign.?in|auth|account|session|passport)/i.test(P)));" +
                "return JSON.stringify({login:!!p,username:!!u,usernameOnly:!p&&!!uh,passwords:F.p.length});})();";
    }

    static String fillScript(String username, String password) {
        return "(function(){" + FIELD_HELPERS +
                "function setv(e,v){if(!e||String(e.value||'').length&&String(e.value)!==String(v))return false;try{var p=e,d;while(p&&!d){p=Object.getPrototypeOf(p);d=p&&Object.getOwnPropertyDescriptor(p,'value');}" +
                "if(d&&d.set)d.set.call(e,v);else e.value=v;var E=typeof InputEvent==='function'?InputEvent:Event;e.dispatchEvent(new E('input',{bubbles:true,inputType:'insertText',data:null}));" +
                "e.dispatchEvent(new Event('change',{bubbles:true}));return true;}catch(x){try{e.value=v;return true;}catch(y){return false;}}}" +
                "var F=fields(),p=password(F),u=username(F,p),a=setv(u," + jsQuote(username) + "),b=setv(p," + jsQuote(password) + ");" +
                "if(p)try{p.focus({preventScroll:true});}catch(x){try{p.focus();}catch(y){}}return JSON.stringify({filled:!!b,username:!!a});})();";
    }

    static String captureScript(String token) {
        return "(function(T){if(location.protocol!=='https:'||!T)return;" + FIELD_HELPERS +
                "var X=window.__medianCredentialCapture;if(X&&typeof X==='object'){X.token=T;return;}" +
                "var K='__median_login_user_'+location.hostname,S={token:T,last:'',autoAt:0},A=window.prompt&&window.prompt.bind(window);" +
                "function remember(e){if(!e||!e.isTrusted)return;var F=fields(),p=password(F),u=username(F,p);if(u&&u.value)try{sessionStorage.setItem(K,String(u.value).slice(0,512));}catch(x){}}" +
                "function send(e){if(!e||!e.isTrusted||!A)return;var F=fields(),p=password(F),u=username(F,p),name=u&&u.value||'';if(!name)try{name=sessionStorage.getItem(K)||'';}catch(x){}" +
                "var pass=p&&p.value||'';name=String(name).trim();if(!name||!pass||name.length>512||pass.length>8192)return;var f=location.hostname+'\\n'+name+'\\n'+pass;if(f===S.last)return;S.last=f;" +
                "try{A('__MEDIAN_CREDENTIAL__'+JSON.stringify({t:S.token,h:location.hostname,u:name,p:pass}),'');}catch(x){}}" +
                "function offer(e){if(!e||!e.isTrusted||!A)return;var n=e.target,F=fields(),p=password(F),u=username(F,p);if(n!==p&&n!==u)return;var z=Date.now();if(z-S.autoAt<1200)return;S.autoAt=z;" +
                "try{A('__MEDIAN_AUTOFILL__'+JSON.stringify({t:S.token,h:location.hostname}),'');}catch(x){}}" +
                "function click(e){if(!e.isTrusted)return;var n=e.target;while(n&&n!==document&&n.nodeType===1&&!/^(BUTTON|INPUT)$/.test(n.tagName))n=n.parentNode;" +
                "if(n&&(/^(submit|button)$/i.test(n.type||'')||n.tagName==='BUTTON'))send(e);}" +
                "var R=roots();for(var i=0;i<R.length;i++)try{R[i].addEventListener('input',remember,true);R[i].addEventListener('change',remember,true);R[i].addEventListener('submit',send,true);R[i].addEventListener('click',click,true);R[i].addEventListener('focusin',offer,true);}catch(x){}" +
                "try{Object.defineProperty(window,'__medianCredentialCapture',{value:S,configurable:true});}catch(x){window.__medianCredentialCapture=S;}" +
                "})(" + jsQuote(token) + ");";
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
