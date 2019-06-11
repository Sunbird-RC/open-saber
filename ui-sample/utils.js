function dataLocalizeRefresh() {
  var opts = { language: "es", pathPrefix: "./lang" };
  $("[data-localize]").localize("stringData", opts);
  console.log("dataLocalizeRefreshEnd");
}
