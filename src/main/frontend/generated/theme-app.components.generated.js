


if (!document['_vaadintheme_app_componentCss']) {
  
  document['_vaadintheme_app_componentCss'] = true;
}

if (import.meta.hot) {
  import.meta.hot.accept((module) => {
    window.location.reload();
  });
}

