package com.xinyv.median;

/** Dependency-free in-page element picker used to create cosmetic ad-block rules. */
final class AdPickerScript {
    private AdPickerScript() {}

    static String build(String token) {
        return "(function(){'use strict';"
                + "delete window.__medianAdSelection;"
                + "if(window.__medianAdPicker){window.__medianAdPicker.cancel();return false;}"
                + "var T=" + quote(token) + ",last=null,marked=[],selected=null,selectedNodes=[],selectedGroup=null,selectedSelector='',bar=null,shield=null,label=null,confirmButton=null,style=null,touchTarget=null,touchX=0,touchY=0,touchMoved=false,touchActive=false,ignoreClickUntil=0,listeners=[],styleReads=[];"
                + "var esc=function(v){v=String(v);if(window.CSS&&CSS.escape)return CSS.escape(v);return v.replace(/[^a-zA-Z0-9_-]/g,function(c){return'\\\\'+c.charCodeAt(0).toString(16)+' ';});};"
                + "var removeNode=function(node){if(node&&node.parentNode)node.parentNode.removeChild(node);};"
                + "var listen=function(type,fn){window.addEventListener(type,fn,{capture:true,passive:false});listeners.push([type,fn]);};"
                + "var clean=function(){listeners.forEach(function(l){window.removeEventListener(l[0],l[1],true);});styleReads=[];marked.forEach(function(n){n.classList.remove('__median_ad_target');});marked=[];removeNode(bar);removeNode(shield);removeNode(style);delete window.__medianAdPicker;};"
                + "var send=function(v){if(v.selector&&selected&&selectedNodes.length)window.__medianAdSelection={selector:v.selector,nodes:selectedNodes.slice(),revalidate:selectedGroup?groupGuard(selectedGroup):null};else delete window.__medianAdSelection;clean();try{window.prompt(T,JSON.stringify(v));}catch(_){delete window.__medianAdSelection;}};"
                + "var inBar=function(e){return bar&&bar.contains(e.target);};"
                + "var block=function(e,cancel){if(cancel&&e.cancelable!==false)e.preventDefault();e.stopImmediatePropagation();};"
                + "var visible=function(e){if(!e||e===document.documentElement||e===document.body||e===shield||bar&&bar.contains(e))return false;var r=e.getBoundingClientRect();return r.width>=8&&r.height>=8;};"
                // Read actual class/stylesheet geometry only on a picker gesture, with a bounded cache.
                + "var readStyle=function(n,limit){if(!n)return{};for(var i=0;i<styleReads.length;i++)if(styleReads[i].n===n)return styleReads[i].s;var s=n.style||{};if(styleReads.length>=(limit||32))return s;try{if(window.getComputedStyle)s=window.getComputedStyle(n);}catch(_){}styleReads.push({n:n,s:s});return s;};"
                + "var transparentTile=function(n){if(!n||!n.style)return false;var s=readStyle(n),o=s.opacity;return(s.position==='fixed'||s.position==='absolute')&&o!==''&&o!=null&&Number(o)<=.05;};"
                + "var at=function(e){var p=e.touches&&e.touches[0]||e.changedTouches&&e.changedTouches[0]||e,n=null,skipped=[];if(shield&&typeof p.clientX==='number'&&typeof p.clientY==='number'){shield.style.pointerEvents='none';try{for(var i=0;i<24;i++){n=document.elementFromPoint(p.clientX,p.clientY);if(!transparentTile(n))break;skipped.push({e:n,v:n.style.getPropertyValue('pointer-events'),p:n.style.getPropertyPriority('pointer-events')});n.style.setProperty('pointer-events','none','important');}}finally{skipped.forEach(function(x){if(x.v)x.e.style.setProperty('pointer-events',x.v,x.p);else x.e.style.removeProperty('pointer-events');});shield.style.pointerEvents='auto';}}return n||e.target;};"
                + "var adHint=/\\b(ad|ads|advert|advertisement|banner|sponsor|sponsored|promo|promotion|commercial|outbrain|taboola|doubleclick)\\b/i;"
                + "var contentBoundary=function(n){if(!n)return false;var v=(n.id||'')+' '+(typeof n.className==='string'?n.className:'');return /^(VIDEO|AUDIO|DIALOG)$/.test(n.tagName)||(n.getAttribute&&n.getAttribute('data-ad-keep')!==null)||(n.getAttribute&&/^(dialog|alertdialog)$/i.test(n.getAttribute('role')||''))||/(?:^|[\\s_-])(?:artplayer|dplayer|player|video|dialog)(?:$|[\\s_-])/i.test(v)||/(?:^|\\s)(?:art-|player|video)[A-Z_a-z-]*/i.test(v);};"
                + "var target=function(e){if(!visible(e))return null;var best=e,floating=null,hinted=false,media=/^(IMG|VIDEO|A)$/.test(e.tagName),screen=Math.max(1,innerWidth*innerHeight),depth=0;for(var n=e;n&&n!==document.body&&depth<7;n=n.parentElement,depth++){if(n!==e&&contentBoundary(n))break;if(!visible(n))continue;var r=n.getBoundingClientRect(),v=(n.id||'')+' '+(typeof n.className==='string'?n.className:'')+' '+(n.getAttribute&&((n.getAttribute('data-ad')||'')+' '+(n.getAttribute('data-ad-slot')||'')+' '+(n.getAttribute('aria-label')||'')+' '+(n.getAttribute('role')||'')));if(!contentBoundary(n)&&readStyle(n).position==='fixed'&&!transparentTile(n)&&r.width>=innerWidth*.4&&r.height<=innerHeight*.55&&r.width*r.height<screen*.65)floating=n;if(!hinted&&adHint.test(v)&&r.width*r.height<screen*.88){best=n;hinted=true;}}if(floating)return floating;if(best===e&&media&&e.parentElement&&!contentBoundary(e.parentElement)&&visible(e.parentElement)&&e.parentElement!==document.body)best=e.parentElement;return best;};"
                + "var generated=function(v){return /^__median/i.test(v)||/^[A-Za-z]{4,16}_\\d+_\\d+$/.test(v)||/^(?=[A-Za-z]{4,16}$)(?=.*[a-z])(?=.*[A-Z]{2})/.test(v)||/^(?:[a-f\\d]{16,}|[a-f\\d]{8}(?:-[a-f\\d]{4}){3}-[a-f\\d]{12}|(?:[A-Za-z_-]*\\d){6,}[A-Za-z_-]*)$/i.test(v);};"
                + "var classes=function(e){var grid=(e.id||'').match(/^([A-Za-z]{4,16})_\\d+_\\d+$/);return Array.prototype.filter.call(e.classList||[],function(c){return c.length<56&&!generated(c)&&(!grid||c!==grid[1])&&!/^(active|selected|hover|focus)$/i.test(c);}).sort(function(a,b){var ap=/ad|advert|banner|promo|sponsor/i.test(a)?0:1,bp=/ad|advert|banner|promo|sponsor/i.test(b)?0:1;return ap-bp||a.length-b.length;}).slice(0,2);};"
                // CSSOM reports bottom:0 as 0px while the untouched style attribute can still contain 0.
                // Match complete zero declarations in their raw and normalized forms, including whitespace.
                + "var zeroPart=function(k,v,raw,st){var m=raw.match(new RegExp('(?:^|;)\\\\s*'+k+'\\\\s*:[^;]*')),values=[],a=[],suffix=st.getPropertyPriority(k)?' !important':'';if(m)values.push(m[0].replace(/^[;\\s]+/,''));[k+':0',k+': 0',k+':0px',k+': 0px'].forEach(function(x){values.push(x+suffix);});values.forEach(function(x){if(a.indexOf('[style*='+JSON.stringify(x+';')+']')<0){a.push('[style*='+JSON.stringify(x+';')+']');a.push('[style$='+JSON.stringify(x)+']');}});return':is('+a.join(',')+')';};"
                + "var layoutPart=function(e){var st=e.style,p=st&&st.position;if(p!=='fixed'&&p!=='absolute')return'';var raw=e.getAttribute&&e.getAttribute('style')||'',s=e.tagName.toLowerCase()+'[style*=\"position\"][style*='+JSON.stringify(p)+']',fields=['bottom','width','z-index','top','left','right'],features=0;for(var i=0;i<fields.length;i++){var k=fields[i],v=st.getPropertyValue(k);if(!v||raw.indexOf(k)<0)continue;if(k==='width'&&!/(?:vw|vh|%)$/.test(v))continue;var add=/^0(?:px|em|rem|vh|vw|%)?$/i.test(v)?zeroPart(k,v,raw,st):'[style*='+JSON.stringify(k)+'][style*='+JSON.stringify(v)+']';if(s.length+add.length>480)continue;s+=add;features++;if(features>=3)break;}if(features<2)return'';try{var a=document.querySelectorAll(s);return a.length===1&&a[0]===e?s:'';}catch(_){return'';}};"
                + "var part=function(e){var s=e.tagName.toLowerCase(),attrs=['data-ad-clamped','data-ad','data-ad-slot','data-testid','aria-label','role'];for(var i=0;i<attrs.length;i++){var v=e.getAttribute&&e.getAttribute(attrs[i]);if(v&&v.length<80&&!generated(v)){s+='['+attrs[i]+'='+JSON.stringify(v)+']';break;}}if(s.indexOf('[')>=0)return s;var layout=layoutPart(e);if(layout)return layout;if(e.id&&!generated(e.id)){try{if(document.querySelectorAll('#'+esc(e.id)).length===1)return'#'+esc(e.id);}catch(_){}}var cs=classes(e);if(cs.length)s+='.'+cs.map(esc).join('.');return s;};"
                + "var selector=function(e){if(!e)return'';var id=part(e);try{if(document.querySelectorAll(id).length===1)return id;}catch(_){}var path=[],n=e;for(var depth=0;n&&n!==document.body&&depth<7;depth++,n=n.parentElement){var s=part(n);if(s.charAt(0)!=='#'&&n.parentElement){var same=Array.prototype.filter.call(n.parentElement.children,function(x){return x.tagName===n.tagName;});if(same.length>1)s+=':nth-of-type('+(same.indexOf(n)+1)+')';}path.unshift(s);var q=path.join(' > ');try{if(document.querySelectorAll(q).length===1)return q;}catch(_){}}return path.join(' > ');};"
                // A real banner may be separate fixed grid tiles injected directly into body.
                // Persist only stable property names after verifying every match belongs to one image grid.
                + "var puzzle=function(e,current){if(e&&(e.tagName!=='DIV'||e.parentElement!==document.body))return null;var q='body > div[style*=\"background-position\"][style*=\"background-size\"][style*=\"bottom\"][style*=\"left\"]',nodes;try{nodes=Array.prototype.slice.call(current||document.querySelectorAll(q));}catch(_){return null;}if(nodes.length<2||nodes.length>100||e&&nodes.indexOf(e)<0)return null;var prefix='',image='',size='',layout={},minX=Infinity,minY=Infinity,maxX=-Infinity,maxY=-Infinity;for(var i=0;i<nodes.length;i++){var n=nodes[i],m=(n.id||'').match(/^([A-Za-z]{4,16})_(\\d+)_(\\d+)$/);if(n.tagName!=='DIV'||n.parentElement!==document.body||contentBoundary(n)||n.children.length||String(n.textContent||'').trim()||!visible(n)||!m||Number(m[2])>99||Number(m[3])>99)return null;if(i&&m[1]!==prefix)return null;prefix=m[1];var cell=Number(m[2])+'_'+Number(m[3]);if(layout[cell])return null;var s=readStyle(n,132),im=s.getPropertyValue('background-image'),sz=s.getPropertyValue('background-size');if(s.position!=='fixed'||Number(s.opacity)<=.05||s.display==='none'||s.visibility==='hidden'||!im||im==='none'||!sz||sz==='auto'||i&&(im!==image||sz!==size))return null;image=im;size=sz;var r=n.getBoundingClientRect();layout[cell]=[r.left,innerHeight-r.top-r.height,r.width,r.height,s.getPropertyValue('background-position')];minX=Math.min(minX,r.left);minY=Math.min(minY,r.top);maxX=Math.max(maxX,r.left+r.width);maxY=Math.max(maxY,r.top+r.height);}var w=maxX-minX,h=maxY-minY;if(!(w>=innerWidth*.4&&h>=8&&h<=innerHeight*.55&&w*h<innerWidth*innerHeight*.65))return null;return{selector:q,nodes:nodes,size:size,layout:layout};};"
                // Carousel ads replace their nodes while the native confirmation dialog is open.
                // Only an independently verified grid with the original cells and geometry may replace them.
                + "var groupGuard=function(original){var q=original.selector,count=original.nodes.length,size=original.size,layout=original.layout;return function(selector,nodes){styleReads=[];try{if(selector!==q||nodes.length!==count)return false;var group=puzzle(null,nodes);if(!group||group.size!==size)return false;var cells=Object.keys(layout);for(var i=0;i<cells.length;i++){var a=layout[cells[i]],b=group.layout[cells[i]];if(!b||a[4]!==b[4])return false;for(var j=0;j<4;j++)if(!isFinite(b[j])||Math.abs(a[j]-b[j])>1)return false;}return true;}finally{styleReads=[];}};};"
                + "var mark=function(e,nodes){if(e===last&&!nodes)return;marked.forEach(function(n){n.classList.remove('__median_ad_target');});last=e;marked=nodes|| (e?[e]:[]);marked.forEach(function(n){n.classList.add('__median_ad_target');});};"
                + "var hover=function(e){if(inBar(e)||e.pointerType==='touch')return;block(e,false);styleReads=[];mark(target(at(e)));};"
                + "var reset=function(){selected=null;selectedNodes=[];selectedGroup=null;selectedSelector='';mark(null);if(confirmButton)confirmButton.disabled=true;if(label)label.textContent='选择要隐藏的元素';};"
                + "var selectTarget=function(node,e){var t=target(node);if(!t)return;if(e&&e.preventDefault)e.preventDefault();if(e&&e.stopImmediatePropagation)e.stopImmediatePropagation();var group=puzzle(t),s=group?group.selector:selector(t);selected=t;selectedNodes=group?group.nodes:[t];selectedGroup=group;selectedSelector=s;mark(t,selectedNodes);if(confirmButton)confirmButton.disabled=!s;if(label)label.textContent=s?(group?'已选中完整广告（'+selectedNodes.length+'块），点击“隐藏此元素”确认':'已选中元素，点击“隐藏此元素”确认'):'无法生成稳定选择器，可点击“手动输入”';ignoreClickUntil=Date.now()+500;};"
                + "var pick=function(e){if(inBar(e))return;block(e,true);if(Date.now()<ignoreClickUntil)return;styleReads=[];selectTarget(at(e),e);};"
                + "var start=function(e){styleReads=[];var p=e.touches&&e.touches[0]||e;touchTarget=at(e);touchX=p.clientX||0;touchY=p.clientY||0;touchMoved=false;mark(target(touchTarget));};"
                + "var move=function(e){var p=e.touches&&e.touches[0]||e;if(p&&((p.clientX-touchX)*(p.clientX-touchX)+(p.clientY-touchY)*(p.clientY-touchY)>144))touchMoved=true;};"
                + "var finish=function(e){var t=touchTarget;touchTarget=null;ignoreClickUntil=Date.now()+800;if(!t||touchMoved){mark(selected,selectedNodes);return;}selectTarget(t,e);};"
                + "var pointerDown=function(e){if(inBar(e))return;block(e,e.pointerType!=='touch');start(e);};"
                + "var pointerMove=function(e){if(inBar(e))return;block(e,false);if(e.pointerType==='touch'){move(e);return;}styleReads=[];mark(target(at(e)));};"
                + "var pointerUp=function(e){if(inBar(e))return;block(e,true);if(e.pointerType==='touch'&&touchActive)return;finish(e);};"
                + "var touchStart=function(e){if(inBar(e))return;block(e,false);touchActive=true;start(e);if(e.touches&&e.touches.length>1)touchMoved=true;};"
                + "var touchMove=function(e){if(inBar(e))return;block(e,false);move(e);};"
                + "var touchEnd=function(e){if(inBar(e))return;block(e,true);touchActive=false;finish(e);};"
                + "var cancelTouch=function(e){if(inBar(e))return;block(e,false);touchTarget=null;touchActive=false;ignoreClickUntil=Date.now()+800;mark(selected,selectedNodes);};"
                + "var suppress=function(e){if(!inBar(e))block(e,true);};"
                + "var key=function(e){if(e.key==='Escape'){e.preventDefault();send({cancel:true});}};"
                + "style=document.createElement('style');style.textContent='.__median_ad_target{outline:3px solid #1a73e8!important;outline-offset:2px!important;background-color:rgba(26,115,232,.12)!important}';(document.head||document.documentElement).appendChild(style);"
                // Catch taps above cross-origin frames and shadow trees without delivering them to the ad.
                // Not cancelling touchstart/touchmove preserves the browser's normal pan/scroll gesture.
                + "shield=document.createElement('div');shield.setAttribute('aria-hidden','true');shield.style.cssText='position:fixed;inset:0;z-index:2147483647;background:transparent;touch-action:auto;pointer-events:auto';document.documentElement.appendChild(shield);"
                + "bar=document.createElement('div');bar.setAttribute('role','dialog');bar.setAttribute('aria-label','广告元素选择器');bar.style.cssText='position:fixed;z-index:2147483647;left:10px;right:10px;top:calc(10px + env(safe-area-inset-top));display:flex;flex-wrap:wrap;align-items:center;gap:7px;padding:10px 11px;border-radius:14px;background:#202124;color:#fff;font:600 13px/1.35 system-ui,sans-serif;box-shadow:0 8px 28px rgba(0,0,0,.35)';label=document.createElement('span');label.textContent='选择要隐藏的元素';label.style.cssText='flex:1 1 100%;min-height:18px';var actions=document.createElement('div');actions.style.cssText='display:flex;flex:1 1 100%;gap:6px;flex-wrap:wrap';var buttonStyle='border:0;border-radius:10px;padding:8px 10px;background:#3c4043;color:#fff;font:inherit;min-height:36px';confirmButton=document.createElement('button');confirmButton.textContent='隐藏此元素';confirmButton.disabled=true;confirmButton.style.cssText=buttonStyle+';background:#1a73e8';var again=document.createElement('button');again.textContent='重新选择';again.style.cssText=buttonStyle;var manual=document.createElement('button');manual.textContent='手动输入';manual.style.cssText=buttonStyle;var cancel=document.createElement('button');cancel.textContent='取消';cancel.style.cssText=buttonStyle;bar.__cancel=cancel;actions.appendChild(confirmButton);actions.appendChild(again);actions.appendChild(manual);actions.appendChild(cancel);bar.appendChild(label);bar.appendChild(actions);document.documentElement.appendChild(bar);"
                + "confirmButton.onclick=function(e){e.preventDefault();e.stopPropagation();if(!selected||!selectedSelector)return;send({selector:selectedSelector,count:selectedNodes.length,tag:selected.tagName.toLowerCase(),text:String(selected.innerText||'').trim().slice(0,120)});};"
                + "again.onclick=function(e){e.preventDefault();e.stopPropagation();reset();};"
                + "manual.onclick=function(e){e.preventDefault();e.stopPropagation();send({manual:true});};"
                + "cancel.onclick=function(e){e.preventDefault();e.stopPropagation();send({cancel:true});};"
                + "listen('pointerover',hover);listen('pointerdown',pointerDown);listen('pointermove',pointerMove);listen('pointerup',pointerUp);listen('pointercancel',cancelTouch);listen('mousedown',suppress);listen('mouseup',suppress);listen('click',pick);listen('dblclick',suppress);listen('contextmenu',suppress);listen('touchstart',touchStart);listen('touchmove',touchMove);listen('touchend',touchEnd);listen('touchcancel',cancelTouch);listen('keydown',key);window.__medianAdPicker={cancel:function(){send({cancel:true});}};"
                + "return true;})();";
    }

