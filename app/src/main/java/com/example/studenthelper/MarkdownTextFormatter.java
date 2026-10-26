package com.example.studenthelper;

import androidx.core.text.HtmlCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the small Markdown and inline-math subset used by Gemini into readable Android text. */
public final class MarkdownTextFormatter {
    private static final Pattern MATH_SPAN = Pattern.compile("\\$\\$?([^$\\n]+?)\\$\\$?");
    private static final Pattern LATEX_FRACTION = Pattern.compile("\\\\frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}");
    private static final Pattern LATEX_TEXT = Pattern.compile("\\\\text\\s*\\{([^{}]*)\\}");

    private MarkdownTextFormatter() { }

    public static CharSequence format(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        normalized = renderMathSpans(normalized);
        String[] lines = normalized.split("\n", -1);
        StringBuilder html = new StringBuilder(normalized.length() + 64);

        for (String rawLine : lines) {
            String line = escapeHtml(rawLine);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                html.append("<br>");
                continue;
            }

            Matcher heading = Pattern.compile("^#{1,6}\\s+(.+?)\\s*#*$").matcher(trimmed);
            if (heading.matches()) {
                html.append("<br><b>").append(formatInline(heading.group(1))).append("</b><br>");
                continue;
            }

            Matcher bullet = Pattern.compile("^[*+\\-]\\s+(.+)$").matcher(trimmed);
            if (bullet.matches()) {
                html.append("&#8226;&nbsp;&nbsp;").append(formatInline(bullet.group(1))).append("<br>");
                continue;
            }

            html.append(formatInline(trimmed)).append("<br>");
        }
        return HtmlCompat.fromHtml(html.toString(), HtmlCompat.FROM_HTML_MODE_COMPACT);
    }

    private static String renderMathSpans(String text) {
        Matcher matcher = MATH_SPAN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            // A dollar amount starts with a digit; leave currency examples untouched.
            String replacement = expression.matches("^[0-9][0-9,]*(?:\\.[0-9]+)?.*$")
                    ? matcher.group()
                    : formatMath(expression);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String formatMath(String expression) {
        String value = expression
                .replace("\\\\", " ")
                .replace("\\left", "")
                .replace("\\right", "")
                .replace("\\cdots", "…")
                .replace("\\cdot", "·")
                .replace("\\times", "×")
                .replace("\\div", "÷")
                .replace("\\pm", "±")
                .replace("\\leq", "≤")
                .replace("\\le", "≤")
                .replace("\\geq", "≥")
                .replace("\\ge", "≥")
                .replace("\\neq", "≠")
                .replace("\\approx", "≈")
                .replace("\\infty", "∞")
                .replace("\\sum", "∑")
                .replace("\\int", "∫")
                .replace("\\rightarrow", "→")
                .replace("\\to", "→")
                .replace("\\max", "max")
                .replace("\\min", "min")
                .replace("\\quad", " ")
                .replace("\\,", " ")
                .replace("\\;", " ")
                .replace("\\!", "")
                .replace("\\{", "{")
                .replace("\\}", "}");

        value = replacePattern(LATEX_TEXT, value, "$1");
        value = replacePattern(LATEX_FRACTION, value, "($1)/($2)");
        value = value.replaceAll("\\\\(?:mathrm|mathbf|mathit|operatorname)\\s*\\{([^{}]*)\\}", "$1");
        value = value.replaceAll("\\\\[a-zA-Z]+", "");
        value = value.replace("{", "").replace("}", "");
        value = formatIndexedVariables(value, "_", false);
        value = formatIndexedVariables(value, "\\^", true);
        return value.replace(" - ", " − ").replace(" + ", " + ");
    }

    private static String formatIndexedVariables(String value, String operator, boolean superscript) {
        Matcher matcher = Pattern.compile("([A-Za-z0-9)])" + operator + "([A-Za-z0-9+-]+)").matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + mapIndex(matcher.group(2), superscript);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String mapIndex(String text, boolean superscript) {
        String source = "0123456789+-=()in";
        String[] sub = {"₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉", "₊", "₋", "₌", "₍", "₎", "ᵢ", "ₙ"};
        String[] sup = {"⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹", "⁺", "⁻", "⁼", "⁽", "⁾", "ⁱ", "ⁿ"};
        StringBuilder mapped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int index = source.indexOf(ch);
            if (index >= 0) mapped.append(superscript ? sup[index] : sub[index]);
            else if (!superscript && (ch == 't' || ch == 'T')) mapped.append('ₜ');
            else if (!superscript && (ch == 'k' || ch == 'K')) mapped.append('ₖ');
            else mapped.append(ch);
        }
        return mapped.toString();
    }

    private static String replacePattern(Pattern pattern, String value, String replacement) {
        return pattern.matcher(value).replaceAll(replacement);
    }

    private static String formatInline(String value) {
        String formatted = value.replaceAll("`([^`]+)`", "<tt>$1</tt>");
        formatted = formatted.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>")
                .replaceAll("__(.+?)__", "<b>$1</b>")
                .replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<i>$1</i>")
                .replaceAll("(?<!_)_([^_]+)_(?!_)", "<i>$1</i>")
                .replaceAll("\\*\\*", "")
                .replaceAll("__", "");
        return formatted;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
