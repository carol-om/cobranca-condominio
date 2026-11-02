package br.com.condominio.cobranca.imagem

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import br.com.condominio.cobranca.dados.Cobranca
import br.com.condominio.cobranca.dados.CobrancaLinha
import br.com.condominio.cobranca.dados.CobrancaUnidade
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

/** Cria a imagem da cobrança usando somente os dados congelados no fechamento. */
class GeradorDeImagem {
    fun gerar(
        context: Context,
        cobranca: Cobranca,
        unidade: CobrancaUnidade,
        linhas: List<CobrancaLinha>,
    ): File {
        // Passo 1: mede o layout completo sem desenhar, obtendo a altura exata.
        // Passo 2: cria o bitmap com essa altura e desenha com as mesmas medidas.
        // Assim a imagem nunca é menor que o conteúdo, não importa quantos itens existam.
        val height = render(null, cobranca, unidade, linhas)
        val bitmap = Bitmap.createBitmap(LARGURA, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        render(canvas, cobranca, unidade, linhas)

        val pasta = File(context.cacheDir, "cobrancas").apply { mkdirs() }
        val arquivo = File(pasta, "cobranca_${cobranca.id}_unidade_${unidade.id}.png")
        FileOutputStream(arquivo).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return arquivo
    }

    fun compartilhar(context: Context, arquivo: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar cobrança"))
    }

    /**
     * Percorre todo o layout. Com [canvas] nulo apenas mede; com [canvas] também desenha.
     * Retorna a altura total necessária, em pixels.
     */
    private fun render(canvas: Canvas?, cobranca: Cobranca, unidade: CobrancaUnidade, linhas: List<CobrancaLinha>): Int {
        val left = MARGEM
        val right = LARGURA - MARGEM
        val contentWidth = right - left
        var y = 0f

        fun lineHeight(paint: Paint): Float {
            val m = paint.fontMetrics
            return ceil(m.descent - m.ascent) + ESPACO_LINHA
        }

        fun baselineOf(paint: Paint, top: Float): Float = top - paint.fontMetrics.ascent

        fun drawText(text: String, paint: Paint, x: Float, baseline: Float, align: Paint.Align = Paint.Align.LEFT) {
            if (canvas == null) return
            paint.textAlign = align
            canvas.drawText(text, x, baseline, paint)
        }

        // Escreve as linhas uma abaixo da outra a partir de y e avança y.
        fun paragraph(lines: List<String>, paint: Paint, x: Float = left) {
            lines.forEach {
                drawText(it, paint, x, baselineOf(paint, y))
                y += lineHeight(paint)
            }
        }

        fun divider(color: Int, thickness: Float) {
            canvas?.drawRect(left, y, right, y + thickness, fill(color))
            y += thickness
        }

        fun section(title: String) {
            val titlePaint = paint(28, true, COR_SECUNDARIA).apply { letterSpacing = 0.08f }
            paragraph(listOf(title), titlePaint)
            y += 6f
            divider(COR_PRIMARIA, 3f)
        }

        // --- Cabeçalho: condomínio e natureza da cobrança ---
        val headerName = cobranca.nomeCondominio?.takeIf { it.isNotBlank() } ?: "Condomínio"
        val headerNamePaint = paint(50, true, Color.WHITE)
        val headerSubPaint = paint(32, false, COR_CABECALHO_SUB)
        val headerNameLines = wrapText(headerName, headerNamePaint, contentWidth)
        val headerSubLines = wrapText(
            "Rateio de taxas condominiais · ${referenciaPorExtenso(cobranca.mes, cobranca.ano)}",
            headerSubPaint,
            contentWidth,
        )
        val headerHeight = PAD_CABECALHO +
            headerNameLines.size * lineHeight(headerNamePaint) + 4f +
            headerSubLines.size * lineHeight(headerSubPaint) + PAD_CABECALHO
        canvas?.drawRect(0f, 0f, LARGURA.toFloat(), headerHeight, fill(COR_PRIMARIA))
        y = PAD_CABECALHO
        paragraph(headerNameLines, headerNamePaint)
        y += 4f
        paragraph(headerSubLines, headerSubPaint)
        y = headerHeight + 40f

        // --- Unidade e vencimento ---
        val unitPaint = paint(42, true, COR_TEXTO)
        val unitTitle = "Unidade ${unidade.numeroUnidade} — ${unidade.nomeMorador.ifBlank { "Sem morador" }}"
        paragraph(wrapText(unitTitle, unitPaint, contentWidth), unitPaint)
        y += 6f
        val dueLabelPaint = paint(34, false, COR_SECUNDARIA)
        val dueValuePaint = paint(34, true, COR_TEXTO)
        val dueBaseline = baselineOf(dueValuePaint, y)
        drawText("Vencimento", dueLabelPaint, left, dueBaseline)
        drawText(formatarData(cobranca.vencimento), dueValuePaint, right, dueBaseline, Paint.Align.RIGHT)
        y += lineHeight(dueValuePaint) + 32f

        // --- Parcelas: descrição à esquerda (quebra em várias linhas), valor à direita ---
        section("PARCELAS DO RATEIO")
        val descPaint = paint(36, false, COR_TEXTO)
        val valuePaint = paint(36, true, COR_TEXTO)
        val values = linhas.map { valor(it.valorCentavos) }
        val valueWidth = values.maxOfOrNull { valuePaint.measureText(it) } ?: 0f
        val descWidth = (contentWidth - valueWidth - ESPACO_COLUNAS).coerceAtLeast(contentWidth / 2f)
        if (linhas.isEmpty()) {
            y += PAD_LINHA
            paragraph(listOf("Nenhum item nesta cobrança."), dueLabelPaint)
            y += PAD_LINHA
        }
        linhas.forEachIndexed { index, linha ->
            y += PAD_LINHA
            drawText(values[index], valuePaint, right, baselineOf(valuePaint, y), Paint.Align.RIGHT)
            paragraph(wrapText(linha.descricao, descPaint, descWidth), descPaint)
            y += PAD_LINHA
            divider(COR_DIVISOR, 2f)
        }

        // --- Total ---
        y += 28f
        val totalLabelPaint = paint(36, true, COR_PRIMARIA_ESCURA)
        val totalValuePaint = paint(54, true, COR_PRIMARIA_ESCURA)
        val boxHeight = PAD_CAIXA * 2 + lineHeight(totalValuePaint)
        canvas?.drawRoundRect(RectF(left, y, right, y + boxHeight), 24f, 24f, fill(COR_DESTAQUE))
        val totalBaseline = baselineOf(totalValuePaint, y + PAD_CAIXA)
        drawText("TOTAL A PAGAR", totalLabelPaint, left + 32f, totalBaseline)
        drawText(valor(linhas.sumOf { it.valorCentavos }), totalValuePaint, right - 32f, totalBaseline, Paint.Align.RIGHT)
        y += boxHeight

        // --- Pagamento (só aparece se houver algo a informar) ---
        val pix = cobranca.chavePix?.takeIf { it.isNotBlank() }
        val sindica = cobranca.nomeSindica?.takeIf { it.isNotBlank() }
        val instrucoes = cobranca.instrucoesPagamento?.takeIf { it.isNotBlank() }
        if (pix != null || sindica != null || instrucoes != null) {
            y += 40f
            section("PAGAMENTO")
            val smallPaint = paint(30, false, COR_SECUNDARIA)
            pix?.let {
                y += 16f
                paragraph(listOf("Chave PIX"), smallPaint)
                val pixPaint = paint(38, true, COR_TEXTO)
                paragraph(wrapText(it, pixPaint, contentWidth), pixPaint)
            }
            instrucoes?.let {
                y += 16f
                val instrPaint = paint(32, false, COR_TEXTO)
                paragraph(wrapText(it, instrPaint, contentWidth), instrPaint)
            }
            sindica?.let {
                y += 16f
                paragraph(wrapText("Síndica: $it", smallPaint, contentWidth), smallPaint)
            }
        }

        y += MARGEM
        return ceil(y).toInt()
    }

    private fun paint(size: Int, bold: Boolean, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size.toFloat()
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    /**
     * Quebra o texto em linhas que cabem em [maxWidth], medindo com o mesmo [paint] usado no desenho
     * (inclusive negrito). Respeita quebras de linha e divide palavras maiores que a largura,
     * como chaves PIX aleatórias, que não têm espaços.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var current = ""
            paragraph.trim().split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val attempt = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(attempt) <= maxWidth) {
                    current = attempt
                } else {
                    if (current.isNotEmpty()) result += current
                    var rest = word
                    while (paint.measureText(rest) > maxWidth) {
                        val count = paint.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
                        result += rest.substring(0, count)
                        rest = rest.substring(count)
                    }
                    current = rest
                }
            }
            result += current
        }
        return result
    }

    private fun referenciaPorExtenso(mes: Int, ano: Int): String {
        val nome = MESES.getOrNull(mes - 1) ?: return "%02d/%d".format(mes, ano)
        return "$nome de $ano"
    }

    private fun valor(centavos: Long): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(BigDecimal.valueOf(centavos).movePointLeft(2))

    private fun formatarData(data: String): String {
        val partes = data.trim().split('/', '-')
        return when {
            partes.size == 3 && partes[0].length == 4 -> "%02d/%02d/%04d".format(partes[2].toIntOrNull() ?: return data, partes[1].toIntOrNull() ?: return data, partes[0].toIntOrNull() ?: return data)
            partes.size == 3 -> "%02d/%02d/%04d".format(partes[0].toIntOrNull() ?: return data, partes[1].toIntOrNull() ?: return data, partes[2].toIntOrNull() ?: return data)
            else -> data
        }
    }

    private companion object {
        const val LARGURA = 1080
        const val MARGEM = 64f
        const val ESPACO_LINHA = 10f
        const val ESPACO_COLUNAS = 32f
        const val PAD_CABECALHO = 48f
        const val PAD_LINHA = 18f
        const val PAD_CAIXA = 30f
        val MESES = listOf(
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
        )

        // Mesma paleta do tema do aplicativo.
        val COR_PRIMARIA = Color.rgb(15, 107, 99)
        val COR_PRIMARIA_ESCURA = Color.rgb(8, 64, 59)
        val COR_DESTAQUE = Color.rgb(213, 240, 236)
        val COR_CABECALHO_SUB = Color.rgb(213, 240, 236)
        val COR_TEXTO = Color.rgb(24, 32, 31)
        val COR_SECUNDARIA = Color.rgb(75, 88, 86)
        val COR_DIVISOR = Color.rgb(208, 218, 216)
    }
}
