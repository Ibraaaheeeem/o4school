package com.haneef._school.service

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.springframework.stereotype.Service

@Service
class HtmlSanitizerService {

    companion object {
        private val SAFE_HTML = Safelist.relaxed()
            .addAttributes("img", "src", "alt", "width", "height", "class")
            .addAttributes("div", "class", "id")
            .addAttributes("span", "class")
            .addAttributes("a", "href", "title", "target", "class")
            .addAttributes("p", "class")
            .addAttributes("h1", "class")
            .addAttributes("h2", "class")
            .addAttributes("h3", "class")
            .addAttributes("h4", "class")
            .addAttributes("h5", "class")
            .addAttributes("h6", "class")
            .addAttributes("ul", "class")
            .addAttributes("ol", "class")
            .addAttributes("li", "class")
            .addAttributes("table", "class")
            .addAttributes("th", "class")
            .addAttributes("td", "class")
            .addAttributes("tr", "class")
            .addAttributes("i", "class")
            .addAttributes("b", "class")
            .addProtocols("img", "src", "http", "https", "data")
            .addProtocols("a", "href", "http", "https", "mailto", "tel")
            .removeTags("script", "object", "embed", "iframe", "form", "input", "button", "textarea", "select", "option")
            .removeAttributes("a", "onclick", "onmouseover", "onfocus", "onblur", "onchange", "onsubmit")
            .removeAttributes("div", "onclick", "onmouseover", "onfocus", "onblur")
            .removeAttributes("img", "onerror", "onload")
            .removeAttributes(":all", "style")
    }

    fun sanitize(html: String?): String {
        if (html.isNullOrBlank()) return ""

        return Jsoup.clean(html, SAFE_HTML)
    }
}
