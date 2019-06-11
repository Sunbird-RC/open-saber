function dataLocalizeRefresh(lang, fullLangText) {
  var opts = { language: lang, pathPrefix: "./lang" };
  $("[data-localize]").localize("stringData", opts);
  document.getElementById("langSelected").innerText = fullLangText
  console.log("dataLocalizeRefreshEnd " + lang);
}

function dataLocalizeRefreshDefault() {
  var langSelected = document.getElementById("langSelected")
  var lang = "en"
  if (langSelected != null) {
    lang = langSelected.innerText
  }
  var opts = { language: lang, pathPrefix: "./lang" };
  $("[data-localize]").localize("stringData", opts);
  console.log("dataLocalizeRefreshEnd " + lang);
}
