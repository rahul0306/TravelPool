import { createRemoteJWKSet, jwtVerify } from "jose";

export interface Env {
	SEARCH_KV: KVNamespace;
	AMADEUS_CLIENT_ID: string;
	AMADEUS_CLIENT_SECRET: string;
	APP_SHARED_SECRET: string;
	FIREBASE_PROJECT_ID: string;
	
	AMADEUS_BASE_URL?: string;
}

const DAILY_LIMIT = 100;
const MONTHLY_LIMIT = 2000;

// ---------- Response helpers ----------
function json(data: unknown, status = 200) {
	return new Response(JSON.stringify(data), {
		status,
		headers: { "content-type": "application/json" },
	});
}

function ymd(d = new Date()) {
	return d.toISOString().slice(0, 10); // YYYY-MM-DD
}
function ym(d = new Date()) {
	return d.toISOString().slice(0, 7); // YYYY-MM
}

function badRequest(message: string) {
	return json({ error: "Bad Request", message }, 400);
}

function unauthorized() {
	return json({ error: "Forbidden" }, 403);
}

// ---------- Security ----------
function requireAppSecret(req: Request, env: Env) {
	const given = (req.headers.get("x-app-secret") || "").trim();
	const expected = (env.APP_SHARED_SECRET || "").trim();
	if (!given || !expected || given !== expected) throw new Error("Forbidden");
}

const FIREBASE_JWKS = createRemoteJWKSet(
	new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);

type FirebaseAuthResult = {
	uid: string;
	claims: Record<string, any>;
};

async function requireFirebaseAuth(req: Request, env: Env): Promise<FirebaseAuthResult> {
	const auth = (req.headers.get("Authorization") || "").trim();
	if (!auth.startsWith("Bearer ")) {
		throw new Error("Missing Bearer token");
	}

	const token = auth.slice("Bearer ".length).trim();
	if (!token) throw new Error("Empty token");

	const projectId = (env.FIREBASE_PROJECT_ID || "").trim();
	if (!projectId) throw new Error("Missing FIREBASE_PROJECT_ID");

	// Firebase requires:
	// aud == projectId, iss == "https://securetoken.google.com/<projectId>", sub non-empty
	// and signature verified using Google keys (kid). :contentReference[oaicite:2]{index=2}
	const { payload } = await jwtVerify(token, FIREBASE_JWKS, {
		audience: projectId,
		issuer: `https://securetoken.google.com/${projectId}`,
	});

	const uid = String(payload.sub || "");
	if (!uid) throw new Error("Token missing sub");

	return { uid, claims: payload as any };
}


// ---------- Rate limiting ----------
async function enforceLimits(env: Env, userKey: string) {
	const dayKey = `rl:day:${userKey}:${ymd()}`;
	const monthKey = `rl:month:${userKey}:${ym()}`;

	const [dayStr, monthStr] = await Promise.all([
		env.SEARCH_KV.get(dayKey),
		env.SEARCH_KV.get(monthKey),
	]);

	const dayCount = (dayStr ? Number(dayStr) : 0) + 1;
	const monthCount = (monthStr ? Number(monthStr) : 0) + 1;

	if (dayCount > DAILY_LIMIT || monthCount > MONTHLY_LIMIT) {
		return { ok: false, dayCount, monthCount };
	}

	await Promise.all([
		env.SEARCH_KV.put(dayKey, String(dayCount), { expirationTtl: 60 * 60 * 24 * 2 }), // ~2 days
		env.SEARCH_KV.put(monthKey, String(monthCount), { expirationTtl: 60 * 60 * 24 * 40 }), // ~40 days
	]);

	return { ok: true, dayCount, monthCount };
}

// ---------- Stable cache keys ----------
function stableStringify(obj: any): string {
	// Deterministic JSON stringify by sorting object keys recursively
	if (obj === null || typeof obj !== "object") return JSON.stringify(obj);
	if (Array.isArray(obj)) return `[${obj.map(stableStringify).join(",")}]`;
	const keys = Object.keys(obj).sort();
	return `{${keys.map((k) => JSON.stringify(k) + ":" + stableStringify(obj[k])).join(",")}}`;
}

function cacheKey(prefix: string, parts: any) {
	const s = stableStringify(parts);
	// Avoid huge KV keys by hashing-ish shortening (cheap)
	// (Not cryptographic—just to keep key size sane)
	let h = 0;
	for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
	return `${prefix}:${h.toString(16)}`;
}

// ---------- Amadeus OAuth + request helpers ----------
const AMADEUS_TEST_BASE = "https://test.api.amadeus.com";

