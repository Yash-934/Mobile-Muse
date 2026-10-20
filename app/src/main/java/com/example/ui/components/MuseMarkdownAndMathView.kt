package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily

private val TextbookIndigoAccent = Color(0xFF5B4BDF)
private val TextbookCalloutBgLight = Color(0xFFF1EFFB)
private val TextbookCalloutBgDark = Color(0xFF1F1D36)
private val CodeBoxBg = Color(0xFF16181D)
private val CodeBoxHeaderBg = Color(0xFF222630)
private val CodeBoxBorder = Color(0xFF333947)

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class FormulaBlock(val formula: String) : MarkdownBlock()
    data class RuleCallout(val title: String, val body: String) : MarkdownBlock()
    data class BulletItem(val content: String) : MarkdownBlock()
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

sealed class InlineMathToken {
    data class TextToken(val text: String) : InlineMathToken()
    data class FractionToken(
        val prefix: String = "",
        val numerator: String,
        val denominator: String,
        val suffix: String = ""
    ) : InlineMathToken()
}

/**
 * Rich Markdown + Textbook Formula + Interactive Codebox Renderer.
 * Matches the user's textbook screenshot:
 * - Vertical indigo accent bar for headings (`| प्रतिशत (प्रत्येक सौ / 100)`)
 * - Real vertical stacked fractions (`\frac{37}{100}` or inline `19% = 19/100`)
 * - Centered block equations (`$$ P% = \frac{P}{100} $$`)
 * - Lavender callout rule boxes (`> नियम: ...` or `नियम: ...`)
 * - Interactive Codeboxes with Copy and Download/Save buttons
 */
@Composable
fun MuseMarkdownAndMathView(
    content: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    isDark: Boolean = false,
    onCopyText: (String) -> Unit = {},
    onDownloadCodeSnippet: (String, String) -> Unit = { _, _ -> }
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    TextbookHeadingView(
                        level = block.level,
                        text = block.text,
                        textColor = textColor
                    )
                }

                is MarkdownBlock.CodeBlock -> {
                    MuseCodeBlockCard(
                        language = block.language,
                        code = block.code,
                        onCopy = { onCopyText(block.code) },
                        onDownload = {
                            val ext = languageToExtension(block.language)
                            val fileName = "snippet_${System.currentTimeMillis() % 10000}.$ext"
                            onDownloadCodeSnippet(fileName, block.code)
                        }
                    )
                }

                is MarkdownBlock.FormulaBlock -> {
                    TextbookBlockFormulaView(
                        formula = block.formula,
                        textColor = textColor
                    )
                }

                is MarkdownBlock.RuleCallout -> {
                    TextbookRuleCalloutCard(
                        title = block.title,
                        body = block.body,
                        isDark = isDark
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(textColor)
                        )
                        RichMathFlowRow(
                            text = block.content,
                            textColor = textColor,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.TableBlock -> {
                    MarkdownTableView(
                        headers = block.headers,
                        rows = block.rows,
                        textColor = textColor
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    RichMathFlowRow(
                        text = block.text,
                        textColor = textColor,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Heading with vertical indigo accent bar on the left, matching `| प्रतिशत (प्रत्येक सौ / 100)` in the screenshot.
 */
@Composable
private fun TextbookHeadingView(
    level: Int,
    text: String,
    textColor: Color
) {
    val fontSize = when (level) {
        1 -> 20.sp
        2 -> 18.sp
        else -> 16.sp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (level == 1) 26.dp else 20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TextbookIndigoAccent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = parseInlineMarkdownAnnotated(text, textColor),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = (fontSize.value + 6).sp
            ),
            color = textColor
        )
    }
}

/**
 * Centered Textbook Equation view (`$$ P% = \frac{P}{100} $$`).
 */
@Composable
private fun TextbookBlockFormulaView(
    formula: String,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        RichMathFlowRow(
            text = formula,
            textColor = textColor,
            fontSize = 20.sp,
            italicVariables = true,
            centered = true
        )
    }
}

/**
 * Lavender textbook rule / note callout box matching the bottom of the user's screenshot:
 * `| नियम: यदि किसी भिन्न को प्रतिशत में बदलना हो तो भिन्न को 100 से गुणा करें और '%' चिह्न लगा दें।`
 */
@Composable
private fun TextbookRuleCalloutCard(
    title: String,
    body: String,
    isDark: Boolean
) {
    val bgColor = if (isDark) TextbookCalloutBgDark else TextbookCalloutBgLight
    val fgColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E1B4B)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(TextbookIndigoAccent)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val combined = if (title.isNotBlank()) "**$title:** $body" else body
                Text(
                    text = parseInlineMarkdownAnnotated(combined, fgColor, defaultItalic = true),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = fgColor
                )
            }
        }
    }
}

/**
 * Interactive Code Box with language badge, Copy button, and Download/Save button.
 */