    /** Returns 1–200 matched elements, 0 for stale/oversized selections, or -1 when invalid. */
    static String validate(String selector) {
        return validate(selector, false);
    }

    static String validate(String selector, boolean requireSelection) {
        return "(function(){'use strict';try{var q=" + quote(selector)
                + ",nodes=document.querySelectorAll(q);" + selectionGuard(requireSelection)
                + "return nodes.length;}catch(_){return -1;}})();";
    }

    static String hide(String selector) {
        return hide(selector, false);
    }

    static String hide(String selector, boolean requireSelection) {
        return "(function(){'use strict';try{var q=" + quote(selector) + ",d=document," +
                "nodes=d.querySelectorAll(q);" + selectionGuard(requireSelection) +
                "var s=d.getElementById('__median_manual_blocks');" +
                "if(!s){s=d.createElement('style');s.id='__median_manual_blocks';" +
                "(d.head||d.documentElement).appendChild(s);}" +
                "s.appendChild(d.createTextNode(q+'{display:none!important}'));" +
                "nodes.forEach(function(e){e.style.setProperty('display','none','important');});return nodes.length;" +
                "}catch(_){return -1;}})();";
    }

    /** Keep the identity check and hide in one JavaScript turn, before any DOM mutation. */
    private static String selectionGuard(boolean requireSelection) {
        String limit = "if(!nodes.length||nodes.length>200)return 0;";
        if (!requireSelection) return limit;
        return limit + "var picked=window.__medianAdSelection;"
                + "if(!picked||picked.selector!==q||!Array.isArray(picked.nodes)||picked.nodes.length!==nodes.length)return 0;"
                + "if(typeof picked.revalidate==='function'){if(!picked.revalidate(q,nodes))return 0;}"
                + "else for(var i=0;i<nodes.length;i++)if(nodes[i]!==picked.nodes[i])return 0;";
    }

    private static String quote(String value) {
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
}