function amadeusBase(env: Env) {
	const base = (env.AMADEUS_BASE_URL || "").trim();
	return base || AMADEUS_TEST_BASE;
}

type AmadeusTokenResponse = {
	access_token: string;
	expires_in: number;
	token_type: string;
};

async function fetchAmadeusToken(env: Env): Promise<string> {
	const resp = await fetch(`${amadeusBase(env)}/v1/security/oauth2/token`, {
		method: "POST",
		headers: { "Content-Type": "application/x-www-form-urlencoded" },
		body: new URLSearchParams({
			grant_type: "client_credentials",
			client_id: env.AMADEUS_CLIENT_ID,
			client_secret: env.AMADEUS_CLIENT_SECRET,
		}).toString(),
	});

	if (!resp.ok) {
		const text = await resp.text();
		throw new Error(`Amadeus token failed: ${resp.status} ${text}`);
	}

	const tok = (await resp.json()) as AmadeusTokenResponse;
	const ttl = Math.max(60, (tok.expires_in ?? 1800) - 60); // safety buffer
	await env.SEARCH_KV.put("amadeus:token", tok.access_token, { expirationTtl: ttl });
	return tok.access_token;
}

async function getAmadeusToken(env: Env): Promise<string> {
	const cached = await env.SEARCH_KV.get("amadeus:token");
	if (cached) return cached;
	return fetchAmadeusToken(env);
}

async function amadeusGet(
	env: Env,
	path: string,
	params?: Record<string, string>
): Promise<any> {
	const makeUrl = () => {
		const url = new URL(`${amadeusBase(env)}${path}`);
		if (params) Object.entries(params).forEach(([k, v]) => url.searchParams.set(k, v));
		return url.toString();
	};

	// First try with cached token
	let token = await getAmadeusToken(env);
	let resp = await fetch(makeUrl(), { headers: { Authorization: `Bearer ${token}` } });

	// Refresh once on 401
	if (resp.status === 401) {
		await env.SEARCH_KV.delete("amadeus:token");
		token = await fetchAmadeusToken(env);
		resp = await fetch(makeUrl(), { headers: { Authorization: `Bearer ${token}` } });
	}

	if (!resp.ok) {
		const text = await resp.text();
		throw {
			status: resp.status,
			path,
			detail: text,
		};
	}

	return resp.json();
}

// ---------- Types returned to the Android app ----------
type AirportSuggestion = {
	iataCode: string;
	name: string;
	cityName: string;
	countryName: string;
};

type HotelSuggestion = {
	hotelId: string; // keep name aligned with Android model
	name: string;
	cityName: string;
	countryName: string;
	address?: string;
	placeId?: string; // we store CITY CODE here for /search/hotels
};

type FlightOfferUi = {
	id: string;
	summary: string;
	price: string;
	deepLink?: string | null;
};

type HotelOfferUi = {
	id: string;
	name: string;
	price: string;
	address?: string | null;
	deepLink?: string | null;
};

// ---------- Provider implementations (Amadeus) ----------
async function autocompleteAirportsProvider(q: string, env: Env): Promise<AirportSuggestion[]> {
	const data = await amadeusGet(env, "/v1/reference-data/locations", {
		keyword: q,
		subType: "AIRPORT",
		"page[limit]": "8",
		view: "LIGHT",
		sort: "analytics.travelers.score",
	});

	return (data?.data ?? []).map((x: any) => ({
		iataCode: x.iataCode ?? "",
		name: x.name ?? "",
		cityName: x.address?.cityName ?? "",
		countryName: x.address?.countryCode ?? "",
	}));
}

// For hotels we recommend city autocomplete. The app label "Hotel / city" still works well.
async function autocompleteHotelsProvider(q: string, env: Env): Promise<HotelSuggestion[]> {
	const data = await amadeusGet(env, "/v1/reference-data/locations", {
		keyword: q,
		subType: "CITY",
		"page[limit]": "8",
		view: "FULL", // ✅ important so geoCode is present more often
		sort: "analytics.travelers.score",
	});

	return (data?.data ?? [])
		.map((x: any) => {
			const cityCode = (x.iataCode ?? "").toUpperCase();
			const cityName = x.address?.cityName ?? x.name ?? "";
			const country = x.address?.countryCode ?? "";

			const lat = x.geoCode?.latitude;
			const lon = x.geoCode?.longitude;

			// ✅ if no geo, return null (we'll filter it out)
			if (lat == null || lon == null) return null;

			return {
				hotelId: cityCode,
				name: cityCode ? `${cityName} (${cityCode})` : cityName,
				cityName,
				countryName: country,
				placeId: `${lat},${lon}`, // ✅ never empty now
			};
		})
		.filter(Boolean);

}