@Composable
private fun MuseCodeBlockCard(
    language: String,
    code: String,
    onCopy: () -> Unit,
    onDownload: () -> Unit
) {
    val displayLang = language.trim().ifBlank { "code" }.uppercase()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CodeBoxBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = CodeBoxBg
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeBoxHeaderBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayLang,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF94A3B8)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onCopy)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDownload)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download Code as File",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code.trimEnd(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    ),
                    color = Color(0xFFF8FAFC)
                )
            }
        }
    }
}

/**
 * Renders inline text mixed with real vertical stacked fractions (`\frac{num}{den}` or math fractions `37/100`)
 * so that fractions sit inline with Hindi/English text and equations exactly like a printed textbook.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichMathFlowRow(
    text: String,
    textColor: Color,
    fontSize: TextUnit = 15.sp,
    italicVariables: Boolean = false,
    centered: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = remember(text) { tokenizeInlineMathAndFractions(text) }

    if (tokens.size == 1 && tokens.first() is InlineMathToken.TextToken && !italicVariables) {
        Text(
            text = parseInlineMarkdownAnnotated((tokens.first() as InlineMathToken.TextToken).text, textColor),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize,
                lineHeight = (fontSize.value + 8).sp
            ),
            color = textColor,
            modifier = modifier
        )
        return
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalArrangement = Arrangement.Center
    ) {
        tokens.forEach { token ->
            when (token) {
                is InlineMathToken.TextToken -> {
                    val words = token.text.split(" ")
                    words.forEachIndexed { idx, word ->
                        if (word.isNotEmpty()) {
                            val trailingSpace = if (idx < words.lastIndex) " " else ""
                            Text(
                                text = parseInlineMarkdownAnnotated(
                                    raw = word + trailingSpace,
                                    baseColor = textColor,
                                    defaultItalic = italicVariables && word.any { it.isLetter() }
                                ),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSize,
                                    fontFamily = if (italicVariables) FontFamily.Serif else FontFamily.Default,
                                    lineHeight = (fontSize.value + 8).sp
                                ),
                                color = textColor,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }

                is InlineMathToken.FractionToken -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        if (token.prefix.isNotBlank()) {
                            Text(
                                text = token.prefix,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSize,
                                    fontStyle = if (italicVariables) FontStyle.Italic else FontStyle.Normal
                                ),
                                color = textColor,
                                modifier = Modifier.padding(end = 3.dp)
                            )
                        }
                        StackedFractionView(
                            numerator = token.numerator,
                            denominator = token.denominator,
                            textColor = textColor,
                            fontSize = (fontSize.value * 0.85f).sp,
                            italic = italicVariables
                        )
                        if (token.suffix.isNotBlank()) {
                            Text(
                                text = token.suffix,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSize,
                                    fontStyle = if (italicVariables) FontStyle.Italic else FontStyle.Normal
                                ),
                                color = textColor,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * True vertical textbook fraction: numerator centered above a crisp horizontal dividing bar above denominator.
 */
@Composable
fun StackedFractionView(
    numerator: String,
    denominator: String,
    textColor: Color,
    fontSize: TextUnit = 13.sp,
    italic: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = 24.dp)
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = formatMathSymbols(numerator),
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            fontStyle = if (italic && numerator.any { it.isLetter() }) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (italic) FontFamily.Serif else FontFamily.Default,
            color = textColor,
            textAlign = TextAlign.Center,
            lineHeight = (fontSize.value + 2).sp,
            modifier = Modifier.padding(horizontal = 3.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.3.dp)
                .background(textColor)
        )
        Text(
            text = formatMathSymbols(denominator),
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            fontStyle = if (italic && denominator.any { it.isLetter() }) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (italic) FontFamily.Serif else FontFamily.Default,
            color = textColor,
            textAlign = TextAlign.Center,
            lineHeight = (fontSize.value + 2).sp,
            modifier = Modifier.padding(horizontal = 3.dp)
        )
    }
}

