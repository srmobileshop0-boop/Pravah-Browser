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
            // More ad networks / video ads / mobile ads
            "smaato.net", "smaato.com", "chartboost.com", "ironsrc.com", "supersonicads.com", "tapjoy.com", "startappservice.com",
            "mintegral.com", "pangleglobal.com", "byteoversea.com", "ads-api.tiktok.com", "doubleverify.com", "serving-sys.com",
            "bidr.io", "tremorhub.com", "spotxchange.com", "stickyadstv.com", "adition.com", "mopub.com", "adtechus.com",
            "adtech.de", "advertising.amazon.com", "yieldlab.net", "indexww.com", "triplelift.com", "33across.com", "gumgum.com",
            "smartadserver.net", "improvedigital.com", "sovrn.com", "undertone.com", "adskeeper.com", "adskeeper.co.uk",
            "ads.mail.ru", "an.yandex.ru", "yandexadexchange.net", "ezoic.net", "ezodn.com", "monetag.com", "mondiad.com",
            "admaven.com", "ad-maven.com", "clickaine.com", "tsyndicate.com", "syndication.exdynsrv.com", "exdynsrv.com",
            "exosrv.com", "realsrv.com", "juicyads.com", "popunder.net", "yllix.com", "evadav.com", "pushnami.com",
            "bidgear.com", "adspyglass.com", "ero-advertising.com", "trafficstars.com", "tsyndicate.com", "adxpansion.com",
            "wwads.cn", "adsco.re", "ad4m.at", "profitablecpmrate.com", "highperformanceformat.com", "highcpmrevenuegate.com",
            "effectivegatecpm.com", "displayvertising.com", "toprevenuegate.com", "acscdn.com", "rtbsystem.com", "onclckmn.com",
            "onclasrv.com", "pubfuture.com", "adexchangeprediction.com", "dtscout.com", "dtscdn.com", "cloudfront-ad.net",
            "adnetworkperformance.com", "go.oclasrv.com", "ad.gt", "adsrvmedia.net", "mc.yandex.ru", "adriver.ru",
            "app.link.ads", "inmobi.net", "applvn.com", "adjust.com.ads", "fyber.com", "vrtcal.com", "verve.com", "pubnative.net",
            "liftoff.io", "moloco.com", "digitalturbine.com", "ogury.com", "adgear.com", "adgrx.com", "adscale.de",
            // Ad networks / affiliate widgets that video-streaming sites rely on
            "exoclick.com", "exosrv.com", "magsrv.com", "pemsrv.com", "trafficjunky.net", "trafficjunky.com", "trafficfactory.biz",
            "juicyads.com", "ero-advertising.com", "clickadilla.com", "rtbsuccess.com", "adxpansion.com", "plugrush.com",
            "popcash.net", "popads.net", "popunder.net", "propellerads.com", "propu.sh", "onclickads.net", "hilltopads.net",
            "stripchat.com", "stripcdn.com", "strpst.com", "strpjmp.com", "xhamsterlive.com", "xlivrdr.com", "xlivecdn.com",
            "chaturbate.com", "bongacams.com", "bongacash.com", "cam4.com", "livejasmin.com", "awempire.com", "awltovhc.com",
            "naughtyads.com", "adsco.re", "adsession.com", "bidswitch.net", "contextweb.com", "lijit.com", "yieldmo.com",
            "teads.tv", "sharethrough.com", "nativo.com", "adform.net", "adition.com", "criteo.com", "criteo.net",
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com", "2mdn.net",
            "moatads.com", "scorecardresearch.com", "quantserve.com", "amazon-adsystem.com", "adsrvr.org", "rubiconproject.com",
            "pubmatic.com", "openx.net", "casalemedia.com", "appnexus.com", "advertising.com", "taboola.com", "outbrain.com",
            "mgid.com", "revcontent.com", "zemanta.com", "adnxs.com", "lkqd.net", "springserve.com", "videoamp.com",
            "mc.yandex.ru", "an.yandex.ru", "adfox.ru", "begun.ru", "ads.pubmatic.com", "tpmn.co.kr", "inmobi.com",
            // Tracking
            "google-analytics.com", "hotjar.com", "mouseflow.com", "crazyegg.com", "branch.io", "app-measurement.com"
    ));

    /** Hosts that are only trackers / link services, never opened as a page by the ad-navigation guard. */
    private static final Set<String> NAV_SAFE = new HashSet<>(Arrays.asList("branch.io", "app-measurement.com", "google-analytics.com"));

    /** True when the host (or a parent domain) is a known ad network. */
    static boolean isAdHost(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        while (true) {
            if (HOSTS.contains(h)) return !NAV_SAFE.contains(h);
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) break;
            h = h.substring(dot + 1);
        }
        return false;
    }

    /** A URL shaped like an ad redirect / pop-under (used only for pages that open it without you tapping). */
    static boolean looksLikeAdRedirect(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        int hits = 0;
        String[] keys = {"zoneid=", "zone_id=", "campaignid=", "campaign_id=", "clickid=", "click_id=", "subid=", "sub_id=",
                "trkid=", "bannerid=", "creativeid=", "adid=", "ad_id=", "popunder", "pop_under", "popads", "/aff_c?", "/click?",
                "utm_medium=pop", "utm_source=pop", "tracker.", "adsterra", "propeller", "clickadu"};
        for (String k : keys) if (u.contains(k)) hits++;
        return hits >= 2 || u.contains("popunder") || u.contains("pop_under") || u.contains("adsterra") || u.contains("clickadu");
    }

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
                || u.contains("adserver.") || u.contains("/adserve/") || u.contains("/adsbygoogle.js")
                || u.contains("/showads") || u.contains("/popunder") || u.contains("/pop-under")
                || u.contains("/ads/banner") || u.contains("/adframe") || u.contains("/ad-iframe")
                || u.contains("/prebid") || u.contains("/ads.js") || u.contains("/adsbox")
                || u.contains("/vast.xml") || u.contains("/vmap.xml") || u.contains("ad_banner")
                || u.contains("/nativeads") || u.contains("/native-ads") || u.contains("/native_ads") || u.contains("/ad-server")
                || u.contains("/adserver") || u.contains("/banners/") || u.contains("/ads/js") || u.contains("/ads_js")
                || u.contains("popunder") || u.contains("pop-under") || u.contains("/click.php?") || u.contains("/aff_c?")
                || u.contains("preroll") && (u.contains(".xml") || u.contains("vast"));
    }
}
