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
            .addAttributes("img", "src", "alt", "width", "height", "class", "style")
            .addAttributes("div", "class", "style", "id")
            .addAttributes("span", "class", "style")
            .addAttributes("a", "href", "title", "target", "class", "style")
            .addAttributes("p", "class", "style")
            .addAttributes("h1", "class", "style")
            .addAttributes("h2", "class", "style")
            .addAttributes("h3", "class", "style")
            .addAttributes("h4", "class", "style")
            .addAttributes("h5", "class", "style")
            .addAttributes("h6", "class", "style")
            .addAttributes("ul", "class", "style")
            .addAttributes("ol", "class", "style")
            .addAttributes("li", "class", "style")
            .addAttributes("table", "class", "style")
            .addAttributes("th", "class", "style")
            .addAttributes("td", "class", "style")
            .addAttributes("tr", "class", "style")
            .addAttributes("i", "class", "style")
            .addAttributes("b", "class", "style")
            .addProtocols("img", "src", "http", "https", "data")
            .addProtocols("a", "href", "http", "https", "mailto", "tel")
            
            // Explicitly remove dangerous tags and attributes
            .removeTags("script", "object", "embed", "iframe", "form", "input", "button", "textarea", "select", "option")
            .removeAttributes("a", "onclick", "onmouseover", "onfocus", "onblur", "onchange", "onsubmit")
            .removeAttributes("div", "onclick", "onmouseover", "onfocus", "onblur")
            .removeAttributes("img", "onerror", "onload")

        return Jsoup.clean(html, safelist)
    }
}
