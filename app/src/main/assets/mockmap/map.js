(function () {
    "use strict";

    function notify(name, a, b) {
        try {
            var bridge = window.ToolboxMap;
            if (bridge && typeof bridge[name] === "function") {
                if (arguments.length > 2) {
                    bridge[name](a, b);
                } else if (arguments.length > 1) {
                    bridge[name](a);
                } else {
                    bridge[name]();
                }
            }
        } catch (ignored) {
        }
    }

    function fail(reason) {
        notify("fail", String(reason || "map"));
    }

    var params = new URLSearchParams(window.location.search);
    var lat = parseFloat(params.get("lat"));
    var lng = parseFloat(params.get("lng"));
    var dark = params.get("dark") === "1";
    var hasStart = Number.isFinite(lat) && Number.isFinite(lng) &&
        lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    var start = hasStart ? [lng, lat] : [105, 35];
    var zoom = hasStart ? 14 : 3;
    var style = dark
        ? "https://tiles.openfreemap.org/styles/fiord"
        : "https://tiles.openfreemap.org/styles/liberty";

    if (typeof maplibregl === "undefined") {
        fail("library");
        return;
    }

    var map;
    var marker = null;

    function place(lngLat) {
        var nextLat = lngLat.lat;
        var nextLng = lngLat.lng;
        if (!Number.isFinite(nextLat) || !Number.isFinite(nextLng)) {
            return;
        }
        if (nextLat < -90 || nextLat > 90 || nextLng < -180 || nextLng > 180) {
            return;
        }
        if (!marker) {
            marker = new maplibregl.Marker({ color: "#c43e1c" })
                .setLngLat(lngLat)
                .addTo(map);
        } else {
            marker.setLngLat(lngLat);
        }
        notify("pick", nextLat, nextLng);
    }

    try {
        map = new maplibregl.Map({
            container: "map",
            style: style,
            center: start,
            zoom: zoom,
            attributionControl: true,
            maplibreLogo: false,
            cooperativeGestures: false,
            fadeDuration: 0,
        });
        map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right");
        map.on("styleimagemissing", function (event) {
            var id = event && event.id;
            if (id && !map.hasImage(id)) {
                map.addImage(id, {
                    width: 1,
                    height: 1,
                    data: new Uint8Array([0, 0, 0, 0]),
                });
            }
        });
        map.once("load", function () {
            notify("ready");
            if (hasStart) {
                place({ lng: start[0], lat: start[1] });
            }
        });
        map.on("click", function (event) {
            place(event.lngLat);
        });
    } catch (error) {
        fail(error && error.message ? error.message : "init");
    }
})();
