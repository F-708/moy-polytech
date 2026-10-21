"use strict";

/*
 * mpk-page.js — отслеживание страницы для DOCX-вьювера «Мой Политех».
 *
 * pdf.mjs сам шлёт «page N OF» и обрабатывает «g<n>» (переход к странице) — этот файл
 * подключается ТОЛЬКО в docx.html, где docx-preview раскладывает страницы как
 * .docx-wrapper > section.docx и сам ничего в порт не сообщает.
 *
 * Порт — тот же WebMessage-канал, что и поиск: window-message «vw-search-port» слушают
 * и find.js (ставит port.onmessage), и этот файл (addEventListener — оба получают события).
 *
 * Протокол (DocWebBridge.kt):
 *   JS → native: «page N OF» (1-based) — какая страница сейчас наверху;
 *   native → JS: «g<n>» — перейти к странице n (1-based).
 *
 * v2.0: рисование (mpk-ink.js) удалено — лагало на слабых устройствах. Остался только
 * учёт страниц для закладок и индикатора «N/M».
 */

(function () {
  var port = null;
  var wrap = null;
  var lastPageMsg = "";

  function pagesWrap() {
    return wrap || (wrap = document.querySelector(".docx-wrapper"));
  }

  function sections() {
    var w = pagesWrap();
    return w ? w.querySelectorAll("section.docx") : [];
  }

  function send(msg) { if (port) port.postMessage(msg); }

  function reportPage() {
    if (!port) return;
    var boxes = sections();
    if (!boxes.length) return;
    var vv = window.visualViewport;
    var top = vv ? vv.pageTop : window.scrollY;
    var vh = vv ? vv.height : window.innerHeight;
    var mid = top + vh / 3;
    var idx = 0;
    for (var i = 0; i < boxes.length; i++) {
      var rect = boxes[i].getBoundingClientRect();
      if (rect.top + top <= mid) idx = i; else break;
    }
    var msg = "page " + (idx + 1) + " " + boxes.length;
    if (msg !== lastPageMsg) { lastPageMsg = msg; send(msg); }
  }

  function onPortCommand(data) {
    if (data.charAt(0) === "g") {
      var n = Math.floor(Number(data.slice(1)));
      var boxes = sections();
      if (isFinite(n) && n >= 1 && n <= boxes.length) {
        boxes[n - 1].scrollIntoView({ block: "start", behavior: "auto" });
        requestAnimationFrame(reportPage);
      }
    }
  }

  window.addEventListener("message", function (e) {
    if (!e.ports || !e.ports.length || port) return;
    port = e.ports[0];
    port.addEventListener("message", function (m) {
      try { onPortCommand(String(m.data || "")); } catch (err) { /* канал не должен умереть */ }
    });
    port.start();
    requestAnimationFrame(reportPage);
  });

  /* Страницы (section.docx) появляются после рендера документа — сообщаем число страниц,
     когда они созданы. */
  var mo = new MutationObserver(function () { requestAnimationFrame(reportPage); });
  function observeWrap() {
    var w = pagesWrap();
    if (w) mo.observe(w, { childList: true });
    else setTimeout(observeWrap, 50);
  }
  observeWrap();

  window.addEventListener("scroll", function () {
    requestAnimationFrame(reportPage);
  }, { passive: true });
})();
