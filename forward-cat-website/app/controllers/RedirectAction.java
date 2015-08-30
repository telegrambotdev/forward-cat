package controllers;

import com.google.common.collect.ImmutableMap;
import play.i18n.Lang;
import play.libs.F;
import play.mvc.Http;
import play.mvc.Result;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedirectAction extends play.mvc.Action.Simple {

    private static final Lang DEFAULT_LANG = Lang.forCode("en");
    private static final Pattern SUBDOMAIN_LANG_PATTTERN = Pattern.compile("^[a-z]{2}\\.forward.cat.*");
    private static final Pattern PATH_LANG_PATTTERN = Pattern.compile("^/(?<lang>[a-z]{2})([/\\?].*)?");
    private static final Map<String, String> SUBDOMAIN_REDIRECTS = new ImmutableMap.Builder<String, String>()
            .put("www", "http://forward.cat")
            .put("en", "http://forward.cat")
            .put("es", "http://forward.cat/es")
            .put("ca", "http://forward.cat/ca")
            .build();

    @Override
    public F.Promise<Result> call(Http.Context ctx) throws Throwable {
        Http.Request request = ctx.request();
        String host = request.host();

        if (host != null) {
            // www subdomain: redirect
            if (host.startsWith("www.")) {
                String redirectUrl = buildUrl("www", request.path(), request.queryString());
                return promise(movedPermanently(redirectUrl));
            }

            // Lang in subdomain: redirect
            if (SUBDOMAIN_LANG_PATTTERN.matcher(host).matches()) {
                String langCode = host.substring(0, 2);
                if (isValidLang(langCode)) {
                    String redirectUrl = buildUrl(langCode, request.path(), request.queryString());
                    return promise(movedPermanently(redirectUrl));
                }
            }
        }

        // Lang in path: validate and set
        Matcher matcher = PATH_LANG_PATTTERN.matcher(request.path());
        if (matcher.matches()) {
            String lang = matcher.group("lang");
            if (!isValidLang(lang) || DEFAULT_LANG.code().equals(lang)) {
                return promise(notFound());
            } else {
                ctx.changeLang(lang);
                return delegate.call(ctx);
            }
        }

        // No lang: use default
        ctx.changeLang(DEFAULT_LANG);
        return delegate.call(ctx);
    }

    private String buildUrl(String subdomain, String path, Map<String, String[]> query) {
        String hostAndPath = SUBDOMAIN_REDIRECTS.get(subdomain);
        return hostAndPath + path + queryString(query);
    }

    private String queryString(Map<String, String[]> query) {
        Optional<String> params = query.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue().length > 0)
                .map(e -> e.getKey() + "=" + e.getValue()[0])
                .reduce((p1, p2) -> p1 + "&" + p2);
        return params.map(p -> "?" + p).orElse("");
    }

    private boolean isValidLang(String langCode) {
        return SUBDOMAIN_REDIRECTS.containsKey(langCode);
    }

    private <A> F.Promise<A> promise(A result) {
        return F.Promise.pure(result);
    }
}
