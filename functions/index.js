// functions/index.js (Versione per Google Maps)

const {onDocumentUpdated, onDocumentCreated, onDocumentWritten} = require("firebase-functions/v2/firestore");
const {setGlobalOptions} = require("firebase-functions/v2");
const admin = require("firebase-admin");
const axios = require("axios");
const {getStorage} = require("firebase-admin/storage");
const {defineString} = require("firebase-functions/params");

// --- Parametri ---
// Ora abbiamo bisogno della chiave API di Google Maps, non di quella di Mapbox
const googleMapsApiKey = defineString("GOOGLE_MAPS_API_KEY");

admin.initializeApp();
setGlobalOptions({region: "europe-west1", maxInstances: 10});

// --- Funzioni Helper per Polyline Encoding ---
// Queste funzioni sono corrette anche per Google Maps, quindi rimangono invariate.
function encodeGooglePolyline(points) {
    let plat = 0;
    let plng = 0;
    let encodedPoints = "";
    if (!points || points.length === 0) {
        return encodedPoints;
    }
    for (const point of points) {
        if (!Array.isArray(point) || point.length < 2) continue;
        const lat = Math.round(point[0] * 1e5);
        const lng = Math.round(point[1] * 1e5);
        const dlat = lat - plat;
        const dlng = lng - plng;
        plat = lat;
        plng = lng;
        encodedPoints += encodeSignedNumber(dlat) + encodeSignedNumber(dlng);
    }
    return encodedPoints;
}
function encodeSignedNumber(num) {
    let sgnNum = num << 1;
    if (num < 0) {
        sgnNum = ~sgnNum;
    }
    return encodeNumber(sgnNum);
}
function encodeNumber(num) {
    let encodeString = "";
    while (num >= 0x20) {
        encodeString += String.fromCharCode((0x20 | (num & 0x1f)) + 63);
        num >>= 5;
    }
    encodeString += String.fromCharCode(num + 63);
    return encodeString;
}

// --- Funzioni Trigger Essenziali ---

/**
 * Funzione 1: Geocodifica Paese del Percorso (su creazione)
 * INVARIATA: Usa Nominatim, non dipende da Google Maps o Mapbox.
 */