async function searchFlightsProvider(body: Record<string, unknown>, env: Env): Promise<FlightOfferUi[]> {
	const origin = String(body.origin || "").trim().toUpperCase();
	const destination = String(body.destination || "").trim().toUpperCase();
	const departureDate = String(body.departureDate || "").trim();
	const returnDate = String(body.returnDate || "").trim();
	const adults = String(body.adults || "1").trim();

	const params: Record<string, string> = {
		originLocationCode: origin,
		destinationLocationCode: destination,
		departureDate,
		adults,
		currencyCode: "USD",
		max: "10",
	};
	if (returnDate) params.returnDate = returnDate;

	const res = await amadeusGet(env, "/v2/shopping/flight-offers", params);

	return (res?.data ?? []).map((o: any, idx: number) => {
		const price = o?.price?.grandTotal;
		const cur = o?.price?.currency;

		// best-effort summary
		const seg0 = o?.itineraries?.[0]?.segments?.[0];
		const dep = seg0?.departure?.iataCode ?? origin;
		const arr = seg0?.arrival?.iataCode ?? destination;

		const stops = Math.max(0, (o?.itineraries?.[0]?.segments?.length ?? 1) - 1);
		const stopsText = stops === 0 ? "nonstop" : `${stops} stop${stops > 1 ? "s" : ""}`;

		return {
			id: o?.id ?? String(idx),
			summary: `${dep} → ${arr} (${departureDate}) • ${stopsText}`,
			price: price && cur ? `${cur} ${price}` : "Price unavailable",
			deepLink: null,
		};
	});
}

async function searchHotelsProvider(body: Record<string, unknown>, env: Env): Promise<HotelOfferUi[]> {
	const placeId = String(body.placeId || "").trim(); // "lat,lon"
	const checkIn = String(body.checkIn || "").trim();
	const checkOut = String(body.checkOut || "").trim();
	const rooms = String(body.rooms || "1").trim();

	const [latStr, lonStr] = placeId.split(",");
	const latitude = (latStr || "").trim();
	const longitude = (lonStr || "").trim();
	if (!latitude || !longitude) return [];

	// Step A: list hotels near geocode (fast)
	const list = await amadeusGet(env, "/v1/reference-data/locations/hotels/by-geocode", {
		latitude,
		longitude,
		radius: "5",
		radiusUnit: "KM",
	});

	const hotelIds: string[] = (list?.data ?? [])
		.map((h: any) => h.hotelId)
		.filter((id: any) => typeof id === "string" && id.length > 0)
		.slice(0, 30);

	if (hotelIds.length === 0) return [];

	// Helper to map offers -> UI
	const toUi = (offers: any): HotelOfferUi[] => {
		return (offers?.data ?? []).map((item: any) => {
			const hotel = item?.hotel ?? {};
			const offer0 = item?.offers?.[0];
			const price = offer0?.price?.total;
			const cur = offer0?.price?.currency;

			const addr =
				hotel?.address
					? [
						Array.isArray(hotel.address.lines) ? hotel.address.lines.join(" ") : hotel.address.lines,
						hotel.address.cityName,
						hotel.address.countryCode,
					].filter(Boolean).join(", ")
					: null;

			return {
				id: hotel.hotelId ?? "",
				name: hotel.name ?? "Hotel",
				price: price && cur ? `${cur} ${price}` : "Price unavailable",
				address: addr,
				deepLink: null,
			};
		});
	};

	// Step B: batch hotelIds in groups of 3 (max) and stop early
	const start = Date.now();
	const TIME_BUDGET_MS = 6500;     // keep under typical mobile timeouts
	const TARGET_RESULTS = 8;

	const results: HotelOfferUi[] = [];
	const seen = new Set<string>();

	// batches of 3
	const batches: string[][] = [];
	for (let i = 0; i < hotelIds.length; i += 3) {
		batches.push(hotelIds.slice(i, i + 3));
	}

	for (const batch of batches) {
		if (Date.now() - start > TIME_BUDGET_MS) break;
		if (results.length >= TARGET_RESULTS) break;

		try {
			const offers = await amadeusGet(env, "/v3/shopping/hotel-offers", {
				hotelIds: batch.join(","),     // ✅ batch of 3
				checkInDate: checkIn,
				checkOutDate: checkOut,
				roomQuantity: rooms,
				adults: "1",
			});

			for (const ui of toUi(offers)) {
				if (!ui.id || seen.has(ui.id)) continue;
				seen.add(ui.id);
				results.push(ui);
				if (results.length >= TARGET_RESULTS) break;
			}
		} catch (e: any) {
			// 429: back off a bit; 400: skip this batch
			const status = e?.status;
			if (status === 429) {
				console.log("hotel-offers 429; backing off briefly");
				await new Promise((r) => setTimeout(r, 350));
			} else {
				console.log("hotel-offers batch failed", batch.join(","), status);
			}
		}
	}

	return results;
}