@Composable
private fun MarkdownTableView(
    headers: List<String>,
    rows: List<List<String>>,
    textColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                headers.forEach { h ->
                    Text(
                        text = h,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = formatMathSymbols(cell),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val lines = raw.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 1. Fenced Code Block ```lang
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing ```
            blocks.add(MarkdownBlock.CodeBlock(language = lang, code = codeLines.joinToString("\n")))
            continue
        }

        // 2. Centered Block Math $$ ... $$ or \[ ... \]
        if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) {
            val isDollar = trimmed.startsWith("$$")
            val closeMarker = if (isDollar) "$$" else "\\]"
            val firstContent = trimmed.removePrefix(if (isDollar) "$$" else "\\[").trim()
            if (firstContent.endsWith(closeMarker) && firstContent.length > closeMarker.length) {
                val eq = firstContent.removeSuffix(closeMarker).trim()
                blocks.add(MarkdownBlock.FormulaBlock(eq))
                i++
                continue
            }
            val eqLines = mutableListOf<String>()
            if (firstContent.isNotEmpty()) eqLines.add(firstContent)
            i++
            while (i < lines.size && !lines[i].trim().endsWith(closeMarker)) {
                eqLines.add(lines[i].trim())
                i++
            }
            if (i < lines.size) {
                val lastPart = lines[i].trim().removeSuffix(closeMarker).trim()
                if (lastPart.isNotEmpty()) eqLines.add(lastPart)
                i++
            }
            blocks.add(MarkdownBlock.FormulaBlock(eqLines.joinToString(" ")))
            continue
        }

        // 3. Headings (#, ##, ###)
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
            val headingText = trimmed.dropWhile { it == '#' }.trim()
            blocks.add(MarkdownBlock.Heading(level = level, text = headingText))
            i++
            continue
        }

        // 4. Rule / Note Callout (`> ...` or `नियम:` / `Rule:` / `सूत्र:` / `Formula:`)
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            val fullQuote = quoteLines.joinToString(" ")
            val cleaned = fullQuote.removePrefix("**").trim()
            if (cleaned.contains(":")) {
                val title = fullQuote.substringBefore(":").replace("**", "").trim()
                val body = fullQuote.substringAfter(":").replace("**", "").trim()
                blocks.add(MarkdownBlock.RuleCallout(title = title, body = body))
            } else {
                blocks.add(MarkdownBlock.RuleCallout(title = "", body = fullQuote))
            }
            continue
        }

        if (trimmed.startsWith("नियम:") || trimmed.startsWith("**नियम:**") ||
            trimmed.startsWith("सूत्र:") || trimmed.startsWith("**सूत्र:**") ||
            trimmed.startsWith("Rule:", ignoreCase = true) || trimmed.startsWith("**Rule:**", ignoreCase = true)
        ) {
            val clean = trimmed.replace("**", "")
            val title = clean.substringBefore(":").trim()
            val body = clean.substringAfter(":").trim()
            blocks.add(MarkdownBlock.RuleCallout(title = title, body = body))
            i++
            continue
        }

        // 5. Markdown Table
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && lines[i + 1].trim().contains("---")) {
            val headers = trimmed.trim('|').split("|").map { it.trim() }
            i += 2 // skip header and separator row
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                rows.add(cells)
                i++
            }
            blocks.add(MarkdownBlock.TableBlock(headers = headers, rows = rows))
            continue
        }

        // 6. Bullet List Items (- , * , • )
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
            val itemText = trimmed.drop(2).trim()
            blocks.add(MarkdownBlock.BulletItem(itemText))
            i++
            continue
        }

        // 7. Standalone single-line formula detection (e.g. `P% = \frac{P}{100}`)
        if (trimmed.length < 65 && (trimmed.contains("\\frac{") || Regex("^[A-Za-z0-9%]+\\s*=\\s*\\S+$").matches(trimmed)) && !trimmed.contains("है")) {
            blocks.add(MarkdownBlock.FormulaBlock(trimmed.trim('$')))
            i++
            continue
        }

        // 8. Standard Paragraph
        blocks.add(MarkdownBlock.Paragraph(trimmed))
        i++
    }

    return blocks
}

/**
 * Splits a line of text into `TextToken` and `FractionToken` segments.
 * Supports:
 * - LaTeX `\frac{numerator}{denominator}` (with optional trailing `%` or punctuation)
 * - Explicit math fractions like `37/100`, `19/100`, `5/9%`, `3/700`, `P/100` (while ignoring dates like `20/10/2026` or URLs `https://...`)
 */