exports.geocodeRouteCountry = onDocumentCreated("percorsi/{routeId}", async (event) => {
    const routeData = event.data.data();
    const routeId = event.params.routeId;
    const startPoint = routeData.startPoint;

    if (!startPoint || !(startPoint.latitude) || !(startPoint.longitude)) {
        console.log(`Route ${routeId} missing startPoint. Exiting geocoding.`);
        return null;
    }

    const {latitude, longitude} = startPoint;
    const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latitude}&lon=${longitude}&zoom=3`;

    try {
        console.log(`Geocoding coordinates for new route ${routeId}`);
        const response = await axios.get(url, {
            headers: {"User-Agent": "CurvApp/1.0 (inserisci_tua_email@example.com)"},
            timeout: 5000,
        });

        const countryCode = response.data?.address?.country_code;

        if (countryCode && typeof countryCode === "string") {
            const finalCountryCode = countryCode.toUpperCase();
            console.log(`Found country code: ${finalCountryCode}. Updating doc ${routeId}.`);
            return event.data.ref.update({countryCode: finalCountryCode});
        } else {
            console.warn(`Could not determine country code for route ${routeId}.`);
            return null;
        }
    } catch (error) {
        console.error(`Error geocoding route ${routeId}:`, error.message);
        return null;
    }
});


/**
 * Funzione 2: Aggrega Likes percorsi
 * INVARIATA: Opera solo su Firestore.
 */
exports.aggregateLikes = onDocumentWritten("percorsi/{routeId}/likes/{userId}", async (event) => {
    const routeId = event.params.routeId;
    const likesRef = admin.firestore().collection("percorsi").doc(routeId).collection("likes");
    const routeRef = admin.firestore().collection("percorsi").doc(routeId);

    try {
        const likesSnapshot = await likesRef.count().get();
        const likeCount = likesSnapshot.data().count;
        console.log(`Updating likeCount for route ${routeId} to ${likeCount}.`);
        return routeRef.update({likeCount: likeCount});
    } catch (error) {
         console.error(`Error aggregating likes for route ${routeId}:`, error);
        return null;
    }
});


/**
 * Funzione 3: Gestione Cambio Stato Percorso (Aggiornamento Documento)
 * MODIFICATA per usare Google Maps Static API.
 */
exports.onRouteStatusChanged = onDocumentUpdated("percorsi/{routeId}", async (event) => {
    const newValue = event.data.after.data();
    const previousValue = event.data.before.data();
    const routeId = event.params.routeId;

    // --- Logica Approvazione (Genera Mappa Statica con Google Maps) ---
    if (previousValue.status === "pending" && newValue.status === "approved" && !newValue.staticMapUrl) {
        console.log(`Route ${routeId} approved. Generating Google static map.`);

        const tracciato = newValue.tracciato;
        if (tracciato && Array.isArray(tracciato) && tracciato.length >= 2) {
            const coordinates = tracciato.map((p) => [p.lat, p.lng]);
            const encodedPolyline = encodeGooglePolyline(coordinates);

            // Colori in formato 0xRRGGBBAA per l'API di Google Maps
            const difficultyColors = {"Easy": "0x059669FF", "Medium": "0xF59E0BFF", "Hard": "0xDC2626FF"};
            const difficulty = newValue.difficulty || "Medium";
            const routeColor = difficultyColors[difficulty] || "0x6B7280FF";

            const apiKey = googleMapsApiKey.value();
            if (apiKey && encodedPolyline) {
                const path = `color:${routeColor}|weight:4|enc:${encodedPolyline}`;
                // URL per Google Maps Static API
                const googleMapsUrl = `https://maps.googleapis.com/maps/api/staticmap?size=400x200&path=${encodeURIComponent(path)}&key=${apiKey}`;

                try {
                    console.log(`Generating Google map preview image for route ${routeId}`);
                    const imageResponse = await axios.get(googleMapsUrl, {responseType: "arraybuffer"});
                    const imageBuffer = Buffer.from(imageResponse.data, "binary");
                    const bucket = getStorage().bucket();
                    const filePath = `map_previews/${routeId}.png`;
                    const file = bucket.file(filePath);

                    await file.save(imageBuffer, {metadata: {contentType: "image/png"}});
                    await file.makePublic();
                    const publicUrl = file.publicUrl();

                    await event.data.after.ref.update({staticMapUrl: publicUrl});
                    console.log(`Successfully generated and saved Google map preview for route ${routeId}: ${publicUrl}`);
                } catch (error) {
                    console.error(`Error generating Google map preview for route ${routeId}:`, error.response ? error.response.statusText : error.message);
                }
            } else {
                console.error("Google Maps API Key not configured or polyline empty for static map on route", routeId);
            }
        } else {
            console.log(`Route ${routeId} approved but has no valid 'tracciato' for preview generation.`);
        }
    }

    // --- Logica Notifica Cambio Stato (INVARIATA) ---
    if (newValue.status !== previousValue.status && (newValue.status === "approved" || newValue.status === "rejected")) {
        const creatorUid = newValue.creatoreUid;
        if (!creatorUid) return null;

        const userDoc = await admin.firestore().collection("utenti").doc(creatorUid).get();
        if (!userDoc.exists) return null;

        const userData = userDoc.data();
        if (userData.notificationsEnabled !== true || !userData.fcmToken) return null;

        const fcmToken = userData.fcmToken;
        const routeName = newValue.nome || "your route";
        let title = "";
        let body = "";

        if (newValue.status === "approved") {
            title = "Route Approved! 🎉";
            body = `Your route "${routeName}" is now live and visible to everyone!`;
        } else {
            title = "Route Reviewed";
            const reason = newValue.rejectionReason;
            body = reason ? `Your route "${routeName}" was rejected. Reason: ${reason}` : `Your route "${routeName}" has been reviewed.`;
        }

        const payload = {
            notification: {title, body},
            token: fcmToken,
        };

        try {
            console.log(`Sending status change notification (${newValue.status}) to user ${creatorUid} for route ${routeId}.`);
            await admin.messaging().send(payload);
        } catch (error) {
            console.error(`Error sending status change notification to ${creatorUid}:`, error);
        }
    }
    return null;
});