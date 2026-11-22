package com.pravah.browser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Network-level ad / tracker blocking (host list + a few URL patterns). */
final class AdBlocker {
    private AdBlocker() {}

    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            // Google ads
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com", "tpc.googlesyndication.com",
            "partner.googleadservices.com", "2mdn.net", "admob.com", "adsense.com", "googleads.g.doubleclick.net",
            "ad.doubleclick.net", "static.doubleclick.net", "securepubads.g.doubleclick.net",
            // Ad networks / exchanges
            "adnxs.com", "adsrvr.org", "adform.net", "advertising.com", "criteo.com", "criteo.net",
            "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "smartadserver.com",
            "taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "yieldmo.com", "media.net",
            "amazon-adsystem.com", "moatads.com", "adsafeprotected.com", "scorecardresearch.com",
            "quantserve.com", "bidswitch.net", "contextweb.com", "lijit.com", "sharethrough.com",
            "teads.tv", "adcolony.com", "applovin.com", "unityads.unity3d.com", "inmobi.com", "vungle.com",
            "ads.yahoo.com", "gemini.yahoo.com", "ads.pubmatic.com", "ads.twitter.com", "ads.linkedin.com",
            "an.facebook.com", "adservice.google.co.in",
            // Pop-up / redirect networks
            "popads.net", "popcash.net", "propellerads.com", "onclickads.net", "exoclick.com", "juicyads.com",
            "adsterra.com", "hilltopads.net", "clickadu.com", "trafficjunky.net", "adcash.com", "bidvertiser.com",
            "adk2.co", "a-ads.com", "richpush.co", "pushwoosh.com", "zedo.com",
            // Tracking
            "google-analytics.com", "hotjar.com", "mouseflow.com", "crazyegg.com", "branch.io", "app-measurement.com"
    ));

    /** Ad click-through links (what you get when an ad is tapped): never opened. */
    static boolean isAdClick(String url, String host) {
        if (url == null || host == null) return false;
        String h = host.toLowerCase(Locale.ROOT), u = url.toLowerCase(Locale.ROOT);
        if (h.endsWith("googleadservices.com") || h.endsWith("doubleclick.net") || h.endsWith("googlesyndication.com")) return true;
        boolean google = h.startsWith("www.google.") || h.equals("google.com") || h.endsWith(".google.com");
        if (google && (u.contains("/aclk?") || u.contains("/pagead/aclk"))) return true;
        return h.endsWith("bing.com") && (u.contains("/aclk?") || u.contains("/aclick?"));
    }

    static boolean isBlocked(String url, String host) {
        if (host == null || host.isEmpty()) return false;
        host = host.toLowerCase(Locale.ROOT);

        // suffix match on host list: a.b.c.com -> b.c.com -> c.com
        String h = host;
        while (true) {
            if (HOSTS.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) break;
            h = h.substring(dot + 1);
        }

        String u = url.toLowerCase(Locale.ROOT);
        boolean yt = host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com");
        boolean google = host.startsWith("www.google.") || host.equals("google.com") || host.endsWith(".google.com");

        if (yt) {
            return u.contains("/api/stats/ads") || u.contains("/pagead/") || u.contains("/ptracking")
                    || u.contains("get_midroll_") || u.contains("&adformat=") || u.contains("?adformat=")
                    || u.contains("/pcs/activeview") || u.contains("ad_type=");
        }
        if (google) {
            return u.contains("/pagead/") || u.contains("/adsense/") || u.contains("/gampad/ads");
        }
        // Generic well-known ad paths
        return u.contains("/pagead/ads") || u.contains("/gampad/ads") || u.contains("/doubleclick/")
                || u.contains("adserver.") || u.contains("/adserve/");
    }
}
