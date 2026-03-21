package dev.rdime.rainfern.ui

internal fun buildRadarMapHtml(
    latitude: Double,
    longitude: Double,
): String = """
    <!doctype html>
    <html lang="en">
    <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <title>Rainfern Radar</title>
        <link
            rel="stylesheet"
            href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
            integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
            crossorigin=""
        />
        <script
            src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
            integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
            crossorigin=""
        ></script>
        <style>
            html, body, #map {
                height: 100%;
                width: 100%;
                margin: 0;
                padding: 0;
                background: #10202b;
                color: #eef8f3;
                font-family: sans-serif;
            }

            .overlay {
                position: absolute;
                left: 12px;
                right: 12px;
                bottom: 12px;
                z-index: 999;
                padding: 10px 12px;
                border-radius: 14px;
                background: rgba(9, 20, 28, 0.78);
                box-shadow: 0 10px 24px rgba(0, 0, 0, 0.24);
                backdrop-filter: blur(12px);
                font-size: 12px;
                line-height: 1.35;
            }

            .badge {
                display: inline-block;
                padding: 4px 8px;
                border-radius: 999px;
                margin-bottom: 6px;
                background: rgba(84, 140, 194, 0.35);
                color: #d9efff;
                font-weight: 600;
            }

            .error {
                position: absolute;
                inset: 0;
                display: none;
                align-items: center;
                justify-content: center;
                z-index: 1200;
                text-align: center;
                padding: 18px;
                background: rgba(8, 16, 22, 0.92);
                color: #f2f7f3;
                font-size: 14px;
            }
        </style>
    </head>
    <body>
        <div id="map" aria-label="Weather radar map"></div>
        <div id="error" class="error">Radar data is currently unavailable.</div>
        <div class="overlay">
            <div class="badge">Past 2h radar</div>
            <div id="frameLabel">Loading RainViewer frames...</div>
            <div>Base map: OpenStreetMap. Radar: RainViewer past precipitation tiles.</div>
        </div>
        <script>
            const center = [${"%.6f".format(latitude)}, ${"%.6f".format(longitude)}];
            const map = L.map("map", {
                center,
                zoom: 5,
                minZoom: 2,
                maxZoom: 7,
                zoomControl: false,
                attributionControl: false
            });

            L.control.zoom({ position: "topright" }).addTo(map);
            L.control.attribution({ position: "bottomright", prefix: false }).addTo(map);

            L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
                maxZoom: 7,
                attribution: '&copy; OpenStreetMap contributors'
            }).addTo(map);

            L.circleMarker(center, {
                radius: 6,
                color: "#dff3ea",
                weight: 2,
                fillColor: "#58a7e2",
                fillOpacity: 0.9
            }).addTo(map);

            let radarLayers = [];
            let frameTimes = [];
            let activeFrame = 0;
            let animationTimer = null;

            function showError(message) {
                const error = document.getElementById("error");
                error.textContent = message;
                error.style.display = "flex";
            }

            function updateFrameLabel() {
                const label = document.getElementById("frameLabel");
                if (!frameTimes.length) {
                    label.textContent = "No radar frames available for this area.";
                    return;
                }

                const value = frameTimes[activeFrame];
                const date = new Date(value * 1000);
                label.textContent = "Frame " + (activeFrame + 1) + "/" + frameTimes.length + " \u2022 " +
                    date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
            }

            function setFrame(index) {
                activeFrame = index;
                radarLayers.forEach((layer, layerIndex) => {
                    layer.setOpacity(layerIndex === activeFrame ? 0.72 : 0.0);
                });
                updateFrameLabel();
            }

            async function loadRadar() {
                try {
                    const response = await fetch("https://api.rainviewer.com/public/weather-maps.json");
                    if (!response.ok) {
                        throw new Error("HTTP " + response.status);
                    }

                    const payload = await response.json();
                    const frames = ((payload.radar && payload.radar.past) || []).slice(-6);
                    if (!frames.length) {
                        showError("RainViewer returned no recent radar frames.");
                        return;
                    }

                    frameTimes = frames.map(frame => frame.time);

                    const coverage = L.tileLayer(payload.host + "/v2/coverage/0/256/{z}/{x}/{y}/0/0_0.png", {
                        opacity: 0.12,
                        maxNativeZoom: 7,
                        maxZoom: 7
                    });
                    coverage.addTo(map);

                    radarLayers = frames.map(frame => {
                        const layer = L.tileLayer(payload.host + frame.path + "/256/{z}/{x}/{y}/3/1_1.png", {
                            opacity: 0.0,
                            maxNativeZoom: 7,
                            maxZoom: 7
                        });
                        layer.addTo(map);
                        return layer;
                    });

                    setFrame(radarLayers.length - 1);
                    animationTimer = window.setInterval(() => {
                        setFrame((activeFrame + 1) % radarLayers.length);
                    }, 1600);
                } catch (error) {
                    showError("Radar map could not load right now.");
                }
            }

            loadRadar();

            window.addEventListener("beforeunload", () => {
                if (animationTimer !== null) {
                    window.clearInterval(animationTimer);
                }
            });
        </script>
    </body>
    </html>
""".trimIndent()
