package br.com.condominio.cobranca.imagem

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import br.com.condominio.cobranca.dados.Cobranca
import br.com.condominio.cobranca.dados.CobrancaLinha
import br.com.condominio.cobranca.dados.CobrancaUnidade
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Cria a imagem da cobrança usando somente os dados congelados no fechamento. */
class GeradorDeImagem {
    fun gerar(
        context: Context,
        cobranca: Cobranca,
        unidade: CobrancaUnidade,
        linhas: List<CobrancaLinha>,
    ): File {
        val conteudo = montarLinhas(cobranca, unidade, linhas)
        val altura = (MARGEM * 2 + conteudo.sumOf { it.altura } + ESPACO_ENTRE_SECOES * 4).coerceAtLeast(900)
        val bitmap = Bitmap.createBitmap(LARGURA, altura, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COR_TEXTO }
        var y = MARGEM.toFloat()
        conteudo.forEach { bloco ->
            paint.textSize = bloco.tamanho.toFloat()
            paint.typeface = if (bloco.destaque) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            bloco.textos.forEach { texto ->
                y += bloco.tamanho
                canvas.drawText(texto, MARGEM.toFloat(), y, paint)
                y += ENTRE_LINHAS
            }
            y += bloco.espacoDepois
        }
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

    private fun montarLinhas(cobranca: Cobranca, unidade: CobrancaUnidade, linhas: List<CobrancaLinha>): List<Bloco> {
        val blocos = mutableListOf<Bloco>()
        cobranca.nomeCondominio?.takeIf { it.isNotBlank() }?.let { blocos += bloco(it, 52, true, 18) }
        blocos += bloco("Unidade ${unidade.numeroUnidade} — ${unidade.nomeMorador.ifBlank { "Sem morador" }}", 44, true, 14)
        blocos += bloco("Referência: ${"%02d".format(cobranca.mes)}/${cobranca.ano}", 34, false, 4)
        blocos += bloco("Vencimento: ${formatarData(cobranca.vencimento)}", 34, false, 24)
        linhas.forEach { linha ->
            blocos += bloco(quebrar(linha.descricao, 36), 36, false, 2)
            blocos += bloco(valor(linha.valorCentavos), 36, false, 12)
        }
        blocos += bloco("TOTAL A PAGAR: ${valor(linhas.sumOf { it.valorCentavos })}", 46, true, 24)
        cobranca.nomeSindica?.takeIf { it.isNotBlank() }?.let { blocos += bloco("Síndica: $it", 34, false, 8) }
        cobranca.chavePix?.takeIf { it.isNotBlank() }?.let { blocos += bloco(quebrar("PIX: $it", 34), 34, true, 8) }
        cobranca.instrucoesPagamento?.takeIf { it.isNotBlank() }?.let { blocos += bloco(quebrar(it, 32), 32, false, 0) }
        return blocos
    }

    private fun bloco(texto: String, tamanho: Int, destaque: Boolean, espacoDepois: Int) = bloco(listOf(texto), tamanho, destaque, espacoDepois)
    private fun bloco(textos: List<String>, tamanho: Int, destaque: Boolean, espacoDepois: Int) = Bloco(textos, tamanho, destaque, espacoDepois, textos.size * (tamanho + ENTRE_LINHAS))

    private fun quebrar(texto: String, tamanho: Int): List<String> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = tamanho.toFloat() }
        val resultado = mutableListOf<String>()
        var restante = texto.trim()
        while (restante.isNotEmpty()) {
            val quantidade = paint.breakText(restante, true, LARGURA - MARGEM * 2.toFloat(), null)
            if (quantidade >= restante.length) {
                resultado += restante
                break
            }
            val corte = restante.lastIndexOf(' ', quantidade).takeIf { it > 0 } ?: quantidade
            resultado += restante.substring(0, corte).trim()
            restante = restante.substring(corte).trim()
        }
        return resultado
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

    private data class Bloco(val textos: List<String>, val tamanho: Int, val destaque: Boolean, val espacoDepois: Int, val altura: Int)

    private companion object {
        const val LARGURA = 1080
        const val MARGEM = 64
        const val ENTRE_LINHAS = 12
        const val ESPACO_ENTRE_SECOES = 12
        val COR_TEXTO = Color.rgb(30, 30, 30)
    }
}
