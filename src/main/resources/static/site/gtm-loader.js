(function (window, document, tagName, layerName, containerId) {
    window[layerName] = window[layerName] || [];
    window[layerName].push({
        "gtm.start": new Date().getTime(),
        event: "gtm.js"
    });

    const firstScript = document.getElementsByTagName(tagName)[0];
    const script = document.createElement(tagName);
    const layerQuery = layerName !== "dataLayer" ? "&l=" + layerName : "";
    script.async = true;
    script.src = "https://www.googletagmanager.com/gtm.js?id=" + containerId + layerQuery;
    firstScript.parentNode.insertBefore(script, firstScript);
})(window, document, "script", "dataLayer", "GTM-5349TBPM");
