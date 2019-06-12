function dataLocalizeRefresh(lang, fullLangText) {
  console.log('data', lang, fullLangText)
  document.getElementById("langSelected").innerHTML = fullLangText

  var opts = { language: lang, pathPrefix: "./lang" };
  $("[data-localize]").localize("stringData", opts);

  console.log("dataLocalizeRefreshEnd " + lang);
}

function dataLocalizeRefreshDefault() {
  var langSelected = document.getElementById("langSelected").innerText
  console.log(langSelected)
  var lang = "en"
  if (langSelected != null) {
    lang = langSelected.innerText
    console.log('lo', langSelected, lang)

  }
  var opts = { language: lang, pathPrefix: "./lang" };
  $("[data-localize]").localize("stringData", opts);
  console.log("dataLocalizeRefreshEnd " + lang);

}