private fun tokenizeInlineMathAndFractions(input: String): List<InlineMathToken> {
    val cleanedInput = input.replace("$$", "").replace("\\(", "").replace("\\)", "")
    // Combined regex matching either \frac{A}{B}(%)? or standalone fraction A/B(%)? not part of a URL or 3-part date
    val fractionRegex = Regex(
        """\\frac\{([^{}]+)\}\{([^{}]+)\}(\s*%)?|(?<![:/\w])([A-Za-z0-9+-]{1,6})\s*/\s*([0-9]{1,6}|[A-Za-z]{1,4})(?![/\w])(\s*%)?"""
    )

    val result = mutableListOf<InlineMathToken>()
    var lastIdx = 0

    for (match in fractionRegex.findAll(cleanedInput)) {
        val range = match.range
        // Skip if this looks like "सौ / 100" inside a heading title with spaces around slash and Hindi word
        val fullMatch = match.value
        if (range.first > lastIdx) {
            val before = cleanedInput.substring(lastIdx, range.first)
            if (before.isNotEmpty()) {
                result.add(InlineMathToken.TextToken(formatMathSymbols(before)))
            }
        }

        val latexNum = match.groups[1]?.value
        val latexDen = match.groups[2]?.value
        val latexPct = match.groups[3]?.value ?: ""

        val slashNum = match.groups[4]?.value
        val slashDen = match.groups[5]?.value
        val slashPct = match.groups[6]?.value ?: ""

        if (latexNum != null && latexDen != null) {
            result.add(
                InlineMathToken.FractionToken(
                    numerator = latexNum.trim(),
                    denominator = latexDen.trim(),
                    suffix = latexPct.trim()
                )
            )
        } else if (slashNum != null && slashDen != null) {
            // Avoid turning words like "On/Off" or "AND/OR" into math fractions
            val isWordSlash = slashNum.all { it.isLetter() } && slashDen.all { it.isLetter() } && slashNum.length > 1 && slashDen.length > 1
            if (isWordSlash) {
                result.add(InlineMathToken.TextToken(fullMatch))
            } else {
                result.add(
                    InlineMathToken.FractionToken(
                        numerator = slashNum.trim(),
                        denominator = slashDen.trim(),
                        suffix = slashPct.trim()
                    )
                )
            }
        } else {
            result.add(InlineMathToken.TextToken(fullMatch))
        }

        lastIdx = range.last + 1
    }

    if (lastIdx < cleanedInput.length) {
        result.add(InlineMathToken.TextToken(formatMathSymbols(cleanedInput.substring(lastIdx))))
    }

    return if (result.isEmpty()) listOf(InlineMathToken.TextToken(formatMathSymbols(cleanedInput))) else result
}

private fun formatMathSymbols(input: String): String {
    return input
        .replace("\\times", "×")
        .replace("\\div", "÷")
        .replace("\\pm", "±")
        .replace("\\sqrt", "√")
        .replace("\\pi", "π")
        .replace("\\theta", "θ")
        .replace("\\alpha", "α")
        .replace("\\beta", "β")
        .replace("\\Delta", "Δ")
        .replace("\\sum", "∑")
        .replace("\\int", "∫")
        .replace("\\neq", "≠")
        .replace("\\leq", "≤")
        .replace("\\geq", "≥")
        .replace("\\Rightarrow", "⇒")
        .replace("\\rightarrow", "→")
        .replace("\\%", "%")
}

/**
 * Parses inline `**bold**`, `*italic*`, `` `code` ``, superscripts `x^2`, and subscripts `H_2O`.
 */
fun parseInlineMarkdownAnnotated(
    raw: String,
    baseColor: Color,
    defaultItalic: Boolean = false
): AnnotatedString {
    val text = formatMathSymbols(raw)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold **...**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = if (defaultItalic) FontStyle.Italic else FontStyle.Normal,
                                color = baseColor
                            )
                        ) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Inline Code `...`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                background = baseColor.copy(alpha = 0.1f),
                                fontWeight = FontWeight.SemiBold,
                                color = baseColor
                            )
                        ) {
                            append(" " + text.substring(i + 1, end) + " ")
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                // Superscript ^2 or ^{...}
                text[i] == '^' && i + 1 < text.length -> {
                    if (text[i + 1] == '{') {
                        val end = text.indexOf('}', i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 11.sp)) {
                                append(text.substring(i + 2, end))
                            }
                            i = end + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        val sup = text.substring(i + 1).takeWhile { it.isLetterOrDigit() || it == '+' || it == '-' }
                        if (sup.isNotEmpty()) {
                            withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 11.sp)) {
                                append(sup)
                            }
                            i += 1 + sup.length
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                }

                // Subscript _2 or _{...}
                text[i] == '_' && i + 1 < text.length && (i > 0 && text[i - 1].isLetterOrDigit()) -> {
                    if (text[i + 1] == '{') {
                        val end = text.indexOf('}', i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 11.sp)) {
                                append(text.substring(i + 2, end))
                            }
                            i = end + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        val sub = text.substring(i + 1).takeWhile { it.isDigit() }
                        if (sub.isNotEmpty()) {
                            withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 11.sp)) {
                                append(sub)
                            }
                            i += 1 + sub.length
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                }

                else -> {
                    if (defaultItalic) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                            append(text[i])
                        }
                    } else {
                        append(text[i])
                    }
                    i++
                }
            }
        }
    }
}

private fun languageToExtension(language: String): String {
    return when (language.trim().lowercase()) {
        "python", "py" -> "py"
        "kotlin", "kt" -> "kt"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "html" -> "html"
        "css" -> "css"
        "bash", "sh", "shell", "zsh" -> "sh"
        "json" -> "json"
        "sql" -> "sql"
        "xml" -> "xml"
        "java" -> "java"
        "cpp", "c++" -> "cpp"
        "c" -> "c"
        "markdown", "md" -> "md"
        else -> "txt"
    }
}
