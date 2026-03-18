package com.haneef._school.service

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.springframework.stereotype.Service

@Service
class HtmlSanitizerService {

    fun sanitize(html: String?): String {
        if (html.isNullOrBlank()) return ""

        // Whitelist for rich text content
        // Relaxed allows: a, b, blockquote, br, caption, cite, code, col, colgroup, dd, div, dl, dt, em, h1-h6, i, img, li, ol, p, pre, q, small, span, strike, strong, sub, sup, table, tbody, td, tfoot, th, thead, tr, u, ul
        val safelist = Safelist.relaxed()
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
            
            // Explicitly remove dangerous tags and attributes
            .removeTags("script", "object", "embed", "iframe", "form", "input", "button", "textarea", "select", "option")
            .removeAttributes("a", "onclick", "onmouseover", "onfocus", "onblur", "onchange", "onsubmit")
            .removeAttributes("div", "onclick", "onmouseover", "onfocus", "onblur")
            .removeAttributes("img", "onerror", "onload")
            .removeAttributes(":all", "style") // Globally remove style attributes

        return Jsoup.clean(html, safelist)
    }
}