// ---------- Main handler ----------
export default {
	async fetch(req: Request, env: Env): Promise<Response> {
		const url = new URL(req.url);

		// Ignore favicon noise
		if (req.method === "GET" && url.pathname === "/favicon.ico") {
			return new Response(null, { status: 204 });
		}

		// Guard access
		let authUser: FirebaseAuthResult;
		try {
			authUser = await requireFirebaseAuth(req, env);
		} catch {
			return unauthorized(); // 403
		}

		// Verified UID from token
		const userId = authUser.uid;
		const rl = await enforceLimits(env, userId);
		if (!rl.ok) return json({ error: "Rate limit exceeded", ...rl }, 429);

		if (req.method === "GET" && url.pathname === "/whoami") {
			return json({ uid: userId });
		}
		
		try {
			// ---- Airports Autocomplete ----
			if (req.method === "GET" && url.pathname === "/autocomplete/airports") {
				const q = (url.searchParams.get("q") || "").trim();
				if (q.length < 2) return json([]);

				const ck = cacheKey("ac_airports", { q: q.toLowerCase() });
				const cached = await env.SEARCH_KV.get(ck);
				if (cached) return json(JSON.parse(cached));

				const result = await autocompleteAirportsProvider(q, env);
				await env.SEARCH_KV.put(ck, JSON.stringify(result), { expirationTtl: 60 * 10 });
				return json(result);
			}

			// ---- Hotels Autocomplete (CITY suggestions) ----
			if (req.method === "GET" && url.pathname === "/autocomplete/hotels") {
				const q = (url.searchParams.get("q") || "").trim();
				if (q.length < 2) return json([]);

				const ck = cacheKey("ac_hotels", { q: q.toLowerCase() });
				const cached = await env.SEARCH_KV.get(ck);
				if (cached) return json(JSON.parse(cached));

				const result = await autocompleteHotelsProvider(q, env);
				await env.SEARCH_KV.put(ck, JSON.stringify(result), { expirationTtl: 60 * 10 });
				return json(result);
			}

			// ---- Flights Search ----
			if (req.method === "POST" && url.pathname === "/search/flights") {
				const body = (await req.json()) as Record<string, unknown>;

				const origin = String(body.origin || "").trim();
				const destination = String(body.destination || "").trim();
				const departureDate = String(body.departureDate || "").trim();
				const adults = String(body.adults || "1").trim();

				if (!origin || !destination || !departureDate || !adults) {
					return badRequest("Missing required fields: origin, destination, departureDate, adults");
				}

				const ck = cacheKey("flights", body);
				const cached = await env.SEARCH_KV.get(ck);
				if (cached) return json(JSON.parse(cached));

				const result = await searchFlightsProvider(body, env);
				await env.SEARCH_KV.put(ck, JSON.stringify(result), { expirationTtl: 60 * 20 });
				return json(result);
			}

			// ---- Hotels Search ----
			if (req.method === "POST" && url.pathname === "/search/hotels") {
				const body = (await req.json()) as Record<string, unknown>;

				const placeId = String(body.placeId || "").trim();
				const checkIn = String(body.checkIn || "").trim();
				const checkOut = String(body.checkOut || "").trim();
				const rooms = String(body.rooms || "1").trim();

				if (!placeId || !checkIn || !checkOut || !rooms) {
					return badRequest("Missing required fields: placeId, checkIn, checkOut, rooms");
				}

				// placeId must be an IATA city code for this implementation
				// (Your Android should set it only when the user picks a suggestion.)
				const ck = cacheKey("hotels", body);
				const cached = await env.SEARCH_KV.get(ck);
				if (cached) return json(JSON.parse(cached));

				const result = await searchHotelsProvider(body, env);
				await env.SEARCH_KV.put(ck, JSON.stringify(result), { expirationTtl: 60 * 20 });
				return json(result);
			}

			return json({ error: "Not found" }, 404);
		} catch (e: any) {
			// Don’t leak secrets. Return a safe error message.
			const status = typeof e?.status === "number" ? e.status : 502;
			return json(
				{
					error: "Upstream error",
					status,
					path: e?.path,
					detail: String(e?.detail ?? e?.message ?? e ?? "Unknown error"),
				},
				status
			);
		}
	},
};
